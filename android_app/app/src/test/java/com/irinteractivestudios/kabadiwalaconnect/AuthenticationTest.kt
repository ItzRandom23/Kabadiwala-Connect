package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.auth.IndianPhoneValidator
import com.irinteractivestudios.kabadiwalaconnect.data.auth.MockOtpService
import com.irinteractivestudios.kabadiwalaconnect.data.auth.OtpVerification
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SecureSessionRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingStep
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingViewModel
import com.irinteractivestudios.kabadiwalaconnect.util.InMemorySecureStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationTest {
    @Test fun indianPhoneValidator_acceptsOnlyTenDigitIndianMobile() {
        assertTrue(IndianPhoneValidator.isValid("9876543210"))
        assertTrue(IndianPhoneValidator.isValid("6123456789"))
        assertFalse(IndianPhoneValidator.isValid("5123456789"))
        assertFalse(IndianPhoneValidator.isValid("987654321"))
        assertFalse(IndianPhoneValidator.isValid("98765abc10"))
    }

    @Test fun mockOtp_supportsSuccessIncorrectExpiryAndAttemptLimit() = runTest {
        val service = MockOtpService(ttlMs = 10, resendCooldownMs = 5, maxAttempts = 2)
        service.send("9876543210", nowEpochMs = 100)
        assertEquals(OtpVerification.Incorrect, service.verify("9876543210", "000000", 101))
        assertEquals(OtpVerification.Incorrect, service.verify("9876543210", "000000", 102))
        assertEquals(OtpVerification.AttemptsExceeded, service.verify("9876543210", "123456", 103))
        service.send("9876543210", nowEpochMs = 106)
        assertTrue(service.verify("9876543210", "123456", 107) is OtpVerification.Success)
        service.send("9876543210", nowEpochMs = 200)
        assertEquals(OtpVerification.Expired, service.verify("9876543210", "123456", 211))
    }

    @Test fun secureSession_expiresAndLogoutClears() {
        val session = SecureSessionRepository(InMemorySecureStorage())
        session.save("token", 100)
        assertTrue(session.isSessionValid(99))
        assertFalse(session.isSessionValid(100))
        session.save("token", 200)
        session.clear()
        assertFalse(session.isSessionValid(99))
    }

    @Test fun onboarding_manualLocationMovesToArea() = runTest {
        val vm = TestAuth.onboarding()
        vm.selectLanguage("mr")
        assertEquals(OnboardingStep.LOCATION_PERMISSION, vm.state.value.step)
        vm.chooseManualLocation()
        assertEquals(OnboardingStep.AREA, vm.state.value.step)
    }

    @Test fun onboarding_reusesFirstRunLanguageAndDoesNotAskAgain() {
        val vm = TestAuth.onboarding("mr")
        assertEquals("mr", vm.state.value.language)
        vm.selectRole(AccountRole.COLLECTOR)
        assertEquals(OnboardingStep.LOCATION_PERMISSION, vm.state.value.step)
    }

    @Test fun onboarding_resetOtpReturnsToPhoneEntry() = runTest {
        val vm = TestAuth.onboarding()
        vm.setOtp("123456")

        vm.resetOtp()

        assertEquals(OnboardingStep.PHONE, vm.state.value.step)
        assertEquals("", vm.state.value.otp)
        assertEquals(null, vm.state.value.challenge)
    }
}
