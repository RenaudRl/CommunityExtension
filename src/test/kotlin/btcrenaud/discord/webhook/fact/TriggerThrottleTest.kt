package btcrenaud.discord.webhook.fact

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerThrottleTest {
    private val alice = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val bob = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val cooldown = 1_000L

    @Test
    fun `the first firing is allowed and an immediate second one is refused`() {
        val throttle = TriggerThrottle()

        assertTrue(throttle.tryAcquire("event", alice, nowMillis = 10_000, cooldownMillis = cooldown))
        assertFalse(throttle.tryAcquire("event", alice, nowMillis = 10_500, cooldownMillis = cooldown))
    }

    @Test
    fun `firing is allowed again once the cooldown has elapsed, counted from the last firing`() {
        val throttle = TriggerThrottle()
        throttle.tryAcquire("event", alice, nowMillis = 10_000, cooldownMillis = cooldown)

        assertFalse(throttle.tryAcquire("event", alice, nowMillis = 10_999, cooldownMillis = cooldown))
        assertTrue(throttle.tryAcquire("event", alice, nowMillis = 11_000, cooldownMillis = cooldown))
    }

    @Test
    fun `a looping chain is held to one firing per window`() {
        val throttle = TriggerThrottle()

        val fired = (0 until 10).count { tick ->
            throttle.tryAcquire("event", alice, nowMillis = 10_000L + tick * 50L, cooldownMillis = cooldown)
        }

        assertTrue(fired == 1)
    }

    @Test
    fun `events and players are throttled independently`() {
        val throttle = TriggerThrottle()
        throttle.tryAcquire("event", alice, nowMillis = 10_000, cooldownMillis = cooldown)

        assertTrue(throttle.tryAcquire("other", alice, nowMillis = 10_001, cooldownMillis = cooldown))
        assertTrue(throttle.tryAcquire("event", bob, nowMillis = 10_001, cooldownMillis = cooldown))
    }

    @Test
    fun `a zero cooldown never refuses`() {
        val throttle = TriggerThrottle()

        assertTrue(throttle.tryAcquire("event", alice, nowMillis = 10_000, cooldownMillis = 0))
        assertTrue(throttle.tryAcquire("event", alice, nowMillis = 10_000, cooldownMillis = 0))
    }

    @Test
    fun `forgetting a player releases their window`() {
        val throttle = TriggerThrottle()
        throttle.tryAcquire("event", alice, nowMillis = 10_000, cooldownMillis = cooldown)

        throttle.forget(alice)

        assertTrue(throttle.tryAcquire("event", alice, nowMillis = 10_001, cooldownMillis = cooldown))
    }
}
