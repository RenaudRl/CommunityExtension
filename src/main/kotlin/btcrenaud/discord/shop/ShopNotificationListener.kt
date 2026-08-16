package btcrenaud.discord.shop

import btcrenaud.discord.shop.entries.ShopNotificationManifestEntry
import btcrenaud.discord.webhook.WebhookEmbed
import btcrenaud.discord.webhook.WebhookEmbedField
import btcrenaud.discord.webhook.WebhookService
import com.typewritermc.core.entries.Query
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

/**
 * Relays completed shop transactions to the destination the shop names.
 *
 * Shops is optional. DiscordExtension registers this listener dynamically only when the
 * ShopTransactionEvent class is available, so the public artifact remains standalone.
 */
class ShopNotificationListener(
    private val webhookService: WebhookService,
) : Listener {

    private val miniMessage = MiniMessage.miniMessage()

    /** Called by the dynamic Bukkit event executor for ShopTransactionEvent. */
    fun onTransaction(event: Event) {
        val transaction = Transaction.from(event) ?: return
        if (transaction.notificationWebhookId.isBlank()) return

        val format = Query.find<ShopNotificationManifestEntry>()
            .firstOrNull { it.covers(transaction.shopId) && it.announces(transaction.isBuy) }
            ?: return
        if (transaction.price < format.minimumPrice) return

        val placeholders = mapOf(
            "player" to transaction.player.name,
            "shop" to transaction.shopId,
            "item" to transaction.item.readableName(),
            "amount" to transaction.amount.toString(),
            "price" to String.format("%.2f", transaction.price),
            "action" to if (transaction.isBuy) format.buyVerb else format.sellVerb,
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
            destinationId = transaction.notificationWebhookId,
            content = format.contentTemplate.fill(placeholders),
            embeds = listOf(embed),
        )
    }

    private data class Transaction(
        val player: Player,
        val shopId: String,
        val item: ItemStack,
        val amount: Int,
        val price: Double,
        val isBuy: Boolean,
        val notificationWebhookId: String,
    ) {
        companion object {
            fun from(event: Event): Transaction? = runCatching {
                val type = event.javaClass
                Transaction(
                    player = type.getMethod("getPlayer").invoke(event) as Player,
                    shopId = type.getMethod("getShopId").invoke(event) as String,
                    item = type.getMethod("getItem").invoke(event) as ItemStack,
                    amount = type.getMethod("getAmount").invoke(event) as Int,
                    price = type.getMethod("getPrice").invoke(event) as Double,
                    isBuy = type.getMethod("isBuy").invoke(event) as Boolean,
                    notificationWebhookId = type.getMethod("getNotificationWebhookId").invoke(event) as String,
                )
            }.getOrNull()
        }
    }

    private fun ShopNotificationManifestEntry.announces(isBuy: Boolean): Boolean =
        if (isBuy) announceBuys else announceSells

    private fun ItemStack.readableName(): String {
        val custom = itemMeta?.displayName()?.let { miniMessage.serialize(it) }
        if (!custom.isNullOrBlank()) return custom
        return type.name.lowercase().split('_')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    private fun String.fill(values: Map<String, String>): String =
        "\\{([^}]+)}".toRegex().replace(this) { match -> values[match.groupValues[1]] ?: "" }

    private fun String.toColorInt(): Int? = trim().removePrefix("#").toIntOrNull(16)
}
