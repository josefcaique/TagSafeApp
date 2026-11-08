package com.digitalsix.YouSafe.registration

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/** Private, excluded from backup. A known employee ID is kept for face-only retries. */
data class RegistrationDraft(
    val name: String = "", val cpf: String = "", val nfc: String = "",
    val sector: String = "", val level: String = "", val unitId: Int? = null,
    val useNfc: Boolean = false, val useFace: Boolean = false,
    val photoName: String? = null, val consentAt: String? = null,
    val employeePublicId: String? = null, val creationUncertain: Boolean = false
)

class RegistrationDraftStore(context: Context, owner: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "registration_$key.json"))
    fun load(): RegistrationDraft? = runCatching {
        file.openRead().bufferedReader().use { Gson().fromJson(it, RegistrationDraft::class.java) }
    }.getOrNull()
    fun save(draft: RegistrationDraft) {
        val output = file.startWrite()
        try {
            output.write(Gson().toJson(draft).toByteArray())
            file.finishWrite(output)
        } catch (e: Exception) { file.failWrite(output); throw e }
    }
    fun clear() { file.delete() }
}

object RegistrationPhotos {
    fun directory(context: Context) = File(context.noBackupFilesDir, "registration_photos").apply { mkdirs() }
    fun find(context: Context, name: String?): File? {
        if (name == null || !Regex("photo_[a-f0-9-]{36}\\.jpg").matches(name)) return null
        return File(directory(context), name).takeIf { it.isFile && it.length() > 0 }
    }
}
