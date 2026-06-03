package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.reporting.CombatStatsTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CombatStatsTrackerTest {
    private fun matchPlayer(name: String = "player") = MatchPlayer(Uuid.random(), name)

    @Test
    fun recordsKillsDeathsAndDamage() {
        val tracker = CombatStatsTracker()
        val p = matchPlayer()
        tracker.recordKill(p)
        tracker.recordDeath(p)
        tracker.recordDamageDealt(p, 10.0)
        tracker.recordDamageTaken(p, 4.0)
        val stats = tracker.participantStats.single().second
        assertEquals(1L, stats.kills)
        assertEquals(1L, stats.deaths)
        assertEquals(10.0, stats.damageDealt)
        assertEquals(4.0, stats.damageTaken)
    }

    @Test
    fun playerStats_dataClassSemantics() {
        val stats = CombatStatsTracker.PlayerStats(name = "p", kills = 2, deaths = 3, damageDealt = 40.0, damageTaken = 50.0)
        assertEquals(stats, stats.copy())
        assertEquals(2L, stats.kills)
        stats.hashCode()
    }

    @Test
    fun clear_resetsStats() {
        val tracker = CombatStatsTracker()
        tracker.recordKill(matchPlayer())
        tracker.clear()
        assertTrue(tracker.participantStats.isEmpty())
    }
}
