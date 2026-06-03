package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class PlayerSnapshot {
    @Serializable
    @SerialName("Offline")
    data object Offline : PlayerSnapshot()

    @Serializable
    @SerialName("Online")
    data class Online(val state: OnlineState) : PlayerSnapshot()
}

@Serializable
sealed class OnlineState {
    @Serializable
    @SerialName("Alive")
    data class Alive(val currentLifeData: CurrentLifeData) : OnlineState()

    @Serializable
    @SerialName("Dead")
    data object Dead : OnlineState()
}

@Serializable
data class CurrentLifeData(
    val spatialData: SpatialData,
    val vitals: Vitals,
    val currentStates: CurrentStates,
    val metadata: LifeMetadata,
)

@Serializable
data class SpatialData(
    val position: Pos4,
    val yaw: Float,
    val pitch: Float,
    val velocity: Velocity
)

@Serializable
data class Vitals(
    val health: Double,
    val maxHealth: Double,
    val foodLevel: Int,
    val saturation: Float,
    val absorption: Double,
    val remainingAir: Int,
    val maxAir: Int,
    val experienceLevel: Int,
    val experienceProgress: Float,
    val totalXpPoints: Int,
)

@Serializable
data class LifeMetadata(
    val gameMode: String,
    val activePotionEffects: List<ActivePotionEffect>,
)

@Serializable
data class CurrentStates(
    val environmentFlags: EnvironmentFlags,
    val movementFlags: MovementFlags,
    val ridingVehicle: String,
)

@Serializable
data class EnvironmentFlags(
    val isBurning: Boolean,
    val isDrowning: Boolean,
    val isSuffocating: Boolean,
    val isFreezing: Boolean,
    val isWadingInWater: Boolean,
    val isWadingInLava: Boolean,
    val isSubmergedInWater: Boolean,
    val isSubmergedInLava: Boolean,
    val isInsideCobweb: Boolean,
    val isInsideSweetBerry: Boolean,
)

@Serializable
data class MovementFlags(
    val isSleeping: Boolean,
    val isRiptiding: Boolean,
    val isClimbing: Boolean,
    val isSwimming: Boolean,
    val isSprinting: Boolean,
    val isSneaking: Boolean,
    val isFlying: Boolean,
    val isGliding: Boolean,
)