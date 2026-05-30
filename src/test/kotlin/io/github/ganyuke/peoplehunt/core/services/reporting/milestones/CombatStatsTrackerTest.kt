package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CombatStatsTrackerTest {
    @Test
    fun recordsKillsDeathsAndDamage() {
        val tracker = CombatStatsTracker()
        val id = Uuid.random()
        tracker.recordKill(id)
        tracker.recordDeath(id)
        tracker.recordDamageDealt(id, 10)
        tracker.recordDamageTaken(id, 4)
        val stats = tracker.participantStats.single().second
        assertEquals(1, stats.kills)
        assertEquals(1, stats.deaths)
        assertEquals(10, stats.damageDealt)
        assertEquals(4, stats.damageTaken)
    }

    @Test
    fun playerStats_dataClassSemantics() {
        val stats = CombatStatsTracker.PlayerStats(2, 3, 40, 50)
        assertEquals(stats, stats.copy())
        assertEquals(2, stats.kills)
        stats.hashCode()
    }

    @Test
    fun clear_resetsStats() {
        val tracker = CombatStatsTracker()
        tracker.recordKill(Uuid.random())
        tracker.clear()
        assertTrue(tracker.participantStats.isEmpty())
    }
}
