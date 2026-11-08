package com.digitalsix.YouSafe.face

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCaptureGateTest {
    private val face = AutoCaptureGate.Face(0f, 0f, 0.9f, 1.3f, 0f, 0f, 0f, 1)
    private val gate = AutoCaptureGate()

    @Test fun centeredFaceCapturesAfterStableIntervalAndOnlyOnce() {
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face), 0))
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face), 500))
        assertEquals(AutoCaptureGate.Hint.CAPTURE, gate.update(listOf(face), 900))
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face), 1200))
    }
    @Test fun rejectsMissingMultipleSmallLargeOffCenterAndTurnedFaces() {
        assertEquals(AutoCaptureGate.Hint.POSITION, gate.update(emptyList(), 0))
        assertEquals(AutoCaptureGate.Hint.ONE_PERSON, gate.update(listOf(face, face), 100))
        assertEquals(AutoCaptureGate.Hint.CLOSER, gate.update(listOf(face.copy(height = 0.5f)), 200))
        assertEquals(AutoCaptureGate.Hint.FARTHER, gate.update(listOf(face.copy(height = 1.9f)), 300))
        assertEquals(AutoCaptureGate.Hint.CENTER, gate.update(listOf(face.copy(x = 0.4f)), 400))
        assertEquals(AutoCaptureGate.Hint.FACE_FORWARD, gate.update(listOf(face.copy(yaw = 25f)), 500))
    }
    @Test fun losingFaceResetsTheWait() {
        gate.update(listOf(face), 0)
        gate.update(listOf(face), 500)
        gate.update(emptyList(), 600)
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face), 900))
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face), 1400))
        assertEquals(AutoCaptureGate.Hint.CAPTURE, gate.update(listOf(face), 1800))
    }
    @Test fun identityChangeMotionAndStaleFramesResetTheWait() {
        gate.update(listOf(face), 0)
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face.copy(trackingId = 2)), 900))
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face.copy(trackingId = 2, x = 0.12f)), 1400))
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face.copy(trackingId = 2, x = 0.12f)), 3000))
    }
    @Test fun pauseOrRetryStartsAnotherFullInterval() {
        gate.update(listOf(face), 0)
        gate.update(listOf(face), 900)
        gate.reset()
        assertEquals(AutoCaptureGate.Hint.HOLD, gate.update(listOf(face), 1000))
        assertEquals(AutoCaptureGate.Hint.CAPTURE, gate.update(listOf(face), 1900))
    }
}
