package btcrenaud.discord.link.service

import btcrenaud.discord.link.data.LinkRecord
import btcrenaud.discord.link.data.PendingLink
import btcrenaud.discord.link.data.asReadable
import btcrenaud.discord.link.data.remainingDuration
import btcrenaud.discord.client.DiscordClientService
import btcrenaud.discord.link.entries.DiscordLinkManifestEntry
import btcrenaud.discord.webhook.WebhookEmbed
import btcrenaud.discord.webhook.WebhookEmbedField
import btcrenaud.discord.webhook.WebhookService
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.server
import net.dv8tion.jda.api.entities.Role
import org.bukkit.entity.Player
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

private const val CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

class DiscordLinkService(
    private val repository: DiscordLinkRepository,
    private val manifest: DiscordLinkManifestEntry,
    private val discordClient: DiscordClientService,
    private val webhookService: WebhookService,
) {
    private val random = SecureRandom()

    // -- Player facing commands ---------------------------------------------

    /** Handles `/{command}`: generates (or repeats) a verification code. */
    fun requestCode(player: Player) {
        val messages = manifest.messages
        if (repository.findLink(player.uniqueId) != null && !manifest.autoOverwriteExistingLink) {
            player.sendMessage(messages.alreadyLinked.asMini())
            return
        }

        val now = System.currentTimeMillis()
        repository.cleanupExpired(now)

        val existing = repository.findPendingByPlayer(player.uniqueId)
        if (existing != null && existing.expiresAt > now) {
            player.sendMessage(
                messages.pendingExists
                    .replace("{code}", existing.code)
                    .replace("{duration}", existing.remainingDuration().asReadable())
                    .asMini()
            )
            return
        }

        val code = generateRandomCode(manifest.codeLength.coerceAtLeast(1))
        val pending = PendingLink(
            code = code,
            playerUuid = player.uniqueId,
            playerName = player.name,
            createdAt = now,
            expiresAt = now + manifest.codeValidity.toMillis(),
        )
        repository.savePending(pending)

        val duration = manifest.codeValidity.asReadable()
        player.sendMessage(
            messages.codeGenerated
                .replace("{code}", code)
                .replace("{duration}", duration)
                .asMini()
        )
        sendWebhookInstruction(code, player.name)
    }

    /** Handles `/{command} unlink`. */
    fun requestUnlink(player: Player) {
        val messages = manifest.messages
        if (unlinkPlayer(player)) {
            player.sendMessage(messages.unlinkSuccess.asMini())
        } else {
            player.sendMessage(messages.unlinkNoLink.asMini())
        }
    }

    // -- Verification (called from the Discord bot listener) ----------------

    fun verifyCode(code: String, discordId: String, discordUsername: String): Boolean {
        val pending = repository.findPending(code) ?: return false

        if (pending.expiresAt < System.currentTimeMillis()) {
            repository.removePending(code)
            return false
        }

        if (repository.isDiscordAlreadyLinked(discordId)) {
            repository.removePending(code)
            return false
        }

        val existing = repository.findLink(pending.playerUuid)
        if (existing != null && !manifest.autoOverwriteExistingLink) {
            repository.removePending(code)
            return false
        }

        val link = LinkRecord(
            playerUuid = pending.playerUuid,
            playerName = pending.playerName,
            discordId = discordId,
            discordUsername = discordUsername,
            linkedAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
            roles = existing?.roles ?: emptyList(),
        )

        repository.saveLink(link)
        repository.removePending(code)

        val confirmation = manifest.messages.linkConfirmed
            .replace("{discord}", link.discordUsername)
            .replace("{player}", link.playerName)
        notifyWebhook(manifest.messages.embedLinkedTitle, confirmation, link, Instant.ofEpochMilli(link.linkedAt))
        notifyStaff(confirmation)

        // Sync roles and fire the configured link triggers when the player is online.
        server.getPlayer(pending.playerUuid)?.let { player ->
            syncRoles(player)
            if (manifest.onLinkTriggers.isNotEmpty()) {
                manifest.onLinkTriggers.triggerEntriesFor(player, context())
            }
        }

        return true
    }

    fun isLinked(playerUuid: UUID): Boolean = repository.findLink(playerUuid) != null

    /**
     * Unlinks a player from Discord, removing their mapped roles and deleting the link record.
     * @return true if successfully unlinked, false if no link existed
     */
    fun unlinkPlayer(player: Player): Boolean {
        val link = repository.findLink(player.uniqueId) ?: return false

        val guild = discordClient.getGuild()
        if (guild != null) {
            guild.retrieveMemberById(link.discordId).queue({ member ->
                val mappedRoles = manifest.roleMappings.mapNotNull { guild.getRoleById(it.discordRoleId) }
                val rolesToRemove = mappedRoles.filter { member.roles.contains(it) }
                if (rolesToRemove.isNotEmpty()) {
                    guild.modifyMemberRoles(member, emptyList(), rolesToRemove).queue({
                        logger.info("Removed Discord roles for ${player.name}: ${rolesToRemove.map { it.name }}")
                    }, { error ->
                        logger.warning("Failed to remove Discord roles for ${player.name}: ${error.message}")
                    })
                }
            }, { error ->
                logger.warning("Failed to retrieve Discord member for ${player.name} (${link.discordId}): ${error.message}")
            })
        }

        repository.removeLink(player.uniqueId)

        val content = manifest.messages.linkRevoked
            .replace("{discord}", link.discordUsername)
            .replace("{player}", link.playerName)
        notifyWebhook(manifest.messages.embedUnlinkedTitle, content, link, Instant.now())

        return true
    }

    fun syncRoles(player: Player) {
        val link = repository.findLink(player.uniqueId) ?: return
        val guild = discordClient.getGuild()
        if (guild == null) {
            logger.warning("Cannot sync roles: Discord Guild not found or bot not connected.")
            return
        }

        guild.retrieveMemberById(link.discordId).queue({ member ->
            val mappings = manifest.roleMappings
            val selectedMapping = mappings.firstOrNull {
                player.hasPermission(manifest.groupPermissionFormat.replace("{group}", it.minecraftGroup))
            }

            val rolesToAdd = mutableListOf<Role>()
            val rolesToRemove = mutableListOf<Role>()

            val mappedRoles = mappings.mapNotNull { guild.getRoleById(it.discordRoleId) }
            val targetRole = selectedMapping?.let { guild.getRoleById(it.discordRoleId) }

            mappedRoles.forEach { role ->
                val shouldHave = role == targetRole
                val hasRole = member.roles.contains(role)
                if (shouldHave && !hasRole) rolesToAdd.add(role)
                if (!shouldHave && hasRole) rolesToRemove.add(role)
            }

            if (rolesToAdd.isNotEmpty() || rolesToRemove.isNotEmpty()) {
                guild.modifyMemberRoles(member, rolesToAdd, rolesToRemove).queue()
                logger.info(
                    "Synced roles for ${player.name}: added ${rolesToAdd.map { it.name }}, removed ${rolesToRemove.map { it.name }}"
                )
            }
        }, { error ->
            logger.warning("Failed to retrieve Discord member for ${player.name} (${link.discordId}): ${error.message}")
        })
    }

    // -- Internals -----------------------------------------------------------

    private fun sendWebhookInstruction(code: String, playerName: String) {
        if (!webhookService.isUsable(manifest.webhook)) return

        val content = manifest.messages.linkInstructions
            .replace("{code}", code)
            .replace("{duration}", manifest.codeValidity.asReadable())
            .replace("{player}", playerName)

        webhookService.send(manifest.webhook, content)
    }

    private fun notifyWebhook(title: String, content: String, link: LinkRecord, timestamp: Instant) {
        if (!webhookService.isUsable(manifest.webhook)) return

        val embed = WebhookEmbed(
            title = title,
            description = content,
            fields = listOf(
                WebhookEmbedField("Player", link.playerName, true),
                WebhookEmbedField("Discord", link.discordUsername, true),
                WebhookEmbedField("At", timestamp.toString(), false),
            ),
        )
        webhookService.send(manifest.webhook, content, listOf(embed))
    }

    private fun notifyStaff(message: String) {
        val permission = manifest.notifyPermission
        if (permission.isBlank()) return
        server.onlinePlayers
            .filter { it.hasPermission(permission) }
            .forEach { it.sendMessage(message.asMini()) }
    }

    private fun generateRandomCode(length: Int): String {
        repository.countCodeGenerated()
        return buildString(length) {
            repeat(length) { append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]) }
        }
    }
}
