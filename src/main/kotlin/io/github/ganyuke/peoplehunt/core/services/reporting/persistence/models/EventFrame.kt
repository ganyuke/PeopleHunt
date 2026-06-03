package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class EventFrame(
    val tick: Int,
    @Contextual val occurredAt: Instant,
    val payload: ReportablePayload
)