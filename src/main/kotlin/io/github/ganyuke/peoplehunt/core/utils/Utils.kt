package io.github.ganyuke.peoplehunt.core.utils

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import kotlin.time.Duration

infix fun MatchPlayer?.isReally(other: MatchPlayer?) = this?.uuid == other?.uuid

infix fun MatchPlayer?.isNotReally(other: MatchPlayer?) = this?.uuid != other?.uuid

infix fun Set<MatchPlayer>.reallyContains(player: MatchPlayer) = any { it.uuid == player.uuid }

fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

const val PEOPLEHUNT_NAMESPACE = "peoplehunt"

data class PhConfig(
    val globalCompass: Boolean,
    val matchMinutesInterval: Duration,
    val compassTickInterval: Long,
    val reportFlushInterval: Duration,
)