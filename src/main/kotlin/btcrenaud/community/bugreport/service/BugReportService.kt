package btcrenaud.community.bugreport.service

import btcrenaud.community.bugreport.data.BooleanInput
import btcrenaud.community.bugreport.data.BugReport
import btcrenaud.community.bugreport.data.BugReportCategory
import btcrenaud.community.bugreport.data.BugReportInput
import btcrenaud.community.bugreport.data.LocationSnapshot
import btcrenaud.community.bugreport.data.TextInput
import btcrenaud.community.bugreport.data.asHumanReadable
import btcrenaud.community.bugreport.entries.BugReportManifestEntry
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.server
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val MINI_TAG_REGEX = Regex("<[^<>]+>")

/**
 * Bug report pipeline for a single [BugReportManifestEntry].
 * Each manifest owns its own service instance, so several report systems can
 * run side by side without sharing any mutable state.
 */
class BugReportService(
    private val manifest: BugReportManifestEntry,
    private val repository: BugReportRepository,
    private val webhookService: BugReportWebhookService,
) {

    fun openCategorySelection(player: Player) {
        if (!checkSubmissionAllowed(player)) return
        val categories = manifest.categories
        if (categories.isEmpty()) {
            player.sendMessage(manifest.messages.noCategoriesAvailable.asMini())
            return
        }

        // Group categories by display line
        val categoriesByLine = categories.groupBy { it.displayLine }
        val maxLine = categoriesByLine.keys.maxOrNull() ?: 0
        val effectiveLines = (maxLine + 1).coerceAtLeast(manifest.categoryLayoutLines)

        val buttonRows = (0 until effectiveLines).map { lineIndex ->
            categoriesByLine[lineIndex]?.map { category ->
                ActionButton.builder(category.displayName.asMini())
                    .action(
                        DialogAction.customClick(
                            { _, _ -> openReportForm(player, category) },
                            ClickCallback.Options.builder().build(),
                        ),
                    )
                    .build()
            } ?: emptyList()
        }.filter { it.isNotEmpty() }

        // multiAction's third argument is a column count, not a line count: the buttons are
        // laid out row by row, `columns` per row. Sizing it to the widest configured line
        // renders each line as its own row. Passing the number of lines instead turned a
        // single line of N buttons into N stacked rows.
        // Lines with fewer buttons than `columns` are padded out by the next line's buttons;
        // the API takes a flat list, so ragged lines cannot be kept apart.
        val columns = buttonRows.maxOf { it.size }
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(manifest.selectMenuTitle.asMini())
                        .body(listOf(DialogBody.plainMessage(manifest.selectMenuBody.asMini())))
                        .build()
                )
                .type(DialogType.multiAction(buttonRows.flatten(), null, columns))
        }

        player.showDialog(dialog)
    }

    fun openReportForm(player: Player, category: BugReportCategory) {
        val configuredInputs = ensureBaseInputs(category)
        val inputs = configuredInputs.map { input ->
            when (input) {
                is TextInput -> DialogInput.text(
                    input.key,
                    200,
                    input.label.asMini(),
                    true,
                    input.placeholder,
                    input.maxLength,
                    multilineOptions(
                        maxLines = if (input.multiline) 3 else 1,
                        height = input.height,
                    ),
                )
                is BooleanInput -> DialogInput.bool(
                    input.key,
                    input.label.asMini(),
                    input.initial,
                    "true",
                    "false",
                )
            }
        }

        val submitAction = ActionButton.builder(manifest.submitButtonText.asMini())
            .action(DialogAction.customClick({ result, _ ->
                val customFields = mutableMapOf<String, String>()
                configuredInputs.forEach { input ->
                    when (input) {
                        is TextInput -> customFields[input.key] = result.getText(input.key) ?: ""
                        is BooleanInput -> {
                            val selected = result.getBoolean(input.key) ?: false
                            customFields[input.key] = if (selected) input.label else ""
                        }
                    }
                }
                submit(player, category, configuredInputs, customFields)
            }, ClickCallback.Options.builder().build()))
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(category.dialogTitle.asMini())
                        .inputs(inputs)
                        .build()
                )
                .type(DialogType.multiAction(listOf(submitAction), null, 1))
        }

        player.showDialog(dialog)
    }

    private fun submit(
        player: Player,
        category: BugReportCategory,
        configuredInputs: List<BugReportInput>,
        customFields: Map<String, String>,
    ) {
        if (!checkSubmissionAllowed(player)) return

        // Enforce configured minimum lengths before accepting the report.
        val tooShort = configuredInputs.filterIsInstance<TextInput>().any { input ->
            (customFields[input.key]?.trim()?.length ?: 0) < input.minLength
        }
        val title = customFields["title"]?.trim().orEmpty()
        val message = customFields["description"]?.trim().orEmpty()
        if (tooShort || (title.isEmpty() && message.isEmpty())) {
            player.sendMessage(manifest.messages.submissionMissingMessage.asMini())
            return
        }

        val now = System.currentTimeMillis()
        val report = BugReport(
            id = repository.nextId(),
            title = title,
            message = message,
            categoryId = category.id,
            statusId = manifest.defaultStatusId,
            playerName = player.name,
            playerUuid = player.uniqueId,
            worldName = player.world.name,
            location = LocationSnapshot(
                player.world.name,
                player.location.x,
                player.location.y,
                player.location.z,
                player.location.yaw,
                player.location.pitch,
            ),
            gameMode = player.gameMode.name,
            serverName = manifest.serverName,
            createdAt = now,
            updatedAt = now,
            customFields = customFields,
        )

        repository.save(report)
        player.sendMessage(
            manifest.messages.submissionSuccess
                .replace("{id}", report.id)
                .replace("{status}", statusDisplayName(report.statusId))
                .asMini()
        )

        notifyStaff(
            manifest.messages.staffNotification
                .replace("{player}", report.playerName)
                .replace("{id}", report.id)
                .replace("{status}", statusDisplayName(report.statusId))
        )

        if (manifest.webhook.enabled) {
            webhookService.send(report, category, manifest.webhook, manifest.serverName)
        }
    }

    // -- Admin subcommands ---------------------------------------------------

    fun listReports(sender: CommandSender, playerFilter: String?) {
        val reports = repository.findAll()
            .filter { playerFilter == null || it.playerName.equals(playerFilter, ignoreCase = true) }
            .sortedByDescending { it.createdAt }
        sendReportList(sender, reports)
    }

    /** Lists the sender's own reports; no permission required. */
    fun listOwnReports(player: Player) {
        val reports = repository.findByPlayer(player.uniqueId).sortedByDescending { it.createdAt }
        sendReportList(player, reports)
    }

    private fun sendReportList(sender: CommandSender, reports: List<BugReport>) {
        if (reports.isEmpty()) {
            sender.sendMessage(manifest.messages.noReportsFound.asMini())
            return
        }
        sender.sendMessage(manifest.messages.listHeader.asMini())
        reports.forEach { report ->
            sender.sendMessage(formatListEntry(report).asMini())
        }
    }

    private fun formatListEntry(report: BugReport): String =
        manifest.messages.listEntry
            .replace("{id}", report.id)
            .replace("{title}", report.title.ifBlank { report.message.take(30) })
            .replace("{status}", statusDisplayName(report.statusId))
            .replace("{player}", report.playerName)
            .replace("{world}", report.worldName)

    // -- Discord slash command variants (plain text, MiniMessage stripped) ---

    fun listReportsPlain(playerFilter: String?): List<String> {
        val reports = repository.findAll()
            .filter { playerFilter == null || it.playerName.equals(playerFilter, ignoreCase = true) }
            .sortedByDescending { it.createdAt }
        if (reports.isEmpty()) return listOf(manifest.messages.noReportsFound.stripMini())
        return listOf(manifest.messages.listHeader.stripMini()) +
            reports.map { formatListEntry(it).stripMini() }
    }

    fun updateStatusPlain(reportId: String, statusId: String): String {
        val report = repository.findById(reportId)
            ?: return manifest.messages.reportNotFound.replace("{id}", reportId).stripMini()
        val status = manifest.statuses.firstOrNull { it.id.equals(statusId, ignoreCase = true) }
            ?: return manifest.messages.unknownStatus
                .replace("{status}", statusId)
                .replace("{statuses}", manifest.statuses.joinToString(", ") { it.id })
                .stripMini()
        report.statusId = status.id
        report.updatedAt = System.currentTimeMillis()
        repository.save(report)
        val feedback = manifest.messages.statusUpdated
            .replace("{id}", report.id)
            .replace("{status}", status.displayName)
        if (status.sendNotification) {
            server.getPlayer(report.playerUuid)?.sendMessage(feedback.asMini())
        }
        return feedback.stripMini()
    }

    fun deleteReportPlain(reportId: String): String =
        if (repository.delete(reportId)) {
            manifest.messages.reportDeleted.replace("{id}", reportId).stripMini()
        } else {
            manifest.messages.reportNotFound.replace("{id}", reportId).stripMini()
        }

    private fun String.stripMini(): String = replace(MINI_TAG_REGEX, "")

    fun updateStatus(sender: CommandSender, reportId: String, statusId: String) {
        val report = repository.findById(reportId)
        if (report == null) {
            sender.sendMessage(manifest.messages.reportNotFound.replace("{id}", reportId).asMini())
            return
        }
        val status = manifest.statuses.firstOrNull { it.id.equals(statusId, ignoreCase = true) }
        if (status == null) {
            sender.sendMessage(
                manifest.messages.unknownStatus
                    .replace("{status}", statusId)
                    .replace("{statuses}", manifest.statuses.joinToString(", ") { it.id })
                    .asMini()
            )
            return
        }
        report.statusId = status.id
        report.updatedAt = System.currentTimeMillis()
        repository.save(report)

        val feedback = manifest.messages.statusUpdated
            .replace("{id}", report.id)
            .replace("{status}", status.displayName)
        sender.sendMessage(feedback.asMini())
        if (status.sendNotification) {
            server.getPlayer(report.playerUuid)?.sendMessage(feedback.asMini())
        }
    }

    fun deleteReport(sender: CommandSender, reportId: String) {
        if (!repository.delete(reportId)) {
            sender.sendMessage(manifest.messages.reportNotFound.replace("{id}", reportId).asMini())
            return
        }
        sender.sendMessage(manifest.messages.reportDeleted.replace("{id}", reportId).asMini())
    }

    // -- Internals -----------------------------------------------------------

    private fun checkSubmissionAllowed(player: Player): Boolean {
        val max = manifest.maxReportsPerPlayer
        if (max > 0 && repository.countForPlayer(player.uniqueId) >= max) {
            player.sendMessage(manifest.messages.submissionLimitReached.asMini())
            return false
        }
        val cooldown = manifest.cooldown
        if (!cooldown.isZero) {
            val last = repository.lastSubmissionOf(player.uniqueId)
            if (last != null) {
                val elapsed = System.currentTimeMillis() - last
                val remaining = cooldown.minusMillis(elapsed)
                if (!remaining.isNegative && !remaining.isZero) {
                    player.sendMessage(
                        manifest.messages.submissionCooldown
                            .replace("{cooldown}", remaining.asHumanReadable())
                            .asMini()
                    )
                    return false
                }
            }
        }
        return true
    }

    private fun notifyStaff(message: String) {
        logger.info(message)
        val permission = manifest.notifyPermission
        if (permission.isBlank()) return
        server.onlinePlayers
            .filter { it.hasPermission(permission) }
            .forEach { it.sendMessage(message.asMini()) }
    }

    private fun statusDisplayName(statusId: String): String =
        manifest.statuses.firstOrNull { it.id == statusId }?.displayName ?: statusId

    private fun ensureBaseInputs(category: BugReportCategory): List<BugReportInput> {
        val hasTitle = category.inputs.any { it.key.equals("title", true) }
        if (hasTitle) return category.inputs
        return listOf(
            TextInput(
                key = "title",
                label = "Title",
                placeholder = "",
                maxLength = 80,
            )
        ) + category.inputs
    }

    private fun multilineOptions(maxLines: Int, height: Int): TextDialogInput.MultilineOptions? {
        if (maxLines <= 1) return null
        val safeHeight = height.coerceIn(1, 512)
        val safeLines = maxLines.coerceIn(2, 50)
        return TextDialogInput.MultilineOptions.create(safeLines, safeHeight)
    }
}
