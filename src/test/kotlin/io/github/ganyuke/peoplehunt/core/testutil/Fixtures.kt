package io.github.ganyuke.peoplehunt.core.testutil

import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.ReportingEngine

data class MatchEngineFixture(
    val engine: MatchEngine,
    val scheduler: FakeScheduler,
    val bus: MatchEventBus,
)

fun matchEngineFixture(
    scheduler: FakeScheduler = FakeScheduler(),
    bus: MatchEventBus = MatchEventBus(),
    config: io.github.ganyuke.peoplehunt.core.Utils.PhConfig = testPhConfig(),
) = MatchEngineFixture(MatchEngine(scheduler, bus, config), scheduler, bus)

data class ReportingEngineFixture(
    val engine: ReportingEngine,
    val scheduler: FakeScheduler,
    val bus: MatchEventBus,
    val logger: FakeLogger,
    val structures: FakeStructureLocator,
)

fun reportingEngineFixture(
    scheduler: FakeScheduler = FakeScheduler(),
    bus: MatchEventBus = MatchEventBus(),
    logger: FakeLogger = FakeLogger(),
    structures: FakeStructureLocator = FakeStructureLocator(),
) = ReportingEngineFixture(ReportingEngine(bus, scheduler, structures, logger), scheduler, bus, logger, structures)
