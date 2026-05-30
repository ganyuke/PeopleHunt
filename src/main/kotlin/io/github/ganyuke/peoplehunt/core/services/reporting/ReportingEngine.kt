package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.Utils.isReally
import io.github.ganyuke.peoplehunt.core.Utils.reallyContains
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.CombatStatsTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.MilestoneTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.paper.adapters.StructureLocatorAdapter

class ReportingEngine(
    private val outbound: MatchEventBus, // need these two for reporting write errors to operators online
    private val scheduler: SchedulerPort,
    private val structureLocatorAdapter: StructureLocatorAdapter,
    private val logger: LoggerPort,
) {
    data class ParticipantStats(val player: MatchEngine.MatchPlayer, val kills: Int, val deaths: Int)

    private val milestoneTracker = MilestoneTracker()
    private val combatStatsTracker = CombatStatsTracker()

    private var currentRunner: MatchEngine.MatchPlayer? = null
    private var currentHunters: Set<MatchEngine.MatchPlayer> = emptySet()

    // will be called by async SQL thread so need to run this on the main thread
    // everything else in this engine runs on the main Bukkit thread
    private fun reportError(message: String) {
        scheduler.runOnMainThread {
            logger.error("ReportingEngine encountered an operational error: $message")
            outbound.post(MatchEvent.OperatorNotification("Error occured in reporting engine: $message"))
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        when (event) {
            is ReportableEvent.EntityDied -> handleEntityDied(event)
            is ReportableEvent.PlayerMoved -> {
                if (event.player isReally currentRunner) {
                    val structureIdentifier = structureLocatorAdapter.getStructureAt(event.pos)
                    logger.info(structureIdentifier.toString())
                    val newlyUnlocked = when (structureIdentifier) {
                        "minecraft:fortress" -> milestoneTracker.trackMilestone(SpeedrunMilestone.EnteredFortress)
                        "minecraft:bastion_remnant" -> milestoneTracker.trackMilestone(SpeedrunMilestone.EnteredBastion)
                        "minecraft:stronghold" -> milestoneTracker.trackMilestone(SpeedrunMilestone.EnteredStronghold)
                        else -> false
                    }
                    if (newlyUnlocked) {
                        logger.info("Milestone Unlocked: Runner (${currentRunner?.name}) entered structure: $structureIdentifier")
                    }
                }
            }

            else -> {}
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
            if (deadPlayer isReally currentRunner || currentHunters reallyContains deadPlayer) {
                logger.info("Combat Stats: ${deadPlayer.name} has died.")
                combatStatsTracker.recordDeath(deadPlayer.uuid)
            } else {
                logger.warn("Received death event for untracked player: ${deadPlayer.name} (UID: ${deadPlayer.uuid})")
            }
        }

        // Log player kills
        event.playerKiller?.let { deadPlayer ->
            if (deadPlayer isReally currentRunner || currentHunters reallyContains deadPlayer) {
                logger.info("Combat Stats: ${deadPlayer.name} scored a kill against ${event.entityIdentifier}.")
                combatStatsTracker.recordKill(deadPlayer.uuid)
            } else {
                logger.warn("Received death event for untracked killer: ${deadPlayer.name} (UID: ${deadPlayer.uuid})")
            }
        }
    }

    fun getParticipantStats(): List<ParticipantStats> {
        return emptyList()
    }
}