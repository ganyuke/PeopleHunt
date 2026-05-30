package io.github.ganyuke.peoplehunt.core

import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import kotlin.time.Duration
import kotlin.uuid.Uuid

object Utils {
    data class Pos4(val x: Int, val y: Int, val z: Int, val w: Uuid)

    infix fun MatchEngine.MatchPlayer?.isReally(other: MatchEngine.MatchPlayer?) = this?.uuid == other?.uuid

    infix fun MatchEngine.MatchPlayer?.isNotReally(other: MatchEngine.MatchPlayer?) = this?.uuid != other?.uuid

    infix fun Set<MatchEngine.MatchPlayer>.reallyContains(player: MatchEngine.MatchPlayer) = any { it.uuid == player.uuid }

    fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    const val PEOPLEHUNT_NAMESPACE = "peoplehunt"

    data class PhConfig(val globalCompass: Boolean, val matchMinutesInterval: Duration, val compassTickInterval: Long)
}
