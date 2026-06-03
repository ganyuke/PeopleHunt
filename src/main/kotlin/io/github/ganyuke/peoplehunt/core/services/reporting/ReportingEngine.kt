package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.OnlineState
import io.github.ganyuke.peoplehunt.core.events.models.PlayerSnapshot
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
    private val structureVisitTracker = StructureVisitTracker()

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
            is ReportablePayload.PlayerMoved -> handlePlayerMoved(payload)
            is ReportablePayload.PlayerRespawned -> handlePlayerRespawned(payload)
            is ReportablePayload.TeleportSnapshot -> handleTeleportSnapshot(payload)
            is ReportablePayload.PlayerGameModeChanged -> handlePlayerGameModeChanged(payload)
            is ReportablePayload.PlayerConnected -> handlePlayerConnected(payload)
            is ReportablePayload.PlayerDisconnected -> handlePlayerDisconnected(payload)
            is ReportablePayload.InventoryKeyframe -> handleInventoryKeyframe(payload)
            is ReportablePayload.InventoryDelta -> handleInventoryDelta(payload)
            is ReportablePayload.MainHandChanged -> handleMainHandChanged(payload)
            is ReportablePayload.PlayerEnteredStructure -> handlePlayerEnteredStructure(payload)
            is ReportablePayload.PlayerExitedStructure -> handlePlayerExitedStructure(payload)
            is ReportablePayload.PlayerEnteredFluid -> handlePlayerEnteredFluid(payload)
            is ReportablePayload.PlayerExitedFluid -> handlePlayerExitedFluid(payload)
            is ReportablePayload.PlayerHealthRegained -> handlePlayerHealthRegained(payload)
            is ReportablePayload.EntityHealthRegained -> handleEntityHealthRegained(payload)
            is ReportablePayload.PlayerHungerChanged -> handlePlayerHungerChanged(payload)
            is ReportablePayload.PlayerBreathChanged -> handlePlayerBreathChanged(payload)
            is ReportablePayload.PlayerXpChanged -> handlePlayerXpChanged(payload)
            is ReportablePayload.PlayerDamagedByEnvironment -> handlePlayerDamagedByEnvironment(payload)
            is ReportablePayload.ProjectileLaunched -> handleProjectileLaunched(payload)
            is ReportablePayload.ProjectileMoved -> handleProjectileMoved(payload)
            is ReportablePayload.ProjectileHit -> handleProjectileHit(payload)
            is ReportablePayload.PlayerAcquiredItem -> handlePlayerAcquiredItem(payload)
            is ReportablePayload.PlayerChangedDimension -> handlePlayerChangedDimension(payload)
            is ReportablePayload.PlayerThrewEnderEye -> handlePlayerThrewEnderEye(payload)
            is ReportablePayload.PlayerFilledBucket -> handlePlayerFilledBucket(payload)
            is ReportablePayload.DragonSnapshot -> handleDragonSnapshot(payload)
            is ReportablePayload.EndCrystalDiscovered -> handleEndCrystalDiscovered(payload)
            is ReportablePayload.EndPortalCompleted -> handleEndPortalCompleted(payload)
            is ReportablePayload.PlayerJoined -> handlePlayerJoined(payload)
            is ReportablePayload.PlayerQuit -> handlePlayerQuit(payload)
            is ReportablePayload.PlayerCraftedItem -> handlePlayerCraftedItem(payload)
            is ReportablePayload.PlayerRepairedItem -> handlePlayerRepairedItem(payload)
            is ReportablePayload.PlayerItemBroke -> handlePlayerItemBroke(payload)
            is ReportablePayload.PlayerConsumedItem -> handlePlayerConsumedItem(payload)
            is ReportablePayload.NearbyMobs -> handleNearbyMobs(payload)
            is ReportablePayload.NetherPortalCreated -> handleNetherPortalCreated(payload)
            is ReportablePayload.WorldSpawnRecorded -> handleWorldSpawnRecorded(payload)
            is ReportablePayload.PlayerSetSpawn -> handlePlayerSetSpawn(payload)
        }

        processMilestoneTracking(event)
    }

    private fun handleDamageDealt(payload: ReportablePayload.PlayerDamagedEntity) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            combatStatsTracker.recordDamageDealt(payload.player.uuid, payload.amount)
            val weapon = payload.weaponType ?: "none"
            val projectile = payload.projectileId?.let { " (projectile=$it)" } ?: ""
            logger.info("DamageDealt: ${payload.player.name} dealt ${payload.amount} to ${payload.entityIdentifier} with $weapon$projectile")
        }
    }

    private fun handleDamageReceived(payload: ReportablePayload.PlayerDamagedByEntity) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            combatStatsTracker.recordDamageTaken(payload.player.uuid, payload.amount)
            val weapon = payload.weaponType ?: "none"
            val projectile = payload.projectileId?.let { " (projectile=$it)" } ?: ""
            logger.info("DamageReceived: ${payload.player.name} took ${payload.amount} from ${payload.entityIdentifier} via $weapon$projectile")
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

            // reporting milestone feat: ender dragon health percentages & end crystal destruction
            is ReportablePayload.PlayerDamagedEntity -> {
                if (payload.player isReally runner) {
                    when (payload.entityIdentifier) {
                        "minecraft:ender_dragon" -> {
                            if (payload.remainingHealth != null) {
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

                        "minecraft:end_crystal" -> {
                            if (!milestoneTracker.hasMilestone(SpeedrunMilestone.DestroyedFirstEndCrystal)) {
                                SpeedrunMilestone.DestroyedFirstEndCrystal
                            } else {
                                SpeedrunMilestone.DestroyedAllEndCrystals
                            }
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
                structureVisitTracker.clear()
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
                val weapon = payload.weaponType ?: "none"
                val projectile = payload.projectileId?.let { " (projectile=$it)" } ?: ""
                logger.info("Combat Stats: ${killer.name} scored a kill against ${payload.entityIdentifier} with $weapon$projectile")
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

    private fun logPlayerPayload(tag: String, player: MatchPlayer, detail: String = "") {
        if (player isReally currentRunner || currentHunters reallyContains player) {
            nameResolver[player.uuid] = player.name
            val detailStr = if (detail.isNotEmpty()) " $detail" else ""
            logger.info("$tag: ${player.name}$detailStr")
        }
    }

    private fun handlePlayerMoved(payload: ReportablePayload.PlayerMoved) {
        logPlayerPayload("Movement", payload.player, "pos=(${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }

    private fun handlePlayerRespawned(payload: ReportablePayload.PlayerRespawned) {
        logPlayerPayload("Respawn", payload.player, "pos=(${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }

    private fun handleTeleportSnapshot(payload: ReportablePayload.TeleportSnapshot) {
        logPlayerPayload("Teleport", payload.player, "${payload.cause} from=(${payload.from.x},${payload.from.y},${payload.from.z}) to=(${payload.to.x},${payload.to.y},${payload.to.z})")
    }

    private fun handlePlayerGameModeChanged(payload: ReportablePayload.PlayerGameModeChanged) {
        logPlayerPayload("Gamemode", payload.player, "${payload.from} -> ${payload.to}")
    }

    private fun handlePlayerConnected(payload: ReportablePayload.PlayerConnected) {
        logPlayerPayload("Connect", payload.player, "pos=(${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }

    private fun handlePlayerDisconnected(payload: ReportablePayload.PlayerDisconnected) {
        logPlayerPayload("Disconnect", payload.player, "pos=(${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }

    private fun handleInventoryKeyframe(payload: ReportablePayload.InventoryKeyframe) {
        logPlayerPayload("InventoryKeyframe", payload.player, "slots=${payload.contents.size} held=${payload.heldItemSlot}")
    }

    private fun handleInventoryDelta(payload: ReportablePayload.InventoryDelta) {
        logPlayerPayload("InventoryDelta", payload.player, "slot=${payload.slot}")
    }

    private fun handleMainHandChanged(payload: ReportablePayload.MainHandChanged) {
        logPlayerPayload("MainHand", payload.player, "slot=${payload.slot}")
    }

    private fun handlePlayerEnteredStructure(payload: ReportablePayload.PlayerEnteredStructure) {
        logPlayerPayload("StructureEnter", payload.player, payload.structureIdentifier)
        if (structureVisitTracker.recordEntry(payload.structureIdentifier, payload.pos)) {
            logger.info("GlobalStructureFirstVisit: ${payload.structureIdentifier} at (${payload.pos.x},${payload.pos.y},${payload.pos.z}) by ${payload.player.name}")
        }
    }

    private fun handlePlayerExitedStructure(payload: ReportablePayload.PlayerExitedStructure) {
        logPlayerPayload("StructureExit", payload.player, payload.structureIdentifier)
    }

    private fun handlePlayerEnteredFluid(payload: ReportablePayload.PlayerEnteredFluid) {
        logPlayerPayload("FluidEnter", payload.player, payload.state.toString())
    }

    private fun handlePlayerExitedFluid(payload: ReportablePayload.PlayerExitedFluid) {
        logPlayerPayload("FluidExit", payload.player, payload.previousState.toString())
    }

    private fun handlePlayerHealthRegained(payload: ReportablePayload.PlayerHealthRegained) {
        logPlayerPayload("HealthRegained", payload.player, "hp=${payload.newHealth}/${payload.maxHealth} cause=${payload.cause}")
    }

    private fun handleEntityHealthRegained(payload: ReportablePayload.EntityHealthRegained) {
        logger.info("EntityHealthRegained: ${payload.entityType} hp=${payload.newHealth}/${payload.maxHealth} cause=${payload.cause}")
    }

    private fun handlePlayerHungerChanged(payload: ReportablePayload.PlayerHungerChanged) {
        logPlayerPayload("Hunger", payload.player, "food=${payload.foodLevel} sat=${payload.saturation}")
    }

    private fun handlePlayerBreathChanged(payload: ReportablePayload.PlayerBreathChanged) {
        logPlayerPayload("Breath", payload.player, "air=${payload.remainingAir}/${payload.maxAir}")
    }

    private fun handlePlayerXpChanged(payload: ReportablePayload.PlayerXpChanged) {
        logPlayerPayload("XP", payload.player, "lvl=${payload.level} progress=${payload.progress}")
    }

    private fun handlePlayerDamagedByEnvironment(payload: ReportablePayload.PlayerDamagedByEnvironment) {
        if (payload.player isReally currentRunner || currentHunters reallyContains payload.player) {
            nameResolver[payload.player.uuid] = payload.player.name
            logger.info("EnvironmentDamage: ${payload.player.name} took ${payload.amount} damage from ${payload.cause} (remaining HP: ${payload.remainingHealth})")
        }
    }

    private fun handleProjectileLaunched(payload: ReportablePayload.ProjectileLaunched) {
        val shooterName = payload.shooter?.name ?: payload.shooterIdentifier ?: "unknown"
        logger.info("Projectile: $shooterName launched ${payload.projectileType} (id=${payload.projectileId}) from (${payload.launchPos.x},${payload.launchPos.y},${payload.launchPos.z})")
    }

    private fun handleProjectileMoved(payload: ReportablePayload.ProjectileMoved) {
        // path reconstruction for rendering — verbose, log sparingly
    }

    private fun handleProjectileHit(payload: ReportablePayload.ProjectileHit) {
        val shooterName = payload.shooter?.name ?: payload.shooterIdentifier ?: "unknown"
        val target = payload.hitPlayer?.name ?: payload.hitEntityIdentifier ?: "block"
        logger.info("Projectile: $shooterName ${payload.projectileType} hit $target at (${payload.hitPos.x},${payload.hitPos.y},${payload.hitPos.z}) for ${payload.damage} damage (id=${payload.projectileId})")
    }

    private fun handlePlayerAcquiredItem(payload: ReportablePayload.PlayerAcquiredItem) {
        logPlayerPayload("AcquireItem", payload.player, "${payload.item} via ${payload.method}")
    }

    private fun handlePlayerChangedDimension(payload: ReportablePayload.PlayerChangedDimension) {
        logPlayerPayload("Dimension", payload.player, "${payload.from} -> ${payload.to}")
    }

    private fun handlePlayerThrewEnderEye(payload: ReportablePayload.PlayerThrewEnderEye) {
        logPlayerPayload("EnderEye", payload.player)
    }

    private fun handlePlayerFilledBucket(payload: ReportablePayload.PlayerFilledBucket) {
        logPlayerPayload("BucketFill", payload.player, payload.fluid)
    }

    private fun handleDragonSnapshot(payload: ReportablePayload.DragonSnapshot) {
        logger.info("DragonSnapshot: pos=(${payload.pos.x},${payload.pos.y},${payload.pos.z}) hp=${payload.health}/${payload.maxHealth}")
    }

    private fun handleEndCrystalDiscovered(payload: ReportablePayload.EndCrystalDiscovered) {
        logger.info("EndCrystal: discovered at (${payload.pos.x},${payload.pos.y},${payload.pos.z}) id=${payload.crystalEntityId}")
    }

    private fun handleEndPortalCompleted(payload: ReportablePayload.EndPortalCompleted) {
        logger.info("EndPortal: completed at (${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }

    private fun handlePlayerJoined(payload: ReportablePayload.PlayerJoined) {
        logPlayerPayload("Join", payload.player)
    }

    private fun handlePlayerQuit(payload: ReportablePayload.PlayerQuit) {
        logPlayerPayload("Quit", payload.player, payload.reason)
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
            val data = (payload.snapshot as? PlayerSnapshot.Online)?.state as? OnlineState.Alive ?: return
            val s = data.currentLifeData
            logger.info("Snapshot: ${payload.player.name} pos=(${s.spatialData.position.x},${s.spatialData.position.y},${s.spatialData.position.z}) hp=${s.vitals.health} food=${s.vitals.foodLevel} effects=${s.metadata.activePotionEffects.size}")
        }
    }

    // -------------------------------------------------------------------------
    // CRAFTING LIFECYCLE
    // -------------------------------------------------------------------------

    private fun handlePlayerCraftedItem(payload: ReportablePayload.PlayerCraftedItem) {
        logPlayerPayload("Craft", payload.player, "${payload.itemType} x${payload.amount}")
    }

    private fun handlePlayerRepairedItem(payload: ReportablePayload.PlayerRepairedItem) {
        logPlayerPayload("Repair", payload.player, payload.itemType)
    }

    private fun handlePlayerItemBroke(payload: ReportablePayload.PlayerItemBroke) {
        logPlayerPayload("ItemBreak", payload.player, payload.itemType)
    }

    // -------------------------------------------------------------------------
    // FOOD TRACKING
    // -------------------------------------------------------------------------

    private fun handlePlayerConsumedItem(payload: ReportablePayload.PlayerConsumedItem) {
        logPlayerPayload("Consume", payload.player, "${payload.itemType} hunger=${payload.hungerRestored} saturation=${payload.saturationRestored}")
    }

    // -------------------------------------------------------------------------
    // MOB TRACKING
    // -------------------------------------------------------------------------

    private fun handleNearbyMobs(payload: ReportablePayload.NearbyMobs) {
        logPlayerPayload("NearbyMobs", payload.player, "${payload.mobs.size} mobs")
    }

    // -------------------------------------------------------------------------
    // LANDMARKS
    // -------------------------------------------------------------------------

    private fun handleNetherPortalCreated(payload: ReportablePayload.NetherPortalCreated) {
        logger.info("NetherPortal: created at (${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }

    private fun handleWorldSpawnRecorded(payload: ReportablePayload.WorldSpawnRecorded) {
        logger.info("WorldSpawn: recorded at (${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }

    // -------------------------------------------------------------------------
    // RESPAWN LOCATIONS
    // -------------------------------------------------------------------------

    private fun handlePlayerSetSpawn(payload: ReportablePayload.PlayerSetSpawn) {
        logPlayerPayload("SetSpawn", payload.player, "${payload.spawnType} at (${payload.pos.x},${payload.pos.y},${payload.pos.z})")
    }
}