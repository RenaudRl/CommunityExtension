package com.borntocraft.typewriter.community.console.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry

/**
 * Manifest entry for console command execution from Discord.
 * Allows executing server commands from a Discord channel.
 */
@Entry("console_channel_manifest", "Configure Discord console channel", Colors.GRAY, "mdi:console")
@Tags("console", "discord", "commands")
class ConsoleChannelEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Whether console channel is enabled")
    val enabled: Boolean = true,
    @Help("Discord channel ID for console commands")
    val discordChannelId: String = "",
    @Help("Whether to allow command execution from Discord")
    val allowCommands: Boolean = true,
    @Help("Commands that are allowed/blocked (depends on blacklistIsWhitelist)")
    val commandList: List<String> = listOf("stop", "restart", "reload"),
    @Help("If true, commandList acts as whitelist. If false, acts as blacklist")
    val blacklistIsWhitelist: Boolean = false,
    @Help("Whether to block commands from bots")
    val blockBots: Boolean = true,
    @Help("Discord role IDs that are allowed to execute commands (empty = everyone)")
    val allowedRoles: List<String> = emptyList(),
    @Help("Whether to log command executions to file")
    val logCommands: Boolean = true,
    @Help("Path to command log file (relative to server root)")
    val logFilePath: String = "logs/discord-console.log",
) : ManifestEntry
