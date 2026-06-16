package btcrenaud.community.webhook

import btcrenaud.community.common.WebhookSettings
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create(),
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
        val payload = mutableMapOf<String, Any?>()

        val contentWithPings = if (pingRoleIds.isNotEmpty()) {
            (content + " " + pingRoleIds.joinToString(" ") { "<@&$it>" }).trim()
        } else content
        payload["content"] = contentWithPings

        settings.username?.let { payload["username"] = it }
        settings.avatarUrl?.let { payload["avatar_url"] = it }

        if (embeds.isNotEmpty()) {
            payload["embeds"] = embeds.map { embed ->
                val embedMap = mutableMapOf<String, Any?>()
                embed.title?.let { embedMap["title"] = it }
                embed.description?.let { embedMap["description"] = it }
                embed.color?.let { embedMap["color"] = it }
                if (embed.fields.isNotEmpty()) {
                    embedMap["fields"] = embed.fields.map { field ->
                        mapOf(
                            "name" to field.name,
                            "value" to field.value,
                            "inline" to field.inline,
                        )
                    }
                }
                embedMap
            }
        }

        threadName?.takeIf { it.isNotBlank() }?.let { payload["thread_name"] = it }

        if (appliedTagIds.isNotEmpty()) {
            payload["applied_tags"] = appliedTagIds
        }

        if (pingRoleIds.isNotEmpty()) {
            payload["allowed_mentions"] = mapOf(
                "parse" to emptyList<String>(),
                "roles" to pingRoleIds,
            )
        }

        return gson.toJson(payload)
    }
}
