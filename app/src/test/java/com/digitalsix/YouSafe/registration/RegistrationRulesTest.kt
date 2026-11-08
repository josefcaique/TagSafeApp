package com.digitalsix.YouSafe.registration

import org.junit.Assert.*
import org.junit.Test

class RegistrationRulesTest {
    @Test fun recoveryRequiresSameEmployeeAndRequestedNfc() {
        val employee = com.digitalsix.YouSafe.network.FuncionarioCadastroResumo(
            "1364a7c4-1234-4567-8901-123456789abc", "Ana", "52998224725", "ABC")
        assertTrue(RegistrationRules.matchesRecovery(employee, " Ana ", "529.982.247-25", "ABC"))
        assertTrue(RegistrationRules.matchesRecovery(employee, "Ana", "52998224725", null))
        assertFalse(RegistrationRules.matchesRecovery(employee, "Bia", "52998224725", "ABC"))
        assertFalse(RegistrationRules.matchesRecovery(employee, "Ana", "11111111111", "ABC"))
        assertFalse(RegistrationRules.matchesRecovery(employee, "Ana", "52998224725", "OTHER"))
        assertFalse(RegistrationRules.matchesRecovery(employee.copy(publicId = "172"), "Ana", "52998224725", "ABC"))
    }

    @Test fun noMethodSelectedNeverEnablesRegistration() {
        assertFalse(RegistrationRules.ready(false, "ABC", false, true))
        assertFalse(RegistrationRules.ready(false, null, false, false))
    }
    @Test fun nfcOnlyRequiresNonBlankCode() {
        assertTrue(RegistrationRules.ready(true, "ABC", false, false))
        assertFalse(RegistrationRules.ready(true, " ", false, false))
        assertFalse(RegistrationRules.ready(true, null, false, false))
    }
    @Test fun faceOnlyRequiresSavedPhoto() {
        assertTrue(RegistrationRules.ready(false, null, true, true))
        assertFalse(RegistrationRules.ready(false, "ABC", true, false))
    }
    @Test fun bothSelectedRequireBothCompleted() {
        assertTrue(RegistrationRules.ready(true, "ABC", true, true))
        assertFalse(RegistrationRules.ready(true, "ABC", true, false))
        assertFalse(RegistrationRules.ready(true, "", true, true))
    }
}
