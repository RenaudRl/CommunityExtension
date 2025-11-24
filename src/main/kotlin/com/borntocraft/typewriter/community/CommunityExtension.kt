package com.borntocraft.typewriter.community

import com.borntocraft.typewriter.community.bugreport.data.BugReportMessages
import com.borntocraft.typewriter.community.bugreport.data.defaultCategories
import com.borntocraft.typewriter.community.bugreport.entries.BugReportManifestEntry
import com.borntocraft.typewriter.community.bugreport.service.BugReportDialogService
import com.borntocraft.typewriter.community.bugreport.service.BugReportRepository
import com.borntocraft.typewriter.community.bugreport.service.BugReportWebhookService
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
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Main entry point for the Community Extension.
 * Initializes shared services for Discord Linking and Bug Reporting.
 */
@Singleton
object CommunityExtension : Initializable {

    lateinit var bugReportRepository: BugReportRepository
    lateinit var discordLinkRepository: DiscordLinkRepository
    lateinit var discordLinkService: DiscordLinkService
    private val webhookSender = WebhookSender()
    private val bugReportWebhookService = BugReportWebhookService(webhookSender)
    val discordClientService = DiscordClientService()

    override suspend fun initialize() {
        // Defensive disconnect in case of hot-reload to avoid duplicated JDA sessions
        discordClientService.disconnect()

        val bugManifest = Query.firstWhere<BugReportManifestEntry> { true }
        val linkManifest = Query.firstWhere<DiscordLinkManifestEntry> { true }

        bugReportRepository = BugReportRepository(bugManifest?.sequenceStorage?.get())
        BugReportDialogService.repository = bugReportRepository
        BugReportDialogService.categories = bugManifest?.categories ?: emptyList()
        BugReportDialogService.defaultStatusId = "open"
        BugReportDialogService.serverName = bugManifest?.serverName ?: "Server"
        BugReportDialogService.selectMenuTitle = bugManifest?.selectMenuTitle ?: "Select Category"
        BugReportDialogService.messages = bugManifest?.messages ?: BugReportMessages()
        BugReportDialogService.webhookSettings = bugManifest?.webhook
        BugReportDialogService.webhookService = bugReportWebhookService

        if (linkManifest != null) {
            val linkStorage = linkManifest.storage?.get()
            discordLinkRepository = DiscordLinkRepository(linkStorage)
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
                    val accepted = discordLinkService.verifyCode(content, event.author.id, event.author.asTag)
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

        CommandRegistry.register("Report") { sender, _ ->
            if (sender is org.bukkit.entity.Player) {
                BugReportDialogService.openCategorySelection(sender)
            }
        }

        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun onJoin(event: PlayerJoinEvent) {
                if (discordClientService.isReady()) {
                    discordLinkService.syncRoles(event.player)
                }
            }
        }, org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(this::class.java))

        logger.info("CommunityExtension initialized")
    }

    override suspend fun shutdown() {
        discordClientService.disconnect()
        logger.info("CommunityExtension stopped")
    }

}
