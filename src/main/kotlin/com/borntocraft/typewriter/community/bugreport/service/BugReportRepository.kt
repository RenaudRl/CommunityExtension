package com.borntocraft.typewriter.community.bugreport.service

import com.borntocraft.typewriter.community.bugreport.data.BugReport
import com.borntocraft.typewriter.community.bugreport.entries.BugReportSequenceArtifactEntry
import com.typewritermc.engine.paper.entry.entries.stringData
import com.typewritermc.engine.paper.logger
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages the persistence and retrieval of bug reports.
 */
class BugReportRepository(sequenceEntry: BugReportSequenceArtifactEntry?) {

    private val cache = ConcurrentHashMap<String, BugReport>()
    private val sequenceEntry: BugReportSequenceArtifactEntry? = sequenceEntry
    private val sequence = AtomicLong(loadSequence())

    fun save(report: BugReport) {
        cache[report.id] = report
        logger.info("Saved bug report ${report.id}")
    }

    fun findById(id: String): BugReport? {
        return cache[id]
    }

    fun findAll(): List<BugReport> {
        return cache.values.toList()
    }

    fun clear() {
        cache.clear()
    }

    fun nextId(): String {
        val next = sequence.incrementAndGet()
        persistSequence(next)
        return next.toString()
    }

    private fun loadSequence(): Long {
        val entry = sequenceEntry ?: return 0
        return runCatching {
            val raw = runBlocking { entry.stringData() }
            raw?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    private fun persistSequence(value: Long) {
        val entry = sequenceEntry ?: return
        runCatching {
            runBlocking { entry.stringData(value.toString()) }
        }
    }
}
