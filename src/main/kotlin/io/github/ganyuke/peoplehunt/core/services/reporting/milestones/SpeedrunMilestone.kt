package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SpeedrunMilestone {
    @Serializable
    enum class AcquisitionMethod { CRAFTED, PICKED_UP, TRADED, KILLED }

    // Overworld
    @Serializable @SerialName("PickedUpWater")
    data object PickedUpWater : SpeedrunMilestone

    @Serializable @SerialName("PickedUpLava")
    data object PickedUpLava : SpeedrunMilestone

    @Serializable @SerialName("EnteredNether")
    data object EnteredNether : SpeedrunMilestone

    // Nether
    @Serializable @SerialName("EnteredFortress")
    data object EnteredFortress : SpeedrunMilestone

    @Serializable @SerialName("EnteredBastion")
    data object EnteredBastion : SpeedrunMilestone

    @Serializable @SerialName("LeftNether")
    data object LeftNether : SpeedrunMilestone

    // End preparation
    @Serializable @SerialName("ThrewEyeOfEnder")
    data object ThrewEyeOfEnder : SpeedrunMilestone

    @Serializable @SerialName("EnteredStronghold")
    data object EnteredStronghold : SpeedrunMilestone

    @Serializable @SerialName("CompletedEndPortal")
    data object CompletedEndPortal : SpeedrunMilestone

    @Serializable @SerialName("EnteredEnd")
    data object EnteredEnd : SpeedrunMilestone

    // Dragon fight
    @Serializable @SerialName("DestroyedFirstEndCrystal")
    data object DestroyedFirstEndCrystal : SpeedrunMilestone

    @Serializable @SerialName("DestroyedAllEndCrystals")
    data object DestroyedAllEndCrystals : SpeedrunMilestone

    @Serializable @SerialName("DragonAt50Percent")
    data object DragonAt50Percent : SpeedrunMilestone

    @Serializable @SerialName("DragonAt25Percent")
    data object DragonAt25Percent : SpeedrunMilestone

    @Serializable @SerialName("DragonAt10Percent")
    data object DragonAt10Percent : SpeedrunMilestone

    @Serializable @SerialName("DragonAt5Percent")
    data object DragonAt5Percent : SpeedrunMilestone

    @Serializable @SerialName("ItemAcquired")
    data class ItemAcquired(
        val item: Item,
        val method: AcquisitionMethod,
    ) : SpeedrunMilestone {
        @Serializable
        enum class Item { IRON_INGOT, BUCKET, ENDER_PEARL, BLAZE_ROD, EYE_OF_ENDER }
    }

    @Serializable
    sealed interface DragonSlain : SpeedrunMilestone {
        @Serializable @SerialName("ByRunner")
        data object ByRunner : DragonSlain

        @Serializable @SerialName("ByOther")
        data class ByOther(val entityIdentifier: String) : DragonSlain
    }

    sealed interface DedupKey {
        data class Item(val item: ItemAcquired.Item) : DedupKey
        data object DragonSlain : DedupKey
        data class Unique(val milestone: SpeedrunMilestone) : DedupKey
    }

    val dedupKey: DedupKey
        get() = when (this) {
            is ItemAcquired -> DedupKey.Item(item)
            is DragonSlain -> DedupKey.DragonSlain
            else -> DedupKey.Unique(this)
        }
}
