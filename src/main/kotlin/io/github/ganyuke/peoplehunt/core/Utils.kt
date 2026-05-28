package io.github.ganyuke.peoplehunt.core

import kotlin.uuid.Uuid

object Utils {
    data class Pos4(val x: Int, val y: Int, val z: Int, val w: Uuid)

    fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
