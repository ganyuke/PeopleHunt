package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.testutil.FakeScheduler
import io.github.ganyuke.peoplehunt.core.testutil.testPhConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MatchIntervalServiceTest {
    @Test
    fun start_returnsNullWhenIntervalZero() {
        val service = MatchIntervalService(testPhConfig(), FakeScheduler(), MatchEventBus())
        assertNull(service.start { Clock.System.now() })
    }

    @Test
    fun start_postsIntervalElapsedOnMainThread() = runBlocking {
        val scheduler = FakeScheduler()
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = MatchIntervalService(
            testPhConfig(matchMinutesInterval = 5.milliseconds),
            scheduler,
            bus,
        )
        val startTime = Clock.System.now()
        val handle = service.start { startTime }
        assertTrue(handle != null)
        withTimeout(2_000.milliseconds) {
            while (events.none { it is MatchEvent.IntervalElapsed }) {
                delay(10.milliseconds)
            }
        }
        handle.cancel()
        service.shutdown()
    }

    @Test
    fun cancel_stopsPosting() = runBlocking {
        val scheduler = FakeScheduler()
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = MatchIntervalService(
            testPhConfig(matchMinutesInterval = 5.milliseconds),
            scheduler,
            bus,
        )
        val handle = service.start { Clock.System.now() }!!
        withTimeout(2_000.milliseconds) {
            while (events.none { it is MatchEvent.IntervalElapsed }) {
                delay(10.milliseconds)
            }
        }
        val countAfterFirst = events.count { it is MatchEvent.IntervalElapsed }
        handle.cancel()
        delay(50.milliseconds)
        assertEquals(countAfterFirst, events.count { it is MatchEvent.IntervalElapsed })
        service.shutdown()
    }
}
