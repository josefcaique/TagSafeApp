package com.digitalsix.YouSafe.face

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.digitalsix.YouSafe.FaceAttendanceActivity
import com.digitalsix.YouSafe.network.RetrofitInstance
import com.digitalsix.YouSafe.utils.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Recognition runs in an active camera screen; the server list stays authoritative. */
class FaceAttendanceController(
    private val activity: AppCompatActivity,
    private val capture: Button,
    private val list: TextView,
    private val count: TextView,
    private val status: TextView,
    private val onBusy: (Boolean) -> Unit
) {
    private var sessionId: String? = null
    private var point = 1
    private var generation = 0
    private var busy = false
    private var reading = false
    private var readingSession: String? = null
    private var readingPoint = 1
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        reading = false
        updateBusy()
        if (sessionId == readingSession && point == readingPoint) {
            status.text = activity.getString(com.digitalsix.YouSafe.R.string.attendance_live_intro)
            refresh()
        }
        readingSession = null
    }
    init {
        capture.setOnClickListener {
            val id = sessionId ?: return@setOnClickListener
            if (busy || reading || !FaceRules.isPublicId(id)) return@setOnClickListener
            readingSession = id; readingPoint = point; reading = true; updateBusy()
            try {
                launcher.launch(Intent(activity, FaceAttendanceActivity::class.java)
                    .putExtra(FaceAttendanceActivity.EXTRA_SESSION, id)
                    .putExtra(FaceAttendanceActivity.EXTRA_POINT, point))
            } catch (e: Exception) {
                reading = false; updateBusy()
                status.text = "Não foi possível abrir o reconhecimento facial. Tente novamente."
            }
        }
    }
    fun restore(state: Bundle?) {
        readingSession = state?.getString("face_reading_session")
        readingPoint = state?.getInt("face_reading_point") ?: 1
        reading = state?.getBoolean("face_reading") ?: false
    }
    fun save(state: Bundle) {
        state.putString("face_reading_session", readingSession)
        state.putInt("face_reading_point", readingPoint)
        state.putBoolean("face_reading", reading)
    }
    fun activate(id: String, point: Int) {
        generation++; sessionId = id; this.point = point; busy = false
        list.text = "Carregando presenças..."; count.text = "Participantes: —"
        updateBusy()
        refresh()
    }
    fun deactivate() { generation++; sessionId = null; busy = false; reading = false; updateBusy() }
    private fun updateBusy() {
        capture.isEnabled = !busy && !reading
        onBusy(sessionId != null && (busy || reading))
    }
    fun refresh() {
        val id = sessionId ?: return
        if (busy || reading) return
        if (!FaceRules.isPublicId(id)) { status.text = "Sessão sem UUID público. Reabra a sessão após atualizar os dados."; return }
        val token = SessionManager(activity).getTokenWithBearer() ?: return
        val currentGeneration = generation
        val currentPoint = point
        busy = true; updateBusy()
        activity.lifecycleScope.launch {
            try {
                val response = RetrofitInstance.faceApi.listarPresencasFaciais(token, id)
                if (generation != currentGeneration) return@launch
                val body = response.body()
                if (response.isSuccessful && body != null && body.sessaoId.equals(id, ignoreCase = true)) {
                    val rows = body.presencas.orEmpty().filter { it.numeroApontamento == currentPoint }
                    list.text = if (rows.isEmpty()) "Nenhuma presença registrada ainda." else rows.joinToString("\n") {
                        val method = when (it.metodo) { "facial" -> "Facial"; "nfc" -> "NFC"; "manual" -> "Manual"; else -> it.metodo }
                        "${it.nome} • $method"
                    }
                    count.text = "Participantes registrados: ${rows.distinctBy { it.publicId }.size}"
                } else status.text = FaceRules.errorMessage(response.code())
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (generation == currentGeneration) status.text = "Não foi possível atualizar as presenças. Verifique a conexão."
            } finally {
                if (generation == currentGeneration) { busy = false; updateBusy() }
            }
        }
    }
}
