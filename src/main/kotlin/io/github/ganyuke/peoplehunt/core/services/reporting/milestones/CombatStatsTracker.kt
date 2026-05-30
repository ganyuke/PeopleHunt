package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import kotlin.uuid.Uuid

class CombatStatsTracker {
    data class PlayerStats(
        var kills: Long = 0,
        var deaths: Long = 0,
        var damageDealt: Long = 0,
        var damageTaken: Long = 0
    )

    private data class MutablePlayerStats(
        var kills: Long = 0,
        var deaths: Long = 0,
        var damageDealt: Long = 0,
        var damageTaken: Long = 0,
    ) {
        fun toImmutable() = PlayerStats(kills, deaths, damageDealt, damageTaken)
    }

    private val playerStats = HashMap<Uuid, MutablePlayerStats>()

    val participantStats: List<Pair<Uuid, PlayerStats>>
        get() =
            playerStats.entries.map { it.key to it.value.toImmutable() }

    private fun statsFor(playerId: Uuid) = playerStats.getOrPut(playerId) { MutablePlayerStats() }

    fun recordKill(playerId: Uuid) {
        statsFor(playerId).kills++
    }

    fun recordDeath(playerId: Uuid) {
        statsFor(playerId).deaths++
    }

    fun recordDamageDealt(playerId: Uuid, amount: Long) {
        statsFor(playerId).damageDealt += amount
    }

    fun recordDamageTaken(playerId: Uuid, amount: Long) {
        statsFor(playerId).damageTaken += amount
    }

    fun clear() {
        playerStats.clear()
    }
}