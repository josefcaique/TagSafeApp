package com.digitalsix.YouSafe.registration

object RegistrationRules {
    fun matchesRecovery(employee: com.digitalsix.YouSafe.network.FuncionarioCadastroResumo,
                        name: String, cpf: String, expectedNfc: String?): Boolean =
        com.digitalsix.YouSafe.face.FaceRules.isPublicId(employee.publicId) &&
            employee.cpf?.filter(Char::isDigit) == cpf.filter(Char::isDigit) &&
            employee.nome?.trim()?.equals(name.trim(), ignoreCase = true) == true &&
            (expectedNfc == null || employee.nfc?.trim() == expectedNfc.trim())

    fun ready(useNfc: Boolean, nfc: String?, useFace: Boolean, photoSaved: Boolean): Boolean =
        (useNfc || useFace) && (!useNfc || !nfc.isNullOrBlank()) && (!useFace || photoSaved)
}
