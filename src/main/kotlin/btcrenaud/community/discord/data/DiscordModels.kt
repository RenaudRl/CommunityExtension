package btcrenaud.community.discord.data

import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Represents a persistent association between a Minecraft player and a Discord account.
 */
data class LinkRecord(
    val playerUuid: UUID,
    var playerName: String,
    var discordId: String,
    var discordUsername: String,
    var linkedAt: Long,
    var lastUpdated: Long,
    var roles: List<String>,
)

/**
 * Configuration for mapping a Minecraft permission group/rank to a Discord Role ID.
 */
data class RoleMapping(
    val minecraftGroup: String = "default",
    val discordRoleId: String = "000000000000000000",
)

/**
 * Represents a pending verification code waiting for confirmation.
 */
data class PendingLink(
    val code: String,
    val playerUuid: UUID,
    val playerName: String,
    val createdAt: Long,
    val expiresAt: Long,
)

/**
 * Messages configurable through the manifest entry.
 */
data class DiscordLinkMessages(
    val alreadyLinked: String = "You have already linked your Discord account.",
    val linkInstructions: String = "Use the code **{code}** within {duration} on Discord to complete the link.",
    val codeGenerated: String = "A new Discord verification code has been generated: {code} (expires in {duration}).",
    val pendingExists: String = "You already have a pending code: {code} (expires in {duration}).",
    val linkConfirmed: String = "Discord account {discord} is now linked to {player}.",
    val linkRevoked: String = "The link with {discord} has been revoked.",
    val unlinkSuccess: String = "Your Discord account has been unlinked successfully.",
    val unlinkNoLink: String = "You don't have a Discord account linked.",
    val linkUnavailable: String = "Discord linking is not configured on this server.",
    val embedLinkedTitle: String = "Discord account linked",
    val embedUnlinkedTitle: String = "Discord account unlinked",
)

/**
 * Storage persisted inside the artifact entry.
 */
data class DiscordLinkStorage(
    var codesGenerated: Long = 0,
    val links: MutableList<LinkRecord> = mutableListOf(),
    val pending: MutableList<PendingLink> = mutableListOf(),
)

internal fun PendingLink.remainingDuration(): Duration {
    val now = Instant.now()
    val expiry = Instant.ofEpochMilli(expiresAt)
    return if (expiry.isAfter(now)) Duration.between(now, expiry) else Duration.ZERO
}

internal fun Duration.asReadable(): String {
    val seconds = coerceAtLeast(Duration.ZERO).seconds
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes % 60)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds % 60)
        else -> String.format(Locale.US, "%ds", seconds)
    }
}
