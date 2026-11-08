package com.digitalsix.YouSafe.network

import com.google.gson.annotations.SerializedName

data class FaceEnrollment(
    val id: String,
    @SerializedName("funcionario_public_id") val funcionarioPublicId: String?,
    @SerializedName("consent_given_at") val consentGivenAt: String?,
    @SerializedName("face_confidence") val faceConfidence: Double?
)
data class FaceEmployee(@SerializedName("public_id") val publicId: String, val nome: String)
data class FaceMatch(
    val matched: Boolean,
    val funcionario: FaceEmployee?,
    val reason: String?,
    @SerializedName("matched_confidence") val matchedConfidence: Double?
)
data class FaceAttendanceList(
    @SerializedName("sessao_id") val sessaoId: String,
    val presencas: List<FaceAttendance>
)
data class FaceAttendance(
    @SerializedName("public_id") val publicId: String,
    val nome: String,
    @SerializedName("numero_apontamento") val numeroApontamento: Int,
    @SerializedName("horario_registro") val horarioRegistro: String?,
    val metodo: String,
    @SerializedName("matched_confidence") val matchedConfidence: Double?
)
data class FaceDeletion(val deleted: Boolean, val id: String)

// Used to reconcile an interrupted registration, scoped to the selected unit.
data class FuncionarioCadastroResumo(
    @SerializedName("public_id") val publicId: String?,
    val nome: String?, val cpf: String?, val nfc: String?
)
