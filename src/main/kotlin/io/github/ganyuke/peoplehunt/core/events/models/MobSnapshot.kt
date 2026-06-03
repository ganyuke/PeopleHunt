package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class MobSnapshot(
    @Contextual val entityUuid: Uuid,
    val pos: Pos4,
    val entityType: String,
    val health: Double,
    val distance: Double,
)
