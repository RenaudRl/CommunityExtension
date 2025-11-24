package com.borntocraft.typewriter.community.webhook

import com.borntocraft.typewriter.community.common.WebhookSettings
import com.typewritermc.engine.paper.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class WebhookEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean,
)

data class WebhookEmbed(
    val title: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val fields: List<WebhookEmbedField> = emptyList(),
)

class WebhookSender(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) {

    fun send(
        settings: WebhookSettings,
        content: String,
        embeds: List<WebhookEmbed> = emptyList(),
        threadName: String? = null,
        appliedTagIds: List<String> = emptyList(),
        pingRoleIds: List<String> = emptyList(),
    ) {
        if (!settings.enabled || settings.url.isBlank()) return

        val payload = buildPayload(settings, content, embeds, threadName, appliedTagIds, pingRoleIds)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(settings.url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        }.onFailure { error ->
            logger.warning("Failed to deliver webhook (${settings.url.takeLast(6)}): ${error.message}")
        }
    }

    private fun buildPayload(
        settings: WebhookSettings,
        content: String,
        embeds: List<WebhookEmbed>,
        threadName: String?,
        appliedTagIds: List<String>,
        pingRoleIds: List<String>,
    ): String {
        val parts = mutableListOf<String>()
        val contentWithPings = if (pingRoleIds.isNotEmpty()) {
            (content + " " + pingRoleIds.joinToString(" ") { "<@&$it>" }).trim()
        } else content
        parts.add("\"content\":\"${contentWithPings.jsonEscape()}\"")
        settings.username?.let { parts.add("\"username\":\"${it.jsonEscape()}\"") }
        settings.avatarUrl?.let { parts.add("\"avatar_url\":\"${it.jsonEscape()}\"") }
        if (embeds.isNotEmpty()) {
            parts.add("\"embeds\":[${embeds.joinToString(",") { embedJson(it) }}]")
        }
        threadName?.takeIf { it.isNotBlank() }?.let {
            parts.add("\"thread_name\":\"${it.jsonEscape()}\"")
        }
        if (appliedTagIds.isNotEmpty()) {
            val tags = appliedTagIds.joinToString(",") { "\"${it.jsonEscape()}\"" }
            parts.add("\"applied_tags\":[$tags]")
        }
        if (pingRoleIds.isNotEmpty()) {
            parts.add("\"allowed_mentions\":{\"parse\":[],\"roles\":[${pingRoleIds.joinToString(",") { "\"${it.jsonEscape()}\"" }}]}")
        }
        return "{${parts.joinToString(",")}}"
    }

    private fun embedJson(embed: WebhookEmbed): String {
        val parts = mutableListOf<String>()
        embed.title?.let { parts.add("\"title\":\"${it.jsonEscape()}\"") }
        embed.description?.let { parts.add("\"description\":\"${it.jsonEscape()}\"") }
        embed.color?.let { parts.add("\"color\":$it") }
        if (embed.fields.isNotEmpty()) {
            val fieldsJson = embed.fields.joinToString(",") { field ->
                val entries = listOf(
                    "\"name\":\"${field.name.jsonEscape()}\"",
                    "\"value\":\"${field.value.jsonEscape()}\"",
                    "\"inline\":${field.inline}",
                )
                "{${entries.joinToString(",")}}"
            }
            parts.add("\"fields\":[${fieldsJson}]")
        }
        return "{${parts.joinToString(",")}}"
    }
}

private fun String.jsonEscape(): String =
    this.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
