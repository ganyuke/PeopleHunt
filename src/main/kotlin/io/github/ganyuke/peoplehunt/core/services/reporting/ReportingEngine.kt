package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.OnlineState
import io.github.ganyuke.peoplehunt.core.events.models.PlayerSnapshot
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.utils.isReally
import io.github.ganyuke.peoplehunt.core.utils.reallyContains

class ReportingEngine(private val logger: LoggerPort) {
    private val combatStatsTracker = CombatStatsTracker()
    private val structureVisitTracker = StructureVisitTracker()

    private var currentRunner: MatchPlayer? = null
    private var currentHunters: Set<MatchPlayer> = emptySet()

    val participantStats
        get() = combatStatsTracker.participantStats

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                currentRunner = event.runner
                currentHunters = event.hunters
                combatStatsTracker.clear()
                structureVisitTracker.clear()
            }

            is MatchEvent.MatchEnd -> {
                currentRunner = null
                currentHunters = emptySet()
            }

            else -> {}
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        val payload = event.payload

        // Stats tracking — only the cases that need real work.
        when (payload) {
            is ReportablePayload.PlayerDied -> {
                if (isTracked(payload.player)) {
                    combatStatsTracker.recordDeath(payload.player)
                } else {
                    logger.warn("Death event for untracked player: ${payload.player.name} (${payload.player.uuid})")
                }
                if (payload.cause is KillCause.KilledByPlayer) {
                    val killer = payload.cause.killer
                    if (isTracked(killer)) combatStatsTracker.recordKill(killer)
                    else logger.warn("Kill event for untracked killer: ${killer.name} (${killer.uuid})")
                }
            }

            is ReportablePayload.EntityDied -> {
                if (payload.cause is KillCause.KilledByPlayer) {
                    val killer = payload.cause.killer
                    if (isTracked(killer)) combatStatsTracker.recordKill(killer)
                    else logger.warn("Kill event for untracked killer: ${killer.name} (${killer.uuid})")
                }
            }

            is ReportablePayload.PlayerDamagedEntity -> {
                if (isTracked(payload.player))
                    combatStatsTracker.recordDamageDealt(payload.player, payload.amount)
            }

            is ReportablePayload.PlayerDamagedByEntity -> {
                if (isTracked(payload.player))
                    combatStatsTracker.recordDamageTaken(payload.player, payload.amount)
            }

            is ReportablePayload.PlayerEnteredStructure -> {
                if (structureVisitTracker.recordEntry(payload.structureIdentifier, payload.pos)) {
                    logger.info("GlobalStructureFirstVisit: ${payload.structureIdentifier} at (${payload.pos.x},${payload.pos.y},${payload.pos.z}) by ${payload.player.name}")
                }
            }

            // PlayerSnapshotChanged is the one event where toString() isn't useful —
            // pull out the interesting bits manually.
            is ReportablePayload.PlayerSnapshotChanged -> {
                if (isTracked(payload.player)) {
                    val alive = (payload.snapshot as? PlayerSnapshot.Online)
                        ?.state as? OnlineState.Alive
                    if (alive != null) {
                        val s = alive.currentLifeData
                        logger.info(
                            "[t=${event.tick}] Snapshot: ${payload.player.name} " +
                                    "pos=(${s.spatialData.position.x},${s.spatialData.position.y},${s.spatialData.position.z}) " +
                                    "hp=${s.vitals.health} food=${s.vitals.foodLevel} " +
                                    "effects=${s.metadata.activePotionEffects.size}"
                        )
                    }
                }
                return // skip the generic log below
            }

            else -> {}
        }

        // generic logging for dumb data claasses
        if (shouldLog(payload)) {
            logger.info("[t=${event.tick}] ${payload::class.simpleName}: $payload")
        }
    }

    private fun isTracked(player: MatchPlayer) =
        player isReally currentRunner || currentHunters reallyContains player

    // exclude high-frequency events that would spam the log.
    private fun shouldLog(payload: ReportablePayload): Boolean = when (payload) {
        is ReportablePayload.ProjectileMoved -> false // per-tick, path data only
        is ReportablePayload.NearbyMobs -> false // per-tick scan
        else -> {
            val player = payload.playerOrNull()
            player == null || isTracked(player)
        }
    }

    // easy extract for primary player
    private fun ReportablePayload.playerOrNull(): MatchPlayer? = when (this) {
        is ReportablePayload.PlayerMovedByBlock -> player
        is ReportablePayload.PlayerRespawned -> player
        is ReportablePayload.TeleportSnapshot -> player
        is ReportablePayload.PlayerGameModeChanged -> player
        is ReportablePayload.PlayerConnected -> player
        is ReportablePayload.PlayerDisconnected -> player
        is ReportablePayload.InventoryKeyframe -> player
        is ReportablePayload.InventoryDelta -> player
        is ReportablePayload.MainHandChanged -> player
        is ReportablePayload.PlayerEnteredStructure -> player
        is ReportablePayload.PlayerExitedStructure -> player
        is ReportablePayload.PlayerEnteredFluid -> player
        is ReportablePayload.PlayerExitedFluid -> player
        is ReportablePayload.PlayerHealthRegained -> player
        is ReportablePayload.PlayerHungerChanged -> player
        is ReportablePayload.PlayerBreathChanged -> player
        is ReportablePayload.PlayerXpChanged -> player
        is ReportablePayload.PlayerDied -> player
        is ReportablePayload.PlayerDamagedEntity -> player
        is ReportablePayload.PlayerDamagedByEntity -> player
        is ReportablePayload.PlayerDamagedByEnvironment -> player
        is ReportablePayload.ProjectileLaunched -> shooter
        is ReportablePayload.ProjectileHit -> shooter
        is ReportablePayload.PlayerAcquiredItem -> player
        is ReportablePayload.PlayerChangedDimension -> player
        is ReportablePayload.PlayerThrewEnderEye -> player
        is ReportablePayload.PlayerFilledBucket -> player
        is ReportablePayload.PotionEffectApplied -> player
        is ReportablePayload.PotionEffectRemoved -> player
        is ReportablePayload.PlayerSnapshotChanged -> player
        is ReportablePayload.PlayerJoined -> player
        is ReportablePayload.PlayerQuit -> player
        is ReportablePayload.PlayerCraftedItem -> player
        is ReportablePayload.PlayerRepairedItem -> player
        is ReportablePayload.PlayerItemBroke -> player
        is ReportablePayload.PlayerConsumedItem -> player
        is ReportablePayload.NearbyMobs -> player
        is ReportablePayload.PlayerSetSpawn -> player
        is ReportablePayload.MilestoneUnlocked -> runner
        // no primary player
        is ReportablePayload.EntityDied -> null
        is ReportablePayload.EntityHealthRegained -> null
        is ReportablePayload.ProjectileMoved -> null
        is ReportablePayload.DragonSnapshot -> null
        is ReportablePayload.EndCrystalDiscovered -> null
        is ReportablePayload.EndPortalCompleted -> null
        is ReportablePayload.NetherPortalCreated -> null
        is ReportablePayload.WorldSpawnRecorded -> null
    }
}