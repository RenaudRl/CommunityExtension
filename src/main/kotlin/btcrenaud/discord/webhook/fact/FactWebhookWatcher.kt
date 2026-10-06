package btcrenaud.discord.webhook.fact

import btcrenaud.discord.webhook.WebhookEmbed
import btcrenaud.discord.webhook.WebhookEmbedField
import btcrenaud.discord.webhook.WebhookService
import btcrenaud.discord.webhook.fact.entries.FactWebhookScope
import btcrenaud.discord.webhook.fact.entries.WebhookFactEventEntry
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.interaction.context as interactionContext
import com.typewritermc.engine.paper.entry.entries.GroupId
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.facts.FactListenerSubscription
import com.typewritermc.engine.paper.facts.FactUpdateContext
import com.typewritermc.engine.paper.facts.listenForFacts
import com.typewritermc.engine.paper.facts.stopListening
import com.typewritermc.engine.paper.entry.inAudience
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.server
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.core.utils.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** Owns the player subscriptions for all configured [WebhookFactEventEntry] entries. */
class FactWebhookWatcher(
    private val webhookService: WebhookService,
) : Listener {

    private val subscriptions = ConcurrentHashMap<UUID, FactListenerSubscription>()
    private val lastValues = ConcurrentHashMap<String, Int>()
    private val triggerThrottle = TriggerThrottle()
    private var entries: List<WebhookFactEventEntry> = emptyList()
    private var facts: List<Ref<ReadableFactEntry>> = emptyList()
    private var startupJob: Job? = null

    fun initialize() {
        entries = Query.find<WebhookFactEventEntry>()
            .filter { it.enabled && it.fact.id.isNotBlank() && it.destination.id.isNotBlank() }
            .toList()
        facts = entries.map { it.fact }.distinct()
        if (facts.isEmpty()) return

        plugin.server.pluginManager.registerEvents(this, plugin)
        startupJob = Dispatchers.UntickedAsync.launch {
            delay(1.seconds)
            server.onlinePlayers.forEach { watch(it) }
        }
        logger.info("Initialized ${entries.size} fact webhook event(s)")
    }

    private fun watch(player: Player) {
        runCatching {
            subscriptions.remove(player.uniqueId)?.cancel(player)
            subscriptions[player.uniqueId] = player.listenForFacts(facts) { onFactChange(this) }
        }.onFailure {
            logger.warning("Could not watch facts for ${player.name}: ${it.message}")
        }
    }

    private fun onFactChange(context: FactUpdateContext) {
        val player = context.player
        entries.asSequence()
            .filter { it.fact == context.ref }
            .filter { entry -> entry.previousValue.all { it.matches(context.oldValue) } }
            .filter { entry -> entry.newValue.all { it.matches(context.newValue) } }
            .filter { entry -> entry.audience.id.isBlank() || player.inAudience(entry.audience) }
            .forEach { entry ->
                val groupId = context.ref.get()?.identifier(player)?.groupId?.id.orEmpty()
                if (!shouldPublish(entry, context, groupId)) return@forEach
                // Chained entries run only for a publication that was actually handed to Discord,
                // for the player whose update was published. The throttle stops a chained action
                // that rewrites the watched fact from re-firing the event in a loop.
                if (publish(entry, context, groupId) && acquireTriggers(entry, player)) {
                    entry.triggers.triggerEntriesFor(player, interactionContext())
                }
            }
    }

    private fun acquireTriggers(entry: WebhookFactEventEntry, player: Player): Boolean {
        if (entry.triggers.isEmpty()) return false
        return triggerThrottle.tryAcquire(
            entryId = entry.id,
            playerId = player.uniqueId,
            nowMillis = System.currentTimeMillis(),
            cooldownMillis = entry.triggerCooldownSeconds * MILLIS_PER_SECOND,
        )
    }

    private fun shouldPublish(
        entry: WebhookFactEventEntry,
        context: FactUpdateContext,
        groupId: String,
    ): Boolean {
        if (entry.scope == FactWebhookScope.PLAYER) return true

        val scopeId = when (entry.scope) {
            FactWebhookScope.PLAYER -> context.player.uniqueId.toString()
            FactWebhookScope.GROUP -> groupId.ifBlank { context.player.uniqueId.toString() }
            FactWebhookScope.GLOBAL -> "global"
        }
        val key = "${entry.id}|${context.ref.id}|${entry.scope}|$scopeId"
        val previous = lastValues.put(key, context.newValue)
        // All members of a Typewriter group observe the same transition. Only the first callback
        // publishes; a later real transition starts from the last value stored here.
        return previous == null || previous == context.oldValue
    }

    /** Sends the webhook; false when the templates rendered nothing to send. */
    private fun publish(entry: WebhookFactEventEntry, context: FactUpdateContext, groupId: String): Boolean {
        val fact = context.ref.get()
        val groupPlayers = if (groupId.isBlank()) {
            ""
        } else {
            fact?.group?.get()?.group(GroupId(groupId))?.players
                ?.joinToString(", ") { it.name }
                .orEmpty()
        }
        val placeholders = mapOf(
            "player" to context.player.name,
            "player_uuid" to context.player.uniqueId.toString(),
            "fact" to context.ref.id,
            "fact_name" to (fact?.name ?: context.ref.id),
            "previous_value" to context.oldValue.toString(),
            "new_value" to context.newValue.toString(),
            "change" to (context.newValue - context.oldValue).toString(),
            "scope" to entry.scope.name.lowercase(),
            "group" to groupId,
            "players" to groupPlayers,
            "timestamp" to Instant.now().toString(),
        )

        val content = entry.contentTemplate.fill(placeholders)
        val embed = entry.embed.takeIf { it.enabled }?.let { template ->
            WebhookEmbed(
                title = template.titleTemplate.fill(placeholders).takeIf { it.isNotBlank() },
                description = template.descriptionTemplate.fill(placeholders).takeIf { it.isNotBlank() },
                color = template.color.toColorInt(),
                fields = template.fields.map {
                    WebhookEmbedField(it.name, it.value.fill(placeholders), it.inline)
                },
            )
        }
        if (content.isBlank() && embed == null) return false

        webhookService.send(
            destination = entry.destination,
            content = content,
            embeds = listOfNotNull(embed),
            threadName = entry.threadNameTemplate.fill(placeholders).takeIf { it.isNotBlank() },
            pingRoleIds = entry.pingRoleIds,
        )
        return true
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (facts.isNotEmpty()) watch(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        subscriptions.remove(event.player.uniqueId)?.cancel(event.player)
        triggerThrottle.forget(event.player.uniqueId)
    }

    fun shutdown() {
        startupJob?.cancel()
        startupJob = null
        HandlerList.unregisterAll(this)
        subscriptions.forEach { (uuid, subscription) ->
            server.getPlayer(uuid)?.stopListening(subscription)
        }
        subscriptions.clear()
        lastValues.clear()
        triggerThrottle.clear()
        entries = emptyList()
        facts = emptyList()
    }

    private fun String.fill(values: Map<String, String>): String =
        "\\{([^}]+)}".toRegex().replace(this) { values[it.groupValues[1]] ?: "" }

    private fun String.toColorInt(): Int? = trim().removePrefix("#").toIntOrNull(16)

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
