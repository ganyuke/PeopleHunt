package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ReportFlushSchedulerLoopTest {
  @Test
  fun runLoop_invokesCallbackOnWallClockInterval() = runBlocking {
    var flushes = 0
    val scope = CoroutineScope(Dispatchers.Default)
    val scheduler = ReportFlushScheduler(30.milliseconds, scope) { flushes++ }
    try {
      scheduler.start(Clock.System.now())
      delay(100)
      assertTrue(flushes >= 1)
    } finally {
      scheduler.stop()
      scope.cancel()
    }
  }

  @Test
  fun pauseAndResume_controlsFlushLoop() = runBlocking {
    var flushes = 0
    val scope = CoroutineScope(Dispatchers.Default)
    val scheduler = ReportFlushScheduler(30.milliseconds, scope) { flushes++ }
    val anchor = Clock.System.now()
    try {
      scheduler.start(anchor)
      delay(50)
      val beforePause = flushes
      scheduler.pause()
      delay(80)
      assertEquals(beforePause, flushes)
      scheduler.resume(anchor)
      delay(80)
      assertTrue(flushes > beforePause)
    } finally {
      scheduler.stop()
      scope.cancel()
    }
  }
}

private fun assertEquals(expected: Int, actual: Int) = kotlin.test.assertEquals(expected, actual)
