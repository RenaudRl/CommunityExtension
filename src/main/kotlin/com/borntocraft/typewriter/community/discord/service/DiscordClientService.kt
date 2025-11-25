package com.borntocraft.typewriter.community.discord.service

import com.borntocraft.typewriter.community.discord.data.DiscordBotSettings
import com.typewritermc.engine.paper.logger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import java.util.logging.Level

class DiscordClientService {

    private var jda: JDA? = null
    private var guildId: String = ""
    private val listeners = mutableListOf<ListenerAdapter>()

    fun connect(settings: DiscordBotSettings) {
        disconnect() // ensure clean state on reload
        if (!settings.enabled || settings.botToken.isBlank()) {
            logger.info("Discord Bot is disabled or token is missing.")
            return
        }

        try {
            jda = JDABuilder.createDefault(settings.botToken)
                .enableIntents(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                )
                .build()
            jda?.awaitReady()
            guildId = settings.guildId
            logger.info("Discord Bot connected as ${jda?.selfUser?.name} guildId=${guildId}")
            val guild = getGuild()
            if (guild == null) {
                logger.warning("Discord Bot connected but guild not found. Check guildId in manifest.")
            } else {
                logger.info("Discord Bot found guild: ${guild.name} (${guild.id})")
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to connect Discord Bot", e)
        }
    }

    fun disconnect() {
        listeners.forEach { jda?.removeEventListener(it) }
        listeners.clear()
        jda?.shutdown()
        jda = null
    }

    fun getGuild(): Guild? {
        if (guildId.isBlank()) return null
        return jda?.getGuildById(guildId)
    }

    fun onMessageInChannel(channelId: String, handler: (MessageReceivedEvent) -> Unit) {
        if (channelId.isBlank() || jda == null) return
        val listener = object : ListenerAdapter() {
            override fun onMessageReceived(event: MessageReceivedEvent) {
                if (event.channel.id != channelId) return
                handler(event)
            }
        }
        listeners.add(listener)
        jda?.addEventListener(listener)
    }

    fun sendMessage(channelId: String, content: String) {
        if (channelId.isBlank()) return
        jda?.getTextChannelById(channelId)
            ?.sendMessage(content)
            ?.queue(
                {},
                { error -> logger.warning("Failed to send message to channel $channelId: ${error.message}") },
            )
    }

    fun isReady(): Boolean = jda?.status == JDA.Status.CONNECTED

    fun addEventListener(listener: Any) {
        jda?.addEventListener(listener)
    }

    fun removeEventListener(listener: Any) {
        jda?.removeEventListener(listener)
    }
}
