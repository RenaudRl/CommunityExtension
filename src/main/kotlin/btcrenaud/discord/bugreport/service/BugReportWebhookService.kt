package btcrenaud.discord.bugreport.service

import btcrenaud.discord.bugreport.data.BugReport
import btcrenaud.discord.bugreport.data.BugReportCategory
import btcrenaud.discord.bugreport.data.BugReportStatus
import btcrenaud.discord.bugreport.data.BugReportWebhookFieldTemplate
import btcrenaud.discord.bugreport.data.BugReportWebhookSettings
import btcrenaud.discord.webhook.WebhookEmbed
import btcrenaud.discord.webhook.WebhookEmbedField
import btcrenaud.discord.webhook.WebhookService
import kotlin.math.roundToInt

class BugReportWebhookService(
    private val webhookService: WebhookService,
) {
    fun send(
        report: BugReport,
        category: BugReportCategory?,
        settings: BugReportWebhookSettings,
        serverName: String,
    ) {
        // The destination alone carries the enabled/disabled state: no point building a full
        // report only to discover it goes nowhere.
        if (!webhookService.isUsable(settings.destination)) return

        val placeholders = buildPlaceholders(report, category, serverName)
        val title = settings.titleTemplate.safeWith(placeholders)
        val fields = settings.fields.map { it.toEmbedField(placeholders) }
        val color = settings.color.toColorInt()
        val embed = WebhookEmbed(title = title, description = null, color = color, fields = fields)
        val content = settings.contentTemplate.safeWith(placeholders)

        val threadName = settings.threadNameTemplate.safeWith(placeholders)
        webhookService.send(
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
