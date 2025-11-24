package com.borntocraft.typewriter.community.bugreport.data

import com.borntocraft.typewriter.community.common.WebhookSettings
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder

data class BugReportWebhookFieldTemplate(
    @Help("Label displayed in the Discord embed field")
    val name: String = "Player",
    @Help("Value shown in the Discord embed field (placeholders supported)")
    val value: String = "{player}",
    @Help("Display this field on the same line when possible")
    val inline: Boolean = false,
)

data class BugReportWebhookSettings(
    @Help("Enable forwarding bug reports to a Discord webhook")
    val enabled: Boolean = false,
    @Help("Webhook configuration that will receive the bug reports")
    val destination: WebhookSettings = WebhookSettings(),
    @Placeholder
    @MultiLine
    @Help("Thread name template when posting to a Discord Forum channel (required for forums). Placeholders: {id},{title},{message},{category},{status},{player},{server},{world},{x},{y},{z},{gamemode},{created_at} + custom fields")
    val threadNameTemplate: String = "Bug #{id}: {title}",
    @Help("Default Discord forum tag IDs applied to the created thread")
    val defaultTagIds: List<String> = emptyList(),
    @Help("Role IDs to ping on new reports (formatted as Discord role IDs)")
    val pingRoleIds: List<String> = emptyList(),
    @Placeholder
    @MultiLine
    @Help("Optional message content sent alongside the embed (supports placeholders: {id},{title},{message},{category},{status},{player},{server},{world},{x},{y},{z},{gamemode},{created_at} and any custom field key)")
    val contentTemplate: String = "",
    @Placeholder
    @MultiLine
    @Help("Title template for the Discord embed (placeholders: {id},{title},{message},{category},{status},{player},{server},{world},{x},{y},{z},{gamemode},{created_at} + custom fields)")
    val titleTemplate: String = "{title}",
    @Help("Hex color applied to the embed sidebar (e.g. #ff5555)")
    val color: String = "#ff5555",
    @Help("Fields added to the embed body")
    val fields: List<BugReportWebhookFieldTemplate> = listOf(
        BugReportWebhookFieldTemplate("Player", "{player}", true),
        BugReportWebhookFieldTemplate("Category", "{category}", true),
        BugReportWebhookFieldTemplate("Server", "{server}", true),
        BugReportWebhookFieldTemplate("World", "{world}", true),
        BugReportWebhookFieldTemplate("Location", "{x}, {y}, {z}", false),
    ),
)
