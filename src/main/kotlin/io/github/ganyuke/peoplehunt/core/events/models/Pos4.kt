package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Pos4(val x: Int, val y: Int, val z: Int, @Contextual val w: Uuid)