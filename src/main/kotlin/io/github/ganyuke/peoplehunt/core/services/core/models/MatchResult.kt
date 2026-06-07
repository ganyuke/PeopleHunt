package io.github.ganyuke.peoplehunt.core.services.core.models

sealed interface MatchResult {
    data class Ok(val message: String? = null) : MatchResult
    data class Err(val reason: MatchFailureReason) : MatchResult
}