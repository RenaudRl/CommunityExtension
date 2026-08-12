package btcrenaud.discord.webhook.migration

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookSchemaMigrationTest {

    private fun entries(json: String): JsonArray = JsonParser.parseString(json).asJsonArray

    private fun definitions(entries: JsonArray): List<JsonObject> = entries
        .map { it.asJsonObject }
        .filter { it.get("type")?.asString == "webhook_definition" }

    @Test
    fun `two manifests on the same destination share one definition`() {
        val page = entries(
            """
            [
              {"id":"chat","type":"chat_sync_manifest",
               "webhook":{"enabled":true,"url":"https://discord.com/api/webhooks/1/a","username":"Server","avatarUrl":""}},
              {"id":"link","type":"discord_link_manifest",
               "webhook":{"enabled":true,"url":"https://discord.com/api/webhooks/1/a","username":"Server","avatarUrl":""}}
            ]
            """.trimIndent(),
        )

        val normalizer = WebhookSchemaNormalizer()
        assertTrue(normalizer.convertEntries(page))
        assertTrue(normalizer.materializePendingDefinitions(page))

        val created = definitions(page)
        assertEquals(1, created.size, "one URL must not produce two definitions")
        val id = created.single().get("id").asString
        assertEquals(id, page[0].asJsonObject.get("webhook").asString)
        assertEquals(id, page[1].asJsonObject.get("webhook").asString)
        assertEquals("https://discord.com/api/webhooks/1/a", created.single().get("url").asString)
        assertTrue(created.single().get("enabled").asBoolean)
    }

    @Test
    fun `bug report loses its second switch and keeps the URL`() {
        val page = entries(
            """
            [
              {"id":"bugs","type":"bugreport_manifest",
               "webhook":{"enabled":false,"titleTemplate":"{title}",
                 "destination":{"enabled":true,"url":"https://discord.com/api/webhooks/2/b","username":"","avatarUrl":""}}}
            ]
            """.trimIndent(),
        )

        val normalizer = WebhookSchemaNormalizer()
        assertTrue(normalizer.convertEntries(page))
        normalizer.materializePendingDefinitions(page)

        val settings = page[0].asJsonObject.getAsJsonObject("webhook")
        assertFalse(settings.has("enabled"), "the outer switch must not survive")
        assertEquals("{title}", settings.get("titleTemplate").asString, "templates are untouched")

        val definition = definitions(page).single()
        assertEquals(definition.get("id").asString, settings.get("destination").asString)
        // The manifest was switched off: the destination is kept but silent, so the operator
        // recovers the URL instead of having to type it again.
        assertFalse(definition.get("enabled").asBoolean)
        assertEquals("https://discord.com/api/webhooks/2/b", definition.get("url").asString)
    }

    @Test
    fun `a destination without URL becomes an empty reference`() {
        val page = entries(
            """[{"id":"chat","type":"chat_sync_manifest","webhook":{"enabled":true,"url":"","username":"","avatarUrl":""}}]""",
        )

        val normalizer = WebhookSchemaNormalizer()
        assertTrue(normalizer.convertEntries(page))
        assertFalse(normalizer.materializePendingDefinitions(page))

        assertEquals("", page[0].asJsonObject.get("webhook").asString)
        assertTrue(definitions(page).isEmpty(), "an empty destination is not worth an entry")
    }

    @Test
    fun `a second pass changes nothing`() {
        val page = entries(
            """
            [{"id":"chat","type":"chat_sync_manifest",
              "webhook":{"enabled":true,"url":"https://discord.com/api/webhooks/3/c","username":"","avatarUrl":""}}]
            """.trimIndent(),
        )

        val first = WebhookSchemaNormalizer()
        first.convertEntries(page)
        first.materializePendingDefinitions(page)
        val afterFirst = page.deepCopy()

        // A fresh normalizer stands for the next server start: it must recognise the definition
        // already present and leave the converted references alone.
        val second = WebhookSchemaNormalizer()
        second.registerExistingDefinitions(page)
        assertFalse(second.convertEntries(page), "converted pages must report no change")
        assertFalse(second.materializePendingDefinitions(page), "no duplicate definition")
        assertEquals(afterFirst, page)
    }
}
