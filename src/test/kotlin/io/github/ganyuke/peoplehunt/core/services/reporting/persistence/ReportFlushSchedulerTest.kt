package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
class ReportFlushSchedulerTest {
  @Test
  fun nextFlushInstant_beforeFirstInterval_returnsStartPlusInterval() {
    val start = Instant.fromEpochMilliseconds(0)
    val now = Instant.fromEpochMilliseconds(0)
    assertEquals(start + 5.minutes, ReportFlushScheduler.nextFlushInstant(start, 5.minutes, now))
  }

  @Test
  fun nextFlushInstant_midInterval_returnsNextGridSlot() {
    val start = Instant.fromEpochMilliseconds(0)
    val now = Instant.fromEpochMilliseconds(7.minutes.inWholeMilliseconds)
    assertEquals(start + 10.minutes, ReportFlushScheduler.nextFlushInstant(start, 5.minutes, now))
  }

  @Test
  fun nextFlushInstant_onGridBoundary_returnsFollowingSlot() {
    val start = Instant.fromEpochMilliseconds(0)
    val now = Instant.fromEpochMilliseconds(5.minutes.inWholeMilliseconds)
    assertEquals(start + 10.minutes, ReportFlushScheduler.nextFlushInstant(start, 5.minutes, now))
  }

}
