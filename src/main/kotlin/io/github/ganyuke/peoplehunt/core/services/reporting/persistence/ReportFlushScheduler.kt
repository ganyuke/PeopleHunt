package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.ports.TaskHandle
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class ReportFlushScheduler(
    private val interval: Duration,
    private val scope: CoroutineScope,
    private val onFlush: () -> Unit,
) {
    private var job: Job? = null
    private var matchStart: Instant? = null
    private var paused = false

    fun start(anchor: Instant): TaskHandle? {
        if (interval == Duration.ZERO) return null
        stop()
        matchStart = anchor
        paused = false
        job = scope.launch { runLoop() }
        return object : TaskHandle {
            override fun cancel() = stop()
        }
    }

    fun pause() {
        paused = true
    }

    fun resume(anchor: Instant) {
        matchStart = anchor
        paused = false
        if (job?.isActive != true) {
            job = scope.launch { runLoop() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        matchStart = null
        paused = false
    }

    private suspend fun runLoop() {
        val anchor = matchStart ?: return
        while (coroutineContext.isActive) {
            val now = Clock.System.now()
            val next = nextFlushInstant(anchor, interval, now)
            val wait = next - now
            if (wait > Duration.ZERO) delay(wait)
            if (!coroutineContext.isActive || paused) continue
            onFlush()
        }
    }

    companion object {
        fun nextFlushInstant(matchStart: Instant, interval: Duration, now: Instant): Instant {
            if (now <= matchStart) return matchStart + interval
            val elapsed = now - matchStart
            val completed = elapsed.inWholeMilliseconds / interval.inWholeMilliseconds
            return matchStart + interval * (completed + 1).toInt()
        }
    }
}
