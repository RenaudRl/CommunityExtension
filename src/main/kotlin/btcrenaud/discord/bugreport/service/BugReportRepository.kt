package btcrenaud.discord.bugreport.service

import btcrenaud.discord.bugreport.data.BugReport
import btcrenaud.discord.bugreport.entries.BugReportSequenceArtifactEntry
import com.google.gson.Gson
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.entry.entries.stringData
import com.typewritermc.engine.paper.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Persists bug reports as a single JSON blob inside the manifest's artifact.
 * Writes happen off-thread; the in-memory cache is the source of truth for reads.
 */
class BugReportRepository(private val sequenceEntry: BugReportSequenceArtifactEntry?) {

    private val cache = ConcurrentHashMap<String, BugReport>()
    private val sequence = AtomicLong(0)
    private val gson = Gson()
    private val persistMutex = Mutex()

    suspend fun load() {
        val entry = sequenceEntry ?: return
        runCatching {
            val raw: String? = entry.stringData()
            if (!raw.isNullOrBlank() && raw.startsWith("{")) {
                val obj = gson.fromJson(raw, StorageData::class.java)
                sequence.set(obj.sequence)
                obj.reports.forEach { cache[it.id] = it }
            } else {
                // Legacy format: the artifact only held the sequence number.
                raw?.toLongOrNull()?.let { sequence.set(it) }
            }
        }.onFailure { e ->
            logger.warning("Failed to load bug reports: ${e.message}")
        }
    }

    fun save(report: BugReport) {
        cache[report.id] = report
        persistAsync()
    }

    fun delete(id: String): Boolean {
        val removed = cache.remove(id) != null
        if (removed) persistAsync()
        return removed
    }

    fun findById(id: String): BugReport? = cache[id]

    fun findAll(): List<BugReport> = cache.values.toList()

    fun findByPlayer(playerUuid: UUID): List<BugReport> =
        cache.values.filter { it.playerUuid == playerUuid }

    fun countForPlayer(playerUuid: UUID): Int =
        cache.values.count { it.playerUuid == playerUuid }

    fun lastSubmissionOf(playerUuid: UUID): Long? =
        cache.values.filter { it.playerUuid == playerUuid }.maxOfOrNull { it.createdAt }

    fun nextId(): String = sequence.incrementAndGet().toString()

    private fun persistAsync() {
        val entry = sequenceEntry ?: return
        Dispatchers.UntickedAsync.launch {
            persistMutex.withLock {
                runCatching {
                    val data = StorageData(sequence.get(), cache.values.toList())
                    entry.stringData(gson.toJson(data))
                }.onFailure { e ->
                    logger.warning("Failed to persist bug reports: ${e.message}")
                }
            }
        }
    }

    private data class StorageData(
        val sequence: Long,
        val reports: List<BugReport>,
    )
}
