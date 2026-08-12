package btcrenaud.discord.link.service

import btcrenaud.discord.link.data.DiscordLinkStorage
import btcrenaud.discord.link.data.LinkRecord
import btcrenaud.discord.link.data.PendingLink
import btcrenaud.discord.link.entries.DiscordLinkArtifactEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.entry.entries.stringData
import com.typewritermc.engine.paper.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Stores confirmed links and pending verification codes inside the manifest's
 * artifact. Writes happen off-thread; the in-memory maps are the source of
 * truth for reads.
 */
class DiscordLinkRepository(private val storage: DiscordLinkArtifactEntry?) {

    private val links = ConcurrentHashMap<UUID, LinkRecord>()
    private val pendingLinks = ConcurrentHashMap<String, PendingLink>()
    private val codesGenerated = AtomicLong(0)
    private val gson = Gson()
    private val persistMutex = Mutex()

    suspend fun load() {
        val entry = storage ?: return
        runCatching {
            val raw = entry.stringData()
            if (raw.isNullOrBlank()) return
            val type: Type = object : TypeToken<DiscordLinkStorage>() {}.type
            val data: DiscordLinkStorage = gson.fromJson(raw, type) ?: return
            codesGenerated.set(data.codesGenerated)
            data.links.forEach { links[it.playerUuid] = it }
            data.pending.forEach { pendingLinks[it.code.lowercase()] = it }
        }.onFailure { e ->
            logger.warning("Failed to load Discord link storage, starting empty: ${e.message}")
        }
    }

    fun cleanupExpired(now: Long) {
        if (pendingLinks.entries.removeIf { (_, value) -> value.expiresAt < now }) {
            persistAsync()
        }
    }

    fun findLink(playerUuid: UUID): LinkRecord? = links[playerUuid]

    fun saveLink(link: LinkRecord) {
        links[link.playerUuid] = link
        persistAsync()
    }

    fun removeLink(playerUuid: UUID) {
        links.remove(playerUuid)
        persistAsync()
    }

    fun savePending(pending: PendingLink) {
        pendingLinks[pending.code.lowercase()] = pending
        persistAsync()
    }

    fun findPending(code: String): PendingLink? = pendingLinks[code.lowercase()]

    fun removePending(code: String) {
        pendingLinks.remove(code.lowercase())
        persistAsync()
    }

    fun findPendingByPlayer(playerUuid: UUID): PendingLink? =
        pendingLinks.values.find { it.playerUuid == playerUuid }

    fun isDiscordAlreadyLinked(discordId: String): Boolean =
        links.values.any { it.discordId == discordId }

    fun countCodeGenerated(): Long = codesGenerated.incrementAndGet()

    private fun persistAsync() {
        val entry = storage ?: return
        Dispatchers.UntickedAsync.launch {
            persistMutex.withLock {
                runCatching {
                    val data = DiscordLinkStorage(
                        codesGenerated = codesGenerated.get(),
                        links = links.values.toMutableList(),
                        pending = pendingLinks.values.toMutableList(),
                    )
                    entry.stringData(gson.toJson(data))
                }.onFailure { e ->
                    logger.warning("Failed to persist Discord link storage: ${e.message}")
                }
            }
        }
    }
}
