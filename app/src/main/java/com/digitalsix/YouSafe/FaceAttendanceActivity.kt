package com.digitalsix.YouSafe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.digitalsix.YouSafe.face.*
import com.digitalsix.YouSafe.network.RetrofitInstance
import com.digitalsix.YouSafe.utils.NetworkMonitor
import com.digitalsix.YouSafe.utils.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FaceAttendanceActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SESSION = "attendance_session"
        const val EXTRA_POINT = "attendance_point"
    }
    private val sessionId get() = intent.getStringExtra(EXTRA_SESSION).orEmpty()
    private val point get() = intent.getIntExtra(EXTRA_POINT, 1)
    private val policy = AttendanceRecognitionPolicy()
    private lateinit var camera: AutoFaceCamera
    private lateinit var status: TextView
    private lateinit var lastMatch: TextView
    private lateinit var attempt: TextView
    private lateinit var action: Button
    private lateinit var close: Button
    private var busy = false
    private var cameraStarted = false
    private var blocked = false
    private var nextPersonAt = 0L
    private var delayed: Job? = null
    private val registeredIds = mutableSetOf<String>()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else cameraError(R.string.attendance_permission_needed)
    }
    private val settings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { requestCamera() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RetrofitInstance.initialize(applicationContext)
        if (!SessionManager(this).isLoggedIn() || !FaceRules.isPublicId(sessionId) || point !in 1..2) { finish(); return }
        setContentView(R.layout.activity_face_attendance)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        status = findViewById(R.id.attendanceLiveStatus)
        lastMatch = findViewById(R.id.attendanceLastMatch)
        attempt = findViewById(R.id.attendanceAttempt)
        action = findViewById(R.id.attendanceLiveAction)
        close = findViewById(R.id.attendanceLiveClose)
        val activity = SessionManager(this).getAtividadeEmProgresso(sessionId)
        findViewById<TextView>(R.id.attendanceContext).text = listOfNotNull(
            activity?.moduloNome, activity?.unidadeNome, getString(R.string.attendance_point, point)
        ).joinToString(" • ")
        val guide = FaceGuideView(this)
        findViewById<FrameLayout>(R.id.attendanceCameraContainer).addView(guide, FrameLayout.LayoutParams(-1, -1))
        camera = AutoFaceCamera(this, findViewById<PreviewView>(R.id.attendancePreview), guide,
            onHint = { hint ->
                if (!busy && policy.state == AttendanceRecognitionPolicy.State.SEEKING && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    val text = getString(hint)
                    if (status.text.toString() != text) status.text = text
                }
            }, onPhoto = ::recognize, onError = ::cameraError, continuous = true,
            onFaceLeft = { policy.faceLeft(); showStatus(R.string.attendance_live_intro); applyPolicy() })
        policy.restore(savedInstanceState?.getString("recognition_state"), savedInstanceState?.getInt("attempts") ?: 0,
            (savedInstanceState?.getLong("next_attempt") ?: 0).coerceAtMost(SystemClock.elapsedRealtime() + 1800))
        savedInstanceState?.getString("last_match")?.takeIf { it.isNotBlank() }?.let {
            lastMatch.text = it; lastMatch.visibility = View.VISIBLE
        }
        registeredIds.addAll(savedInstanceState?.getStringArrayList("registered_ids").orEmpty())
        action.setOnClickListener {
            if (busy || blocked) return@setOnClickListener
            when (policy.state) {
                AttendanceRecognitionPolicy.State.UNCERTAIN -> checkAttendance()
                AttendanceRecognitionPolicy.State.SEEKING, AttendanceRecognitionPolicy.State.RETRY_WAIT -> {
                    policy.pause(); showStatus(R.string.attendance_paused); applyPolicy()
                }
                AttendanceRecognitionPolicy.State.PAUSED -> {
                    policy.restart(); showStatus(R.string.attendance_live_intro)
                    if (!cameraStarted) requestCamera() else applyPolicy()
                }
                AttendanceRecognitionPolicy.State.WAIT_CLEAR -> if (!cameraStarted) requestCamera()
                else -> {}
            }
        }
        close.setOnClickListener { finishReading() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishReading() }
        })
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { applyPolicy() }
            override fun onPause(owner: LifecycleOwner) {
                delayed?.cancel(); delayed = null; camera.pauseCapture()
            }
        })
        if (!validSession()) { blockSession(); return }
        requestCamera()
        if (policy.state == AttendanceRecognitionPolicy.State.UNCERTAIN) {
            showStatus(R.string.attendance_uncertain); updateButtons()
        } else checkAttendance(initial = true)
    }

    private fun validSession(): Boolean = SessionManager(this).getAtividadeEmProgresso(sessionId)?.let {
        it.metodoPresenca == "facial" && it.apontamentoAtual == point && !it.offline
    } == true

    private fun requestCamera() {
        if (blocked) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else if (getPreferences(MODE_PRIVATE).getBoolean("camera_requested", false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this).setTitle(R.string.face_permission_title)
                .setMessage(R.string.attendance_permission_needed)
                .setNegativeButton(R.string.face_enroll_later, null)
                .setPositiveButton(R.string.face_open_settings) { _, _ ->
                    settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }.show()
            policy.pause(); showStatus(R.string.attendance_permission_needed); updateButtons()
        } else {
            getPreferences(MODE_PRIVATE).edit().putBoolean("camera_requested", true).apply()
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    private fun startCamera() {
        if (blocked) return
        cameraStarted = true
        camera.start()
        applyPolicy()
    }
    private fun cameraError(message: Int) {
        cameraStarted = false
        if (busy || blocked) return
        if (policy.state != AttendanceRecognitionPolicy.State.UNCERTAIN) policy.pause()
        status.setText(message); delayed?.cancel(); updateButtons()
    }
    private fun showStatus(message: Int) { status.setText(message) }
    private fun updateButtons() {
        action.isEnabled = !busy && !blocked && (policy.state != AttendanceRecognitionPolicy.State.WAIT_CLEAR || !cameraStarted)
        close.isEnabled = !busy
        action.setText(when (policy.state) {
            AttendanceRecognitionPolicy.State.UNCERTAIN -> R.string.attendance_check_list
            AttendanceRecognitionPolicy.State.PAUSED -> R.string.attendance_restart
            AttendanceRecognitionPolicy.State.WAIT_CLEAR -> if (cameraStarted) R.string.attendance_next_person else R.string.attendance_restart
            else -> R.string.attendance_pause
        })
        attempt.text = if (policy.attempts == 0) getString(R.string.attendance_no_shutter)
            else getString(R.string.attendance_attempt_count, policy.attempts, policy.maxAttempts)
        findViewById<View>(R.id.attendanceLiveProgress).visibility = if (busy) View.VISIBLE else View.GONE
    }
    private fun applyPolicy() {
        delayed?.cancel(); delayed = null
        updateButtons()
        if (!cameraStarted) return
        if (busy || blocked || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { camera.pauseCapture(); return }
        when (policy.state) {
            AttendanceRecognitionPolicy.State.SEEKING -> camera.resumeCapture()
            AttendanceRecognitionPolicy.State.RETRY_WAIT -> {
                camera.pauseCapture()
                delayed = lifecycleScope.launch {
                    delay((policy.nextAttemptAt - SystemClock.elapsedRealtime()).coerceIn(0, 1800))
                    delayed = null
                    if (policy.resumeRetry(SystemClock.elapsedRealtime())) { showStatus(R.string.attendance_live_intro); applyPolicy() }
                }
            }
            AttendanceRecognitionPolicy.State.WAIT_CLEAR -> {
                camera.pauseCapture()
                delayed = lifecycleScope.launch {
                    delay((nextPersonAt - SystemClock.elapsedRealtime()).coerceIn(0, 1600))
                    delayed = null
                    if (!busy && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        showStatus(R.string.attendance_leave_frame); camera.resumeAfterFaceLeaves()
                    }
                }
            }
            else -> camera.pauseCapture()
        }
    }

    private fun recognize(file: File) {
        if (busy || blocked || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { file.delete(); return }
        if (!validSession()) { file.delete(); blockSession(); return }
        if (!NetworkMonitor.isOnline(this)) {
            file.delete(); policy.pause(); showStatus(R.string.attendance_offline); applyPolicy(); return
        }
        if (!policy.capture(SystemClock.elapsedRealtime())) { file.delete(); return }
        val token = SessionManager(this).getTokenWithBearer()
        if (token == null) { file.delete(); policy.pause(); showStatus(R.string.attendance_login_needed); applyPolicy(); return }
        busy = true; showStatus(R.string.attendance_recognizing); updateButtons()
        lifecycleScope.launch {
            var sent = false
            try {
                val part = withContext(Dispatchers.IO) { FaceImage.prepare(file); FaceCamera.multipart(file) }
                sent = true
                when (val result = FaceAttendanceGateway(RetrofitInstance.faceApi).recognize(token, sessionId, part)) {
                    is AttendanceRecognition.Matched -> {
                        val already = !registeredIds.add(result.employee.publicId)
                        lastMatch.text = getString(if (already) R.string.attendance_already_registered else R.string.attendance_registered,
                            result.employee.nome.orEmpty())
                        lastMatch.visibility = View.VISIBLE
                        policy.matched(); nextPersonAt = SystemClock.elapsedRealtime() + 1600
                        showStatus(R.string.attendance_match_success)
                    }
                    is AttendanceRecognition.Unmatched -> {
                        policy.unmatched(result.reason, SystemClock.elapsedRealtime())
                        status.text = FaceRules.unrecognizedMessage(result.reason) + " " + getString(
                            if (policy.state == AttendanceRecognitionPolicy.State.RETRY_WAIT) R.string.attendance_adjust_retry else R.string.attendance_not_registered)
                    }
                    is AttendanceRecognition.Error -> {
                        if (result.status >= 500 || result.status == 408) {
                            policy.uncertain()
                            status.text = FaceRules.errorMessage(result.status) + " " + getString(R.string.attendance_uncertain)
                        } else { policy.pause(); status.text = FaceRules.errorMessage(result.status) }
                    }
                    AttendanceRecognition.Uncertain -> { policy.uncertain(); showStatus(R.string.attendance_uncertain) }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (sent) { policy.uncertain(); showStatus(R.string.attendance_uncertain) }
                else { policy.pause(); showStatus(R.string.face_capture_failed) }
            } finally { file.delete(); busy = false; applyPolicy() }
        }
    }

    private fun checkAttendance(initial: Boolean = false) {
        if (busy || blocked) return
        if (!validSession()) { blockSession(); return }
        val token = SessionManager(this).getTokenWithBearer() ?: return
        busy = true; camera.pauseCapture(); showStatus(R.string.attendance_checking); updateButtons()
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.faceApi.listarPresencasFaciais(token, sessionId)
                val body = response.body()
                check(response.isSuccessful && body != null && body.sessaoId.equals(sessionId, ignoreCase = true))
                registeredIds.clear()
                registeredIds.addAll(body.presencas.orEmpty().filter { it.numeroApontamento == point }.map { it.publicId })
                if (initial) {
                    when (policy.state) {
                        AttendanceRecognitionPolicy.State.PAUSED -> showStatus(R.string.attendance_paused)
                        AttendanceRecognitionPolicy.State.WAIT_CLEAR -> showStatus(R.string.attendance_leave_frame)
                        else -> showStatus(R.string.attendance_live_intro)
                    }
                } else {
                    policy.pause()
                    status.text = getString(R.string.attendance_checked, registeredIds.size)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { policy.uncertain(); showStatus(R.string.attendance_check_failed)
            } finally { busy = false; applyPolicy() }
        }
    }
    private fun blockSession() {
        blocked = true; policy.pause(); camera.stop(); cameraStarted = false
        showStatus(R.string.attendance_session_changed); updateButtons()
    }
    private fun finishReading() {
        if (busy) { Toast.makeText(this, R.string.attendance_wait_request, Toast.LENGTH_SHORT).show(); return }
        camera.stop()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SESSION, sessionId).putExtra(EXTRA_POINT, point))
        finish()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("recognition_state", policy.state.name)
        outState.putInt("attempts", policy.attempts); outState.putLong("next_attempt", policy.nextAttemptAt)
        if (::lastMatch.isInitialized) outState.putString("last_match", lastMatch.text.toString())
        outState.putStringArrayList("registered_ids", ArrayList(registeredIds))
    }
}
