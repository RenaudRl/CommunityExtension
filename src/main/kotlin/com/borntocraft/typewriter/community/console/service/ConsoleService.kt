package com.borntocraft.typewriter.community.console.service

import com.borntocraft.typewriter.community.console.entries.ConsoleChannelEntry
import com.typewritermc.engine.paper.logger
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.bukkit.Bukkit
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for handling console commands from Discord.
 */
class ConsoleService(
    private val manifest: ConsoleChannelEntry
) : ListenerAdapter() {

    private val logFile: File? = if (manifest.logCommands) {
        File(manifest.logFilePath).apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }
    } else null

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!manifest.enabled || !manifest.allowCommands) return

        // Check if message is in console channel
        if (event.channel.id != manifest.discordChannelId) return

        // Check if message is from bot
        if (manifest.blockBots && event.author.isBot) {
            logger.info("Blocked console command from bot: ${event.author.name}")
            return
        }

        // Check if message is from self
        if (event.author.id == event.jda.selfUser.id) return

        // Check role permissions
        if (manifest.allowedRoles.isNotEmpty()) {
            val member = event.member
            if (member == null || !member.roles.any { it.id in manifest.allowedRoles }) {
                event.message.addReaction(Emoji.fromUnicode("❌")).queue()
                logger.warning("User ${event.author.name} (${event.author.id}) attempted to execute command without permission")
                return
            }
        }

        val command = event.message.contentRaw.trim()
        if (command.isBlank()) return

        // Extract command name (first word)
        val commandName = command.split(" ")[0].lowercase()

        // Check whitelist/blacklist
        val isAllowed = if (manifest.blacklistIsWhitelist) {
            // Whitelist mode: command must be in list
            manifest.commandList.any { it.lowercase() == commandName }
        } else {
            // Blacklist mode: command must NOT be in list
            !manifest.commandList.any { it.lowercase() == commandName }
        }

        if (!isAllowed) {
            event.message.addReaction(Emoji.fromUnicode("⛔")).queue()
            logger.warning("Blocked command from Discord: $commandName (user: ${event.author.name})")
            return
        }

        // Log command execution
        logCommand(event.author.name, event.author.id, command)

        // Execute command on main thread
        Bukkit.getScheduler().runTask(
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(ConsoleService::class.java)
        ) { _ ->
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                event.message.addReaction(Emoji.fromUnicode("✅")).queue()
                logger.info("Executed console command from Discord: $command (user: ${event.author.name})")
            } catch (e: Exception) {
                event.message.addReaction(Emoji.fromUnicode("❌")).queue()
                logger.warning("Failed to execute console command: $command - ${e.message}")
            }
        }
    }

    private fun logCommand(username: String, userId: String, command: String) {
        if (logFile == null) return

        try {
            val timestamp = LocalDateTime.now().format(timeFormatter)
            val logEntry = "[$timestamp | ID $userId] $username: $command${System.lineSeparator()}"
            
            Files.write(
                logFile.toPath(),
                logEntry.toByteArray(),
                StandardOpenOption.APPEND
            )
        } catch (e: Exception) {
            logger.warning("Failed to log console command to file: ${logFile.absolutePath} - ${e.message}")
        }
    }
}
