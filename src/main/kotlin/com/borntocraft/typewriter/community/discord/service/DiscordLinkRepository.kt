package com.borntocraft.typewriter.community.discord.service

import com.borntocraft.typewriter.community.discord.data.DiscordLinkStorage
import com.borntocraft.typewriter.community.discord.data.LinkRecord
import com.borntocraft.typewriter.community.discord.data.PendingLink
import com.borntocraft.typewriter.community.discord.entries.DiscordLinkArtifactEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.typewritermc.engine.paper.entry.entries.stringData
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DiscordLinkRepository(private val storage: DiscordLinkArtifactEntry?) {

    private val links = ConcurrentHashMap<UUID, LinkRecord>()
    private val pendingLinks = ConcurrentHashMap<String, PendingLink>()
    private val lock = ReentrantLock()
    private val gson = Gson()

    init { load() }

    fun cleanupExpired(now: Long) {
        pendingLinks.entries.removeIf { (_, value) -> value.expiresAt < now }
    }

    fun clear() {
        links.clear()
        pendingLinks.clear()
    }

    fun findLink(playerUuid: UUID): LinkRecord? {
        return links[playerUuid]
    }

    fun saveLink(link: LinkRecord) {
        lock.withLock {
            links[link.playerUuid] = link
            persist()
        }
    }

    fun removeLink(playerUuid: UUID) {
        lock.withLock {
            links.remove(playerUuid)
            persist()
        }
    }

    fun savePending(pending: PendingLink) {
        pendingLinks[pending.code.lowercase()] = pending
    }

    fun findPending(code: String): PendingLink? {
        return pendingLinks[code.lowercase()]
    }

    fun removePending(code: String) {
        pendingLinks.remove(code.lowercase())
    }

    fun findPendingByPlayer(playerUuid: UUID): PendingLink? {
        return pendingLinks.values.find { it.playerUuid == playerUuid }
    }

    fun countPendingForPlayer(playerUuid: UUID): Int {
        return pendingLinks.values.count { it.playerUuid == playerUuid }
    }

    fun isDiscordAlreadyLinked(discordId: String): Boolean {
        return links.values.any { it.discordId == discordId }
    }

    private fun persist() {
        val entry = storage ?: return
        runCatching {
            val data = DiscordLinkStorage(
                links = links.values.toMutableList(),
                pending = pendingLinks.values.toMutableList(),
            )
            val json = gson.toJson(data)
            runBlocking { entry.stringData(json) }
        }
    }

    private fun load() {
        val entry = storage ?: return
        runCatching {
            val raw = runBlocking { entry.stringData() }
            // If artifact doesn't exist or is empty, initialize with empty storage
            if (raw.isNullOrBlank()) {
                initializeEmptyStorage()
                return
            }
            val type: Type = object : TypeToken<DiscordLinkStorage>() {}.type
            val data: DiscordLinkStorage = gson.fromJson(raw, type) ?: return
            data.links.forEach { links[it.playerUuid] = it }
            data.pending.forEach { pendingLinks[it.code.lowercase()] = it }
        }.onFailure {
            // Artifact file not found or corrupted - initialize with empty storage
            initializeEmptyStorage()
        }
    }

    /**
     * Ensures the artifact storage file exists by creating it with empty data if needed.
     */
    fun ensureInitialized() {
        val entry = storage ?: return
        runCatching {
            val raw = runBlocking { entry.stringData() }
            if (raw.isNullOrBlank()) {
                initializeEmptyStorage()
            }
        }.onFailure {
            initializeEmptyStorage()
        }
    }

    private fun initializeEmptyStorage() {
        val entry = storage ?: return
        runCatching {
            val data = DiscordLinkStorage()
            val json = gson.toJson(data)
            runBlocking { entry.stringData(json) }
        }
    }
}
