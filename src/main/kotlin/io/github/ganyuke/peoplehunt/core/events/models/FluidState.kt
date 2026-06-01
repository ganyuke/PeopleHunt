package io.github.ganyuke.peoplehunt.core.events.models

import kotlin.time.Instant

    sealed class FluidState {
        object Dry : FluidState()
        data class WadingInWater(val since: Instant) : FluidState() // in water, head above
        data class SubmergedInWater(val since: Instant) : FluidState() // breath depletes when head below water
        data class InLava(val since: Instant) : FluidState() // lava doesn't really have swimming
        data class SuffocatingInBlock(val since: Instant) : FluidState()
    }