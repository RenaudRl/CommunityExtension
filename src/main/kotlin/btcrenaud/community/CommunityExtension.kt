package btcrenaud.community

import btcrenaud.community.bugreport.entries.BugReportManifestEntry
import btcrenaud.community.bugreport.service.BugReportRepository
import btcrenaud.community.bugreport.service.BugReportService
import btcrenaud.community.bugreport.service.BugReportWebhookService
import btcrenaud.community.bugreport.service.DiscordBugReportBinding
import btcrenaud.community.bugreport.service.DiscordBugReportCommands
import btcrenaud.community.chat.entries.ChatSyncManifestEntry
import btcrenaud.community.chat.service.ChatSyncService
import btcrenaud.community.console.entries.ConsoleChannelEntry
import btcrenaud.community.console.service.ConsoleService
import btcrenaud.community.discord.entries.DiscordLinkManifestEntry
import btcrenaud.community.discord.service.DiscordClientService
import btcrenaud.community.discord.service.DiscordLinkRepository
import btcrenaud.community.discord.service.DiscordLinkService
import btcrenaud.community.webhook.WebhookSender
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.server
import lirand.api.extensions.server.registerEvents
import net.dv8tion.jda.api.entities.emoji.Emoji
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Main entry point for the Community Extension.
 * Wires the Discord link, bug report, chat sync and console services from
 * their manifest entries. Commands are registered by the engine through
 * [com.typewritermc.engine.paper.entry.entries.CustomCommandEntry] on the
 * manifests themselves.
 */
@Singleton
object CommunityExtension : Initializable, Listener {

    private val webhookSender = WebhookSender()
    private val bugReportWebhookService = BugReportWebhookService(webhookSender)
    private val discordClientService = DiscordClientService()

    private val bugReportServices = ConcurrentHashMap<String, BugReportService>()
    @Volatile
    private var discordLinkService: DiscordLinkService? = null
    private val chatSyncServices = mutableListOf<ChatSyncService>()
    private val consoleServices = mutableListOf<ConsoleService>()
    private val discordCommandListeners = mutableListOf<DiscordBugReportCommands>()

    /** Service bound to the bug report manifest with the given entry id, if initialized. */
    fun bugReportSystem(manifestId: String): BugReportService? = bugReportServices[manifestId]

    /** Discord link service, or null when no manifest is configured. */
    fun discordLinkService(): DiscordLinkService? = discordLinkService

    override suspend fun initialize() {
        // Defensive cleanup so a reload never stacks listeners or bot sessions.
        shutdown()

        initializeDiscordLink()
        initializeBugReports()
        initializeChatSync()
        initializeConsoleChannels()
        initializeDiscordBridges()

        plugin.registerEvents(this)
        logger.info("CommunityExtension initialized")
    }

    /**
     * Wires the features that need a connected bot: Discord→Minecraft chat
     * relay and bug report slash commands.
     */
    private fun initializeDiscordBridges() {
        if (!discordClientService.isReady()) return

        // Discord -> Minecraft chat relay.
        Query.find<ChatSyncManifestEntry>().filter { it.enabled }.forEach { manifest ->
            manifest.channels
                .filter { it.enabled && it.discordToMinecraft && it.discordChannelIds.isNotEmpty() }
                .forEach { channel ->
                    channel.discordChannelIds.forEach { channelId ->
                        discordClientService.onMessageInChannel(channelId) { event ->
                            if (event.author.isBot || event.isWebhookMessage) return@onMessageInChannel
                            val content = event.message.contentRaw.trim()
                            if (content.isEmpty()) return@onMessageInChannel
                            val formatted = channel.minecraftFormat
                                .replace("{author}", event.author.effectiveName)
                                .replace("{message}", content)
                                .asMini()
                            server.onlinePlayers
                                .filter { channel.permission.isBlank() || it.hasPermission(channel.permission) }
                                .forEach { it.sendMessage(formatted) }
                        }
                    }
                }
        }

        // Bug report slash commands.
        val bindings = Query.find<BugReportManifestEntry>()
            .filter { it.discordCommands }
            .map { manifest ->
                DiscordBugReportBinding(
                    commandName = manifest.commandName.ifBlank { "bugreport" }.lowercase(),
                    manifestId = manifest.id,
                    adminRoleIds = manifest.discordAdminRoleIds,
                )
            }
            .toList()
        if (bindings.isEmpty()) return
        val guild = discordClientService.getGuild()
        if (guild == null) {
            logger.warning("Cannot register Discord bug report commands: guild not found.")
            return
        }
        val commands = DiscordBugReportCommands(bindings)
        commands.register(guild)
        discordClientService.addEventListener(commands)
        discordCommandListeners.add(commands)
    }

