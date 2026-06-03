package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportDocument
import io.github.ganyuke.peoplehunt.core.utils.InstantSerializer
import io.github.ganyuke.peoplehunt.core.utils.UuidSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Instant
import kotlin.uuid.Uuid

class JsonStorage(private val outputPath: Path) : ReportStorage {
    val json = Json {
        serializersModule = SerializersModule {
            contextual(Uuid::class, UuidSerializer)
            contextual(Instant::class, InstantSerializer)
        }
    }

    override suspend fun write(doc: ReportDocument) {
        val outputTarget = outputPath.resolve("${doc.matchId}.json")
        outputTarget.writeText(json.encodeToString(doc))
    }
}