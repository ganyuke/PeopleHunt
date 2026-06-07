package io.github.ganyuke.peoplehunt.core.services.core.models

enum class MatchFailureReason {
    ALREADY_PRIMED,
    ALREADY_STARTED,
    NOT_RUNNING,
    NO_RUNNER_SPECIFIED,
    PLAYER_ALREADY_RUNNER,
    PLAYER_ALREADY_HUNTER,
    PLAYER_NOT_IN_GROUP,
}