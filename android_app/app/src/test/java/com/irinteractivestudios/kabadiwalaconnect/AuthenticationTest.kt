package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.auth.IndianPhoneValidator
import com.irinteractivestudios.kabadiwalaconnect.data.auth.AuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.CollectorProfileRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.MockOtpService
import com.irinteractivestudios.kabadiwalaconnect.data.auth.OtpChallenge
import com.irinteractivestudios.kabadiwalaconnect.data.auth.OtpVerification
import com.irinteractivestudios.kabadiwalaconnect.data.auth.PhoneAccountRequest
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SecureSessionRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingStep
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingViewModel
import com.irinteractivestudios.kabadiwalaconnect.util.InMemorySecureStorage
import com.irinteractivestudios.kabadiwalaconnect.util.CurrentLocation
import com.irinteractivestudios.kabadiwalaconnect.util.LocationProvider
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    @Test fun indianPhoneValidator_normalizesCommonIndianFormats() {
        assertEquals("9310707756", IndianPhoneValidator.normalize("+91 93107-07756"))
        assertEquals("9310707756", IndianPhoneValidator.normalize("0091 9310707756"))
        assertEquals("9310707756", IndianPhoneValidator.normalize("09310707756"))
        assertTrue(IndianPhoneValidator.isValid("+91 93107-07756"))
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

    @Test fun onboarding_invalidPhoneCanBeCorrected() {
        val vm = TestAuth.onboarding()
        vm.setPhone("1234567890")
        vm.requestOtp()

        assertTrue(vm.state.value.phoneError)
        vm.setPhone("9876543210")

        assertFalse(vm.state.value.phoneError)
        assertEquals("9876543210", vm.state.value.phone)
    }

    @Test fun onboarding_backAndStartOverKeepPeopleOutOfDeadEnds() {
        val vm = TestAuth.onboarding()
        vm.selectRole(AccountRole.COLLECTOR)
        vm.chooseManualLocation()
        vm.setArea("Pune")
        vm.continueToPhone()

        vm.goBack()
        assertEquals(OnboardingStep.AREA, vm.state.value.step)
        assertEquals("Pune", vm.state.value.area)

        vm.startOver()
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
        assertEquals("", vm.state.value.phone)
        assertEquals("", vm.state.value.area)
    }

    @Test fun onboarding_nameIsOptionalAndCanContinueToPhone() {
        val vm = TestAuth.onboarding()
        vm.selectRole(AccountRole.COLLECTOR)
        vm.chooseManualLocation()
        vm.setArea("Pune")
        vm.setDisplayName("")

        vm.continueToPhone()

        assertEquals(OnboardingStep.PHONE, vm.state.value.step)
    }

    @Test fun onboarding_successfulLegacyOtpCompletesInsteadOfSendingUserBack() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        try {
            val vm = TestAuth.onboarding()
            vm.setPhone("9876543210")
            vm.requestOtp()
            advanceUntilIdle()
            assertEquals(OnboardingStep.OTP, vm.state.value.step)

            vm.setOtp("123456")
            vm.verifyOtp()
            advanceUntilIdle()

            assertEquals(OnboardingStep.COMPLETE, vm.state.value.step)
            assertTrue(vm.state.value.completed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun onboarding_retriesVerifiedExistingPhoneWhenRegistrationConflicts() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        try {
            var legacySignInCalls = 0
            val auth = object : AuthenticationRepository {
                override suspend fun requestOtp(phoneNumber: String) = OtpChallenge(phoneNumber, Long.MAX_VALUE, 0)
                override suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification {
                    legacySignInCalls++
                    return OtpVerification.Success("existing-token", Long.MAX_VALUE, "existing-collector")
                }
                override suspend fun verifyOtp(phoneNumber: String, code: String, account: PhoneAccountRequest) = OtpVerification.AccountConflict
                override fun isSessionValid() = false
                override fun logout() = Unit
            }
            val profiles = object : CollectorProfileRepository {
                override fun observe(): Flow<CollectorProfile?> = emptyFlow()
                override suspend fun save(profile: CollectorProfile) = Unit
                override suspend fun clear() = Unit
            }
            val vm = OnboardingViewModel(auth, profiles)
            vm.setPhone("9876543210")
            vm.requestOtp()
            advanceUntilIdle()
            vm.setOtp("123456")
            vm.verifyOtp()
            advanceUntilIdle()

            assertEquals(1, legacySignInCalls)
            assertEquals(OnboardingStep.COMPLETE, vm.state.value.step)
            assertTrue(vm.state.value.completed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun onboarding_retriesVerifiedCollectorPhoneEvenWhenOptionalEmailWasEntered() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        try {
            var legacySignInCalls = 0
            val auth = object : AuthenticationRepository {
                override suspend fun requestOtp(phoneNumber: String) = OtpChallenge(phoneNumber, Long.MAX_VALUE, 0)
                override suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification {
                    legacySignInCalls++
                    return OtpVerification.Success("existing-token", Long.MAX_VALUE, "existing-collector")
                }
                override suspend fun verifyOtp(phoneNumber: String, code: String, account: PhoneAccountRequest) = OtpVerification.AccountConflict
                override fun isSessionValid() = false
                override fun logout() = Unit
            }
            val profiles = object : CollectorProfileRepository {
                override fun observe(): Flow<CollectorProfile?> = emptyFlow()
                override suspend fun save(profile: CollectorProfile) = Unit
                override suspend fun clear() = Unit
            }
            val vm = OnboardingViewModel(auth, profiles)
            vm.setPhone("9876543210")
            vm.chooseManualLocation()
            vm.setArea("Pune")
            vm.setEmail("friend@example.com")
            vm.requestOtp()
            advanceUntilIdle()
            vm.setOtp("123456")
            vm.verifyOtp()
            advanceUntilIdle()

            assertEquals(1, legacySignInCalls)
            assertEquals(OnboardingStep.COMPLETE, vm.state.value.step)
            assertTrue(vm.state.value.completed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun onboarding_doesNotVerifyWhenRequiredRegistrationFieldsAreMissing() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        try {
            var registrationCalls = 0
            val auth = object : AuthenticationRepository {
                override suspend fun requestOtp(phoneNumber: String) = OtpChallenge(phoneNumber, Long.MAX_VALUE, 0)
                override suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification =
                    OtpVerification.Success("token", Long.MAX_VALUE, "collector")
                override suspend fun verifyOtp(phoneNumber: String, code: String, account: PhoneAccountRequest): OtpVerification {
                    registrationCalls++
                    return OtpVerification.Success("token", Long.MAX_VALUE, "collector")
                }
                override fun isSessionValid() = false
                override fun logout() = Unit
            }
            val profiles = object : CollectorProfileRepository {
                override fun observe(): Flow<CollectorProfile?> = emptyFlow()
                override suspend fun save(profile: CollectorProfile) = Unit
                override suspend fun clear() = Unit
            }
            val vm = OnboardingViewModel(auth, profiles)
            vm.setPhone("9876543210")
            vm.requestOtp()
            advanceUntilIdle()
            vm.setOtp("123456")
            vm.setDisplayName("Asha")

            vm.verifyOtp()
            advanceUntilIdle()

            assertEquals(0, registrationCalls)
            assertEquals(OnboardingStep.OTP, vm.state.value.step)
            assertFalse(vm.state.value.completed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun onboarding_sendsTrimmedMandatoryFieldsAndLeavesOptionalFieldsOptional() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        try {
            var captured: PhoneAccountRequest? = null
            val auth = object : AuthenticationRepository {
                override suspend fun requestOtp(phoneNumber: String) = OtpChallenge(phoneNumber, Long.MAX_VALUE, 0)
                override suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification =
                    OtpVerification.Success("token", Long.MAX_VALUE, "collector")
                override suspend fun verifyOtp(phoneNumber: String, code: String, account: PhoneAccountRequest): OtpVerification {
                    captured = account
                    return OtpVerification.Success("token", Long.MAX_VALUE, "collector")
                }
                override fun isSessionValid() = false
                override fun logout() = Unit
            }
            val profiles = object : CollectorProfileRepository {
                override fun observe(): Flow<CollectorProfile?> = emptyFlow()
                override suspend fun save(profile: CollectorProfile) = Unit
                override suspend fun clear() = Unit
            }
            val vm = OnboardingViewModel(auth, profiles)
            vm.setPhone("9876543210")
            vm.chooseManualLocation()
            vm.setArea("  Pune  ")
            vm.setDisplayName("  ")
            vm.setEmail("   ")
            vm.requestOtp()
            advanceUntilIdle()
            vm.setOtp("123456")

            vm.verifyOtp()
            advanceUntilIdle()

            assertEquals("Pune", captured?.areaName)
            assertEquals("", captured?.displayName)
            assertEquals("", captured?.email)
            assertEquals(OnboardingStep.COMPLETE, vm.state.value.step)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun onboarding_gpsStoresDetectedAreaAndCoordinates() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        val vm = TestAuth.onboarding(
            locationProvider = LocationProvider { CurrentLocation(18.5204, 73.8567, "Pune") }
        )

        try {
            vm.locationPermissionResult(true)
            advanceUntilIdle()

            assertEquals(OnboardingStep.AREA, vm.state.value.step)
            assertEquals("Pune", vm.state.value.area)
            assertEquals(18.5204, vm.state.value.latitude ?: 0.0, 0.000001)
            assertEquals(73.8567, vm.state.value.longitude ?: 0.0, 0.000001)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
