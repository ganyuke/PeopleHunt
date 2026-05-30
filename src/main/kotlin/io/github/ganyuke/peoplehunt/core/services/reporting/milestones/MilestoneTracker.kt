package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import kotlin.time.Clock
import kotlin.time.Instant

class MilestoneTracker {
    data class MilestoneRecord(val milestone: SpeedrunMilestone, val recordedAt: Instant)

    private val milestones = LinkedHashMap<SpeedrunMilestone.DedupKey, MilestoneRecord>()

    val milestoneRecords: List<MilestoneRecord> get() = milestones.values.toList()

    fun trackMilestone(milestone: SpeedrunMilestone): Boolean {
        val key = milestone.dedupKey // since milestones can have data, need to have a unique surrogate key
        if (milestones.containsKey(key)) {
            return false
        }
        milestones[key] = MilestoneRecord(milestone, Clock.System.now())
        return true
    }

    fun clear() {
        milestones.clear()
    }
}