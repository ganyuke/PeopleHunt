package io.github.ganyuke.peoplehunt.core.testutil

import io.github.ganyuke.peoplehunt.core.events.MobSnapshot
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.SpawnType
import io.github.ganyuke.peoplehunt.core.events.models.FluidState
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.events.models.TeleportCause
import io.github.ganyuke.peoplehunt.core.events.models.Velocity
import kotlin.time.Clock
import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone

private const val DEFAULT_TICK = 0

fun playerMoved(
    player: MatchPlayer,
    pos: Pos4 = pos(),
    yaw: Float = 0f,
    pitch: Float = 0f,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerMoved(
        player = player,
        pos = pos,
        yaw = yaw,
        pitch = pitch,
        sprinting = false,
        sneaking = false,
        flying = false,
        swimming = false,
        gliding = false,
    ),
)

fun playerRespawned(player: MatchPlayer, pos: Pos4 = pos()) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerRespawned(player, pos),
)

fun playerDied(
    player: MatchPlayer,
    pos: Pos4 = pos(),
    cause: KillCause = KillCause.Environmental,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerDied(player, pos, cause, deathMessage = null),
)

fun entityDied(
    entityIdentifier: String,
    pos: Pos4 = pos(),
    cause: KillCause = KillCause.Environmental,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.EntityDied(entityIdentifier, pos, cause),
)

fun playerDamagedEntity(
    player: MatchPlayer,
    entityIdentifier: String,
    amount: Double,
    remainingHealth: Double? = null,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerDamagedEntity(player, entityIdentifier, amount, remainingHealth),
)

fun playerDamagedByEntity(
    player: MatchPlayer,
    entityIdentifier: String,
    amount: Double,
    remainingHealth: Double? = null,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerDamagedByEntity(player, entityIdentifier, amount, remainingHealth),
)

fun playerDamagedByEnvironment(
    player: MatchPlayer,
    cause: String = "FALL",
    amount: Double = 10.0,
    remainingHealth: Double? = null,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerDamagedByEnvironment(player, cause, amount, remainingHealth),
)

fun projectileLaunched(
    projectileId: Int = 1,
    shooter: MatchPlayer? = null,
    shooterIdentifier: String? = null,
    projectileType: String = "minecraft:arrow",
    launchPos: Pos4 = pos(),
    velocity: Velocity = Velocity(0.0, 0.0, 0.0),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.ProjectileLaunched(projectileId, shooter, shooterIdentifier, projectileType, launchPos, velocity),
)

fun projectileMoved(
    projectileId: Int = 1,
    pos: Pos4 = pos(),
    velocity: Velocity = Velocity(0.0, 0.0, 0.0),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.ProjectileMoved(projectileId, pos, velocity),
)

fun projectileHit(
    projectileId: Int = 1,
    shooter: MatchPlayer? = null,
    shooterIdentifier: String? = null,
    projectileType: String = "minecraft:arrow",
    hitPos: Pos4 = pos(),
    hitEntityIdentifier: String? = null,
    hitPlayer: MatchPlayer? = null,
    damage: Double = 0.0,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.ProjectileHit(projectileId, shooter, shooterIdentifier, projectileType, hitPos, hitEntityIdentifier, hitPlayer, damage),
)

fun playerAcquiredItem(
    player: MatchPlayer,
    item: SpeedrunMilestone.ItemAcquired.Item,
    method: SpeedrunMilestone.AcquisitionMethod,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerAcquiredItem(player, item, method),
)

fun playerChangedDimension(
    player: MatchPlayer,
    from: String,
    to: String,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerChangedDimension(player, from, to),
)

fun playerThrewEnderEye(player: MatchPlayer) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerThrewEnderEye(player),
)

fun playerFilledBucket(player: MatchPlayer, fluid: String) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerFilledBucket(player, fluid),
)

fun endPortalCompleted(pos: Pos4) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.EndPortalCompleted(pos),
)

fun playerEnteredStructure(
    player: MatchPlayer,
    structure: String,
    pos: Pos4 = pos(),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerEnteredStructure(player, structure, pos),
)

fun potionEffectApplied(
    player: MatchPlayer,
    effectType: String = "minecraft:speed",
    amplifier: Int = 0,
    duration: Int = 200,
    cause: String = "minecraft:arrow",
    reapplication: Boolean = false,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PotionEffectApplied(player, effectType, amplifier, duration, cause, reapplication),
)

fun potionEffectRemoved(
    player: MatchPlayer,
    effectType: String = "minecraft:speed",
    cause: String = "minecraft:arrow",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PotionEffectRemoved(player, effectType, cause),
)

fun playerSnapshotChanged(player: MatchPlayer) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerSnapshotChanged(
        player = player,
        snapshot = io.github.ganyuke.peoplehunt.core.events.models.PlayerSnapshot.Online(
            io.github.ganyuke.peoplehunt.core.events.models.OnlineState.Alive(
                io.github.ganyuke.peoplehunt.core.events.models.CurrentLifeData(
                    spatialData = io.github.ganyuke.peoplehunt.core.events.models.SpatialData(
                        position = pos(),
                        yaw = 0f,
                        pitch = 0f,
                        velocity = Velocity(0.0, 0.0, 0.0),
                    ),
                    vitals = io.github.ganyuke.peoplehunt.core.events.models.Vitals(
                        health = 20.0,
                        maxHealth = 20.0,
                        foodLevel = 20,
                        saturation = 5.0f,
                        absorption = 0.0,
                        remainingAir = 300,
                        maxAir = 300,
                        experienceLevel = 0,
                        experienceProgress = 0.0f,
                        totalXpPoints = 0,
                    ),
                    currentStates = io.github.ganyuke.peoplehunt.core.events.models.CurrentStates(
                        environmentFlags = io.github.ganyuke.peoplehunt.core.events.models.EnvironmentFlags(
                            isBurning = false,
                            isDrowning = false,
                            isSuffocating = false,
                            isFreezing = false,
                            isWadingInWater = false,
                            isWadingInLava = false,
                            isSubmergedInWater = false,
                            isSubmergedInLava = false,
                            isInsideCobweb = false,
                            isInsideSweetBerry = false,
                        ),
                        movementFlags = io.github.ganyuke.peoplehunt.core.events.models.MovementFlags(
                            isSleeping = false,
                            isRiptiding = false,
                            isClimbing = false,
                            isSwimming = false,
                            isSprinting = false,
                            isSneaking = false,
                            isFlying = false,
                            isGliding = false,
                        ),
                        ridingVehicle = "none",
                    ),
                    metadata = io.github.ganyuke.peoplehunt.core.events.models.LifeMetadata(
                        gameMode = "SURVIVAL",
                        activePotionEffects = emptyList(),
                    ),
                ),
            ),
        ),
    ),
)

