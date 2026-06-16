package btcrenaud.community.bugreport.service

import btcrenaud.community.bugreport.data.BugReport
import btcrenaud.community.bugreport.entries.BugReportSequenceArtifactEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.typewritermc.engine.paper.entry.entries.stringData
import com.typewritermc.engine.paper.logger
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages persistence of bug reports via JSON artifact storage.
 * Reports now survive server restarts (previously memory-only).
 */
class BugReportRepository(private val sequenceEntry: BugReportSequenceArtifactEntry?) {

    private val cache = ConcurrentHashMap<String, BugReport>()
    private val sequence = AtomicLong(0)
    private val gson = Gson()

    init {
        loadAll()
    }

    fun save(report: BugReport) {
        cache[report.id] = report
        persistAll()
    }

    fun findById(id: String): BugReport? = cache[id]

    fun findAll(): List<BugReport> = cache.values.toList()

    fun clear() {
        cache.clear()
        persistAll()
    }

    fun nextId(): String {
        val next = sequence.incrementAndGet()
        persistAll()
        return next.toString()
    }

    // -- Persistence: wraps sequence + reports in a single JSON blob --

    private fun loadAll() {
        val entry = sequenceEntry ?: return
        runCatching {
            val raw: String? = runBlocking { entry.stringData() }
            if (!raw.isNullOrBlank() && raw.startsWith("{")) {
                val obj = gson.fromJson(raw, StorageData::class.java)
                sequence.set(obj.sequence)
                obj.reports.forEach { cache[it.id] = it }
            } else {
                // Legacy: just a sequence number
                raw?.toLongOrNull()?.let { sequence.set(it) }
            }
        }.onFailure { e ->
            logger.warning("Failed to load bug reports: ${e.message}")
        }
    }

    private fun persistAll() {
        val entry = sequenceEntry ?: return
        runCatching {
            val data = StorageData(sequence.get(), cache.values.toList())
            val json: String = gson.toJson(data)
            runBlocking { entry.stringData(json) }
        }.onFailure { e ->
            logger.warning("Failed to persist bug reports: ${e.message}")
        }
    }

    @Suppress("unused")
    private data class StorageData(
        val sequence: Long,
        val reports: List<BugReport>,
    )
}
