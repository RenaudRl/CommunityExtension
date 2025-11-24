package com.borntocraft.typewriter.community

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.SimpleCommandMap
import org.bukkit.command.defaults.BukkitCommand
import org.slf4j.LoggerFactory
import java.lang.reflect.Field

object CommandRegistry {
    private val logger = LoggerFactory.getLogger("CommunityCommands")
    private var commandMap: CommandMap? = null

    fun register(name: String, executor: (org.bukkit.command.CommandSender, Array<out String>) -> Unit) {
        val map = getCommandMap() ?: return
        
        val command = object : BukkitCommand(name) {
            override fun execute(sender: org.bukkit.command.CommandSender, label: String, args: Array<out String>): Boolean {
                executor(sender, args)
                return true
            }
        }
        
        map.register("community", command)
        logger.info("Registered command: /$name")
    }

    private fun getCommandMap(): CommandMap? {
        if (commandMap != null) return commandMap
        return try {
            val server = Bukkit.getServer()
            val field = server.javaClass.getDeclaredField("commandMap")
            field.isAccessible = true
            commandMap = field.get(server) as? CommandMap
            commandMap
        } catch (e: Exception) {
            logger.error("Failed to get CommandMap via reflection", e)
            null
        }
    }
}