fun teleportSnapshot(
    player: MatchPlayer = player(),
    from: Pos4 = pos(),
    to: Pos4 = pos(1, 64, 1),
    cause: TeleportCause = TeleportCause.ENDER_PEARL,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.TeleportSnapshot(player, from, to, cause),
)

fun playerGameModeChanged(
    player: MatchPlayer,
    from: String = "SURVIVAL",
    to: String = "CREATIVE",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerGameModeChanged(player, from, to),
)

fun playerConnected(
    player: MatchPlayer,
    pos: Pos4 = pos(),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerConnected(player, pos),
)

fun playerDisconnected(
    player: MatchPlayer,
    pos: Pos4 = pos(),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerDisconnected(player, pos),
)

fun playerExitedStructure(
    player: MatchPlayer,
    structureIdentifier: String = "minecraft:fortress",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerExitedStructure(player, structureIdentifier),
)

fun playerEnteredFluid(
    player: MatchPlayer,
    state: FluidState = FluidState.SubmergedInWater(Clock.System.now()),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerEnteredFluid(player, state),
)

fun playerExitedFluid(
    player: MatchPlayer,
    previousState: FluidState = FluidState.SubmergedInWater(Clock.System.now()),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerExitedFluid(player, previousState),
)

fun playerHealthRegained(
    player: MatchPlayer,
    newHealth: Double = 20.0,
    maxHealth: Double = 20.0,
    cause: String = "CUSTOM",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerHealthRegained(player, newHealth, maxHealth, cause),
)

fun entityHealthRegained(
    entityUuid: Uuid = Uuid.random(),
    entityType: String = "minecraft:ender_dragon",
    newHealth: Double = 200.0,
    maxHealth: Double = 300.0,
    cause: String = "ENDER_CRYSTAL",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.EntityHealthRegained(entityUuid, entityType, newHealth, maxHealth, cause),
)

fun playerHungerChanged(
    player: MatchPlayer,
    foodLevel: Int = 20,
    saturation: Float = 5.0f,
    exhaustion: Float = 0.0f,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerHungerChanged(player, foodLevel, saturation, exhaustion),
)

fun playerBreathChanged(
    player: MatchPlayer,
    remainingAir: Int = 300,
    maxAir: Int = 300,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerBreathChanged(player, remainingAir, maxAir),
)

fun playerXpChanged(
    player: MatchPlayer,
    level: Int = 0,
    progress: Float = 0.0f,
    totalExp: Int = 0,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerXpChanged(player, level, progress, totalExp),
)

fun playerJoined(player: MatchPlayer) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerJoined(player),
)

fun playerQuit(
    player: MatchPlayer,
    reason: String = "QUIT",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerQuit(player, reason),
)

// -------------------------------------------------------------------------
// CRAFTING LIFECYCLE
// -------------------------------------------------------------------------

fun playerCraftedItem(
    player: MatchPlayer,
    itemType: String = "minecraft:diamond_sword",
    amount: Int = 1,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerCraftedItem(player, itemType, amount),
)

fun playerRepairedItem(
    player: MatchPlayer,
    itemType: String = "minecraft:diamond_sword",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerRepairedItem(player, itemType),
)

fun playerItemBroke(
    player: MatchPlayer,
    itemType: String = "minecraft:diamond_sword",
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerItemBroke(player, itemType),
)

// -------------------------------------------------------------------------
// FOOD TRACKING
// -------------------------------------------------------------------------

fun playerConsumedItem(
    player: MatchPlayer,
    itemType: String = "minecraft:cooked_beef",
    hungerRestored: Int = 8,
    saturationRestored: Float = 12.8f,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerConsumedItem(player, itemType, hungerRestored, saturationRestored),
)

// -------------------------------------------------------------------------
// MOB TRACKING
// -------------------------------------------------------------------------

fun nearbyMobs(
    player: MatchPlayer,
    mobs: List<MobSnapshot> = emptyList(),
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.NearbyMobs(player, mobs),
)

// -------------------------------------------------------------------------
// LANDMARKS
// -------------------------------------------------------------------------

fun netherPortalCreated(pos: Pos4 = pos()) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.NetherPortalCreated(pos),
)

fun worldSpawnRecorded(pos: Pos4 = pos()) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.WorldSpawnRecorded(pos),
)

// -------------------------------------------------------------------------
// RESPAWN LOCATIONS
// -------------------------------------------------------------------------

fun playerSetSpawn(
    player: MatchPlayer,
    pos: Pos4 = pos(),
    spawnType: SpawnType = SpawnType.BED,
) = ReportableEvent(
    tick = DEFAULT_TICK,
    payload = ReportablePayload.PlayerSetSpawn(player, pos, spawnType),
)
