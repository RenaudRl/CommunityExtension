package btcrenaud.discord.shop.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry

data class ShopNotificationField(
    @Help("Label displayed in the Discord embed field")
    val name: String = "Player",
    @Help("Value shown in the Discord embed field (placeholders supported)")
    val value: String = "{player}",
    @Help("Display this field on the same line when possible")
    val inline: Boolean = true,
)

/**
 * How a shop transaction reads once it reaches Discord.
 *
 * The destination is deliberately absent: it belongs to the shop, which names it on its own
 * definition. This entry only says how the message looks, so one presentation can serve every
 * shop and each shop still posts to its own channel.
 *
 * Without this entry nothing is sent, whatever the shops declare.
 */
@Entry(
    name = "shop_notification_manifest",
    description = "Format shop transactions announced on Discord",
    color = Colors.GREEN,
    icon = "mdi:cart-arrow-right",
)
@Tags("discord", "shop", "manifest")
class ShopNotificationManifestEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Shop ids this format applies to. Empty = every shop that names a destination.")
    val shopIds: List<String> = emptyList(),
    @Help("Announce purchases")
    val announceBuys: Boolean = true,
    @Help("Announce sales")
    val announceSells: Boolean = true,
    @Help("Announce nothing below this amount of money, so bulk trading does not flood the channel")
    val minimumPrice: Double = 0.0,
    @Placeholder
    @MultiLine
    @Help("Title of the embed. Placeholders: {player},{shop},{item},{amount},{price},{action}")
    val titleTemplate: String = "{player} {action} {amount}x {item}",
    @Placeholder
    @MultiLine
    @Help("Optional message content sent alongside the embed. Same placeholders as the title.")
    val contentTemplate: String = "",
    @Help("Wording substituted for {action} on a purchase")
    val buyVerb: String = "bought",
    @Help("Wording substituted for {action} on a sale")
    val sellVerb: String = "sold",
    @Help("Hex color applied to the embed sidebar (e.g. #55ff55)")
    val color: String = "#55ff55",
    @Help("Fields added to the embed body")
    val fields: List<ShopNotificationField> = listOf(
        ShopNotificationField("Shop", "{shop}", true),
        ShopNotificationField("Price", "{price}", true),
    ),
) : ManifestEntry {

    /** True when [shopId] is covered by this format. */
    fun covers(shopId: String): Boolean = shopIds.isEmpty() || shopId in shopIds
}
