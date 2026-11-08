package com.digitalsix.YouSafe.face

import com.digitalsix.YouSafe.network.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FaceAttendanceGatewayTest {
    private val session = "1364a7c4-1234-4567-8901-123456789abc"
    private val employee = "4b415bff-5a43-48f7-9841-80b28791dcc6"
    private fun gateway(code: Int, json: String): FaceAttendanceGateway {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/face-recognition/sessoes/$session/attendance", request.url().encodedPath())
            assertEquals(1, (request.body() as MultipartBody).size())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(ResponseBody.create(MediaType.parse("application/json"), json)).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        return FaceAttendanceGateway(api)
    }
    private fun photo() = MultipartBody.Part.createFormData("file", "frame.jpg",
        RequestBody.create(MediaType.parse("image/jpeg"), byteArrayOf(1, 2, 3)))
    @Test fun confirmedMatchReturnsPublicIdentity() = runBlocking {
        val result = gateway(200, """{"matched":true,"funcionario":{"public_id":"$employee","nome":"Ana"}}""")
            .recognize("Bearer test", session, photo())
        assertEquals(employee, (result as AttendanceRecognition.Matched).employee.publicId)
    }
    @Test fun unmatched422IsNotAnApiErrorOrAttendanceConfirmation() = runBlocking {
        val result = gateway(422, """{"matched":false,"reason":"BELOW_THRESHOLD"}""")
            .recognize("Bearer test", session, photo())
        assertEquals("BELOW_THRESHOLD", (result as AttendanceRecognition.Unmatched).reason)
    }
    @Test fun noEligibleEnrollmentRemainsAnUnmatchedResult() = runBlocking {
        val result = gateway(422, """{"matched":false,"reason":"NO_ELIGIBLE_ENROLLMENTS"}""")
            .recognize("Bearer test", session, photo())
        assertEquals("NO_ELIGIBLE_ENROLLMENTS", (result as AttendanceRecognition.Unmatched).reason)
    }
    @Test fun malformedSuccessDoesNotClaimAttendanceWasConfirmed() = runBlocking {
        val result = gateway(200, """{"matched":true}""").recognize("Bearer test", session, photo())
        assertSame(AttendanceRecognition.Uncertain, result)
    }
    @Test fun serviceFailureIsNotUnmatched() = runBlocking {
        val result = gateway(500, """{"error":"engine unavailable"}""")
            .recognize("Bearer test", session, photo())
        assertEquals(500, (result as AttendanceRecognition.Error).status)
    }
    @Test fun rejectsInternalSessionIdBeforeUploading() = runBlocking {
        try { gateway(200, "{}").recognize("Bearer test", "172", photo()); fail("UUID required") }
        catch (_: IllegalArgumentException) { }
    }
}
