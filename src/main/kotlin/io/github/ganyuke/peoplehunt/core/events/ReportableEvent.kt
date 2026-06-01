package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.events.models.FluidState
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.events.models.TeleportCause
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import kotlin.time.Clock
import kotlin.time.Instant

data class ReportableEvent(
    val tick: Int,
    val occurredAt: Instant = Clock.System.now(),
    val payload: ReportablePayload,
)

sealed class ReportablePayload {
    // -------------------------------------------------------------------------
    // CORE MOVEMENT
    // -------------------------------------------------------------------------

    data class PlayerMoved(
        val player: MatchPlayer,
        val pos: Pos4,
        val yaw: Float,
        val pitch: Float,
        val sprinting: Boolean,
        val sneaking: Boolean,
        val flying: Boolean,
        val swimming: Boolean,
        val gliding: Boolean,
    ) : ReportablePayload()

    data class PlayerRespawned(
        val player: MatchPlayer,
        val pos: Pos4,
    ) : ReportablePayload()

    // for discontinuities in the position stream (ender pearl, chorus fruit, etc.)
    // should be useful for rendering later
    data class TeleportSnapshot(
        val player: MatchPlayer,
        val from: Pos4,
        val to: Pos4,
        val cause: TeleportCause,
    ) : ReportablePayload()

    data class PlayerGameModeChanged(
        val player: MatchPlayer,
        val from: String,
        val to: String,
    ) : ReportablePayload()

    data class PlayerConnected(
        val player: MatchPlayer,
        val pos: Pos4,
    ) : ReportablePayload()

    data class PlayerDisconnected(
        val player: MatchPlayer,
        val pos: Pos4,
    ) : ReportablePayload()

    //
    // INVENTORY DETECTION
    //

    data class InventoryKeyframe(
        val player: MatchPlayer,
        val contentsHex: String
    ) : ReportablePayload()

    data class InventoryDelta(
        val player: MatchPlayer,
        val slot: Int,
        val itemHex: String
    ) : ReportablePayload()

    data class MainHandChanged(
        val player: MatchPlayer,
        val slot: Int
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // STRUCTURE DETECTION
    // -------------------------------------------------------------------------

    data class PlayerEnteredStructure(
        val player: MatchPlayer,
        val structureIdentifier: String,
    ) : ReportablePayload()

    data class PlayerExitedStructure(
        val player: MatchPlayer,
        val structureIdentifier: String,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // FLUID DETECTION
    // -------------------------------------------------------------------------

    data class PlayerEnteredFluid(
        val player: MatchPlayer,
        val state: FluidState,
    ) : ReportablePayload()

    data class PlayerExitedFluid(
        val player: MatchPlayer,
        val previousState: FluidState,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // VITALS
    // -------------------------------------------------------------------------

    data class PlayerHealthChanged(
        val player: MatchPlayer,
        val newHealth: Double,
        val maxHealth: Double,
        val absorption: Double,
    ) : ReportablePayload()

    data class PlayerHungerChanged(
        val player: MatchPlayer,
        val foodLevel: Int,
        val saturation: Float,
        val exhaustion: Float,
    ) : ReportablePayload()

    data class PlayerBreathChanged(
        val player: MatchPlayer,
        val remainingAir: Int,
        val maxAir: Int,
    ) : ReportablePayload()

    data class PlayerXpChanged(
        val player: MatchPlayer,
        val level: Int,
        val progress: Float,
        val totalExp: Int,
    ) : ReportablePayload()

    // -------------------------------------------------------------------------
    // COMBAT
    // -------------------------------------------------------------------------

    // for non-player entity deaths
    data class EntityDied(
        val entityIdentifier: String,
        val pos: Pos4,
        val cause: KillCause,
    ) : ReportablePayload()

    data class PlayerDied(
        val player: MatchPlayer,
        val pos: Pos4,
        val cause: KillCause,
        val deathMessage: String?,
    ) : ReportablePayload()

    data class PlayerDamagedEntity(
        val player: MatchPlayer,
        val entityIdentifier: String,
        val amount: Double,
        val remainingHealth: Double? = null,
    ) : ReportablePayload()

    data class PlayerDamagedByEntity(
        val player: MatchPlayer,
        val entityIdentifier: String,
        val amount: Double,
        val remainingHealth: Double? = null,
    ) : ReportablePayload()

    data class PlayerDamagedByEnvironment(
        val player: MatchPlayer,
        val cause: String,
        val amount: Double,
        val remainingHealth: Double? = null,
    ) : ReportablePayload()

    data class ProjectileLaunched(
        val player: MatchPlayer,
        val projectileType: String, // arrow, trident, snowball, etc.
        val launchPos: Pos4,
        val velocity: , // for path reconstruction
    ) : ReportablePayload()

    data class ProjectileHit(
        val shooter: MatchPlayer?, // null if not fired by player
        val projectileType: String,
        val hitPos: Pos4,
        val hitEntityIdentifier: String?, // null for hitting block
        val hitPlayer: MatchPlayer?,
    ) : ReportablePayload()


    // -------------------------------------------------------------------------
    // SPEEDRUNNER MILESTONES
    // -------------------------------------------------------------------------

    data class PlayerAcquiredItem(
        val player: MatchPlayer,
        val item: SpeedrunMilestone.ItemAcquired.Item,
        val method: SpeedrunMilestone.AcquisitionMethod,
    ) : ReportablePayload()

    // Uses Dimension instead of raw String — the set of vanilla dimensions is
    // finite and well-known. Custom dimensions fall through to Dimension.Custom.
    data class PlayerChangedDimension(
        val player: MatchPlayer,
        val from: String,
        val to: String,
    ) : ReportablePayload()

    data class PlayerThrewEnderEye(
        val player: MatchPlayer,
    ) : ReportablePayload()

    data class PlayerFilledBucket(
        val player: MatchPlayer,
        val fluid: String,
    ) : ReportablePayload()

    data class EndCrystalDestroyed(
        val player: MatchPlayer?,
    ) : ReportablePayload()

    data class EndPortalCompleted(
        val pos: Pos4,
    ) : ReportablePayload()
}