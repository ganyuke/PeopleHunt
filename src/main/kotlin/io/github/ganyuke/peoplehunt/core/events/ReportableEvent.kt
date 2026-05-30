package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.MovementSnapshot
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone

sealed class ReportableEvent {
    // core events for manhunt gameplay
    data class PlayerMoved(val movementSnapshot: MovementSnapshot) : ReportableEvent()
    data class PlayerRespawned(val player: MatchPlayer, val pos: Pos4) : ReportableEvent()
    data class EntityDied(
        val player: MatchPlayer?,
        val entityIdentifier: String,
        val pos: Pos4,
        val playerKiller: MatchPlayer?,
        val entityKiller: String?,
    ) : ReportableEvent()

    // combat stats
    data class PlayerDamagedEntity(
        val player: MatchPlayer,
        val entityIdentifier: String,
        val amount: Double,
        val remainingHealth: Double? = null
    ) : ReportableEvent()
    data class PlayerDamagedByEntity(val player: MatchPlayer, val amount: Double) : ReportableEvent()

    // item milestones
    data class PlayerAcquiredItem(
        val player: MatchPlayer,
        val item: SpeedrunMilestone.ItemAcquired.Item,
        val method: SpeedrunMilestone.AcquisitionMethod,
    ) : ReportableEvent()

    // world milestones
    data class PlayerChangedDimension(val player: MatchPlayer, val from: String, val to: String) : ReportableEvent()
    data class PlayerThrewItem(val player: MatchPlayer, val item: String) : ReportableEvent()
    data class PlayerThrewEnderEye(val player: MatchPlayer) : ReportableEvent()
    data class PlayerFilledBucket(val player: MatchPlayer, val fluid: String) : ReportableEvent()
    data class EndCrystalDestroyed(val player: MatchPlayer?) : ReportableEvent()
    data class EndPortalCompleted(val pos: Pos4) : ReportableEvent()
}
