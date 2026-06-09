package io.github.ganyuke.peoplehunt.core.services.reporting.highlighter

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import kotlin.uuid.Uuid

class CombatStatsTracker {
    data class PlayerStats(
        val name: String = "",
        val kills: Long = 0,
        val deaths: Long = 0,
        val damageDealt: Double = 0.0,
        val damageTaken: Double = 0.0
    )

    private data class MutablePlayerStats(
        var name: String = "",
        var kills: Long = 0,
        var deaths: Long = 0,
        var damageDealt: Double = 0.0,
        var damageTaken: Double = 0.0,
    ) {
        fun toImmutable() = PlayerStats(name, kills, deaths, damageDealt, damageTaken)
    }

    private val playerStats = HashMap<Uuid, MutablePlayerStats>()

    val participantStats: List<Pair<MatchPlayer, PlayerStats>>
        get() =
            playerStats.entries.map { MatchPlayer(it.key,it.value.name) to it.value.toImmutable() }

    private fun statsFor(matchPlayer: MatchPlayer) = playerStats.getOrPut(matchPlayer.uuid) { MutablePlayerStats(matchPlayer.name) }

    fun recordKill(matchPlayer: MatchPlayer) {
        statsFor(matchPlayer).kills++
    }

    fun recordDeath(matchPlayer: MatchPlayer) {
        statsFor(matchPlayer).deaths++
    }

    fun recordDamageDealt(matchPlayer: MatchPlayer, amount: Double) {
        statsFor(matchPlayer).damageDealt += amount
    }

    fun recordDamageTaken(matchPlayer: MatchPlayer, amount: Double) {
        statsFor(matchPlayer).damageTaken += amount
    }

    fun clear() {
        playerStats.clear()
    }
}