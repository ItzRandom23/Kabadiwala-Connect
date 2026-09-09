package com.irinteractivestudios.kabadiwalaconnect.data.auth

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.CollectorUpdateDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.EmailAuthRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.LocationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.OtpRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.VerifyOtpRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.data.auth.saveAccount
import java.io.IOException
import com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager

/** Real collector authentication against the versioned backend API. */
class RemoteAuthenticationRepository(
    private val api: ApiService,
    private val session: SessionRepository,
    private val storage: SecureStorage? = null
) : AuthenticationRepository {

    override suspend fun requestOtp(phoneNumber: String): OtpChallenge {
        api.requestOtp(OtpRequestDto(phoneNumber)).requireData()
        val now = System.currentTimeMillis()
        return OtpChallenge(
            phoneNumber,
            now + OTP_TTL_MS,
            now + RESEND_COOLDOWN_MS,
            // SMS delivery is intentionally disabled for the current beta.
            // Keep the configured development code available on every build
            // so sideloaded APKs behave exactly like local ADB builds.
            developmentCodeHint = DEVELOPMENT_OTP_CODE
        )
    }

    override suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification = verifyOtpInternal(phoneNumber, code, null)

    override suspend fun verifyOtp(phoneNumber: String, code: String, account: PhoneAccountRequest): OtpVerification = verifyOtpInternal(phoneNumber, code, account)

    private suspend fun verifyOtpInternal(phoneNumber: String, code: String, account: PhoneAccountRequest?): OtpVerification {
        fun String?.trimmedOrNull() = this?.trim()?.takeIf { it.isNotEmpty() }
        val materials = account?.materialsAccepted
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
        return try {
            val auth = api.verifyOtp(
                VerifyOtpRequestDto(
                    phone = phoneNumber,
                    otp = code,
                    role = account?.role?.wireName(),
                    preferredLanguage = account?.preferredLanguage?.let(LocaleManager::toBackendName),
                    areaName = account?.areaName.trimmedOrNull(),
                    displayName = account?.displayName.trimmedOrNull(),
                    email = account?.email.trimmedOrNull(),
                    businessName = account?.businessName.trimmedOrNull(),
                    authorizationNumber = account?.authorizationNumber.trimmedOrNull(),
                    materialsAccepted = materials,
                    pickupAvailable = account?.pickupAvailable,
                    serviceRadiusKm = account?.serviceRadiusKm,
                    latitude = account?.latitude,
                    longitude = account?.longitude
                )
            ).requireData()
            val expiry = jwtExpiry(auth.token) ?: (System.currentTimeMillis() + SESSION_FALLBACK_MS)
            session.save(auth.token, expiry)
            val profile = auth.user?.toDomain() ?: auth.collector?.let { collector ->
                AccountProfile(
                    id = collector.id,
                    email = collector.email.orEmpty(),
                    role = AccountRole.COLLECTOR,
                    preferredLanguage = LocaleManager.fromBackendName(collector.preferredLanguage),
                    accountStatus = collector.accountStatus ?: "ACTIVE",
                    profileId = collector.id,
                    phoneNumber = collector.phone,
                    displayName = collector.displayName,
                    areaName = collector.primaryLocation?.areaName
                )
            }
            profile?.let { storage?.saveAccount(it) }
            OtpVerification.Success(auth.token, expiry, profile?.profileId.orEmpty(), profile)
        } catch (error: Exception) {
            when (errorCode(error)) {
                "OTP_EXPIRED" -> OtpVerification.Expired
                "OTP_ATTEMPTS_EXCEEDED" -> OtpVerification.AttemptsExceeded
                "OTP_INVALID" -> OtpVerification.Incorrect
                "ACCOUNT_CONFLICT", "CONFLICT" -> OtpVerification.AccountConflict
                else -> if (error is IOException) OtpVerification.NetworkError else OtpVerification.ServerError
            }
        }
    }

    override suspend fun updateProfile(profile: CollectorProfile) {
        api.updateCollector(
            CollectorUpdateDto(
                preferredLanguage = LocaleManager.toBackendName(profile.preferredLanguage),
                primaryLocation = LocationDto(
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    areaName = profile.primaryLocation,
                    precision = if (profile.locationSource == "gps") "GPS" else "MANUAL"
                ),
            )
        ).requireData()
    }

    override suspend fun authenticateEmail(request: EmailAccountRequest): EmailAuthentication {
        if (!EmailValidator.isValid(request.email) || request.password.length < 8) return EmailAuthentication.InvalidInput
        return try {
            val body = EmailAuthRequestDto(
                email = request.email,
                password = request.password,
                role = request.role.wireName(),
                preferredLanguage = LocaleManager.toBackendName(request.preferredLanguage),
                areaName = request.areaName,
                businessName = request.businessName,
                authorizationNumber = request.authorizationNumber,
                materialsAccepted = request.materialsAccepted,
                pickupAvailable = request.pickupAvailable,
                serviceRadiusKm = request.serviceRadiusKm
            )
            val auth = if (request.isReturning) api.login(body) else api.signup(body)
            val result = auth.requireData()
            val expiry = jwtExpiry(result.token) ?: (System.currentTimeMillis() + SESSION_FALLBACK_MS)
            session.save(result.token, expiry)
            val profile = result.user.toDomain()
            storage?.saveAccount(profile)
            EmailAuthentication.Success(result.token, expiry, profile)
        } catch (error: Exception) {
            when {
                error is IOException -> EmailAuthentication.NetworkError
                errorCode(error) == "EMAIL_IN_USE" -> EmailAuthentication.EmailInUse
                errorCode(error) == "INVALID_CREDENTIALS" || error is RemoteApiException && error.httpCode == 401 -> EmailAuthentication.InvalidCredentials
                else -> EmailAuthentication.NetworkError
            }
        }
    }

    override suspend fun refreshAccount(): AccountProfile? = runCatching {
        api.getAccountProfile().requireData().toDomain().also { storage?.saveAccount(it) }
    }.getOrNull()

    override fun isSessionValid() = session.isSessionValid()

    override fun logout() {
        // Logout is stateless on the backend; clearing the bearer token is the
        // security boundary and works even when the phone is offline.
        session.clear()
    }


    private fun errorCode(error: Exception): String? {
        if (error !is RemoteApiException) return null
        val raw = error.message.orEmpty()
        return runCatching {
            val root = Gson().fromJson(raw, JsonObject::class.java)
            val errorBody = root?.getAsJsonObject("error") ?: return@runCatching null
            errorBody.get("code")?.asString
                ?: errorBody.getAsJsonObject("details")?.get("code")?.asString
        }.getOrNull()
    }

    private fun jwtExpiry(token: String): Long? = runCatching {
        val part = token.split('.').getOrNull(1) ?: return@runCatching null
        val json = String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
        Gson().fromJson(json, Map::class.java)["exp"]?.toString()?.toDouble()?.times(1000L)?.toLong()
    }.getOrNull()

    companion object {
        private const val OTP_TTL_MS = 10 * 60 * 1000L
        private const val RESEND_COOLDOWN_MS = 30 * 1000L
        private const val SESSION_FALLBACK_MS = 30L * 24L * 60L * 60L * 1000L
        private const val DEVELOPMENT_OTP_CODE = "123456"
    }
}

private fun com.irinteractivestudios.kabadiwalaconnect.data.remote.AccountProfileDto.toDomain() = AccountProfile(
    id = id,
    email = email.orEmpty(),
    role = if (role == "RECYCLER") AccountRole.RECYCLER else AccountRole.COLLECTOR,
    preferredLanguage = LocaleManager.fromBackendName(preferredLanguage),
    accountStatus = accountStatus,
    verificationStatus = runCatching { RecyclerVerificationStatus.valueOf(verificationStatus) }.getOrDefault(RecyclerVerificationStatus.VERIFIED),
    profileId = profileId.ifBlank { id },
    businessName = profile?.name,
    phoneNumber = phone ?: profile?.contact?.phone.orEmpty(),
    displayName = displayName ?: profile?.name,
    areaName = areaName ?: profile?.facilityLocation?.areaName,
    latitude = latitude,
    longitude = longitude
)

private fun AccountRole.wireName(): String = if (this == AccountRole.RECYCLER) "RECYCLER" else "COLLECTOR"
