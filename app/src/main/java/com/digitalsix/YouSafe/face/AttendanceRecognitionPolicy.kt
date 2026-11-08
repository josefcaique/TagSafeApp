package com.digitalsix.YouSafe.face

/** One upload at a time; bounded retries apply only to a valid but unrecognized face. */
class AttendanceRecognitionPolicy {
    enum class State { SEEKING, SENDING, RETRY_WAIT, WAIT_CLEAR, PAUSED, UNCERTAIN }
    var state = State.SEEKING
        private set
    var attempts = 0
        private set
    var nextAttemptAt = 0L
        private set
    val maxAttempts = 4

    fun capture(now: Long): Boolean {
        if (state != State.SEEKING || now < nextAttemptAt || attempts >= maxAttempts) return false
        attempts++; state = State.SENDING; return true
    }
    fun unmatched(reason: String?, now: Long) {
        if (state != State.SENDING) return
        if (reason == "BELOW_THRESHOLD" && attempts < maxAttempts) {
            state = State.RETRY_WAIT; nextAttemptAt = now + 1800
        } else state = State.PAUSED
    }
    fun resumeRetry(now: Long): Boolean {
        if (state != State.RETRY_WAIT || now < nextAttemptAt) return false
        state = State.SEEKING; return true
    }
    fun matched() { state = State.WAIT_CLEAR }
    fun faceLeft() { if (state == State.WAIT_CLEAR) restart() }
    fun pause() { if (state != State.SENDING) state = State.PAUSED }
    fun uncertain() { state = State.UNCERTAIN }
    fun restart() { state = State.SEEKING; attempts = 0; nextAttemptAt = 0 }
    fun restore(saved: String?, count: Int, next: Long) {
        state = runCatching { State.valueOf(saved.orEmpty()) }.getOrDefault(State.SEEKING)
        if (state == State.SENDING) state = State.UNCERTAIN
        attempts = count.coerceIn(0, maxAttempts); nextAttemptAt = next
    }
}
