package io.github.ganyuke.peoplehunt.core.ports

import io.github.ganyuke.peoplehunt.core.testutil.FakeScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortDefaultMethodsTest {
    @Test
    fun schedulerPort_everyTicks_usesIntervalAsDefaultDelay() {
        var capturedDelay = -1L
        val scheduler = object : SchedulerPort {
            override fun everyTicks(interval: Long, delay: Long, task: () -> Unit): TaskHandle {
                capturedDelay = delay
                return FakeScheduler.CancellableHandle()
            }

            override fun after(delay: Long, task: () -> Unit) = FakeScheduler.CancellableHandle()

            override fun runOnMainThread(task: () -> Unit) = task()
        }
        scheduler.everyTicks(15L) { }
        assertEquals(15L, capturedDelay)
    }

    @Test
    fun loggerPort_error_acceptsOptionalCause() {
        val messages = mutableListOf<Pair<String, Throwable?>>()
        val logger = object : LoggerPort {
            override fun info(message: String) = Unit
            override fun warn(message: String) = Unit
            override fun error(message: String, cause: Throwable?) {
                messages += message to cause
            }
        }
        logger.error("boom", null)
        assertEquals("boom" to null, messages.single())
        val ex = IllegalStateException()
        logger.error("with cause", ex)
        assertEquals("with cause" to ex, messages.last())
    }
}
