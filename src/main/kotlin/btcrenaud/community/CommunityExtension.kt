package btcrenaud.community

import btcrenaud.community.bugreport.entries.BugReportManifestEntry
import btcrenaud.community.bugreport.service.BugReportDialogService
import btcrenaud.community.bugreport.service.BugReportRepository
import btcrenaud.community.bugreport.service.BugReportWebhookService
import btcrenaud.community.chat.entries.ChatSyncManifestEntry
import btcrenaud.community.chat.service.ChatSyncService
import btcrenaud.community.console.entries.ConsoleChannelEntry
import btcrenaud.community.console.service.ConsoleService
import btcrenaud.community.discord.data.asReadable
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
import net.dv8tion.jda.api.entities.emoji.Emoji
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

@Singleton
class CommunityExtension : Initializable {

    private val webhookSender = WebhookSender()
    private val bugReportWebhookService = BugReportWebhookService(webhookSender)
    private val discordClientService = DiscordClientService()

    private var bugReportDialogService: BugReportDialogService? = null
    private var discordLinkRepository: DiscordLinkRepository? = null
    private var discordLinkService: DiscordLinkService? = null
    private var eventListener: Listener? = null
    private val chatSyncServices = mutableListOf<ChatSyncService>()
    private val consoleServices = mutableListOf<ConsoleService>()

