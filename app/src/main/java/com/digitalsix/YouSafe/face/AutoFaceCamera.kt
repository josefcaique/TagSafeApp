package com.digitalsix.YouSafe.face

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.digitalsix.YouSafe.R
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File

/** Face detection only guides framing. Recognition stays on the API; there is no liveness check. */
class AutoFaceCamera(
    private val activity: AppCompatActivity,
    private val preview: PreviewView,
    private val guide: FaceGuideView,
    private val onHint: (Int) -> Unit,
    private val onPhoto: (File) -> Unit,
    private val onError: (Int) -> Unit,
    private val continuous: Boolean = false,
    private val onFaceLeft: () -> Unit = {}
) : DefaultLifecycleObserver {
    private var controller: LifecycleCameraController? = null
    private var detector: FaceDetector? = null
    private val gate = AutoCaptureGate()
    private var generation = 0
    private var takingPhoto = false
    private var paused = false
    private var waitForClear = false
    private var clearSince: Long? = null
    private val executor = ContextCompat.getMainExecutor(activity)

    init { activity.lifecycle.addObserver(this) }

    @SuppressLint("MissingPermission") // Checked here and requested by the enrollment activity.
    fun start() {
        stop()
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onError(R.string.face_permission_needed); return
        }
        val run = generation
        onHint(R.string.face_camera_starting)
        try {
            val faceDetector = FaceDetection.getClient(FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking().build())
            detector = faceDetector
            val camera = LifecycleCameraController(activity)
            controller = camera
            preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            preview.controller = camera
            camera.setImageAnalysisAnalyzer(executor, MlKitAnalyzer(
                listOf(faceDetector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor
            ) { result ->
                if (generation != run || guide.radius <= 0 ||
                    !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@MlKitAnalyzer
                if (result.getThrowable(faceDetector) != null) {
                    stop(); onError(R.string.face_detection_failed); return@MlKitAnalyzer
                }
                val faces = result.getValue(faceDetector) ?: return@MlKitAnalyzer
                if (waitForClear) {
                    val now = SystemClock.elapsedRealtime()
                    if (faces.isEmpty()) {
                        if (clearSince == null) clearSince = now
                        if (now - clearSince!! >= 800) {
                            resumeCapture(); onFaceLeft()
                        }
                    } else clearSince = null
                    return@MlKitAnalyzer
                }
                if (paused || takingPhoto) return@MlKitAnalyzer
                val radius = guide.radius
                val normalized = faces.map { face ->
                    val box = face.boundingBox
                    AutoCaptureGate.Face(
                        (box.exactCenterX() - guide.width / 2f) / radius,
                        (box.exactCenterY() - guide.height / 2f) / radius,
                        box.width() / radius, box.height() / radius,
                        face.headEulerAngleY, face.headEulerAngleX, face.headEulerAngleZ, face.trackingId
                    )
                }
                val hint = gate.update(normalized, SystemClock.elapsedRealtime())
                guide.aligned = hint == AutoCaptureGate.Hint.HOLD || hint == AutoCaptureGate.Hint.CAPTURE
                onHint(when (hint) {
                    AutoCaptureGate.Hint.POSITION -> R.string.face_position
                    AutoCaptureGate.Hint.ONE_PERSON -> R.string.face_one_person
                    AutoCaptureGate.Hint.CLOSER -> R.string.face_move_closer
                    AutoCaptureGate.Hint.FARTHER -> R.string.face_move_farther
                    AutoCaptureGate.Hint.CENTER -> R.string.face_center
                    AutoCaptureGate.Hint.FACE_FORWARD -> R.string.face_forward
                    AutoCaptureGate.Hint.HOLD -> R.string.face_hold
                    AutoCaptureGate.Hint.CAPTURE -> R.string.face_capturing
                })
                if (hint == AutoCaptureGate.Hint.CAPTURE) capture(camera, run)
            })
            camera.initializationFuture.addListener({
                if (generation != run) return@addListener
                try {
                    camera.initializationFuture.get()
                    camera.cameraSelector = when {
                        camera.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        camera.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        else -> { stop(); onError(R.string.face_camera_unavailable); return@addListener }
                    }
                    camera.bindToLifecycle(activity)
                } catch (e: Exception) { stop(); onError(R.string.face_camera_unavailable) }
            }, executor)
        } catch (e: Exception) { stop(); onError(R.string.face_camera_unavailable) }
    }

    private fun capture(camera: LifecycleCameraController, run: Int) {
        takingPhoto = true
        var file: File? = null
        try {
            val dir = File(activity.cacheDir, "face_photos").apply { mkdirs() }
            val photo = File.createTempFile("enrollment_", ".jpg", dir)
            file = photo
            val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = false }
            camera.takePicture(ImageCapture.OutputFileOptions.Builder(photo).setMetadata(metadata).build(), executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (generation != run || activity.isDestroyed) { photo.delete(); return }
                        if (!continuous) stop()
                        onPhoto(photo)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        photo.delete()
                        if (generation == run) { stop(); onError(R.string.face_capture_failed) }
                    }
                })
        } catch (e: Exception) { file?.delete(); stop(); onError(R.string.face_capture_failed) }
    }

    fun pauseCapture() { paused = true; waitForClear = false; gate.reset(); guide.aligned = false }
    fun resumeCapture() { paused = false; waitForClear = false; clearSince = null; takingPhoto = false; gate.reset(); guide.aligned = false }
    fun resumeAfterFaceLeaves() {
        paused = true; waitForClear = true; clearSince = null; takingPhoto = false; gate.reset(); guide.aligned = false
    }

    fun stop() {
        generation++; takingPhoto = false; paused = false; waitForClear = false; clearSince = null
        gate.reset(); guide.aligned = false
        controller?.clearImageAnalysisAnalyzer()
        controller?.unbind(); controller = null
        preview.controller = null
        detector?.close(); detector = null
    }
    override fun onPause(owner: LifecycleOwner) { gate.reset(); clearSince = null; guide.aligned = false }
    override fun onDestroy(owner: LifecycleOwner) { stop() }
}
