package io.github.ganyuke.peoplehunt.core.testutil

import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.ReportingEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer
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
    val logger: FakeLogger,
)

fun reportingEngineFixture(
    logger: FakeLogger = FakeLogger()
) = ReportingEngineFixture(ReportingEngine(logger), logger)

data class ReportStenographerFixture(
    val stenographer: ReportStenographer,
    val storage: FakeReportStorage,
    val scheduler: FakeScheduler,
    val logger: FakeLogger,
    val bus: MatchEventBus,
)

fun reportStenographerFixture(
    scheduler: FakeScheduler = FakeScheduler(),
    bus: MatchEventBus = MatchEventBus(),
    logger: FakeLogger = FakeLogger(),
    storage: FakeReportStorage = FakeReportStorage(),
    config: PhConfig = testPhConfig(reportFlushInterval = kotlin.time.Duration.ZERO),
) = ReportStenographerFixture(
    ReportStenographer(bus, scheduler, logger, storage, config),
    storage,
    scheduler,
    logger,
    bus,
)
