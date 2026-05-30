package io.github.ganyuke.peoplehunt.core.testutil

import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.ReportingEngine
import io.github.ganyuke.peoplehunt.core.utils.PhConfig

data class MatchEngineFixture(
    val engine: MatchEngine,
    val scheduler: FakeScheduler,
    val bus: MatchEventBus,
)

fun matchEngineFixture(
    scheduler: FakeScheduler = FakeScheduler(),
    bus: MatchEventBus = MatchEventBus(),
    config: PhConfig = testPhConfig(),
) = MatchEngineFixture(MatchEngine(scheduler, bus, config), scheduler, bus)

data class ReportingEngineFixture(
    val engine: ReportingEngine,
    val scheduler: FakeScheduler,
    val bus: MatchEventBus,
    val logger: FakeLogger,
)

fun reportingEngineFixture(
    scheduler: FakeScheduler = FakeScheduler(),
    bus: MatchEventBus = MatchEventBus(),
    logger: FakeLogger = FakeLogger()
) = ReportingEngineFixture(ReportingEngine(bus, scheduler, logger), scheduler, bus, logger)
