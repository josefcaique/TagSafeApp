package com.digitalsix.YouSafe.registration

import com.digitalsix.YouSafe.face.FaceRules
import com.digitalsix.YouSafe.network.ApiService
import com.digitalsix.YouSafe.network.criarFuncionarioRequestBody
import com.google.gson.JsonObject
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody

class RegistrationFailure(message: String) : Exception(message)

/** Employee creation and face enrollment are separate API operations. Never repeat known creation. */
class FuncionarioRegistration(private val api: ApiService, private val faceApi: ApiService) {
    suspend fun submit(
        token: String, payload: JsonObject, requiresFace: Boolean,
        photo: MultipartBody.Part?, consentAt: String?, existingEmployeeId: String?,
        beforeCreate: () -> Unit, createRejected: () -> Unit, employeeCreated: (String) -> Unit
    ): String? {
        val nfc = payload.get("nfc")?.takeUnless { it.isJsonNull }?.asString
        require(!nfc.isNullOrBlank() || requiresFace) { "Adicione NFC ou uma foto antes de cadastrar." }
        require(!requiresFace || (photo != null && !consentAt.isNullOrBlank())) { "Salve a foto antes de cadastrar." }
        var employeeId = existingEmployeeId
        if (employeeId == null) {
            beforeCreate()
            val response = api.criarFuncionario(token, criarFuncionarioRequestBody(payload))
            if (!response.isSuccessful) {
                // Gateway/server failures may happen after a commit; reconcile before retrying creation.
                if (response.code() in 400..499 && response.code() != 408) createRejected()
                val body = runCatching { com.google.gson.JsonParser.parseString(response.errorBody()?.string()).asJsonObject }.getOrNull()
                throw RegistrationFailure(body?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: body?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: "Não foi possível cadastrar o funcionário (${response.code()}).")
            }
            employeeId = FaceRules.employeePublicId(response.body())
                ?: response.headers()["Location"]?.trimEnd('/')?.substringAfterLast('/')?.takeIf(FaceRules::isPublicId)
                ?: throw RegistrationFailure("Os dados foram salvos, mas é preciso verificar o cadastro antes de continuar.")
            employeeCreated(employeeId)
        }
        require(FaceRules.isPublicId(employeeId)) { "Funcionário sem UUID público válido." }
        if (!requiresFace) return null
        val response = faceApi.cadastrarRosto(token, photo!!, text(employeeId), text(consentAt!!))
        if (!response.isSuccessful) throw RegistrationFailure("Os dados do funcionário foram salvos. Falta concluir a facial: ${FaceRules.errorMessage(response.code())}")
        return response.body()?.id?.takeIf(FaceRules::isPublicId)
            ?: throw RegistrationFailure("Não foi possível confirmar o vínculo facial. Verifique a conexão e tente concluir novamente.")
    }
    private fun text(value: String) = RequestBody.create(MediaType.parse("text/plain"), value)
}
