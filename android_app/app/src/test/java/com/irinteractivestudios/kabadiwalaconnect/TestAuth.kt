package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.auth.AuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.MockOtpService
import com.irinteractivestudios.kabadiwalaconnect.data.auth.OtpChallenge
import com.irinteractivestudios.kabadiwalaconnect.data.auth.OtpVerification
import com.irinteractivestudios.kabadiwalaconnect.data.auth.CollectorProfileRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingViewModel
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal object TestAuth {
    fun onboarding(initialLanguage: String = LocaleManager.ENGLISH) = OnboardingViewModel(
        object : AuthenticationRepository {
            private val otp = MockOtpService()
            override suspend fun requestOtp(phoneNumber: String): OtpChallenge = otp.send(phoneNumber, 0)
            override suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification = otp.verify(phoneNumber, code, 1)
            override fun isSessionValid() = false
            override fun logout() = Unit
        },
        object : CollectorProfileRepository {
            override fun observe(): Flow<CollectorProfile?> = emptyFlow()
            override suspend fun save(profile: CollectorProfile) = Unit
            override suspend fun clear() = Unit
        },
        initialLanguage = initialLanguage
    )
}
