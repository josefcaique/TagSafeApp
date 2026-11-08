package com.digitalsix.YouSafe.face

import kotlin.math.abs
import kotlin.math.hypot

/** Enquadramento estável por 900 ms. Não verifica identidade nem prova de vida. */
class AutoCaptureGate {
    data class Face(val x: Float, val y: Float, val width: Float, val height: Float,
                    val yaw: Float, val pitch: Float, val roll: Float, val trackingId: Int?)
    enum class Hint { POSITION, ONE_PERSON, CLOSER, FARTHER, CENTER, FACE_FORWARD, HOLD, CAPTURE }
    private var since: Long? = null
    private var lastAt: Long? = null
    private var anchor: Face? = null
    private var captured = false

    fun reset() { since = null; lastAt = null; anchor = null; captured = false }

    // Face coordinates are normalized by the guide circle's radius, origin at its center.
    fun update(faces: List<Face>, now: Long): Hint {
        if (captured) return Hint.HOLD
        val hint = when {
            faces.isEmpty() -> Hint.POSITION
            faces.size > 1 -> Hint.ONE_PERSON
            else -> {
                val face = faces.single()
                when {
                    abs(face.yaw) > 15 || abs(face.pitch) > 15 || abs(face.roll) > 15 -> Hint.FACE_FORWARD
                    face.height < 0.95f || face.width < 0.55f -> Hint.CLOSER
                    face.height > 1.7f || face.width > 1.55f -> Hint.FARTHER
                    hypot(face.x, face.y) > 0.18f -> Hint.CENTER
                    else -> Hint.HOLD
                }
            }
        }
        if (hint != Hint.HOLD) { since = null; lastAt = null; anchor = null; return hint }
        val face = faces.single()
        val previous = anchor
        val gap = lastAt?.let { now - it } ?: Long.MAX_VALUE
        if (previous == null || gap !in 0..1000 || previous.trackingId != face.trackingId ||
            hypot(previous.x - face.x, previous.y - face.y) > 0.08f ||
            abs(previous.height - face.height) > 0.12f) {
            since = now; anchor = face
        }
        lastAt = now
        if (now - (since ?: now) >= 900) { captured = true; return Hint.CAPTURE }
        return Hint.HOLD
    }
}
