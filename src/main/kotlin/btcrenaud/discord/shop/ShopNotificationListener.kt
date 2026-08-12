package btcrenaud.discord.shop

import btcrenaud.discord.shop.entries.ShopNotificationManifestEntry
import btcrenaud.discord.webhook.WebhookEmbed
import btcrenaud.discord.webhook.WebhookEmbedField
import btcrenaud.discord.webhook.WebhookService
import com.btc.shops.api.ShopTransactionEvent
import com.typewritermc.core.entries.Query
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Relays completed shop transactions to the destination the shop names.
 *
 * The coupling runs one way only: Shops publishes an event and knows nothing of Discord, while
 * this listener is compiled against Shops and registered only when the class is actually on the
 * server. A server without the Shops extension never loads this class.
 */
class ShopNotificationListener(
    private val webhookService: WebhookService,
) : Listener {

    private val miniMessage = MiniMessage.miniMessage()

    // The event is announced after the fact and carries no cancellation, so MONITOR is the only
    // honest priority here.
    @EventHandler(priority = EventPriority.MONITOR)
    fun onTransaction(event: ShopTransactionEvent) {
        if (event.notificationWebhookId.isBlank()) return

        val format = Query.find<ShopNotificationManifestEntry>()
            .firstOrNull { it.covers(event.shopId) && it.announces(event.isBuy) }
            ?: return
        if (event.price < format.minimumPrice) return

        val placeholders = mapOf(
            "player" to event.player.name,
            "shop" to event.shopId,
            "item" to event.item.readableName(),
            "amount" to event.amount.toString(),
            "price" to String.format("%.2f", event.price),
            "action" to if (event.isBuy) format.buyVerb else format.sellVerb,
        )

        val embed = WebhookEmbed(
            title = format.titleTemplate.fill(placeholders),
            description = null,
            color = format.color.toColorInt(),
            fields = format.fields.map {
                WebhookEmbedField(it.name, it.value.fill(placeholders), it.inline)
            },
        )
        webhookService.send(
            destinationId = event.notificationWebhookId,
            content = format.contentTemplate.fill(placeholders),
            embeds = listOf(embed),
        )
    }

    private fun ShopNotificationManifestEntry.announces(isBuy: Boolean): Boolean =
        if (isBuy) announceBuys else announceSells

    private fun org.bukkit.inventory.ItemStack.readableName(): String {
        val custom = itemMeta?.displayName()?.let { miniMessage.serialize(it) }
        if (!custom.isNullOrBlank()) return custom
        return type.name.lowercase().split('_')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    private fun String.fill(values: Map<String, String>): String =
        "\\{([^}]+)}".toRegex().replace(this) { match -> values[match.groupValues[1]] ?: "" }

    private fun String.toColorInt(): Int? = trim().removePrefix("#").toIntOrNull(16)
}
