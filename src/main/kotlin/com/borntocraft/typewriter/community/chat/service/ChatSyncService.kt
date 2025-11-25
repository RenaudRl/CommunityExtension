package com.borntocraft.typewriter.community.chat.service

import com.borntocraft.typewriter.community.chat.entries.ChatChannelConfig
import com.borntocraft.typewriter.community.chat.entries.ChatSyncManifestEntry
import com.borntocraft.typewriter.community.webhook.WebhookSender
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
 * Service for synchronizing Minecraft chat to Discord channels.
 */
class ChatSyncService(
    private val manifest: ChatSyncManifestEntry,
    private val webhookSender: WebhookSender
) : Listener {

    private val plainSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncChatEvent) {
        if (!manifest.enabled) return

        val player = event.player
        val message = plainSerializer.serialize(event.message())
        
        // Find the appropriate channel (use default for now)
        val channel = manifest.channels.find { 
            it.channelName == manifest.defaultChannel && it.enabled 
        } ?: return

        // Check permission if required
        if (channel.permission.isNotBlank() && !player.hasPermission(channel.permission)) {
            return
        }

        sendChatMessage(player, message, channel)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!manifest.enabled || !manifest.sendJoinLeave) return

        val player = event.player
        val channel = manifest.channels.find { 
            it.channelName == manifest.defaultChannel && it.enabled 
        } ?: return

        val displayName = plainSerializer.serialize(player.displayName())
        val formattedMessage = manifest.joinMessageFormat
            .replace("{player}", player.name)
            .replace("{displayname}", displayName)

        sendToDiscord(channel, formattedMessage)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!manifest.enabled || !manifest.sendJoinLeave) return

        val player = event.player
        val channel = manifest.channels.find { 
            it.channelName == manifest.defaultChannel && it.enabled 
        } ?: return

        val displayName = plainSerializer.serialize(player.displayName())
        val formattedMessage = manifest.leaveMessageFormat
            .replace("{player}", player.name)
            .replace("{displayname}", displayName)

        sendToDiscord(channel, formattedMessage)
    }

    private fun sendChatMessage(player: Player, message: String, channel: ChatChannelConfig) {
        val displayName = plainSerializer.serialize(player.displayName())
        
        val formattedMessage = channel.messageFormat
            .replace("{player}", player.name)
            .replace("{displayname}", displayName)
            .replace("{message}", message)
            .replace("{world}", player.world.name)
            .replace("{x}", player.location.blockX.toString())
            .replace("{y}", player.location.blockY.toString())
            .replace("{z}", player.location.blockZ.toString())

        sendToDiscord(channel, formattedMessage)
    }

    private fun sendToDiscord(channel: ChatChannelConfig, content: String) {
        if (manifest.webhook.url.isBlank()) {
            logger.warning("Chat sync webhook URL is not configured")
            return
        }

        try {
            webhookSender.send(
                manifest.webhook,
                content,
                emptyList()
            )
        } catch (e: Exception) {
            logger.warning("Failed to send chat message to Discord: ${e.message}")
        }
    }
}
