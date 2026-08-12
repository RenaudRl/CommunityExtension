package btcrenaud.discord.chat.service

import btcrenaud.discord.chat.entries.ChatChannelConfig
import btcrenaud.discord.chat.entries.ChatSyncManifestEntry
import btcrenaud.discord.client.DiscordClientService
import btcrenaud.discord.webhook.WebhookService
import com.typewritermc.engine.paper.logger
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Synchronizes Minecraft chat to Discord.
 *
 * Each matching channel is delivered independently: channels with configured
 * `discordChannelIds` go through the bot client, the others fall back to the
 * manifest webhook.
 */
class ChatSyncService(
    private val manifest: ChatSyncManifestEntry,
    private val webhookService: WebhookService,
    private val discordClient: DiscordClientService,
) : Listener {

    private val plainSerializer = PlainTextComponentSerializer.plainText()

    /** Find all channels that match for a given player. */
    private fun matchingChannels(player: Player): List<ChatChannelConfig> =
        manifest.channels.filter { channel ->
            channel.enabled && (channel.permission.isBlank() || player.hasPermission(channel.permission))
        }.ifEmpty {
            // Fallback to default channel if no specific match
            manifest.channels.filter { it.channelName == manifest.defaultChannel && it.enabled }
        }

    private fun formattedName(player: Player): String {
        val displayName = plainSerializer.serialize(player.displayName())
        return manifest.playerNameFormat
            .replace("{player}", player.name)
            .replace("{displayname}", displayName)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncChatEvent) {
        if (!manifest.enabled) return
        val player = event.player
        val message = plainSerializer.serialize(event.message())

        for (channel in matchingChannels(player)) {
            val content = channel.messageFormat
                .replace("{player}", formattedName(player))
                .replace("{displayname}", plainSerializer.serialize(player.displayName()))
                .replace("{message}", message)
                .replace("{world}", player.world.name)
                .replace("{x}", player.location.blockX.toString())
                .replace("{y}", player.location.blockY.toString())
                .replace("{z}", player.location.blockZ.toString())
            deliver(channel, content)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!manifest.enabled || !manifest.sendJoinLeave) return
        sendPresenceMessage(event.player, manifest.joinMessageFormat)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!manifest.enabled || !manifest.sendJoinLeave) return
        sendPresenceMessage(event.player, manifest.leaveMessageFormat)
    }

    private fun sendPresenceMessage(player: Player, format: String) {
        val content = format
            .replace("{player}", formattedName(player))
            .replace("{displayname}", plainSerializer.serialize(player.displayName()))
        for (channel in matchingChannels(player)) {
            deliver(channel, content)
        }
    }

    private fun deliver(channel: ChatChannelConfig, content: String) {
        try {
            if (channel.discordChannelIds.isNotEmpty() && discordClient.isReady()) {
                channel.discordChannelIds.forEach { discordClient.sendMessage(it, content) }
                return
            }
            // Webhook fallback when no bot channel is configured for this channel. A missing
            // or disabled destination is a silent no-send, not an error.
            webhookService.send(manifest.webhook, content, emptyList())
        } catch (e: Exception) {
            logger.warning("Failed to send to Discord (channel ${channel.channelName}): ${e.message}")
        }
    }
}
