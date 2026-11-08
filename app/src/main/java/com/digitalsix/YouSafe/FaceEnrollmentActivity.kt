package com.digitalsix.YouSafe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.digitalsix.YouSafe.face.*
import com.digitalsix.YouSafe.utils.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.digitalsix.YouSafe.registration.RegistrationPhotos

class FaceEnrollmentActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PHOTO_NAME = "registration_photo_name"
        const val EXTRA_CONSENT_AT = "registration_consent_at"
    }
    private lateinit var camera: AutoFaceCamera
    private lateinit var status: TextView
    private lateinit var action: Button
    private lateinit var close: Button
    private lateinit var cameraContainer: FrameLayout
    private var consentAt: String? = null
    private var busy = false
    private var completed = false
    private var consentDialog: AlertDialog? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showFailure(R.string.face_permission_needed)
    }
    private val settings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requestCamera()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!SessionManager(this).isLoggedIn()) { finish(); return }
        setContentView(R.layout.activity_face_enrollment)
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        status = findViewById(R.id.faceEnrollmentStatus)
        action = findViewById(R.id.faceEnrollmentAction)
        close = findViewById(R.id.faceEnrollmentClose)
        cameraContainer = findViewById(R.id.faceCameraContainer)
        findViewById<TextView>(R.id.faceEmployeeName).text = intent.getStringExtra("funcionario_nome").orEmpty()
        val guide = FaceGuideView(this)
        cameraContainer.addView(guide, FrameLayout.LayoutParams(-1, -1))
        camera = AutoFaceCamera(this, findViewById<PreviewView>(R.id.facePreview), guide,
            { hint -> status.setText(hint) }, ::salvarFoto, ::showFailure)
        consentAt = state?.getString("consent_at")
        close.setOnClickListener { if (!busy) finish() }
        action.setOnClickListener {
            if (completed) finish()
            else if (consentAt == null) consent()
            else requestCamera()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (busy) Toast.makeText(this@FaceEnrollmentActivity, R.string.registration_saving_photo, Toast.LENGTH_SHORT).show()
                else finish()
            }
        })
        if (consentAt != null) {
            // A capture interrupted by recreation must be taken again.
            showFailure(R.string.face_resume_capture)
        } else if (state == null) consent()
    }

    private fun consent() {
        if (consentDialog?.isShowing == true) return
        consentDialog = AlertDialog.Builder(this).setTitle(R.string.face_consent_title)
            .setMessage(R.string.face_consent_text)
            .setNegativeButton(R.string.face_decline) { _, _ -> finish() }
            .setPositiveButton(R.string.face_accept) { _, _ ->
                consentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                requestCamera()
            }.show()
    }

    private fun requestCamera() {
        if (busy || completed || consentAt == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else if (getPreferences(MODE_PRIVATE).getBoolean("camera_requested", false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this).setTitle(R.string.face_permission_title)
                .setMessage(R.string.face_permission_needed)
                .setNegativeButton(R.string.face_enroll_later, null)
                .setPositiveButton(R.string.face_open_settings) { _, _ ->
                    settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }.show()
        } else {
            getPreferences(MODE_PRIVATE).edit().putBoolean("camera_requested", true).apply()
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        if (consentAt == null || completed || busy) return
        cameraContainer.visibility = View.VISIBLE
        action.visibility = View.GONE
        camera.start()
    }

    private fun salvarFoto(file: File) {
        val accepted = consentAt
        if (accepted == null || busy || completed) { file.delete(); return }
        setBusy(true)
        lifecycleScope.launch {
            var saved: File? = null
            var returned = false
            try {
                withContext(Dispatchers.IO) {
                    FaceImage.prepare(file)
                    FaceCamera.multipart(file) // Check format and size before returning to the form.
                    saved = File(RegistrationPhotos.directory(this@FaceEnrollmentActivity), "photo_${UUID.randomUUID()}.jpg")
                    file.copyTo(saved!!)
                }
                setResult(RESULT_OK, Intent()
                    .putExtra(EXTRA_PHOTO_NAME, saved!!.name)
                    .putExtra(EXTRA_CONSENT_AT, accepted))
                completed = true
                returned = true
                finish()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { showFailure(R.string.face_capture_failed)
            } finally {
                file.delete()
                if (!returned) saved?.delete()
                setBusy(false)
            }
        }
    }

    private fun showFailure(message: Int) {
        status.setText(message)
        action.visibility = View.VISIBLE
        action.setText(R.string.face_try_again)
    }
    private fun setBusy(value: Boolean) {
        busy = value
        findViewById<View>(R.id.faceEnrollmentProgress).visibility = if (value) View.VISIBLE else View.GONE
        action.isEnabled = !value; close.isEnabled = !value
        if (value) { status.setText(R.string.registration_saving_photo); action.visibility = View.GONE }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("consent_at", consentAt)
    }
    override fun onDestroy() { consentDialog?.dismiss(); super.onDestroy() }
}
