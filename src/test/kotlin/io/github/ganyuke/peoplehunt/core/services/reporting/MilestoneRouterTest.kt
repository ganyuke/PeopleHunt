package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.core.testutil.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MilestoneRouterTest {
  private fun fixture(): Triple<MilestoneRouter, ReportableEventBus, FakeLogger> {
    val bus = ReportableEventBus()
    val logger = FakeLogger()
    return Triple(MilestoneRouter(bus, logger), bus, logger)
  }

  private fun start(router: MilestoneRouter, runner: io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer = player("runner")) {
    router.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
  }

  private fun postedMilestones(bus: ReportableEventBus): List<SpeedrunMilestone> {
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { event ->
      val payload = event.payload
      if (payload is ReportablePayload.MilestoneUnlocked) milestones += payload.milestone
    }
    return milestones
  }

  @Test
  fun matchEnd_clearsRunner() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val posted = mutableListOf<ReportableEvent>()
    bus.register { posted += it }
    router.onMatchEvent(MatchEvent.MatchEnd(
      io.github.ganyuke.peoplehunt.core.services.core.MatchEngine.MatchState.Finished(
        runner, emptySet(), kotlin.time.Clock.System.now(), kotlin.time.Clock.System.now(),
        io.github.ganyuke.peoplehunt.core.services.core.MatchEngine.MatchOutcome.INCONCLUSIVE,
      ),
    ))
    router.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))
    assertTrue(posted.isEmpty())
  }

  @Test
  fun enteredFortress_unlocksMilestone() {
    val (router, bus, logger) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }
    router.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))
    assertEquals(1, milestones.size)
    assertEquals(SpeedrunMilestone.EnteredFortress, milestones.single())
    assertTrue(logger.infoMessages.any { it.contains("EnteredFortress") })
  }

  @Test
  fun filledBucket_waterAndLava() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }
    router.onReportableEvent(playerFilledBucket(runner, "minecraft:water"))
    router.onReportableEvent(playerFilledBucket(runner, "minecraft:lava"))
    assertEquals(listOf(SpeedrunMilestone.PickedUpWater, SpeedrunMilestone.PickedUpLava), milestones)
  }

  @Test
  fun changedDimension_netherEndAndLeftNether() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(playerChangedDimension(runner, "minecraft:overworld", "minecraft:the_nether"))
    router.onReportableEvent(
      playerAcquiredItem(runner, SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD, SpeedrunMilestone.AcquisitionMethod.PICKED_UP),
    )
    router.onReportableEvent(playerChangedDimension(runner, "minecraft:the_nether", "minecraft:overworld"))
    router.onReportableEvent(playerChangedDimension(runner, "minecraft:overworld", "minecraft:the_end"))

    assertTrue(milestones.contains(SpeedrunMilestone.EnteredNether))
    assertTrue(milestones.contains(SpeedrunMilestone.LeftNether))
    assertTrue(milestones.contains(SpeedrunMilestone.EnteredEnd))
  }

  @Test
  fun dragonDamage_tracksHealthThresholds() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, 100.0))
    router.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, 40.0))
    router.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, 8.0))

    assertTrue(milestones.contains(SpeedrunMilestone.DragonAt50Percent))
    assertTrue(milestones.contains(SpeedrunMilestone.DragonAt25Percent))
    assertTrue(milestones.contains(SpeedrunMilestone.DragonAt5Percent))
  }

  @Test
  fun endCrystal_tracksFirstAndAllDestroyed() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(playerDamagedEntity(runner, "minecraft:end_crystal", 1.0))
    router.onReportableEvent(playerDamagedEntity(runner, "minecraft:end_crystal", 1.0))

    assertEquals(
      listOf(SpeedrunMilestone.DestroyedFirstEndCrystal, SpeedrunMilestone.DestroyedAllEndCrystals),
      milestones,
    )
  }

  @Test
  fun dragonSlain_byRunnerAndOther() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    val hunter = player("hunter")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(entityDied("minecraft:ender_dragon", cause = KillCause.KilledByPlayer(runner)))
    router.onMatchEvent(MatchEvent.MatchStart(hunter, emptySet()))
    router.onReportableEvent(entityDied("minecraft:ender_dragon", cause = KillCause.Environmental))

    assertTrue(milestones.any { it is SpeedrunMilestone.DragonSlain.ByRunner })
    assertTrue(milestones.any { it is SpeedrunMilestone.DragonSlain.ByOther })
  }

  @Test
  fun ignoresEventsWithoutActiveRunner() {
    val (router, bus, _) = fixture()
    val posted = mutableListOf<ReportableEvent>()
    bus.register { posted += it }
    router.onReportableEvent(playerThrewEnderEye(player("runner")))
    assertTrue(posted.isEmpty())
  }

  @Test
  fun enteredBastionAndStronghold_unlockMilestones() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(playerEnteredStructure(runner, "minecraft:bastion_remnant"))
    router.onReportableEvent(playerEnteredStructure(runner, "minecraft:stronghold"))

    assertEquals(
      listOf(SpeedrunMilestone.EnteredBastion, SpeedrunMilestone.EnteredStronghold),
      milestones,
    )
  }

  @Test
  fun acquiredItem_threwEye_andEndPortal() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(
      playerAcquiredItem(runner, SpeedrunMilestone.ItemAcquired.Item.ENDER_PEARL, SpeedrunMilestone.AcquisitionMethod.PICKED_UP),
    )
    router.onReportableEvent(playerThrewEnderEye(runner))
    router.onReportableEvent(endPortalCompleted(pos()))

    assertTrue(milestones.contains(SpeedrunMilestone.ItemAcquired(SpeedrunMilestone.ItemAcquired.Item.ENDER_PEARL, SpeedrunMilestone.AcquisitionMethod.PICKED_UP)))
    assertTrue(milestones.contains(SpeedrunMilestone.ThrewEyeOfEnder))
    assertTrue(milestones.contains(SpeedrunMilestone.CompletedEndPortal))
  }

  @Test
  fun duplicateMilestone_doesNotPostTwice() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val posted = mutableListOf<ReportableEvent>()
    bus.register { posted += it }

    router.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))
    router.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))

    assertEquals(1, posted.size)
  }

  @Test
  fun ignoresNonRunnerPlayerEvents() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    val hunter = player("hunter")
    start(router, runner)
    val posted = mutableListOf<ReportableEvent>()
    bus.register { posted += it }

    router.onReportableEvent(playerEnteredStructure(hunter, "minecraft:fortress"))
    assertTrue(posted.isEmpty())
  }

  @Test
  fun dragonSlain_byEntity() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(entityDied("minecraft:ender_dragon", cause = KillCause.KilledByEntity("minecraft:arrow")))

    assertEquals(SpeedrunMilestone.DragonSlain.ByOther("minecraft:arrow"), milestones.single())
  }

  @Test
  fun dragonDamage_withoutRemainingHealth_isIgnored() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val posted = mutableListOf<ReportableEvent>()
    bus.register { posted += it }

    router.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, null))
    assertTrue(posted.isEmpty())
  }

  @Test
  fun leftNether_withoutBlazeRod_doesNotUnlock() {
    val (router, bus, _) = fixture()
    val runner = player("runner")
    start(router, runner)
    val milestones = mutableListOf<SpeedrunMilestone>()
    bus.register { if (it.payload is ReportablePayload.MilestoneUnlocked) milestones += (it.payload as ReportablePayload.MilestoneUnlocked).milestone }

    router.onReportableEvent(playerChangedDimension(runner, "minecraft:the_nether", "minecraft:overworld"))
    assertTrue(milestones.none { it == SpeedrunMilestone.LeftNether })
  }
}
