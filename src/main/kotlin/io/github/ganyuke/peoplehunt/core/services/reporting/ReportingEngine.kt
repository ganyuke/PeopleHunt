package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.Utils.isReally
import io.github.ganyuke.peoplehunt.core.Utils.reallyContains
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.MilestoneTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.core.ports.StructureLocatorPort
import kotlin.uuid.Uuid

class ReportingEngine(
    private val outbound: MatchEventBus, // need these two for reporting write errors to operators online
    private val scheduler: SchedulerPort,
    private val structureLocator: StructureLocatorPort,
    private val logger: LoggerPort,
) {
    private val milestoneTracker = MilestoneTracker()
    private val combatStatsTracker = CombatStatsTracker()

    private var currentRunner: MatchEngine.MatchPlayer? = null
    private var currentHunters: Set<MatchEngine.MatchPlayer> = emptySet()

    private val nameResolver = HashMap<Uuid, String>()

    val participantStats
        get() = combatStatsTracker.participantStats.map { pair ->
            MatchEngine.MatchPlayer(
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
        when (event) {
            is ReportableEvent.EntityDied -> handleEntityDied(event)
            is ReportableEvent.PlayerDamagedEntity -> handleDamageDealt(event)
            is ReportableEvent.PlayerDamagedByEntity -> handleDamageReceived(event)
            else -> {}
        }

        processMilestoneTracking(event)
    }

    private fun handleDamageDealt(event: ReportableEvent.PlayerDamagedEntity) {
        if (event.player isReally currentRunner || currentHunters reallyContains event.player) {
            nameResolver[event.player.uuid] = event.player.name
            combatStatsTracker.recordDamageDealt(event.player.uuid, event.amount)
        }
    }

    private fun handleDamageReceived(event: ReportableEvent.PlayerDamagedByEntity) {
        if (event.player isReally currentRunner || currentHunters reallyContains event.player) {
            nameResolver[event.player.uuid] = event.player.name
            combatStatsTracker.recordDamageTaken(event.player.uuid, event.amount)
        }
    }

    private fun processMilestoneTracking(event: ReportableEvent) {
        val runner = currentRunner ?: return

        val milestone: SpeedrunMilestone? = when (event) {
            // reporting milestone feat: runner enters a key structure for progression
            // i.e. the fortress (for blaze rods), bastion (for piglin bartering for pearls)
            // and of course the stronghold to get to the End in the first place
            is ReportableEvent.PlayerMoved -> {
                if (event.player isReally runner) {
                    val structureIdentifier = structureLocator.getStructureAt(event.pos)
                    when (structureIdentifier) {
                        "minecraft:fortress" -> SpeedrunMilestone.EnteredFortress
                        "minecraft:bastion_remnant" -> SpeedrunMilestone.EnteredBastion
                        "minecraft:stronghold" -> SpeedrunMilestone.EnteredStronghold
                        else -> null
                    }
                } else null
            }

            // reporting milestone feat: runner picks up important item toward progression
            // i.e. iron ingot, bucket (for speedrunner:tm: portal), blaze rod, eye of ender
            is ReportableEvent.PlayerAcquiredItem -> {
                if (event.player isReally runner) {
                    SpeedrunMilestone.ItemAcquired(event.item, event.method)
                } else null
            }

            // reporting milestone feat: water & lava buckets to indicate
            // progress toward building Nether Portal the speedrunner:tm: way
            is ReportableEvent.PlayerFilledBucket -> {
                if (event.player isReally runner) {
                    when (event.fluid) {
                        "minecraft:water" -> SpeedrunMilestone.PickedUpWater
                        "minecraft:lava" -> SpeedrunMilestone.PickedUpLava
                        else -> null
                    }
                } else null
            }

            // reporting milestone feat: first entered nether/end & exited nether
            // nether exit requires blaze rods to avoid counting when the runner
            // walks back into the overworld immediately after entered the nether
            is ReportableEvent.PlayerChangedDimension -> {
                if (event.player isReally runner) {
                    when {
                        event.from == "minecraft:overworld" && event.to == "minecraft:the_nether" -> SpeedrunMilestone.EnteredNether
                        event.from == "minecraft:the_nether" && event.to == "minecraft:overworld" && milestoneTracker.hasMilestone(
                            SpeedrunMilestone.ItemAcquired(
                                SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD,
                                SpeedrunMilestone.AcquisitionMethod.PICKED_UP
                            )
                        ) -> SpeedrunMilestone.LeftNether

                        event.to == "minecraft:the_end" -> SpeedrunMilestone.EnteredEnd
                        else -> null
                    }
                } else null
            }

            // reporting milestone feat: first eye of ender thrown
            // to track when runner has started moving toward the stronghold
            is ReportableEvent.PlayerThrewEnderEye -> {
                if (event.player isReally runner) {
                    SpeedrunMilestone.ThrewEyeOfEnder
                } else null
            }

            // reporting milestone feat: end portal creation event
            // probably could just get rid of this in favor of the enter End
            // but some people like to taunt the hunters
            // also this doesn't guard if it's the runner who created the portal
            // but like, a portal is a portal, and if the hunters created the portal,
            // good on them i guess?
            is ReportableEvent.EndPortalCompleted -> {
                SpeedrunMilestone.CompletedEndPortal
            }

            // reporting milestone feat: end crystal destruction progress
            is ReportableEvent.EndCrystalDestroyed -> {
                // regardless of whether a runner blew up the crystal, track crystal death
                if (!milestoneTracker.hasMilestone(SpeedrunMilestone.DestroyedFirstEndCrystal)) {
                    SpeedrunMilestone.DestroyedFirstEndCrystal
                } else {
                    SpeedrunMilestone.DestroyedAllEndCrystals
                }
            }

            // reporting milestone feat: ender dragon death attribution
            is ReportableEvent.EntityDied -> {
                if (event.entityIdentifier == "minecraft:ender_dragon") {
                    if (event.playerKiller isReally runner) {
                        SpeedrunMilestone.DragonSlain.ByRunner
                    } else {
                        SpeedrunMilestone.DragonSlain.ByOther(
                            event.playerKiller?.name ?: event.entityKiller ?: "environment"
                        )
                    }
                } else null
            }

            // reporting milestone feat: ender dragon health percentages
            is ReportableEvent.PlayerDamagedEntity -> {
                if (event.player isReally runner && event.entityIdentifier == "minecraft:ender_dragon" && event.remainingHealth != null) {
                    // Ender Dragon standard max health is 200.0
                    val maxHealth = 200.0
                    val healthPercentage = (event.remainingHealth / maxHealth) * 100.0

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

    private fun handleEntityDied(event: ReportableEvent.EntityDied) {
        // Log player deaths
        event.player?.let { deadPlayer ->
            nameResolver[deadPlayer.uuid] = deadPlayer.name

            if (deadPlayer isReally currentRunner || currentHunters reallyContains deadPlayer) {
                logger.info("Combat Stats: ${deadPlayer.name} has died.")
                combatStatsTracker.recordDeath(deadPlayer.uuid)
            } else {
                logger.warn("Received death event for untracked player: ${deadPlayer.name} (UID: ${deadPlayer.uuid})")
            }
        }

        // Log player kills
        event.playerKiller?.let { deadPlayer ->
            nameResolver[deadPlayer.uuid] = deadPlayer.name

            if (deadPlayer isReally currentRunner || currentHunters reallyContains deadPlayer) {
                logger.info("Combat Stats: ${deadPlayer.name} scored a kill against ${event.entityIdentifier}.")
                combatStatsTracker.recordKill(deadPlayer.uuid)
            } else {
                logger.warn("Received death event for untracked killer: ${deadPlayer.name} (UID: ${deadPlayer.uuid})")
            }
        }
    }
}