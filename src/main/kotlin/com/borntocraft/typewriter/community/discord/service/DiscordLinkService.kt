package com.borntocraft.typewriter.community.discord.service

import com.borntocraft.typewriter.community.discord.data.DiscordLinkMessages
import com.borntocraft.typewriter.community.discord.data.LinkRecord
import com.borntocraft.typewriter.community.discord.data.PendingLink
import com.borntocraft.typewriter.community.discord.entries.DiscordLinkManifestEntry
import com.borntocraft.typewriter.community.discord.data.asReadable
import com.borntocraft.typewriter.community.webhook.WebhookEmbed
import com.borntocraft.typewriter.community.webhook.WebhookEmbedField
import com.borntocraft.typewriter.community.webhook.WebhookSender
import com.typewritermc.engine.paper.logger
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class DiscordLinkService(
    private val repository: DiscordLinkRepository,
    private val manifest: DiscordLinkManifestEntry,
    private val discordClient: DiscordClientService,
    private val webhookSender: WebhookSender,
) {

    fun generateCode(player: Player): String {
        val now = System.currentTimeMillis()
        repository.cleanupExpired(now)
        val existing = repository.findPendingByPlayer(player.uniqueId)
        if (existing != null && existing.expiresAt > System.currentTimeMillis()) {
            return existing.code
        }

        // If limit reached and nothing active after cleanup, still generate a fresh code to avoid empty responses

        val code = generateRandomCode(manifest.codeLength)
        val pending = PendingLink(
            code = code,
            playerUuid = player.uniqueId,
            playerName = player.name,
            createdAt = now,
            expiresAt = now + manifest.codeValidity.toMillis(),
        )
        repository.savePending(pending)

        return code
    }

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

        notifyWebhookLinked(link, manifest.messages)

        // Try immediate role sync if the player is online
        val onlinePlayer = org.bukkit.Bukkit.getPlayer(pending.playerUuid)
        if (onlinePlayer != null && onlinePlayer.isOnline) {
            syncRoles(onlinePlayer)
        }

        return true
    }

    fun unlink(playerUuid: UUID) {
        repository.removeLink(playerUuid)
    }

    fun isLinked(playerUuid: UUID): Boolean = repository.findLink(playerUuid) != null

    fun syncRoles(player: Player) {
        val link = repository.findLink(player.uniqueId) ?: return
        val guild = discordClient.getGuild()
        if (guild == null) {
            logger.warning("Cannot sync roles: Discord Guild not found or bot not connected.")
            return
        }

        guild.retrieveMemberById(link.discordId).queue({ member ->
            val mappings = manifest.roleMappings
            val selectedMapping = mappings.firstOrNull { player.hasPermission("group.${it.minecraftGroup}") }

            val rolesToAdd = mutableListOf<net.dv8tion.jda.api.entities.Role>()
            val rolesToRemove = mutableListOf<net.dv8tion.jda.api.entities.Role>()

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

    private fun sendWebhookInstruction(code: String, playerName: String, messages: DiscordLinkMessages) {
        val settings = manifest.webhook
        if (!settings.enabled || settings.url.isBlank()) return

        val duration = manifest.codeValidity.asReadable()
        val content = messages.linkInstructions
            .replace("{code}", code)
            .replace("{duration}", duration)
            .replace("{player}", playerName)

        webhookSender.send(settings, content)
    }

    private fun notifyWebhookLinked(link: LinkRecord, messages: DiscordLinkMessages) {
        val settings = manifest.webhook
        if (!settings.enabled || settings.url.isBlank()) return

        val content = messages.linkConfirmed
            .replace("{discord}", link.discordUsername)
            .replace("{player}", link.playerName)

        val embed = WebhookEmbed(
            title = "Discord account linked",
            description = content,
            fields = listOf(
                WebhookEmbedField("Player", link.playerName, true),
                WebhookEmbedField("Discord", link.discordUsername, true),
                WebhookEmbedField("Linked at", Instant.ofEpochMilli(link.linkedAt).toString(), false),
            ),
        )
        webhookSender.send(settings, content, listOf(embed))
    }

    private fun generateRandomCode(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}
