package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.MilestoneTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.core.utils.isReally

class MilestoneRouter(
    private val bus: ReportableEventBus,
    private val logger: LoggerPort,
) {
    private val milestoneTracker = MilestoneTracker()
    private var currentRunner: MatchPlayer? = null

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                currentRunner = event.runner
                milestoneTracker.clear()
            }
            is MatchEvent.MatchEnd -> currentRunner = null
            else -> {}
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        val runner = currentRunner ?: return

        val milestone: SpeedrunMilestone? = when (val payload = event.payload) {
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

            is ReportablePayload.PlayerAcquiredItem -> {
                if (payload.player isReally runner)
                    SpeedrunMilestone.ItemAcquired(payload.item, payload.method)
                else null
            }

            is ReportablePayload.PlayerFilledBucket -> {
                if (payload.player isReally runner) {
                    when (payload.fluid) {
                        "minecraft:water" -> SpeedrunMilestone.PickedUpWater
                        "minecraft:lava" -> SpeedrunMilestone.PickedUpLava
                        else -> null
                    }
                } else null
            }

            is ReportablePayload.PlayerChangedDimension -> {
                if (payload.player isReally runner) {
                    when {
                        payload.from == "minecraft:overworld" && payload.to == "minecraft:the_nether" ->
                            SpeedrunMilestone.EnteredNether

                        payload.from == "minecraft:the_nether" && payload.to == "minecraft:overworld" &&
                                milestoneTracker.hasMilestone(
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

            is ReportablePayload.PlayerThrewEnderEye -> {
                if (payload.player isReally runner) SpeedrunMilestone.ThrewEyeOfEnder else null
            }

            is ReportablePayload.EndPortalCompleted -> SpeedrunMilestone.CompletedEndPortal

            is ReportablePayload.EntityDied -> {
                if (payload.entityIdentifier != "minecraft:ender_dragon") return
                when (val cause = payload.cause) {
                    is KillCause.KilledByPlayer -> if (cause.killer isReally runner)
                        SpeedrunMilestone.DragonSlain.ByRunner
                    else
                        SpeedrunMilestone.DragonSlain.ByOther(cause.killer.name)
                    is KillCause.KilledByEntity -> SpeedrunMilestone.DragonSlain.ByOther(cause.entityIdentifier)
                    KillCause.Environmental,
                    KillCause.Unknown -> SpeedrunMilestone.DragonSlain.ByOther("environment")
                }
            }

            is ReportablePayload.PlayerDamagedEntity -> {
                if (payload.player isReally runner) {
                    when (payload.entityIdentifier) {
                        "minecraft:ender_dragon" -> {
                            val hp = payload.remainingHealth ?: return
                            val pct = (hp / 200.0) * 100.0
                            when {
                                pct <= 5.0 -> SpeedrunMilestone.DragonAt5Percent
                                pct <= 10.0 -> SpeedrunMilestone.DragonAt10Percent
                                pct <= 25.0 -> SpeedrunMilestone.DragonAt25Percent
                                pct <= 50.0 -> SpeedrunMilestone.DragonAt50Percent
                                else -> null
                            }
                        }
                        "minecraft:end_crystal" -> {
                            if (!milestoneTracker.hasMilestone(SpeedrunMilestone.DestroyedFirstEndCrystal))
                                SpeedrunMilestone.DestroyedFirstEndCrystal
                            else
                                SpeedrunMilestone.DestroyedAllEndCrystals
                        }
                        else -> null
                    }
                } else null
            }

            else -> null
        }

        if (milestone != null) {
            val newlyUnlocked = milestoneTracker.trackMilestone(milestone)
            if (newlyUnlocked) {
                logger.info("Milestone unlocked: ${runner.name} achieved $milestone")
                bus.post(
                    ReportableEvent(
                        tick = event.tick,
                        occurredAt = event.occurredAt,
                        payload = ReportablePayload.MilestoneUnlocked(runner, milestone),
                    )
                )
            }
        }
    }
}
