package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.core.testutil.*
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

        val pm = playerMoved(p, location)
        assertEquals(p, (pm.payload as ReportablePayload.PlayerMovedByBlock).player)

        val pr = playerRespawned(p, location)
        assertEquals(p, (pr.payload as ReportablePayload.PlayerRespawned).player)

        val ed = entityDied("minecraft:zombie", location, KillCause.KilledByPlayer(p))
        val edPayload = ed.payload as ReportablePayload.EntityDied
        assertEquals("minecraft:zombie", edPayload.entityIdentifier)
        assertEquals(p, (edPayload.cause as KillCause.KilledByPlayer).killer)

        val pde = playerDamagedEntity(p, "minecraft:zombie", 2.5)
        assertEquals(2.5, (pde.payload as ReportablePayload.PlayerDamagedEntity).amount)

        val pdb = playerDamagedByEntity(p, "minecraft:zombie", 1.0)
        assertEquals(1.0, (pdb.payload as ReportablePayload.PlayerDamagedByEntity).amount)

        val pai = playerAcquiredItem(p, item.item, item.method)
        val paiPayload = pai.payload as ReportablePayload.PlayerAcquiredItem
        assertEquals(item.item, paiPayload.item)
        assertEquals(item.method, paiPayload.method)

        val pcd = playerChangedDimension(p, "overworld", "nether")
        val pcdPayload = pcd.payload as ReportablePayload.PlayerChangedDimension
        assertEquals("overworld", pcdPayload.from)
        assertEquals("nether", pcdPayload.to)

        val pte = playerThrewEnderEye(p)
        assertEquals(p, (pte.payload as ReportablePayload.PlayerThrewEnderEye).player)

        val pfb = playerFilledBucket(p, "water")
        assertEquals("water", (pfb.payload as ReportablePayload.PlayerFilledBucket).fluid)

        val portalPos = Pos4(1, 2, 3, Uuid.random())
        val epc = endPortalCompleted(portalPos)
        assertEquals(portalPos, (epc.payload as ReportablePayload.EndPortalCompleted).pos)

        assertNotEquals(pm, pr)
    }

    @Test
    fun matchEventDataClasses_supportEquality() {
        val notification = MatchEvent.BroadcastNotification("a")
        assertEquals(notification, notification.copy())
        assertEquals(notification, MatchEvent.BroadcastNotification("a"))
        notification.hashCode()
    }
}
