package com.digitalsix.YouSafe.registration

import com.digitalsix.YouSafe.network.ApiService
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class FuncionarioRegistrationTest {
    private val employee = "1364a7c4-1234-4567-8901-123456789abc"
    private val enrollment = "4b415bff-5a43-48f7-9841-80b28791dcc6"
    private val consent = "2026-11-07T21:00:00.000Z"
    private val calls = mutableListOf<String>()
    private var created: String? = null
    private var uncertain = false
    private var failFace = false
    private var createStatus = 201
    private var disconnect = false
    private val api: ApiService by lazy {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            calls.add(req.url().encodedPath())
            if (disconnect) throw IOException("connection lost")
            val isCreate = req.url().encodedPath() == "/funcionarios"
            val code = if (isCreate) createStatus else if (failFace) 500 else 201
            val json = if (isCreate && code == 201) """{"public_id":"$employee"}"""
                else if (!isCreate && code == 201) """{"id":"$enrollment"}""" else """{"error":"request failed"}"""
            if (!isCreate) {
                val buffer = Buffer(); req.body()!!.writeTo(buffer)
                val body = buffer.readUtf8()
                assertTrue(body.contains(employee))
                assertTrue(body.contains(consent))
                assertTrue(body.contains("name=\"file\""))
            }
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(ResponseBody.create(MediaType.parse("application/json"), json)).build()
        }.build()
        Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
    private fun payload(nfc: String?) = JsonObject().apply {
        addProperty("nome", "Ana"); addProperty("cpf", "52998224725")
        addProperty("unidade_id", employee); addProperty("nfc", nfc)
    }
    private fun photo() = MultipartBody.Part.createFormData("file", "photo.jpg",
        RequestBody.create(MediaType.parse("image/jpeg"), byteArrayOf(1, 2, 3)))
    private suspend fun submit(nfc: String?, face: Boolean, savedPhoto: Boolean = true): String? =
        FuncionarioRegistration(api, api).submit("Bearer test", payload(nfc), face,
            if (savedPhoto && face) photo() else null, if (face) consent else null, created,
            beforeCreate = { uncertain = true }, createRejected = { uncertain = false },
            employeeCreated = { created = it; uncertain = false })

    @Test fun faceWithoutPreparedPhotoNeverCreatesEmployee() = runBlocking {
        try { submit(null, true, false); fail("Should reject missing photo") } catch (_: IllegalArgumentException) { }
        assertTrue(calls.isEmpty())
    }
    @Test fun neitherNfcNorFaceNeverCreatesEmployee() = runBlocking {
        try { submit(" ", false); fail("Should reject missing methods") } catch (_: IllegalArgumentException) { }
        assertTrue(calls.isEmpty())
    }
    @Test fun faceOnlyCreatesThenEnrollsAndPreservesAcceptanceTime() = runBlocking {
        assertEquals(enrollment, submit(null, true))
        assertEquals(listOf("/funcionarios", "/face-recognition/enrollments"), calls)
        assertEquals(employee, created); assertFalse(uncertain)
    }
    @Test fun nfcOnlyDoesNotCallFaceApi() = runBlocking {
        assertNull(submit("ABC", false))
        assertEquals(listOf("/funcionarios"), calls)
    }
    @Test fun bothMethodsCallBothEndpoints() = runBlocking {
        assertEquals(enrollment, submit("ABC", true))
        assertEquals(listOf("/funcionarios", "/face-recognition/enrollments"), calls)
    }
    @Test fun retryAfterEnrollmentFailureDoesNotCreateEmployeeAgain() = runBlocking {
        failFace = true
        try { submit(null, true); fail("Should report face failure") } catch (_: RegistrationFailure) { }
        assertEquals(employee, created)
        failFace = false
        assertEquals(enrollment, submit(null, true))
        assertEquals(1, calls.count { it == "/funcionarios" })
        assertEquals(2, calls.count { it == "/face-recognition/enrollments" })
    }
    @Test fun serverFailureKeepsCreationUncertainForReconciliation() = runBlocking {
        createStatus = 502
        try { submit(null, true); fail("Should fail") } catch (_: RegistrationFailure) { }
        assertTrue(uncertain); assertNull(created)
    }
    @Test fun validationRejectionDoesNotKeepUncertainCreation() = runBlocking {
        createStatus = 409
        try { submit(null, true); fail("Should fail") } catch (_: RegistrationFailure) { }
        assertFalse(uncertain); assertNull(created)
    }
    @Test fun disconnectedCreationMustBeCheckedBeforeAnyNewAttempt() = runBlocking {
        disconnect = true
        try { submit(null, true); fail("Should disconnect") } catch (_: IOException) { }
        assertTrue(uncertain); assertNull(created)
        assertEquals(listOf("/funcionarios"), calls)
    }
}
