package com.digitalsix.YouSafe.face

import com.google.gson.JsonElement

object FaceRules {
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    fun isPublicId(value: String?): Boolean = value != null &&
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(value)

    // Never fall back to an internal numeric id.
    fun employeePublicId(body: JsonElement?): String? {
        if (body == null || !body.isJsonObject) return null
        val obj = body.asJsonObject
        obj.get("public_id")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf(::isPublicId)?.let { return it }
        for (key in listOf("funcionario", "data")) {
            employeePublicId(obj.get(key))?.let { return it }
        }
        return null
    }

    fun sessionPublicId(body: JsonElement?): String? {
        if (body == null) return null
        if (body.isJsonPrimitive) return body.asString.takeIf(::isPublicId)
        if (!body.isJsonObject) return null
        val obj = body.asJsonObject
        for (key in listOf("public_id", "sessao_public_id", "sessao_id", "sessaoId", "id_sessao", "session_id", "sessionId", "aula_id", "id")) {
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(::isPublicId)?.let { return it }
        }
        for (key in listOf("sessao", "data")) {
            sessionPublicId(obj.get(key))?.let { return it }
        }
        return null
    }

    fun errorMessage(status: Int): String = when (status) {
        400 -> "Foto inválida. Use JPEG ou PNG de até 8 MB e confirme o consentimento no cadastro."
        401 -> "Sessão expirada. Faça login novamente."
        403 -> "Sem permissão para este funcionário ou sessão nesta unidade."
        404 -> "Funcionário, sessão ou cadastro facial não encontrado. Verifique o UUID público."
        500, 502, 503, 504 -> "Serviço de reconhecimento facial indisponível. Tente novamente."
        else -> "Não foi possível concluir a operação facial ($status)."
    }

    fun unrecognizedMessage(reason: String?): String = when (reason) {
        "BELOW_THRESHOLD" -> "O rosto não foi reconhecido com confiança suficiente."
        "NO_ENROLLMENT" -> "Não há cadastro facial para este funcionário."
        "NO_ELIGIBLE_ENROLLMENTS" -> "Não há cadastros faciais elegíveis nesta sessão."
        else -> "Não foi possível reconhecer o rosto."
    }
}
