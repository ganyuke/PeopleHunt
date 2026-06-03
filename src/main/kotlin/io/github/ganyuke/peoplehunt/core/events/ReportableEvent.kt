package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.events.models.*
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ReportableEvent(
    val tick: Int,
    @Contextual val occurredAt: Instant = Clock.System.now(), // Handled via contextual or string serializer
    val payload: ReportablePayload,
)

@Serializable
sealed class ReportablePayload {
    // -------------------------------------------------------------------------
    // CORE MOVEMENT
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerMoved")
    data class PlayerMovedByBlock(
        val player: MatchPlayer,
        val pos: Pos4,
        val isSneaking: Boolean,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerRespawned")
    data class PlayerRespawned(
        val player: MatchPlayer,
        val pos: Pos4,
    ) : ReportablePayload()

    @Serializable
    @SerialName("TeleportSnapshot")
    data class TeleportSnapshot(
        val player: MatchPlayer,
        val from: Pos4,
        val to: Pos4,
        val cause: TeleportCause,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerGameModeChanged")
    data class PlayerGameModeChanged(
        val player: MatchPlayer,
        val from: String,
        val to: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerConnected")
    data class PlayerConnected(
        val player: MatchPlayer,
        val pos: Pos4,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerDisconnected")
    data class PlayerDisconnected(
        val player: MatchPlayer,
        val pos: Pos4,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // INVENTORY DETECTION
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("InventoryKeyframe")
    data class InventoryKeyframe(
        val player: MatchPlayer,
        val contents: List<String>,
        val heldItemSlot: Int,
    ) : ReportablePayload()

    @Serializable
    @SerialName("InventoryDelta")
    data class InventoryDelta(
        val player: MatchPlayer,
        val slot: Int,
        val item: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("MainHandChanged")
    data class MainHandChanged(
        val player: MatchPlayer,
        val slot: Int
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // STRUCTURE DETECTION
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerEnteredStructure")
    data class PlayerEnteredStructure(
        val player: MatchPlayer,
        val structureIdentifier: String,
        val pos: Pos4,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerExitedStructure")
    data class PlayerExitedStructure(
        val player: MatchPlayer,
        val structureIdentifier: String,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // FLUID DETECTION
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerEnteredFluid")
    data class PlayerEnteredFluid(
        val player: MatchPlayer,
        val state: FluidState,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerExitedFluid")
    data class PlayerExitedFluid(
        val player: MatchPlayer,
        val previousState: FluidState,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // VITALS
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerHealthRegained")
    data class PlayerHealthRegained(
        val player: MatchPlayer,
        val newHealth: Double,
        val maxHealth: Double,
        val cause: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("EntityHealthRegained")
    data class EntityHealthRegained(
        @Contextual val entityUuid: Uuid,
        val entityType: String,
        val newHealth: Double,
        val maxHealth: Double,
        val cause: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerHungerChanged")
    data class PlayerHungerChanged(
        val player: MatchPlayer,
        val foodLevel: Int,
        val saturation: Float,
        val exhaustion: Float,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerBreathChanged")
    data class PlayerBreathChanged(
        val player: MatchPlayer,
        val remainingAir: Int,
        val maxAir: Int,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerXpChanged")
    data class PlayerXpChanged(
        val player: MatchPlayer,
        val level: Int,
        val progress: Float,
        val totalExp: Int,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // COMBAT
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("EntityDied")
    data class EntityDied(
        val entityIdentifier: String,
        val pos: Pos4,
        val cause: KillCause,
        val weaponType: String? = null,
        val projectileId: Int? = null,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerDied")
    data class PlayerDied(
        val player: MatchPlayer,
        val pos: Pos4,
        val cause: KillCause,
        val deathMessage: String?,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerDamagedEntity")
    data class PlayerDamagedEntity(
        val player: MatchPlayer,
        val entityIdentifier: String,
        val amount: Double,
        val remainingHealth: Double? = null,
        val victimPos: Pos4? = null,
        val weaponType: String? = null,
        val projectileId: Int? = null,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerDamagedByEntity")
    data class PlayerDamagedByEntity(
        val player: MatchPlayer,
        val entityIdentifier: String,
        val amount: Double,
        val remainingHealth: Double? = null,
        val weaponType: String? = null,
        val projectileId: Int? = null,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerDamagedByEnvironment")
    data class PlayerDamagedByEnvironment(
        val player: MatchPlayer,
        val cause: String,
        val amount: Double,
        val remainingHealth: Double? = null,
    ) : ReportablePayload()

    @Serializable
    @SerialName("ProjectileLaunched")
    data class ProjectileLaunched(
        val projectileId: Int,
        val shooter: MatchPlayer?,
        val shooterIdentifier: String?,
        val projectileType: String,
        val launchPos: Pos4,
        val velocity: Velocity,
    ) : ReportablePayload()

    @Serializable
    @SerialName("ProjectileMoved")
    data class ProjectileMoved(
        val projectileId: Int,
        val pos: Pos4,
        val velocity: Velocity,
    ) : ReportablePayload()

    @Serializable
    @SerialName("ProjectileHit")
    data class ProjectileHit(
        val projectileId: Int,
        val shooter: MatchPlayer?,
        val shooterIdentifier: String?,
        val projectileType: String,
        val hitPos: Pos4,
        val hitEntityIdentifier: String?,
        val hitPlayer: MatchPlayer?,
        val damage: Double,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // SPEEDRUNNER MILESTONES
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerAcquiredItem")
    data class PlayerAcquiredItem(
        val player: MatchPlayer,
        val item: SpeedrunMilestone.ItemAcquired.Item,
        val method: SpeedrunMilestone.AcquisitionMethod,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerChangedDimension")
    data class PlayerChangedDimension(
        val player: MatchPlayer,
        val from: String,
        val to: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerThrewEnderEye")
    data class PlayerThrewEnderEye(
        val player: MatchPlayer,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerFilledBucket")
    data class PlayerFilledBucket(
        val player: MatchPlayer,
        val fluid: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("DragonSnapshot")
    data class DragonSnapshot(
        val pos: Pos4,
        val health: Double,
        val maxHealth: Double,
    ) : ReportablePayload()

    @Serializable
    @SerialName("EndCrystalDiscovered")
    data class EndCrystalDiscovered(
        val pos: Pos4,
        val crystalEntityId: Int,
    ) : ReportablePayload()

    @Serializable
    @SerialName("EndPortalCompleted")
    data class EndPortalCompleted(
        val pos: Pos4,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // POTION EFFECTS
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PotionEffectApplied")
    data class PotionEffectApplied(
        val player: MatchPlayer,
        val effectType: String,
        val amplifier: Int,
        val duration: Int,
        val cause: String,
        val reapplication: Boolean,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PotionEffectRemoved")
    data class PotionEffectRemoved(
        val player: MatchPlayer,
        val effectType: String,
        val cause: String,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // SNAPSHOTS
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerSnapshotChanged")
    data class PlayerSnapshotChanged(
        val player: MatchPlayer,
        val snapshot: PlayerSnapshot,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerJoined")
    data class PlayerJoined(
        val player: MatchPlayer
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerQuit")
    data class PlayerQuit(
        val player: MatchPlayer,
        val reason: String
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // CRAFTING LIFECYCLE
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerCraftedItem")
    data class PlayerCraftedItem(
        val player: MatchPlayer,
        val itemType: String,
        val amount: Int,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerRepairedItem")
    data class PlayerRepairedItem(
        val player: MatchPlayer,
        val itemType: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("PlayerItemBroke")
    data class PlayerItemBroke(
        val player: MatchPlayer,
        val itemType: String,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // FOOD TRACKING
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerConsumedItem")
    data class PlayerConsumedItem(
        val player: MatchPlayer,
        val itemType: String,
        val hungerRestored: Int,
        val saturationRestored: Float,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // MOB TRACKING
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("NearbyMobs")
    data class NearbyMobs(
        val player: MatchPlayer,
        val mobs: List<MobSnapshot>,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // LANDMARKS
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("NetherPortalCreated")
    data class NetherPortalCreated(
        val pos: Pos4,
    ) : ReportablePayload()

    @Serializable
    @SerialName("WorldSpawnRecorded")
    data class WorldSpawnRecorded(
        val pos: Pos4,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // RESPAWN LOCATIONS
    // -------------------------------------------------------------------------

    @Serializable
    @SerialName("PlayerSetSpawn")
    data class PlayerSetSpawn(
        val player: MatchPlayer,
        val pos: Pos4,
        val spawnType: String,
    ) : ReportablePayload()

    @Serializable
    @SerialName("MilestoneUnlocked")
    data class MilestoneUnlocked(
        val runner: MatchPlayer,
        val milestone: SpeedrunMilestone,
    ) : ReportablePayload()
}