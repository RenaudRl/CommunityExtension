package btcrenaud.community.chat.service

import btcrenaud.community.chat.entries.ChatChannelConfig
import btcrenaud.community.chat.entries.ChatSyncManifestEntry
import btcrenaud.community.webhook.WebhookSender
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
 * Synchronizes Minecraft chat to Discord channels.
 * Now properly matches all enabled channels (not just the default) so
 * per-world or per-permission channel routing works as configured.
 */
class ChatSyncService(
    private val manifest: ChatSyncManifestEntry,
    private val webhookSender: WebhookSender,
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncChatEvent) {
        if (!manifest.enabled) return
        val player = event.player
        val message = plainSerializer.serialize(event.message())

        for (channel in matchingChannels(player)) {
            sendChatMessage(player, message, channel)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!manifest.enabled || !manifest.sendJoinLeave) return
        val player = event.player
        val displayName = plainSerializer.serialize(player.displayName())
        val content = manifest.joinMessageFormat
            .replace("{player}", player.name)
            .replace("{displayname}", displayName)

        for (channel in matchingChannels(player)) {
            sendToDiscord(channel, content)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!manifest.enabled || !manifest.sendJoinLeave) return
        val player = event.player
        val displayName = plainSerializer.serialize(player.displayName())
        val content = manifest.leaveMessageFormat
            .replace("{player}", player.name)
            .replace("{displayname}", displayName)

        for (channel in matchingChannels(player)) {
            sendToDiscord(channel, content)
        }
    }

    private fun sendChatMessage(player: Player, message: String, channel: ChatChannelConfig) {
        val displayName = plainSerializer.serialize(player.displayName())
        val content = channel.messageFormat
            .replace("{player}", player.name)
            .replace("{displayname}", displayName)
            .replace("{message}", message)
            .replace("{world}", player.world.name)
            .replace("{x}", player.location.blockX.toString())
            .replace("{y}", player.location.blockY.toString())
            .replace("{z}", player.location.blockZ.toString())
        sendToDiscord(channel, content)
    }

    private fun sendToDiscord(channel: ChatChannelConfig, content: String) {
        if (manifest.webhook.url.isBlank()) return
        try {
            webhookSender.send(manifest.webhook, content, emptyList())
        } catch (e: Exception) {
            logger.warning("Failed to send to Discord (channel ${channel.channelName}): ${e.message}")
        }
    }
}
