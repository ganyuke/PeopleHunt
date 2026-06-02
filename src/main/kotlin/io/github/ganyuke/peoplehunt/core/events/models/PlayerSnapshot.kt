package io.github.ganyuke.peoplehunt.core.events.models

sealed class PlayerSnapshot {
    data object Offline : PlayerSnapshot()
    data class Online(val state: OnlineState) : PlayerSnapshot()
}

sealed class OnlineState {
    data class Alive(val currentLifeData: CurrentLifeData) : OnlineState()
    data object Dead : OnlineState()
}

data class CurrentLifeData(
    val spatialData: SpatialData,
    val vitals: Vitals,
    val currentStates: CurrentStates,
    val metadata: LifeMetadata,
)

data class SpatialData(
    val position: Pos4,
    val yaw: Float,
    val pitch: Float,
)

data class Vitals(
    val health: Double,
    val maxHealth: Double,
    val foodLevel: Int,
    val saturation: Double,
    val absorption: Double,
    val remainingAir: Int,
    val maxAir: Int,
    val experienceLevel: Int,
    val experienceProgress: Double,
    val totalXpPoints: Int,
)

data class LifeMetadata(
    val gameMode: String,
    val activePotionEffects: List<ActivePotionEffect>,
)

data class CurrentStates(
    val environmentFlags: EnvironmentFlags,
    val movementFlags: MovementFlags,
    val ridingVehicle: String,
)

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
