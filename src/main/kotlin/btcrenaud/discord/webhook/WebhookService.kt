package btcrenaud.discord.webhook

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.logger
import org.koin.java.KoinJavaComponent

/**
 * The single send point towards [WebhookDefinitionEntry] destinations.
 *
 * The HTTP client used to live in a `WebhookSender` built by hand in the extension's initializer
 * and handed to every service as a parameter: nothing outside could deliver a message without
 * being given that instance. The service is now a Koin singleton, injectable by any extension —
 * which is what lets a corpse, a dungeon or a shop publish without duplicating the client nor
 * the fallback logic.
 */
@Singleton
class WebhookService {

    private val sender = WebhookSender()

    /**
     * Delivers to the referenced destination.
     *
     * An empty, missing or disabled reference is NOT an error: that is the normal way of
     * publishing nothing. Only a reference naming an entry that does not exist is reported,
     * because it betrays a page pointing at a deleted destination.
     *
     * @return true if a message was sent.
     */
    fun send(
        destination: Ref<WebhookDefinitionEntry>,
        content: String,
        embeds: List<WebhookEmbed> = emptyList(),
        threadName: String? = null,
        appliedTagIds: List<String> = emptyList(),
        pingRoleIds: List<String> = emptyList(),
    ): Boolean {
        if (destination.id.isBlank()) return false
        val entry = destination.get()
        if (entry == null) {
            logger.warning("[Webhook] Destination '${destination.id}' not found; message dropped.")
            return false
        }
        if (!entry.isUsable) return false

        // The destination's roles and the caller's add up: the destination carries the permanent
        // mentions, the caller the ones that depend on the event.
        sender.send(
            entry = entry,
            content = content,
            embeds = embeds,
            threadName = threadName,
            appliedTagIds = appliedTagIds,
            pingRoleIds = (entry.pingRoleIds + pingRoleIds).distinct(),
        )
        return true
    }

    /**
     * Same delivery, for a caller holding a raw id.
     *
     * An extension that must not depend on this one — Shops announcing a sale, for instance —
     * cannot type a `Ref<WebhookDefinitionEntry>`. It carries the id the author wrote and this
     * overload turns it into a reference.
     */
    fun send(
        destinationId: String,
        content: String,
        embeds: List<WebhookEmbed> = emptyList(),
        threadName: String? = null,
        appliedTagIds: List<String> = emptyList(),
        pingRoleIds: List<String> = emptyList(),
    ): Boolean = send(
        Ref(destinationId, WebhookDefinitionEntry::class),
        content,
        embeds,
        threadName,
        appliedTagIds,
        pingRoleIds,
    )

    /** True when [destination] names an entry able to receive a message. */
    fun isUsable(destination: Ref<WebhookDefinitionEntry>): Boolean =
        destination.id.isNotBlank() && destination.get()?.isUsable == true

    companion object {
        /**
         * Fetches the service outside injection, for callers the container does not build (Bukkit
         * listeners, commands). Returns null while the extension is not initialized, rather than
         * throwing as a class loads.
         */
        fun getOrNull(): WebhookService? =
            runCatching { KoinJavaComponent.get<WebhookService>(WebhookService::class.java) }.getOrNull()
    }
}
