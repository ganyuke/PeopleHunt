package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object FlushBatchCodec {
    private val frameListSerializer = ListSerializer(EventFrame.serializer())

    fun encode(frames: List<EventFrame>, json: Json): ByteArray {
        val payload = json.encodeToString(frameListSerializer, frames).toByteArray(Charsets.UTF_8)
        return gzip(payload)
    }

    fun decode(bytes: ByteArray, json: Json): List<EventFrame> {
        val payload = gunzip(bytes)
        return json.decodeFromString(frameListSerializer, payload.toString(Charsets.UTF_8))
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { input -> input.copyTo(out) }
        return out.toByteArray()
    }
}
