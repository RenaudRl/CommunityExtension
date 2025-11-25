package com.borntocraft.typewriter.community.bugreport.data

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.AlgebraicTypeInfo
import com.typewritermc.core.extension.annotations.Help
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Represents a single bug report stored within TypeWriter.
 */
data class BugReport(
    val id: String,
    var title: String,
    var message: String,
    var categoryId: String?,
    var statusId: String,
    val playerName: String,
    val playerUuid: UUID,
    val worldName: String,
    val location: LocationSnapshot,
    val gameMode: String,
    val serverName: String,
    val createdAt: Long,
    var updatedAt: Long,
    val customFields: Map<String, String> = emptyMap(),
)

data class LocationSnapshot(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

data class BugReportStatus(
    @Help("Unique identifier for this status")
    val id: String = "open",
    @Help("Display name shown to players")
    val displayName: String = "Open",
    @Help("Description of what this status means")
    val description: String = "",
    @Help("Whether to send notifications when a report enters this status")
    val sendNotification: Boolean = true,
)

/**
 * Configuration of a bug report category with a custom form.
 */
data class BugReportCategory(
    @Help("Unique identifier for this category")
    val id: String = "general",
    @Help("Display name shown in the menu")
    val displayName: String = "General",
    @Help("Custom title shown on the dialog window")
    val dialogTitle: String = "New Bug Report",
    @Help("Custom inputs for this category (TextInput or BooleanInput only)")
    val inputs: List<BugReportInput> = emptyList(),
    @Help("Line number where this category appears in the selection menu (0-indexed)")
    val displayLine: Int = 0,
)

sealed interface BugReportInput {
    @Help("Unique key used to store/retrieve this input value (also usable as placeholder)")
    val key: String
}

@AlgebraicTypeInfo("text_input", Colors.GREEN, "fa6-solid:keyboard")
data class TextInput(
    override val key: String = "description",
    @Help("Label shown above the input")
    val label: String = "Description",
    @Help("Placeholder text shown when empty")
    val placeholder: String = "Enter text...",
    @Help("Minimum character length")
    val minLength: Int = 0,
    @Help("Maximum character length")
    val maxLength: Int = 256,
    @Help("Whether to allow multiple lines")
    val multiline: Boolean = false,
    @Help("Height in pixels when multiline; clamped to 1..512 by runtime")
    val height: Int = 128,
) : BugReportInput

@AlgebraicTypeInfo("boolean_input", Colors.PURPLE, "fa6-solid:toggle-on")
data class BooleanInput(
    override val key: String = "reproducible",
    @Help("Label for the toggle")
    val label: String = "Reproducible?",
    @Help("Initial state of the toggle")
    val initial: Boolean = false,
) : BugReportInput

data class BugReportMessages(
    val submissionSuccess: String = "Thank you! Your report has been submitted as #{id}.",
    val submissionCooldown: String = "Please wait {cooldown} before submitting another report.",
    val submissionLimitReached: String = "You have reached the maximum number of open reports.",
    val submissionMissingMessage: String = "Please describe the issue before submitting a report.",
    val staffNotification: String = "{player} submitted bug report #{id} ({status}).",
    val reportNotFound: String = "Unable to find bug report #{id}.",
    val statusUpdated: String = "Report #{id} is now {status}.",
    val reportDeleted: String = "Report #{id} has been deleted.",
    val noReportsFound: String = "There are no bug reports matching your filters.",
    val listHeader: String = "Recent bug reports:",
    val listEntry: String = "#{id} - {title} [{status}] by {player} in {world}",
)

internal val defaultCategories = emptyList<BugReportCategory>()

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.US).withZone(ZoneId.systemDefault())

internal fun BugReport.formatTimestamp(): String = formatter.format(Instant.ofEpochMilli(createdAt))

internal fun Duration.asHumanReadable(): String {
    val seconds = this.seconds
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes % 60)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds % 60)
        else -> String.format(Locale.US, "%ds", seconds)
    }
}
