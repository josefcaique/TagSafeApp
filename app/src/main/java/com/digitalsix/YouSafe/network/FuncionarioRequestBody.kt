package com.digitalsix.YouSafe.network

import com.google.gson.JsonObject
import okhttp3.MediaType
import okhttp3.RequestBody

fun criarFuncionarioRequestBody(dados: JsonObject): RequestBody {
    val payload = dados.deepCopy()
    val nfc = payload.get("nfc")?.takeUnless { it.isJsonNull }
        ?.asString?.trim()?.takeIf { it.isNotBlank() }
    payload.addProperty("nfc", nfc)
    // Preserve explicit JSON null for this endpoint without changing other Gson requests.
    return RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload.toString())
}
