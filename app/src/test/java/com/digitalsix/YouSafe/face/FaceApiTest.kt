package com.digitalsix.YouSafe.face

import com.digitalsix.YouSafe.network.*
import com.digitalsix.YouSafe.utils.AtividadeEmProgresso
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FaceApiTest {
    private val uuid = "1364a7c4-1234-4567-8901-123456789abc"
    private val enrollment = "4b415bff-5a43-48f7-9841-80b28791dcc6"
    private fun api(code: Int, json: String, inspect: (Request) -> Unit): ApiService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test")
                .body(ResponseBody.create(MediaType.parse("application/json"), json)).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
    private fun photo() = MultipartBody.Part.createFormData("file", "face.jpg",
        RequestBody.create(MediaType.parse("image/jpeg"), byteArrayOf(1, 2, 3)))
    private fun text(value: String) = RequestBody.create(MediaType.parse("text/plain"), value)

    @Test fun enrollmentSendsPublicUuidAndActualConsentTimestamp() = runBlocking {
        val accepted = "2026-10-10T22:33:48.000Z"
        val service = api(201, """{"id":"$enrollment","funcionario_id":172,"funcionario_public_id":"$uuid","consent_given_at":"$accepted"}""") {
            assertEquals("/face-recognition/enrollments", it.url().encodedPath())
            assertEquals("Bearer test", it.header("Authorization"))
            val buffer = Buffer(); it.body()!!.writeTo(buffer)
            val body = buffer.readUtf8()
            assertTrue(body.contains("name=\"file\""))
            assertTrue(body.contains("name=\"funcionario_id\""))
            assertTrue(body.contains(uuid)); assertTrue(body.contains(accepted))
        }
        val response = service.cadastrarRosto("Bearer test", photo(), text(uuid), text(accepted))
        assertEquals(enrollment, response.body()!!.id)
        assertEquals(uuid, response.body()!!.funcionarioPublicId)
    }
    @Test fun checkIn422IsUnmatchedAndUploadContainsOnlyPhoto() = runBlocking {
        val service = api(422, """{"matched":false,"reason":"BELOW_THRESHOLD","best_similarity":0.41}""") {
            assertEquals("/face-recognition/sessoes/$uuid/attendance", it.url().encodedPath())
            assertEquals("POST", it.method())
            assertEquals(1, (it.body() as MultipartBody).size())
        }
        val response = service.registrarPresencaFacial("Bearer test", uuid, photo())
        val match = Gson().fromJson(response.errorBody()!!.string(), FaceMatch::class.java)
        assertFalse(match.matched)
        assertEquals("BELOW_THRESHOLD", match.reason)
        assertTrue(FaceRules.unrecognizedMessage(match.reason).contains("confiança"))
    }
    @Test fun recognizedCheckInReturnsEmployeePublicId() = runBlocking {
        val service = api(200, """{"matched":true,"funcionario":{"public_id":"$uuid","nome":"João"},"matched_confidence":0.91}""") { }
        val result = service.registrarPresencaFacial("Bearer test", uuid, photo()).body()!!
        assertTrue(result.matched); assertEquals(uuid, result.funcionario!!.publicId)
    }
    @Test fun attendanceListKeepsMethodAndPoint() = runBlocking {
        val service = api(200, """{"sessao_id":"$uuid","presencas":[{"public_id":"$uuid","nome":"João","numero_apontamento":2,"metodo":"facial","matched_confidence":0.91}]}""") {
            assertEquals("GET", it.method())
        }
        val result = service.listarPresencasFaciais("Bearer test", uuid).body()!!
        assertEquals(2, result.presencas.single().numeroApontamento)
        assertEquals("facial", result.presencas.single().metodo)
    }
    @Test fun deleteUsesEnrollmentId() = runBlocking {
        val service = api(200, """{"deleted":true,"id":"$enrollment"}""") {
            assertEquals("DELETE", it.method())
            assertEquals("/face-recognition/enrollments/$enrollment", it.url().encodedPath())
        }
        assertTrue(service.excluirRosto("Bearer test", enrollment).body()!!.deleted)
    }
    @Test fun neverUsesInternalEmployeeId() {
        assertNull(FaceRules.employeePublicId(JsonParser.parseString("""{"id":172}""")))
        assertEquals(uuid, FaceRules.employeePublicId(JsonParser.parseString("""{"id":172,"funcionario":{"public_id":"$uuid"}}""")))
        assertFalse(FaceRules.isPublicId("172")); assertFalse(FaceRules.isPublicId(null))
    }
    @Test fun sessionUuidTakesPriorityOverInternalId() {
        assertEquals(uuid, FaceRules.sessionPublicId(JsonParser.parseString("""{"id":172,"sessao_id":"$uuid"}""")))
        assertEquals(uuid, FaceRules.sessionPublicId(JsonParser.parseString("""{"id":172,"sessao":{"public_id":"$uuid"}}""")))
        assertNull(FaceRules.sessionPublicId(JsonParser.parseString("""{"id":172}""")))
    }
    @Test fun sessionRecoveryPreservesChosenMethodAndOldSessionsUseNfc() {
        val gson = Gson()
        val session = AtividadeEmProgresso(uuid, 1L, uuid, "Unidade", "Módulo", metodoPresenca = "facial")
        assertEquals("facial", gson.fromJson(gson.toJson(session), AtividadeEmProgresso::class.java).metodoPresenca)
        val old = gson.fromJson("""{"uuid":"$uuid","inicioMs":1}""", AtividadeEmProgresso::class.java)
        assertEquals("nfc", old.metodoPresenca ?: "nfc")
    }
}
