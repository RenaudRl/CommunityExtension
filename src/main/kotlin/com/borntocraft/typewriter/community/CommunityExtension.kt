package com.borntocraft.typewriter.community

import com.borntocraft.typewriter.community.bugreport.data.BugReportMessages
import com.borntocraft.typewriter.community.bugreport.data.defaultCategories
import com.borntocraft.typewriter.community.bugreport.entries.BugReportManifestEntry
import com.borntocraft.typewriter.community.bugreport.service.BugReportDialogService
import com.borntocraft.typewriter.community.bugreport.service.BugReportRepository
import com.borntocraft.typewriter.community.bugreport.service.BugReportWebhookService
import com.borntocraft.typewriter.community.chat.entries.ChatSyncManifestEntry
import com.borntocraft.typewriter.community.chat.service.ChatSyncService
import com.borntocraft.typewriter.community.console.entries.ConsoleChannelEntry
import com.borntocraft.typewriter.community.console.service.ConsoleService
import com.borntocraft.typewriter.community.discord.data.DiscordLinkMessages
import com.borntocraft.typewriter.community.discord.data.asReadable
import com.borntocraft.typewriter.community.discord.entries.DiscordLinkArtifactEntry
import com.borntocraft.typewriter.community.discord.entries.DiscordLinkManifestEntry
import com.borntocraft.typewriter.community.discord.service.DiscordClientService
import com.borntocraft.typewriter.community.discord.service.DiscordLinkRepository
import com.borntocraft.typewriter.community.discord.service.DiscordLinkService
import com.borntocraft.typewriter.community.webhook.WebhookSender
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.logger
import net.dv8tion.jda.api.entities.emoji.Emoji
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent


/**
 * Main entry point for the Community Extension.
 * Initializes shared services for Discord Linking and Bug Reporting.
 */
@Singleton
object CommunityExtension : Initializable {

    private val plugin = Bukkit.getPluginManager().getPlugin("TypeWriter")
        ?: error("TypeWriter plugin is required")
    private val webhookSender = WebhookSender()
    private val bugReportWebhookService = BugReportWebhookService(webhookSender)
    val discordClientService = DiscordClientService()
    
    private lateinit var bugReportRepository: BugReportRepository
    private lateinit var discordLinkRepository: DiscordLinkRepository
    private lateinit var discordLinkService: DiscordLinkService
    private var eventListener: Listener? = null
    private val chatSyncServices = mutableListOf<ChatSyncService>()
    private val consoleServices = mutableListOf<ConsoleService>()

    override suspend fun initialize() {
        // Clean up previous state
        shutdown()
        
        discordClientService.disconnect()

        val bugManifests = Query.find<BugReportManifestEntry>()
        val linkManifest = Query.firstWhere<DiscordLinkManifestEntry> { true }

        if (linkManifest != null) {
            val linkStorage = linkManifest.storage?.get()
            discordLinkRepository = DiscordLinkRepository(linkStorage)
            // Ensure the artifact storage file exists
            discordLinkRepository.ensureInitialized()
            discordLinkService = DiscordLinkService(
                discordLinkRepository,
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
                    val accepted = discordLinkService.verifyCode(content, event.author.id, event.author.getAsTag())
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
                val code = discordLinkService.generateCode(sender)
                if (code.isBlank()) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text("No link code available, try again."))
                    return@register
                }
                val messages: DiscordLinkMessages = linkManifest?.messages ?: DiscordLinkMessages()
                val duration = (linkManifest?.codeValidity ?: java.time.Duration.ofMinutes(10)).asReadable()
                val feedback = messages.codeGenerated
                    .replace("{code}", code)
                    .replace("{duration}", duration)
                sender.sendMessage(net.kyori.adventure.text.Component.text(feedback))
            }
        }

        CommandRegistry.register("DiscordUnlink") { sender, _ ->
            if (sender is org.bukkit.entity.Player) {
                val messages: DiscordLinkMessages = linkManifest?.messages ?: DiscordLinkMessages()
                val success = discordLinkService.unlinkPlayer(sender)
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
            
            CommandRegistry.register(manifest.commandName) { sender, _ ->
                if (sender is org.bukkit.entity.Player) {
                    openBugReportDialog(sender, manifest)
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
                if (discordClientService.isReady()) {
                    discordLinkService.syncRoles(event.player)
                }
            }
        }
        eventListener = listener
        Bukkit.getPluginManager().registerEvents(listener, plugin)

        logger.info("CommunityExtension initialized")
    }

    private fun openBugReportDialog(player: org.bukkit.entity.Player, manifest: BugReportManifestEntry) {
        // Create isolated repository for this manifest
        val repository = BugReportRepository(manifest.sequenceStorage?.get())
        
        // Configure dialog service with manifest-specific settings
        BugReportDialogService.repository = repository
        BugReportDialogService.categories = manifest.categories
        BugReportDialogService.defaultStatusId = "open"
        BugReportDialogService.serverName = manifest.serverName
        BugReportDialogService.selectMenuTitle = manifest.selectMenuTitle
        BugReportDialogService.submitButtonText = manifest.submitButtonText
        BugReportDialogService.categoryLayoutLines = manifest.categoryLayoutLines
        BugReportDialogService.messages = manifest.messages
        BugReportDialogService.webhookSettings = manifest.webhook
        BugReportDialogService.webhookService = bugReportWebhookService
        
        // Open the dialog
        BugReportDialogService.openCategorySelection(player)
    }

    override suspend fun shutdown() {
        // Unregister commands
        CommandRegistry.unregisterAll()
        
        // Unregister event listeners
        eventListener?.let { HandlerList.unregisterAll(it) }
        eventListener = null
        
        // Unregister chat sync services
        chatSyncServices.forEach { service ->
            HandlerList.unregisterAll(service)
        }
        chatSyncServices.clear()
        
        // Unregister console services
        consoleServices.forEach { service ->
            discordClientService.removeEventListener(service)
        }
        consoleServices.clear()
        
        // Disconnect Discord client
        discordClientService.disconnect()
        
        // Reset dialog service state
        BugReportDialogService.reset()
        
        logger.info("CommunityExtension stopped")
    }

}
