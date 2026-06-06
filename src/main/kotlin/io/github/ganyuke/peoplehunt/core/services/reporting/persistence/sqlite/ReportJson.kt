package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.utils.InstantSerializer
import io.github.ganyuke.peoplehunt.core.utils.UuidSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant
import kotlin.uuid.Uuid

object ReportJson {
    val instance: Json = Json {
        serializersModule = SerializersModule {
            contextual(Uuid::class, UuidSerializer)
            contextual(Instant::class, InstantSerializer)
        }
    }
}
