package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.FlushBatchCodec
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.ReportJson
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class FlushBatchCodecTest {
  private val json = ReportJson.instance

  @Test
  fun gzipRoundTrip_preservesFrames() {
    val frames = listOf(
      EventFrame(
        tick = 10,
        occurredAt = Instant.fromEpochMilliseconds(1_000L),
        payload = ReportablePayload.PlayerJoined(player("runner")),
      ),
      EventFrame(
        tick = 20,
        occurredAt = Instant.fromEpochMilliseconds(1_000L),
        payload = ReportablePayload.PlayerDied(
          player("runner"),
          pos(),
          io.github.ganyuke.peoplehunt.core.events.models.KillCause.Environmental,
          deathMessage = null,
        ),
      ),
    )
    val encoded = FlushBatchCodec.encode(frames, json)
    assertNotEquals(frames.toString(), encoded.decodeToString())
    val decoded = FlushBatchCodec.decode(encoded, json)
    assertEquals(frames, decoded)
  }
}
