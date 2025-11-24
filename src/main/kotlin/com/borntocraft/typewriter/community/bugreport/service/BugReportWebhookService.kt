package com.borntocraft.typewriter.community.bugreport.service

import com.borntocraft.typewriter.community.bugreport.data.BugReport
import com.borntocraft.typewriter.community.bugreport.data.BugReportCategory
import com.borntocraft.typewriter.community.bugreport.data.BugReportStatus
import com.borntocraft.typewriter.community.bugreport.data.BugReportWebhookFieldTemplate
import com.borntocraft.typewriter.community.bugreport.data.BugReportWebhookSettings
import com.borntocraft.typewriter.community.webhook.WebhookEmbed
import com.borntocraft.typewriter.community.webhook.WebhookEmbedField
import com.borntocraft.typewriter.community.webhook.WebhookSender
import kotlin.math.roundToInt

class BugReportWebhookService(
    private val webhookSender: WebhookSender,
) {
    fun send(
        report: BugReport,
        category: BugReportCategory?,
        settings: BugReportWebhookSettings,
        serverName: String,
    ) {
        if (!settings.enabled || settings.destination.url.isBlank()) return

        val placeholders = buildPlaceholders(report, category, serverName)
        val title = settings.titleTemplate.safeWith(placeholders)
        val fields = settings.fields.map { it.toEmbedField(placeholders) }
        val color = settings.color.toColorInt()
        val embed = WebhookEmbed(title = title, description = null, color = color, fields = fields)
        val content = settings.contentTemplate.safeWith(placeholders)

        val threadName = settings.threadNameTemplate.safeWith(placeholders)
        webhookSender.send(
            settings.destination,
            content,
            listOf(embed),
            threadName = threadName,
            appliedTagIds = settings.defaultTagIds,
            pingRoleIds = settings.pingRoleIds,
        )
    }

    private fun BugReportWebhookFieldTemplate.toEmbedField(placeholders: Map<String, String>): WebhookEmbedField {
        val value = value.safeWith(placeholders)
        return WebhookEmbedField(name, value, inline)
    }

    private fun String.safeWith(values: Map<String, String>): String {
        val regex = "\\{([^}]+)}".toRegex()
        return regex.replace(this) { match ->
            values[match.groupValues[1]] ?: ""
        }
    }

    private fun buildPlaceholders(
        report: BugReport,
        category: BugReportCategory?,
        serverName: String,
    ): Map<String, String> = buildMap {
        put("id", report.id)
        put("title", report.title)
        put("message", report.message)
        put("category", category?.displayName ?: (report.categoryId ?: ""))
        put("status", report.statusId.ifBlank { "open" })
        put("player", report.playerName)
        put("server", serverName)
        put("world", report.worldName)
        put("x", report.location.x.roundToInt().toString())
        put("y", report.location.y.roundToInt().toString())
        put("z", report.location.z.roundToInt().toString())
        put("gamemode", report.gameMode)
        put("created_at", report.createdAt.toString())
        putAll(report.customFields)
    }

    private fun String.toColorInt(): Int? {
        val cleaned = this.trim().removePrefix("#")
        return cleaned.toIntOrNull(16)
    }
}
