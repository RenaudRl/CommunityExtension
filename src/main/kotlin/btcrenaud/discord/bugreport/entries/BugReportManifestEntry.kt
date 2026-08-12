package btcrenaud.discord.bugreport.entries

import btcrenaud.discord.DiscordExtension
import btcrenaud.discord.bugreport.data.BugReportCategory
import btcrenaud.discord.bugreport.data.BugReportMessages
import btcrenaud.discord.bugreport.data.BugReportStatus
import btcrenaud.discord.bugreport.data.BugReportWebhookSettings
import btcrenaud.discord.bugreport.data.defaultCategories
import btcrenaud.discord.bugreport.data.defaultStatuses
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.command.dsl.DslCommand
import com.typewritermc.engine.paper.command.dsl.command
import com.typewritermc.engine.paper.command.dsl.executePlayer
import com.typewritermc.engine.paper.command.dsl.sender
import com.typewritermc.engine.paper.command.dsl.withPermission
import com.typewritermc.engine.paper.command.dsl.word
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.CustomCommandEntry
import io.papermc.paper.command.brigadier.CommandSourceStack
import java.time.Duration

/**
 * Manifest entry centralizing every configuration knob for the bug report system.
 *
 * The command is registered through the engine's [CustomCommandEntry] pipeline,
 * so it follows engine reloads without any reflection.
 */
@Entry("bugreport_manifest", "Configure bug report behaviour", Colors.RED, "mdi:ladybug")
@Tags("bugreport", "manifest")
class BugReportManifestEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Command name to open this bug report system (e.g., 'report', 'bugreport', 'ticket')")
    val commandName: String = "",
    @Help("Name displayed when referencing this server in reports")
    val serverName: String = "",
    @Help("Artifact storing the reports and the incremental sequence for bug IDs")
    val sequenceStorage: Ref<BugReportSequenceArtifactEntry> = emptyRef(),
    @Help("Title displayed on the category selection dialog")
    val selectMenuTitle: String = "Select Category",
    @Help("Body text displayed on the category selection dialog")
    val selectMenuBody: String = "Choose a category for your report:",
    @Help("Text displayed on the submit button")
    val submitButtonText: String = "Submit",
    @Help("Number of lines to display categories across in the selection menu")
    val categoryLayoutLines: Int = 1,
    @Help("Maximum amount of non-deleted reports each player may keep (0 = unlimited)")
    val maxReportsPerPlayer: Int = 0,
    @Help("Cooldown in seconds between two submissions from the same player")
    val submissionCooldown: Int = 0,
    @Help("Permission required to receive staff notifications on new reports")
    val notifyPermission: String = "bugreport.notify",
    @Help("Permission required to use the list/status/delete admin subcommands")
    val adminPermission: String = "bugreport.admin",
    @Help("Statuses a report can be moved through with the status subcommand")
    val statuses: List<BugReportStatus> = defaultStatuses,
    @Help("Available categories shown to players, each with their own form inputs")
    val categories: List<BugReportCategory> = defaultCategories,
    @Help("Player facing messages")
    val messages: BugReportMessages = BugReportMessages(),
    @Help("Discord webhook delivery configuration")
    val webhook: BugReportWebhookSettings = BugReportWebhookSettings(),
    @Help("Register Discord slash commands (list/status/delete) for this report system")
    val discordCommands: Boolean = false,
    @Help("Discord role IDs allowed to use the slash commands (empty = server administrators only)")
    val discordAdminRoleIds: List<String> = emptyList(),
) : ManifestEntry, CustomCommandEntry {
    val cooldown: Duration
        get() = Duration.ofSeconds(submissionCooldown.toLong().coerceAtLeast(0))

    val defaultStatusId: String
        get() = statuses.firstOrNull()?.id ?: "open"

    override fun command(): DslCommand<CommandSourceStack> = command(commandName.ifBlank { "bugreport" }) {
        executePlayer { player ->
            DiscordExtension.bugReportSystem(id)?.openCategorySelection(player)
        }
        literal("mine") {
            executePlayer { player ->
                DiscordExtension.bugReportSystem(id)?.listOwnReports(player)
            }
        }
        literal("list") {
            withPermission(adminPermission)
            executes {
                DiscordExtension.bugReportSystem(id)?.listReports(sender, playerFilter = null)
            }
            word("player") { playerName ->
                executes {
                    DiscordExtension.bugReportSystem(id)?.listReports(sender, playerFilter = playerName())
                }
            }
        }
        literal("status") {
            withPermission(adminPermission)
            word("reportId") { reportId ->
                word("statusId") { statusId ->
                    executes {
                        DiscordExtension.bugReportSystem(id)?.updateStatus(sender, reportId(), statusId())
                    }
                }
            }
        }
        literal("delete") {
            withPermission(adminPermission)
            word("reportId") { reportId ->
                executes {
                    DiscordExtension.bugReportSystem(id)?.deleteReport(sender, reportId())
                }
            }
        }
    }
}

@Entry("bugreport_sequence_storage", "Bug report sequence storage", Colors.ORANGE, "mdi:database")
@Tags("bugreport", "artifact")
class BugReportSequenceArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique identifier for the stored sequence")
    override val artifactId: String = "",
) : com.typewritermc.engine.paper.entry.entries.ArtifactEntry
