package io.github.ganyuke.peoplehunt.core.events.models

import kotlin.uuid.Uuid

data class MatchPlayer(val uuid: Uuid, val name: String)