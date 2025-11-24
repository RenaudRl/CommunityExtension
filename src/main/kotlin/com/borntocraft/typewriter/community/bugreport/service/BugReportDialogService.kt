package com.borntocraft.typewriter.community.bugreport.service

import com.borntocraft.typewriter.community.bugreport.data.BooleanInput
import com.borntocraft.typewriter.community.bugreport.data.BugReport
import com.borntocraft.typewriter.community.bugreport.data.BugReportCategory
import com.borntocraft.typewriter.community.bugreport.data.BugReportInput
import com.borntocraft.typewriter.community.bugreport.data.BugReportMessages
import com.borntocraft.typewriter.community.bugreport.data.BugReportWebhookSettings
import com.borntocraft.typewriter.community.bugreport.data.LocationSnapshot
import com.borntocraft.typewriter.community.bugreport.data.TextInput
import com.borntocraft.typewriter.community.bugreport.data.defaultCategories
import com.typewritermc.engine.paper.logger
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object BugReportDialogService {

    lateinit var repository: BugReportRepository
    var categories: List<BugReportCategory> = defaultCategories
    var defaultStatusId: String = "open"
    var serverName: String = "BornToCraft"
    var selectMenuTitle: String = "Select Category"
    var messages: BugReportMessages = BugReportMessages()
    var webhookSettings: BugReportWebhookSettings? = null
    var webhookService: BugReportWebhookService? = null

    fun openCategorySelection(player: Player) = openCategorySelection(player, categories)

    private fun ensureBaseInputs(category: BugReportCategory): List<BugReportInput> {
        val inputs = mutableListOf<BugReportInput>()
        val hasTitle = category.inputs.any { it.key.equals("title", true) }

        if (!hasTitle) {
            inputs.add(
                TextInput(
                    key = "title",
                    label = "Title",
                    placeholder = "",
                    maxLength = 80,
                ),
            )
        }

        inputs.addAll(category.inputs)
        return inputs
    }

    fun openCategorySelection(player: Player, availableCategories: List<BugReportCategory>) {
        if (availableCategories.isEmpty()) {
            player.sendMessage(Component.text("No categories available for bug reports."))
            return
        }

        val buttons = availableCategories.map { category ->
            ActionButton.builder(Component.text(category.displayName))
                .action(
                    io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                        { _, _ -> openReportForm(player, category) },
                        net.kyori.adventure.text.event.ClickCallback.Options.builder().build(),
                    ),
                )
                .build()
        }

        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(DialogBase.builder(Component.text(selectMenuTitle))
                    .body(listOf(DialogBody.plainMessage(Component.text("Choose a category for your report:"))))
                    .build())
                .type(DialogType.multiAction(buttons, null, 1))
        }

        player.showDialog(dialog)
    }

    fun openReportForm(player: Player, category: BugReportCategory) {
        val configuredInputs = ensureBaseInputs(category)
        val inputs = configuredInputs.flatMap { input ->
            when (input) {
                is TextInput -> listOf(
                    DialogInput.text(
                        input.key,
                        200,
                        Component.text(input.label),
                        true,
                        input.placeholder,
                        input.maxLength,
                        multilineOptions(
                            maxLines = if (input.multiline) 3 else 1,
                            height = 128,
                        ),
                    ),
                )
                is BooleanInput -> listOf(
                    DialogInput.bool(
                        input.key,
                        Component.text(input.label),
                        input.initial,
                        "Yes",
                        "No",
                    ),
                )
            }
        }

        val submitAction = ActionButton.builder(Component.text("Submit"))
            .action(io.papermc.paper.registry.data.dialog.action.DialogAction.customClick({ result, _ ->
                val customFields = mutableMapOf<String, String>()

                // Helper function to extract values recursively
                fun extractValue(input: BugReportInput) {
                    when (input) {
                        is TextInput -> {
                            val value = result.getText(input.key) ?: ""
                            customFields[input.key] = value
                        }
                        is BooleanInput -> {
                            val selected = result.getBoolean(input.key) ?: false
                            customFields[input.key] = if (selected) input.label else ""
                        }
                    }
                }

                configuredInputs.forEach { extractValue(it) }

                val title = customFields["title"]?.takeIf { it.isNotBlank() } ?: ""
                val message = customFields["description"]?.takeIf { it.isNotBlank() } ?: ""

                val report = BugReport(
                    id = repository.nextId(),
                    title = title,
                    message = message,
                    categoryId = category.id,
                    statusId = defaultStatusId,
                    playerName = player.name,
                    playerUuid = player.uniqueId,
                    worldName = player.world.name,
                    location = LocationSnapshot(
                        player.world.name,
                        player.location.x,
                        player.location.y,
                        player.location.z,
                        player.location.yaw,
                        player.location.pitch
                    ),
                    gameMode = player.gameMode.name,
                    serverName = serverName,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    customFields = customFields
                )

                repository.save(report)
                val success = messages.submissionSuccess
                    .replace("{id}", report.id)
                    .replace("{status}", report.statusId)
                player.sendMessage(Component.text(success))

                webhookSettings?.let { settings ->
                    webhookService?.send(
                        report,
                        category,
                        settings,
                        serverName,
                    )
                }
            }, net.kyori.adventure.text.event.ClickCallback.Options.builder().build()))
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(DialogBase.builder(Component.text(category.dialogTitle))
                    .inputs(inputs)
                    .build())
                .type(DialogType.multiAction(listOf(submitAction), null, 1))
        }

        player.showDialog(dialog)
    }

    private fun multilineOptions(maxLines: Int, height: Int): TextDialogInput.MultilineOptions? {
        if (maxLines <= 1) return null
        val safeHeight = height.coerceIn(1, 512)
        val safeLines = maxLines.coerceIn(2, 50)
        return TextDialogInput.MultilineOptions.create(safeLines, safeHeight)
    }
}
