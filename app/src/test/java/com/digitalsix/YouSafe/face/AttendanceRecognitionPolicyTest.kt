package com.digitalsix.YouSafe.face

import org.junit.Assert.*
import org.junit.Test

class AttendanceRecognitionPolicyTest {
    @Test fun neverStartsOverlappingUploads() {
        val p = AttendanceRecognitionPolicy()
        assertTrue(p.capture(0)); assertFalse(p.capture(100))
        p.pause()
        assertEquals(AttendanceRecognitionPolicy.State.SENDING, p.state)
    }
    @Test fun retriesBelowThresholdWithIntervalAndStopsAfterFourAttempts() {
        val p = AttendanceRecognitionPolicy()
        var now = 0L
        repeat(p.maxAttempts) { index ->
            assertTrue(p.capture(now))
            p.unmatched("BELOW_THRESHOLD", now)
            if (index < p.maxAttempts - 1) {
                assertFalse(p.capture(now)); assertFalse(p.resumeRetry(now + 1799))
                now += 1800
                assertTrue(p.resumeRetry(now))
            }
        }
        assertEquals(AttendanceRecognitionPolicy.State.PAUSED, p.state)
        assertFalse(p.capture(now + 100000))
    }
    @Test fun noEnrollmentAndNoEligibleEnrollmentsDoNotAutoRetry() {
        for (reason in listOf("NO_ENROLLMENT", "NO_ELIGIBLE_ENROLLMENTS", null)) {
            val p = AttendanceRecognitionPolicy(); p.capture(0); p.unmatched(reason, 0)
            assertEquals(AttendanceRecognitionPolicy.State.PAUSED, p.state)
            assertFalse(p.resumeRetry(100000))
        }
    }
    @Test fun matchRequiresFaceToLeaveBeforeNextCapture() {
        val p = AttendanceRecognitionPolicy(); p.capture(0); p.matched()
        assertFalse(p.capture(100000))
        p.faceLeft()
        assertEquals(0, p.attempts); assertTrue(p.capture(100000))
    }
    @Test fun uncertainResultStopsRequestsUntilExplicitAction() {
        val p = AttendanceRecognitionPolicy(); p.capture(0); p.uncertain()
        assertFalse(p.capture(100000)); assertFalse(p.resumeRetry(100000))
    }
    @Test fun restoringInterruptedRequestNeverReplaysIt() {
        val p = AttendanceRecognitionPolicy()
        p.restore("SENDING", 1, 0)
        assertEquals(AttendanceRecognitionPolicy.State.UNCERTAIN, p.state)
        assertFalse(p.capture(100000))
    }
    @Test fun pauseCancelsAutomaticRetryAndRestartBeginsNewCycle() {
        val p = AttendanceRecognitionPolicy(); p.capture(0); p.unmatched("BELOW_THRESHOLD", 0)
        p.pause(); assertFalse(p.resumeRetry(2000))
        p.restart(); assertEquals(0, p.attempts); assertTrue(p.capture(2000))
    }
}