    private suspend fun initializeDiscordLink() {
        val manifest = Query.firstWhere<DiscordLinkManifestEntry> { true }
        if (manifest == null) {
            logger.warning("discord_link_manifest not found; Discord link is disabled.")
            return
        }

        val repository = DiscordLinkRepository(manifest.storage.get())
        repository.load()
        val service = DiscordLinkService(repository, manifest, discordClientService, webhookSender)
        discordLinkService = service

        if (!manifest.bot.enabled) {
            logger.warning("Discord bot is disabled in discord_link_manifest; code verification and role sync will not run.")
            return
        }

        discordClientService.connect(manifest.bot)
        if (manifest.verificationChannelId.isBlank()) return

        discordClientService.onMessageInChannel(manifest.verificationChannelId) { event ->
            if (event.author.isBot || event.isWebhookMessage) return@onMessageInChannel
            val content = event.message.contentRaw.trim()
            val expectedLength = manifest.codeLength.coerceAtLeast(1)
            if (content.length != expectedLength || !content.all { it.isLetterOrDigit() }) {
                return@onMessageInChannel
            }
            val accepted = service.verifyCode(content, event.author.id, event.author.name)
            val emoji = if (accepted) "✅" else "❌"
            event.message.addReaction(Emoji.fromUnicode(emoji)).queue()
            val reply = (if (accepted) manifest.bot.messages.success else manifest.bot.messages.failure)
                .replace("{player}", event.author.name)
                .replace("{code}", content)
            if (accepted) {
                event.message.delete().queue()
            }
            if (reply.isNotBlank()) {
                event.channel.sendMessage(reply).queue { msg ->
                    msg.delete().queueAfter(5, TimeUnit.SECONDS)
                }
            }
        }
    }

    private suspend fun initializeBugReports() {
        Query.find<BugReportManifestEntry>().forEach { manifest ->
            if (manifest.commandName.isBlank()) {
                logger.warning("Bug report manifest '${manifest.name}' has no command name; its command falls back to /bugreport.")
            }
            val repository = BugReportRepository(manifest.sequenceStorage.get())
            repository.load()
            bugReportServices[manifest.id] = BugReportService(manifest, repository, bugReportWebhookService)
            logger.info("Initialized bug report system '${manifest.name}' (/${manifest.commandName.ifBlank { "bugreport" }})")
        }
    }

    private fun initializeChatSync() {
        Query.find<ChatSyncManifestEntry>().forEach { manifest ->
            if (!manifest.enabled) {
                logger.info("Chat sync manifest '${manifest.name}' is disabled, skipping")
                return@forEach
            }
            val chatService = ChatSyncService(manifest, webhookSender, discordClientService)
            chatSyncServices.add(chatService)
            plugin.registerEvents(chatService)
            logger.info("Initialized chat sync for manifest '${manifest.name}'")
        }
    }

    private fun initializeConsoleChannels() {
        Query.find<ConsoleChannelEntry>().forEach { manifest ->
            if (!manifest.enabled) {
                logger.info("Console channel '${manifest.name}' is disabled, skipping")
                return@forEach
            }
            val consoleService = ConsoleService(manifest)
            consoleServices.add(consoleService)
            discordClientService.addEventListener(consoleService)
            logger.info("Initialized console channel for manifest '${manifest.name}'")
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val service = discordLinkService ?: return
        if (discordClientService.isReady()) {
            service.syncRoles(event.player)
        }
    }

    override suspend fun shutdown() {
        HandlerList.unregisterAll(this)

        chatSyncServices.forEach { HandlerList.unregisterAll(it) }
        chatSyncServices.clear()

        consoleServices.forEach { discordClientService.removeEventListener(it) }
        consoleServices.clear()

        discordCommandListeners.forEach { discordClientService.removeEventListener(it) }
        discordCommandListeners.clear()

        discordClientService.disconnect()
        discordLinkService = null
        bugReportServices.clear()

        logger.info("CommunityExtension stopped")
    }
}
