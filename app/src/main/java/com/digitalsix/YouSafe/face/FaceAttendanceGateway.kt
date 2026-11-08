package com.digitalsix.YouSafe.face

import com.digitalsix.YouSafe.network.ApiService
import com.digitalsix.YouSafe.network.FaceEmployee
import com.digitalsix.YouSafe.network.FaceMatch
import com.google.gson.Gson
import okhttp3.MultipartBody

sealed class AttendanceRecognition {
    data class Matched(val employee: FaceEmployee) : AttendanceRecognition()
    data class Unmatched(val reason: String?) : AttendanceRecognition()
    data class Error(val status: Int) : AttendanceRecognition()
    object Uncertain : AttendanceRecognition()
}

class FaceAttendanceGateway(private val api: ApiService) {
    suspend fun recognize(token: String, sessionId: String, photo: MultipartBody.Part): AttendanceRecognition {
        require(FaceRules.isPublicId(sessionId))
        val response = api.registrarPresencaFacial(token, sessionId, photo)
        val body = response.body()
        if (response.isSuccessful && body?.matched == true) {
            val employee = body.funcionario
            return if (employee != null && FaceRules.isPublicId(employee.publicId)) AttendanceRecognition.Matched(employee)
                else AttendanceRecognition.Uncertain
        }
        if (response.code() == 422 || (response.isSuccessful && body?.matched == false)) {
            val unmatched = body ?: runCatching {
                Gson().fromJson(response.errorBody()?.string(), FaceMatch::class.java)
            }.getOrNull()
            return AttendanceRecognition.Unmatched(unmatched?.reason)
        }
        return if (response.isSuccessful) AttendanceRecognition.Uncertain else AttendanceRecognition.Error(response.code())
    }
}
