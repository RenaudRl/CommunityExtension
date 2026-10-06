package btcrenaud.discord.webhook.fact

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Re-entrance guard for the entries chained after a fact event.
 *
 * A chained action may well write the very fact the event watches. The engine queues triggers, so the
 * loop never shows up as a nested call: it comes back as a fresh fact change a tick later. Refusing a
 * second firing for the same event and player inside a short window cuts that loop to one pass per
 * window without needing to know what the chained entries do.
 */
class TriggerThrottle {
    private val lastFired = ConcurrentHashMap<Key, Long>()

    private data class Key(val entryId: String, val playerId: UUID)

    /**
     * Claims the right to fire [entryId] for [playerId] at [nowMillis]. True when the last firing is
     * at least [cooldownMillis] old (or there was none); a `<= 0` cooldown never refuses.
     */
    fun tryAcquire(entryId: String, playerId: UUID, nowMillis: Long, cooldownMillis: Long): Boolean {
        if (cooldownMillis <= 0) return true
        var acquired = false
        lastFired.compute(Key(entryId, playerId)) { _, last ->
            if (last == null || nowMillis - last >= cooldownMillis) {
                acquired = true
                nowMillis
            } else {
                last
            }
        }
        return acquired
    }

    fun forget(playerId: UUID) {
        lastFired.keys.removeIf { it.playerId == playerId }
    }

    fun clear() = lastFired.clear()
}
