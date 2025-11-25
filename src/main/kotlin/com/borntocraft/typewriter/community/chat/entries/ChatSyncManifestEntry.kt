package com.borntocraft.typewriter.community.chat.entries

import com.borntocraft.typewriter.community.common.WebhookSettings
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry

/**
 * Manifest entry for chat synchronization to Discord.
 * Allows Minecraft chat messages to be sent to Discord channels.
 */
@Entry("chat_sync_manifest", "Configure Minecraft to Discord chat synchronization", Colors.BLUE, "mdi:chat")
@Tags("chat", "discord", "sync")
class ChatSyncManifestEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Whether chat synchronization is enabled")
    val enabled: Boolean = true,
    @Help("Chat channels configuration")
    val channels: List<ChatChannelConfig> = emptyList(),
    @Help("Webhook settings for sending messages to Discord")
    val webhook: WebhookSettings = WebhookSettings(),
    @Help("Default channel to use if none specified")
    val defaultChannel: String = "global",
    @Help("Format for player names in Discord. Placeholders: {player}, {displayname}")
    val playerNameFormat: String = "{player}",
    @Help("Whether to send join/leave messages to Discord")
    val sendJoinLeave: Boolean = true,
    @Help("Join message format. Placeholders: {player}")
    val joinMessageFormat: String = "**{player}** joined the server",
    @Help("Leave message format. Placeholders: {player}")
    val leaveMessageFormat: String = "**{player}** left the server",
) : ManifestEntry

/**
 * Configuration for a single chat channel.
 */
data class ChatChannelConfig(
    @Help("Unique identifier for this channel (e.g., 'global', 'staff', 'trade')")
    val channelName: String = "global",
    @Help("Discord channel IDs where messages will be sent")
    val discordChannelIds: List<String> = emptyList(),
    @Help("Message format. Placeholders: {player}, {displayname}, {message}, {world}, {x}, {y}, {z}")
    val messageFormat: String = "**{player}**: {message}",
    @Help("Whether this channel is enabled")
    val enabled: Boolean = true,
    @Help("Permission required to send messages to this channel (empty = no permission required)")
    val permission: String = "",
)
