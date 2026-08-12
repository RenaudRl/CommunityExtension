package btcrenaud.discord.webhook

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry

/**
 * A Discord destination, declared once and referenced everywhere.
 *
 * Each subsystem used to carry its own copy of the settings (URL, username, avatar): changing
 * channel meant editing every manifest, and nothing guaranteed they pointed at the same place.
 * The destination becomes an entry of its own, which makes it reusable — a bug report, a shop
 * sale and a player death can target the same channel without the URL being copied three times.
 *
 * Any extension can consume it: put a `Ref<WebhookDefinitionEntry>` on your entry and call
 * [WebhookService].
 */
@Entry("webhook_definition", "A reusable Discord webhook destination", Colors.MEDIUM_PURPLE, "mdi:webhook")
@Tags("discord", "webhook")
class WebhookDefinitionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Silences delivery to this destination without having to remove the references pointing at it.")
    val enabled: Boolean = true,
    @Help("Full Discord webhook URL.")
    val url: String = "",
    @Help("Username shown instead of the webhook's own. Empty = the one configured on Discord.")
    val username: String = "",
    @Help("Avatar URL shown instead of the webhook's own. Empty = the one configured on Discord.")
    val avatarUrl: String = "",
    @Help("Roles mentioned by EVERY message going through this destination. One-off mentions stay the caller's business.")
    val pingRoleIds: List<String> = emptyList(),
) : ManifestEntry {

    /** True when this destination can actually receive a message. */
    val isUsable: Boolean get() = enabled && url.isNotBlank()
}
