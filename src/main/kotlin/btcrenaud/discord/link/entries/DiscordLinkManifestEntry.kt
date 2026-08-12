package btcrenaud.discord.link.entries

import btcrenaud.discord.DiscordExtension
import btcrenaud.discord.webhook.WebhookDefinitionEntry
import btcrenaud.discord.link.data.DiscordBotSettings
import btcrenaud.discord.link.data.DiscordLinkMessages
import btcrenaud.discord.link.data.RoleMapping
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.command.dsl.DslCommand
import com.typewritermc.engine.paper.command.dsl.command
import com.typewritermc.engine.paper.command.dsl.executePlayer
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import com.typewritermc.engine.paper.entry.entries.CustomCommandEntry
import com.typewritermc.engine.paper.utils.asMini
import io.papermc.paper.command.brigadier.CommandSourceStack
import java.util.UUID
import java.time.Duration

/**
 * Manifest entry configuring how Discord linking behaves inside TypeWriter.
 *
 * The `/{commandName}` command (with its `unlink` subcommand) is registered
 * through the engine's [CustomCommandEntry] pipeline, so it follows engine
 * reloads without any reflection.
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
    @Help("Command players run to generate a link code; '<command> unlink' removes the link")
    val commandName: String = "discordlink",
    @Help("Artifact entry used to persist link information")
    val storage: Ref<DiscordLinkArtifactEntry> = emptyRef(),
    @Help("Webhook used to post link instructions/confirmations")
    val webhook: Ref<WebhookDefinitionEntry> = emptyRef(),
    @Help("Discord Bot configuration for listening to codes and syncing roles")
    val bot: DiscordBotSettings = DiscordBotSettings(),
    @Help("Discord text channel ID where the bot listens for verification codes")
    val verificationChannelId: String = "",
    @Help("Number of characters for generated verification codes")
    val codeLength: Int = 6,
    @Help("Validity duration (in minutes) for generated codes")
    val codeValidityMinutes: Long = 10,
    @Help("Automatically overwrite existing links when a new code is confirmed")
    val autoOverwriteExistingLink: Boolean = false,
    @Help("Permission required to receive link notifications (empty disables them)")
    val notifyPermission: String = "discordlink.notify",
    @Help("Permission checked per mapping to pick the player's group. {group} is replaced by the mapping's Minecraft group")
    val groupPermissionFormat: String = "group.{group}",
    @Help("Mappings between Minecraft groups and Discord Role IDs")
    val roleMappings: List<RoleMapping> = emptyList(),
    @Help("Entries triggered for the player when their Discord link is confirmed")
    val onLinkTriggers: List<Ref<com.typewritermc.engine.paper.entry.TriggerableEntry>> = emptyList(),
    @Help("Player facing messages, including menu text (placeholders: {code}, {duration}, {discord}, {player})")
    val messages: DiscordLinkMessages = DiscordLinkMessages(),
) : ManifestEntry, CustomCommandEntry {
    val codeValidity: Duration
        get() = Duration.ofMinutes(codeValidityMinutes.coerceAtLeast(1))

    override fun command(): DslCommand<CommandSourceStack> = command(commandName.ifBlank { "discordlink" }) {
        executePlayer { player ->
            DiscordExtension.discordLinkService()?.requestCode(player)
                ?: player.sendMessage(messages.linkUnavailable.asMini())
        }
        literal("unlink") {
            executePlayer { player ->
                DiscordExtension.discordLinkService()?.requestUnlink(player)
                    ?: player.sendMessage(messages.linkUnavailable.asMini())
            }
        }
    }
}

/**
 * Artifact entry used to store Discord link data.
 */
@Entry(
    name = "discord_link_storage",
    description = "Discord link storage",
    color = Colors.PURPLE,
    icon = "mdi:database",
)
@Tags("discord", "link", "artifact")
class DiscordLinkArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique identifier for persisted link data")
    override val artifactId: String = UUID.randomUUID().toString(),
) : ArtifactEntry
