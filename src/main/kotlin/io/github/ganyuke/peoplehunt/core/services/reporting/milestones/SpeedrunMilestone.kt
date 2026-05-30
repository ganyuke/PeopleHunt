package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

sealed interface SpeedrunMilestone {
    enum class AcquisitionMethod { CRAFTED, PICKED_UP, TRADED, KILLED }

    // Overworld
    // ItemAcquired: Iron Ingot
    // ItemAcquired: Bucket
    data object PickedUpWater : SpeedrunMilestone
    data object PickedUpLava : SpeedrunMilestone
    data object EnteredNether : SpeedrunMilestone

    // Nether
    data object EnteredFortress : SpeedrunMilestone
    data object EnteredBastion : SpeedrunMilestone
    // ItemAcquired: Ender Pearl
    // ItemAcquired: Blaze Rod
    data object LeftNether : SpeedrunMilestone

    // End preparation
    // ItemAcquired: Eye of Ender
    data object ThrewEyeOfEnder : SpeedrunMilestone
    data object EnteredStronghold : SpeedrunMilestone
    data object CompletedEndPortal : SpeedrunMilestone
    data object EnteredEnd : SpeedrunMilestone

    // Dragon fight
    data object DestroyedFirstEndCrystal : SpeedrunMilestone
    data object DestroyedAllEndCrystals : SpeedrunMilestone
    data object DragonAt50Percent : SpeedrunMilestone
    data object DragonAt25Percent : SpeedrunMilestone
    data object DragonAt10Percent : SpeedrunMilestone
    data object DragonAt5Percent : SpeedrunMilestone

    data class ItemAcquired(
        val item: Item,
        val method: AcquisitionMethod,
    ) : SpeedrunMilestone {
        enum class Item { IRON_INGOT, BUCKET, ENDER_PEARL, BLAZE_ROD, EYE_OF_ENDER }
    }

    sealed interface DragonSlain : SpeedrunMilestone {
        data object ByRunner : DragonSlain
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