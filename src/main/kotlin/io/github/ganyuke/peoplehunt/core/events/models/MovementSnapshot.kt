package io.github.ganyuke.peoplehunt.core.events.models

import kotlin.time.Instant

data class MovementSnapshot(
    val tick: Int,
    val player: MatchPlayer,
    val pos: Pos4,
    val yaw: Float,
    val pitch: Float,
    val sprinting: Boolean,
    val sneaking: Boolean,
    val flying: Boolean,
    val swimming: Boolean,
    val structure: String?,
    val recordedAt: Instant,
)