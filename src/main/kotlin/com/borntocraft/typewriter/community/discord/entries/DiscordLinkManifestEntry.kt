package com.borntocraft.typewriter.community.discord.entries

import com.borntocraft.typewriter.community.common.WebhookSettings
import com.borntocraft.typewriter.community.discord.data.DiscordBotSettings
import com.borntocraft.typewriter.community.discord.data.DiscordLinkMessages
import com.borntocraft.typewriter.community.discord.data.RoleMapping
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import java.time.Duration

/**
 * Manifest entry configuring how Discord linking behaves inside TypeWriter.
 */
@Entry(
    name = "discord_link_manifest",
    description = "Configure Discord linking and role synchronization",
    color = Colors.BLUE,
    icon = "mdi:discord",
)
@Tags("discord", "link", "manifest")
class DiscordLinkManifestEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Artifact entry used to persist link information")
    val storage: Ref<DiscordLinkArtifactEntry> = emptyRef(),
    @Help("Webhook used to post link instructions/confirmations")
    val webhook: WebhookSettings = WebhookSettings(),
    @Help("Discord Bot configuration for listening to codes and syncing roles")
    val bot: DiscordBotSettings = DiscordBotSettings(),
    @Help("Discord text channel ID where the bot listens for verification codes")
    val verificationChannelId: String = "",
    @Help("Number of characters for generated verification codes")
    val codeLength: Int = 6,
    @Help("Validity duration (in minutes) for generated codes")
    val codeValidityMinutes: Long = 10,
    @Help("Maximum simultaneous pending codes per player")
    val maxPendingCodesPerPlayer: Int = 1,
    @Help("Automatically overwrite existing links when a new code is confirmed")
    val autoOverwriteExistingLink: Boolean = false,
    @Help("Permission required to receive link notifications")
    val notifyPermission: String = "discordlink.notify",
    @Help("Mappings between Minecraft groups and Discord Role IDs")
    val roleMappings: List<RoleMapping> = emptyList(),
    @Help("Player facing messages, including menu text (placeholders: {code}, {duration}, {discord}, {player})")
    val messages: DiscordLinkMessages = DiscordLinkMessages(),
) : ManifestEntry {
    val codeValidity: Duration
        get() = Duration.ofMinutes(codeValidityMinutes.coerceAtLeast(1))
}

/**
 * Artifact entry used to store Discord link data.
 */
@Entry(
    name = "discord_link_storage",
    description = "Discord link storage",
    color = Colors.MEDIUM_PURPLE,
    icon = "mdi:database",
)
@Tags("discord", "link", "artifact")
class DiscordLinkArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique identifier for persisted link data")
    override val artifactId: String = "",
) : ArtifactEntry