    override suspend fun initialize() {
        shutdown()

        discordClientService.disconnect()

        val bugManifests = Query.find<BugReportManifestEntry>()
        val linkManifest = Query.firstWhere<DiscordLinkManifestEntry> { true }

        // Initialize bug report dialog service with default settings
        bugReportDialogService = BugReportDialogService(
            defaultRepository = BugReportRepository(null), // Will be set per-manifest
            defaultCategories = emptyList(),
            defaultMessages = btcrenaud.community.bugreport.data.BugReportMessages(),
            defaultWebhookService = bugReportWebhookService,
        )

        if (linkManifest != null) {
            val linkStorage = linkManifest.storage.get()
            discordLinkRepository = DiscordLinkRepository(linkStorage)
            discordLinkRepository?.ensureInitialized()
            discordLinkService = DiscordLinkService(
                discordLinkRepository!!,
                linkManifest,
                discordClientService,
                webhookSender,
            )

            if (linkManifest.bot.enabled) {
                discordClientService.connect(linkManifest.bot)
                if (linkManifest.verificationChannelId.isNotBlank()) {
                    discordClientService.onMessageInChannel(linkManifest.verificationChannelId) { event ->
                        if (event.author.isBot || event.isWebhookMessage) return@onMessageInChannel
                        val content = event.message.contentRaw.trim()
                        val expectedLength = linkManifest.codeLength.coerceAtLeast(1)
                        if (content.length != expectedLength || !content.all { it.isLetterOrDigit() }) {
                            return@onMessageInChannel
                        }
                        val service = discordLinkService ?: return@onMessageInChannel
                        val accepted = service.verifyCode(content, event.author.id, event.author.name)
                        val emoji = if (accepted) "✅" else "❌"
                        event.message.addReaction(Emoji.fromUnicode(emoji)).queue()
                        if (accepted) {
                            event.message.delete().queue()
                            val reply = linkManifest.bot.messages.success
                                .replace("{player}", event.author.name)
                                .replace("{code}", content)
                            if (reply.isNotBlank()) {
                                event.channel.sendMessage(reply).queue { msg ->
                                    msg.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS)
                                }
                            }
                        } else {
                            val reply = linkManifest.bot.messages.failure
                                .replace("{player}", event.author.name)
                                .replace("{code}", content)
                            if (reply.isNotBlank()) {
                                event.channel.sendMessage(reply).queue { msg ->
                                    msg.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS)
                                }
                            }
                        }
                    }
                }
            } else {
                logger.warning("Discord bot is disabled in discord_link_manifest; role sync will not run.")
            }
        } else {
            logger.warning("discord_link_manifest not found; Discord link is disabled.")
        }

        // Unregister old commands before registering new ones
        CommandRegistry.unregisterAll()

        CommandRegistry.register("DiscordLink") { sender, _ ->
            if (sender is org.bukkit.entity.Player) {
                val service = discordLinkService ?: return@register
                val code = service.generateCode(sender)
                if (code.isBlank()) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text("No link code available, try again."))
                    return@register
                }
                val manifest = linkManifest ?: return@register
                val duration = manifest.codeValidity.asReadable()
                val feedback = manifest.messages.codeGenerated
                    .replace("{code}", code)
                    .replace("{duration}", duration)
                sender.sendMessage(net.kyori.adventure.text.Component.text(feedback))
            }
        }

        CommandRegistry.register("DiscordUnlink") { sender, _ ->
            if (sender is org.bukkit.entity.Player) {
                val service = discordLinkService ?: return@register
                val success = service.unlinkPlayer(sender)
                val messages = linkManifest?.messages ?: btcrenaud.community.discord.data.DiscordLinkMessages()
                if (success) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text(messages.unlinkSuccess))
                } else {
                    sender.sendMessage(net.kyori.adventure.text.Component.text(messages.unlinkNoLink))
                }
            }
        }

        // Register bug report commands for each manifest
        bugManifests.forEach { manifest ->
            if (manifest.commandName.isBlank()) {
                logger.warning("Bug report manifest '${manifest.name}' has no command name, skipping")
                return@forEach
            }

            val repository = BugReportRepository(manifest.sequenceStorage.get())

            CommandRegistry.register(manifest.commandName) { sender, _ ->
                if (sender is org.bukkit.entity.Player) {
                    val dialogService = BugReportDialogService(
                        defaultRepository = repository,
                        defaultCategories = manifest.categories,
                        defaultMessages = manifest.messages,
                        defaultWebhookService = bugReportWebhookService,
                    )
                    dialogService.openCategorySelection(sender)
                }
            }
            logger.info("Registered bug report command: /${manifest.commandName} for manifest '${manifest.name}'")
        }

        // Initialize chat sync services
        val chatManifests = Query.find<ChatSyncManifestEntry>()
        chatManifests.forEach { manifest ->
            if (!manifest.enabled) {
                logger.info("Chat sync manifest '${manifest.name}' is disabled, skipping")
                return@forEach
            }

            val chatService = ChatSyncService(manifest, webhookSender)
            chatSyncServices.add(chatService)
            Bukkit.getPluginManager().registerEvents(chatService, plugin)
            logger.info("Initialized chat sync for manifest '${manifest.name}'")
        }

        // Initialize console services
        val consoleManifests = Query.find<ConsoleChannelEntry>()
        consoleManifests.forEach { manifest ->
            if (!manifest.enabled) {
                logger.info("Console channel '${manifest.name}' is disabled, skipping")
                return@forEach
            }

            val consoleService = ConsoleService(manifest)
            consoleServices.add(consoleService)
            discordClientService.addEventListener(consoleService)
            logger.info("Initialized console channel for manifest '${manifest.name}'")
        }

        // Unregister old listener if exists
        eventListener?.let { HandlerList.unregisterAll(it) }

        // Register new listener
        val listener = object : Listener {
            @EventHandler
            fun onJoin(event: PlayerJoinEvent) {
                val service = discordLinkService ?: return
                if (discordClientService.isReady()) {
                    service.syncRoles(event.player)
                }
            }
        }
        eventListener = listener
        Bukkit.getPluginManager().registerEvents(listener, plugin)

        logger.info("CommunityExtension initialized")
    }

    override suspend fun shutdown() {
        CommandRegistry.unregisterAll()

        eventListener?.let { HandlerList.unregisterAll(it) }
        eventListener = null

        chatSyncServices.forEach { service ->
            HandlerList.unregisterAll(service)
        }
        chatSyncServices.clear()

        consoleServices.forEach { service ->
            discordClientService.removeEventListener(service)
        }
        consoleServices.clear()

        discordClientService.disconnect()

        logger.info("CommunityExtension stopped")
    }
}
