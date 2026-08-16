package btcrenaud.discord.webhook.fact.entries

import btcrenaud.discord.webhook.WebhookDefinitionEntry
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.AudienceEntry
import com.typewritermc.engine.paper.entry.entries.EventEntry
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.entry.TriggerableEntry

enum class FactWebhookScope {
    PLAYER,
    GROUP,
    GLOBAL,
}

enum class FactValueOperator {
    ANY,
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUALS,
}

data class FactValueCondition(
    @Help("Comparison applied to the value before or after the fact update")
    val operator: FactValueOperator = FactValueOperator.ANY,
    @Help("Value used by the comparison")
    val value: Int = 0,
) {
    fun matches(candidate: Int): Boolean = when (operator) {
        FactValueOperator.ANY -> true
        FactValueOperator.EQUALS -> candidate == value
        FactValueOperator.NOT_EQUALS -> candidate != value
        FactValueOperator.GREATER_THAN -> candidate > value
        FactValueOperator.GREATER_THAN_OR_EQUALS -> candidate >= value
        FactValueOperator.LESS_THAN -> candidate < value
        FactValueOperator.LESS_THAN_OR_EQUALS -> candidate <= value
    }
}

data class FactWebhookField(
    @Help("Label displayed in the Discord embed")
    val name: String = "Fact",
    @Placeholder
    @MultiLine
    @Help("Field value. Supports the fact event placeholders")
    val value: String = "{fact}: {new_value}",
    @Help("Display this field inline when possible")
    val inline: Boolean = true,
)

data class FactWebhookEmbed(
    @Help("Send an embed in addition to the message content")
    val enabled: Boolean = true,
    @Placeholder
    @MultiLine
    @Help("Embed title template")
    val titleTemplate: String = "{fact}",
    @Placeholder
    @MultiLine
    @Help("Embed description template")
    val descriptionTemplate: String = "{player} changed the fact from {previous_value} to {new_value}.",
    @Help("Hex color, for example #5865F2")
    val color: String = "#5865F2",
    @Help("Fields added to the embed")
    val fields: List<FactWebhookField> = listOf(
        FactWebhookField("Player", "{player}", true),
        FactWebhookField("Value", "{previous_value} → {new_value}", true),
    ),
)

/**
 * Publishes a Discord webhook whenever a Typewriter fact changes.
 *
 * The watcher uses Typewriter's player-facing fact tracker. This means a grouped fact is read
 * through the same group implementation as the rest of the engine instead of reading storage
 * directly, and the entry remains compatible with custom fact implementations.
 */
@Entry(
    "webhook_fact_event",
    "Publish a Discord webhook when a Typewriter fact changes",
    Colors.MEDIUM_PURPLE,
    "mdi:chart-line",
)
@Tags("discord", "webhook", "fact", "event")
class WebhookFactEventEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Keep this event disabled without removing the manifest entry")
    val enabled: Boolean = true,
    @Help("Readable Typewriter fact to observe")
    val fact: Ref<ReadableFactEntry> = emptyRef(),
    @Help("Reusable Discord destination receiving the event")
    val destination: Ref<WebhookDefinitionEntry> = emptyRef(),
    @Help("How often the event is published: once per player, group or fact globally")
    val scope: FactWebhookScope = FactWebhookScope.PLAYER,
    @Help("Optional Typewriter audience. Empty means every player observing the fact")
    val audience: Ref<out AudienceEntry> = emptyRef(),
    @Help("Previous value must satisfy all configured conditions")
    val previousValue: List<FactValueCondition> = emptyList(),
    @Help("New value must satisfy all configured conditions")
    val newValue: List<FactValueCondition> = emptyList(),
    @Placeholder
    @MultiLine
    @Help("Message content. Placeholders: {player}, {player_uuid}, {fact}, {fact_name}, {previous_value}, {new_value}, {change}, {scope}, {group}, {players}, {timestamp}")
    val contentTemplate: String = "{player} updated {fact}: {previous_value} → {new_value}",
    @Help("Optional Discord forum thread name")
    val threadNameTemplate: String = "",
    @Help("Optional embed configuration")
    val embed: FactWebhookEmbed = FactWebhookEmbed(),
    @Help("Additional Discord role IDs to mention")
    val pingRoleIds: List<String> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
) : EventEntry
