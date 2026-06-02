package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.MilestoneTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.core.utils.isReally
import io.github.ganyuke.peoplehunt.core.utils.reallyContains
import kotlin.uuid.Uuid

class ReportingEngine(
    private val outbound: MatchEventBus, // need these two for reporting write errors to operators online
    private val scheduler: SchedulerPort,
    private val logger: LoggerPort,
) {
    private val milestoneTracker = MilestoneTracker()
    private val combatStatsTracker = CombatStatsTracker()

    private var currentRunner: MatchPlayer? = null
    private var currentHunters: Set<MatchPlayer> = emptySet()

    private val nameResolver = HashMap<Uuid, String>()

    val participantStats
        get() = combatStatsTracker.participantStats.map { pair ->
            MatchPlayer(
                pair.first,
                nameResolver[pair.first] ?: "unknown"
            ) to pair.second
        }

    // will be called by async SQL thread so need to run this on the main thread
    // everything else in this engine runs on the main Bukkit thread
    internal fun reportError(message: String) {
        scheduler.runOnMainThread {
            logger.error("ReportingEngine encountered an operational error: $message")
            outbound.post(MatchEvent.OperatorNotification("Error occured in reporting engine: $message"))
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        when (val payload = event.payload) {
            is ReportablePayload.PlayerDied -> handlePlayerDied(payload)
            is ReportablePayload.EntityDied -> handleEntityDied(payload)
            is ReportablePayload.PlayerDamagedEntity -> handleDamageDealt(payload)
            is ReportablePayload.PlayerDamagedByEntity -> handleDamageReceived(payload)
            is ReportablePayload.PotionEffectApplied -> handlePotionEffectApplied(payload)
            is ReportablePayload.PotionEffectRemoved -> handlePotionEffectRemoved(payload)
            is ReportablePayload.PlayerSnapshotChanged -> handleSnapshot(payload)
            else -> {}
        }

        processMilestoneTracking(event)
    }

    private fun handleDamageDealt(payload: ReportablePayload.PlayerDamagedEntity) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            combatStatsTracker.recordDamageDealt(payload.player.uuid, payload.amount)
        }
    }

    private fun handleDamageReceived(payload: ReportablePayload.PlayerDamagedByEntity) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            combatStatsTracker.recordDamageTaken(payload.player.uuid, payload.amount)
        }
    }

    private fun processMilestoneTracking(event: ReportableEvent) {
        val runner = currentRunner ?: return

        val milestone: SpeedrunMilestone? = when (val payload = event.payload) {
            // reporting milestone feat: runner enters a key structure for progression
            // i.e. the fortress (for blaze rods), bastion (for piglin bartering for pearls)
            // and of course the stronghold to get to the End in the first place
            is ReportablePayload.PlayerEnteredStructure -> {
                if (payload.player isReally runner) {
                    when (payload.structureIdentifier) {
                        "minecraft:fortress" -> SpeedrunMilestone.EnteredFortress
                        "minecraft:bastion_remnant" -> SpeedrunMilestone.EnteredBastion
                        "minecraft:stronghold" -> SpeedrunMilestone.EnteredStronghold
                        else -> null
                    }
                } else null
            }

            // reporting milestone feat: runner picks up important item toward progression
            // i.e. iron ingot, bucket (for speedrunner:tm: portal), blaze rod, eye of ender
            is ReportablePayload.PlayerAcquiredItem -> {
                if (payload.player isReally runner) {
                    SpeedrunMilestone.ItemAcquired(payload.item, payload.method)
                } else null
            }

            // reporting milestone feat: water & lava buckets to indicate
            // progress toward building Nether Portal the speedrunner:tm: way
            is ReportablePayload.PlayerFilledBucket -> {
                if (payload.player isReally runner) {
                    when (payload.fluid) {
                        "minecraft:water" -> SpeedrunMilestone.PickedUpWater
                        "minecraft:lava" -> SpeedrunMilestone.PickedUpLava
                        else -> null
                    }
                } else null
            }

            // reporting milestone feat: first entered nether/end & exited nether
            // nether exit requires blaze rods to avoid counting when the runner
            // walks back into the overworld immediately after entered the nether
            is ReportablePayload.PlayerChangedDimension -> {
                if (payload.player isReally runner) {
                    when {
                        payload.from == "minecraft:overworld" && payload.to == "minecraft:the_nether" -> SpeedrunMilestone.EnteredNether
                        payload.from == "minecraft:the_nether" && payload.to == "minecraft:overworld" && milestoneTracker.hasMilestone(
                            SpeedrunMilestone.ItemAcquired(
                                SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD,
                                SpeedrunMilestone.AcquisitionMethod.PICKED_UP
                            )
                        ) -> SpeedrunMilestone.LeftNether

                        payload.to == "minecraft:the_end" -> SpeedrunMilestone.EnteredEnd
                        else -> null
                    }
                } else null
            }

            // reporting milestone feat: first eye of ender thrown
            // to track when runner has started moving toward the stronghold
            is ReportablePayload.PlayerThrewEnderEye -> {
                if (payload.player isReally runner) {
                    SpeedrunMilestone.ThrewEyeOfEnder
                } else null
            }

            // reporting milestone feat: end portal creation event
            // probably could just get rid of this in favor of the enter End
            // but some people like to taunt the hunters
            // also this doesn't guard if it's the runner who created the portal
            // but like, a portal is a portal, and if the hunters created the portal,
            // good on them i guess?
            is ReportablePayload.EndPortalCompleted -> {
                SpeedrunMilestone.CompletedEndPortal
            }

            // reporting milestone feat: end crystal destruction progress
            is ReportablePayload.EndCrystalDestroyed -> {
                // regardless of whether a runner blew up the crystal, track crystal death
                if (!milestoneTracker.hasMilestone(SpeedrunMilestone.DestroyedFirstEndCrystal)) {
                    SpeedrunMilestone.DestroyedFirstEndCrystal
                } else {
                    SpeedrunMilestone.DestroyedAllEndCrystals
                }
            }

            // reporting milestone feat: ender dragon death attribution
            is ReportablePayload.EntityDied -> {
                if (payload.entityIdentifier != "minecraft:ender_dragon") return
                when (val cause = payload.cause) {
                    is KillCause.KilledByPlayer -> if (cause.killer isReally runner) {
                        SpeedrunMilestone.DragonSlain.ByRunner
                    } else {
                        SpeedrunMilestone.DragonSlain.ByOther(cause.killer.name)
                    }
                    is KillCause.KilledByEntity -> SpeedrunMilestone.DragonSlain.ByOther(cause.entityIdentifier)
                    KillCause.Environmental,
                    KillCause.Unknown -> SpeedrunMilestone.DragonSlain.ByOther("environment")
                }
            }

            // reporting milestone feat: ender dragon health percentages
            is ReportablePayload.PlayerDamagedEntity -> {
                if (payload.player isReally runner && payload.entityIdentifier == "minecraft:ender_dragon" && payload.remainingHealth != null) {
                    // Ender Dragon standard max health is 200.0
                    val maxHealth = 200.0
                    val healthPercentage = (payload.remainingHealth / maxHealth) * 100.0

                    when {
                        healthPercentage <= 5.0 -> SpeedrunMilestone.DragonAt5Percent
                        healthPercentage <= 10.0 -> SpeedrunMilestone.DragonAt10Percent
                        healthPercentage <= 25.0 -> SpeedrunMilestone.DragonAt25Percent
                        healthPercentage <= 50.0 -> SpeedrunMilestone.DragonAt50Percent
                        else -> null
                    }
                } else null
            }

            else -> null
        }

        if (milestone != null) {
            val newlyUnlocked = milestoneTracker.trackMilestone(milestone)
            if (newlyUnlocked) {
                logger.info("Milestone Unlocked: Runner (${runner.name}) achieved: $milestone")
            }
        }
    }

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                currentRunner = event.runner
                currentHunters = event.hunters
                milestoneTracker.clear()
                combatStatsTracker.clear()
            }

            is MatchEvent.MatchEnd -> {
                currentRunner = null
                currentHunters = emptySet()
            }

            else -> {}
        }
    }

    private fun handlePlayerDied(payload: ReportablePayload.PlayerDied) {
        nameResolver[payload.player.uuid] = payload.player.name

        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            logger.info("Combat Stats: ${payload.player.name} has died.")
            combatStatsTracker.recordDeath(payload.player.uuid)
        } else {
            logger.warn("Received death event for untracked player: ${payload.player.name} (UID: ${payload.player.uuid})")
        }

        if (payload.cause is KillCause.KilledByPlayer) {
            val killer = payload.cause.killer
            nameResolver[killer.uuid] = killer.name

            if (killer isReally currentRunner || currentHunters reallyContains killer) {
                logger.info("Combat Stats: ${killer.name} scored a kill.")
                combatStatsTracker.recordKill(killer.uuid)
            } else {
                logger.warn("Received kill event for untracked killer: ${killer.name} (UID: ${killer.uuid})")
            }
        }
    }

    private fun handleEntityDied(payload: ReportablePayload.EntityDied) {
        if (payload.cause is KillCause.KilledByPlayer) {
            val killer = payload.cause.killer
            nameResolver[killer.uuid] = killer.name

            if (killer isReally currentRunner || currentHunters reallyContains killer) {
                logger.info("Combat Stats: ${killer.name} scored a kill against ${payload.entityIdentifier}.")
                combatStatsTracker.recordKill(killer.uuid)
            } else {
                logger.warn("Received kill event for untracked killer: ${killer.name} (UID: ${killer.uuid})")
            }
        }
    }

    private fun handlePotionEffectApplied(payload: ReportablePayload.PotionEffectApplied) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            val verb = if (payload.reapplication) "Reapplied" else "Applied"
            logger.info("Potion: $verb ${payload.effectType} (${payload.amplifier}, ${payload.duration}t) to ${payload.player.name} via ${payload.cause}")
        }
    }

    private fun handlePotionEffectRemoved(payload: ReportablePayload.PotionEffectRemoved) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            logger.info("Potion: Removed ${payload.effectType} from ${payload.player.name} via ${payload.cause}")
        }
    }

    private fun handleSnapshot(payload: ReportablePayload.PlayerSnapshotChanged) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            val s = payload.snapshot
            logger.info("Snapshot: ${payload.player.name} pos=(${s.spatialData.position.x},${s.spatialData.position.y},${s.spatialData.position.z}) hp=${s.vitals.health} food=${s.vitals.foodLevel} effects=${s.metadata.activePotionEffects.size}")
        }
    }
}