package com.digitalsix.YouSafe.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FuncionarioRequestBodyTest {
    @Test fun cadastroEnviaNullExplicitoParaNfcVazioOuAusente() {
        for (nfc in listOf(null, "", "   ", "\t\n")) {
            verificarEnvio(nfc, null)
        }
        verificarEnvio(null, null, omitido = true)
    }

    @Test fun cadastroPreservaCodigoPreenchido() {
        verificarEnvio("  ABC123  ", "ABC123")
    }

    private fun verificarEnvio(nfc: String?, esperado: String?, omitido: Boolean = false) = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/funcionarios", request.url().encodedPath())
            assertEquals("application/json; charset=utf-8", request.body()!!.contentType().toString())
            val buffer = Buffer()
            request.body()!!.writeTo(buffer)
            val json = JsonParser.parseString(buffer.readUtf8()).asJsonObject
            assertTrue(json.has("nfc"))
            if (esperado == null) assertTrue(json["nfc"].isJsonNull)
            else assertEquals(esperado, json["nfc"].asString)
            assertEquals("Ana", json["nome"].asString)
            assertEquals("unidade-uuid", json["unidade_id"].asString)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(201).message("Created")
                .body(ResponseBody.create(MediaType.parse("application/json"), "{}" )).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        val dados = JsonObject().apply {
            addProperty("nome", "Ana")
            addProperty("unidade_id", "unidade-uuid")
            if (!omitido) addProperty("nfc", nfc)
        }
        assertTrue(api.criarFuncionario("Bearer test", criarFuncionarioRequestBody(dados)).isSuccessful)
    }
}
