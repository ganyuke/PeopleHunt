package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

class CoreEventsTest {
    @Test
    fun matchEventVariants_holdData() {
        val runner = player("runner")
        val hunters = setOf(player("h1"))
        val finished = MatchEngine.MatchState.Finished(
            runner,
            hunters,
            kotlin.time.Clock.System.now(),
            kotlin.time.Clock.System.now(),
            MatchEngine.MatchOutcome.RUNNER_VICTORY,
        )
        val world = Uuid.random()
        val p = pos(1, 2, 3, world)
        val events: List<MatchEvent> = listOf(
            MatchEvent.MatchStart(runner, hunters),
            MatchEvent.MatchEnd(finished),
            MatchEvent.CompassTick,
            MatchEvent.GiveHuntersCompass(hunters.map { it.uuid }.toSet()),
            MatchEvent.CompassUpdate(p, mapOf(world to p), hunters.map { it.uuid }.toSet()),
            MatchEvent.IntervalElapsed(5),
            MatchEvent.BroadcastNotification("broadcast"),
            MatchEvent.OperatorNotification("operator"),
        )
        assertEquals(8, events.size)
        assertEquals("broadcast", (events[6] as MatchEvent.BroadcastNotification).message)
        assertEquals(p, (events[4] as MatchEvent.CompassUpdate).pos)
    }

    @Test
    fun reportableEventVariants_holdData() {
        val p = player("p")
        val location = pos()
        val item = SpeedrunMilestone.ItemAcquired(
            SpeedrunMilestone.ItemAcquired.Item.BUCKET,
            SpeedrunMilestone.AcquisitionMethod.CRAFTED,
        )
        val events: List<ReportableEvent> = listOf(
            ReportableEvent.PlayerMoved(p, location),
            ReportableEvent.PlayerRespawned(p, location),
            ReportableEvent.EntityDied(p, "minecraft:zombie", location, p, "minecraft:skeleton"),
            ReportableEvent.PlayerDamagedEntity(p, 2.5),
            ReportableEvent.PlayerDamagedByEntity(p, 1.0),
            ReportableEvent.PlayerAcquiredItem(p, item.item, item.method),
            ReportableEvent.PlayerChangedDimension(p, "overworld", "nether"),
            ReportableEvent.PlayerThrewItem(p, "minecraft:ender_eye"),
            ReportableEvent.PlayerFilledBucket(p, "water"),
            ReportableEvent.EndCrystalDestroyed(p),
            ReportableEvent.EndPortalCompleted(p),
        )
        assertEquals(11, events.size)
        assertNotEquals(events[0], events[1])
        assertEquals("nether", (events[6] as ReportableEvent.PlayerChangedDimension).to)
        events.forEach { event ->
            assertEquals(event, event)
            event.hashCode()
            when (event) {
                is ReportableEvent.PlayerMoved -> {
                    assertEquals(p, event.player)
                    event.copy(player = p)
                }
                is ReportableEvent.PlayerAcquiredItem -> event.copy(method = SpeedrunMilestone.AcquisitionMethod.TRADED)
                else -> Unit
            }
        }
    }

    @Test
    fun matchEventDataClasses_supportEquality() {
        val notification = MatchEvent.BroadcastNotification("a")
        assertEquals(notification, notification.copy())
        assertEquals(notification, MatchEvent.BroadcastNotification("a"))
        notification.hashCode()
    }
}
