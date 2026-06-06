package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import kotlin.uuid.Uuid

fun Uuid.toCompactString(): String = toString().replace("-", "")

fun Uuid.Companion.fromCompactString(value: String): Uuid = parse(value)
