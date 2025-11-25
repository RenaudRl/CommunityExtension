package com.borntocraft.typewriter.community.bugreport.entries

import com.borntocraft.typewriter.community.bugreport.data.BugReportCategory
import com.borntocraft.typewriter.community.bugreport.data.BugReportMessages
import com.borntocraft.typewriter.community.bugreport.data.BugReportWebhookSettings
import com.borntocraft.typewriter.community.bugreport.data.defaultCategories
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry
import java.time.Duration

/**
 * Manifest entry centralizing every configuration knob for the bug report system.
 */
@Entry("bugreport_manifest", "Configure bug report behaviour", Colors.RED, "mdi:ladybug")
@Tags("bugreport", "manifest")
class BugReportManifestEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Command name to open this bug report system (e.g., 'report', 'bugreport', 'ticket')")
    val commandName: String = "",
    @Help("Name displayed when referencing this server in reports")
    val serverName: String = "BornToCraft",
    @Help("Artifact storing the incremental sequence for bug IDs")
    val sequenceStorage: Ref<BugReportSequenceArtifactEntry> = emptyRef(),
    @Help("Title displayed on the category selection dialog")
    val selectMenuTitle: String = "Select Category",
    @Help("Text displayed on the submit button")
    val submitButtonText: String = "Submit",
    @Help("Number of lines to display categories across in the selection menu")
    val categoryLayoutLines: Int = 1,
    @Help("Maximum amount of non-deleted reports each player may keep (0 = unlimited)")
    val maxReportsPerPlayer: Int = 0,
    @Help("Cooldown in seconds between two submissions from the same player")
    val submissionCooldown: Int = 0,
    @Help("Permission required to receive staff notifications")
    val notifyPermission: String = "bugreport.notify",
    @Help("Available categories shown to players, each with their own form inputs")
    val categories: List<BugReportCategory> = defaultCategories,
    @Help("Player facing messages")
    val messages: BugReportMessages = BugReportMessages(),
    @Help("Discord webhook delivery configuration")
    val webhook: BugReportWebhookSettings = BugReportWebhookSettings(),
) : ManifestEntry {
    val cooldown: Duration
        get() = Duration.ofSeconds(submissionCooldown.toLong().coerceAtLeast(0))
}

@Entry("bugreport_sequence_storage", "Bug report sequence storage", Colors.ORANGE, "mdi:database")
@Tags("bugreport", "artifact")
class BugReportSequenceArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique identifier for the stored sequence")
    override val artifactId: String = "",
    @Help("Next ID to use when creating a bug report")
    var nextId: Long = 0,
) : com.typewritermc.engine.paper.entry.entries.ArtifactEntry
