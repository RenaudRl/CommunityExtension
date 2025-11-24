package com.borntocraft.typewriter.community.discord.data

import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder

data class DiscordBotSettings(
    @Help("Enable the bot (required for code verification & role sync)")
    val enabled: Boolean = false,
    @Help("Bot token from the Discord Developer Portal")
    val botToken: String = "",
    @Help("Discord server (guild) ID where the bot operates")
    val guildId: String = "",
    @Help("Messages sent by the bot in the verification channel")
    val messages: DiscordBotVerificationMessages = DiscordBotVerificationMessages(),
)

data class DiscordBotVerificationMessages(
    @Placeholder @MultiLine
    @Help("Reply posted when a code is accepted (placeholders: {player}, {code})")
    val success: String = "Code validé pour {player}.",
    @Placeholder @MultiLine
    @Help("Reply posted when a code is rejected/invalid (placeholders: {player}, {code})")
    val failure: String = "Code invalide pour {player}.",
)
