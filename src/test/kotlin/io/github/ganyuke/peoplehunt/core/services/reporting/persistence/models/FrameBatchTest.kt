package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.testutil.player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FrameBatchTest {
  @Test
  fun isEmpty_whenAllListsEmpty() {
    assertTrue(FrameBatch(emptyList(), emptyList(), emptyList()).isEmpty())
  }

  @Test
  fun tickRange_spansAllBuffers() {
    val frame = EventFrame(10, Instant.fromEpochMilliseconds(0), ReportablePayload.PlayerJoined(player()))
    val batch = FrameBatch(
      projectiles = listOf(EventFrame(5, Instant.fromEpochMilliseconds(0), ReportablePayload.PlayerJoined(player()))),
      snapshots = listOf(EventFrame(20, Instant.fromEpochMilliseconds(0), ReportablePayload.PlayerJoined(player()))),
      events = listOf(frame),
    )
    assertEquals(5..20, batch.tickRange())
  }

  @Test
  fun tickRange_nullWhenEmpty() {
    assertNull(FrameBatch(emptyList(), emptyList(), emptyList()).tickRange())
  }
}
