package com.irinteractivestudios.kabadiwalaconnect.data.auth

import android.util.Log
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
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RefreshTokenRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.AccountDeletionRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.data.auth.saveAccount
import java.io.IOException
import com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Real collector authentication against the versioned backend API. */
class RemoteAuthenticationRepository(
    private val api: ApiService,
    private val session: SessionRepository,
    private val storage: SecureStorage? = null
) : AuthenticationRepository {

    // Access-token expiry can make several in-flight requests enter OkHttp's
    // authenticator at once. Refresh tokens are single-use, so serialize the
    // rotation and let waiters reuse the newly saved access token instead of
    // sending the same refresh token a second time.
    private val refreshMutex = Mutex()

    override suspend fun requestOtp(phoneNumber: String): OtpChallenge {
        api.requestOtp(OtpRequestDto(phoneNumber)).requireData()
        val now = System.currentTimeMillis()
        return OtpChallenge(
            phoneNumber,
            now + OTP_TTL_MS,
            now + RESEND_COOLDOWN_MS,
            // SMS delivery is intentionally disabled for local debug builds.
            // Never expose the development code in a release APK: the hint is
            // rendered by the sign-in UI and would bypass the OTP channel.
            developmentCodeHint = DEVELOPMENT_OTP_CODE.takeIf { BuildConfig.DEBUG }
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
            session.save(auth.token, expiry, auth.refreshToken)
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
                // Login identifies the role from the account record. Sending
                // the onboarding default here could mislabel a Household or
                // Recycler account before the server has resolved it.
                role = request.role.wireName().takeUnless { request.isReturning },
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
            session.save(result.token, expiry, result.refreshToken)
            val profile = result.user.toDomain()
            storage?.saveAccount(profile)
            EmailAuthentication.Success(result.token, expiry, profile)
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) {
                val remote = error as? RemoteApiException
                Log.w(TAG, "Email authentication failed: type=${error::class.java.simpleName}, code=${remote?.code ?: "IO_OR_PARSE"}, http=${remote?.httpCode ?: "-"}")
            }
            when {
                error is IOException -> EmailAuthentication.NetworkError
                errorCode(error) == "EMAIL_IN_USE" -> EmailAuthentication.EmailInUse
                errorCode(error) == "INVALID_CREDENTIALS" || error is RemoteApiException && error.httpCode == 401 -> EmailAuthentication.InvalidCredentials
                else -> EmailAuthentication.NetworkError
            }
        }
    }

    override suspend fun authenticateAdmin(email: String, password: String): EmailAuthentication {
        if (!EmailValidator.isValid(email) || password.length < 8) return EmailAuthentication.InvalidInput
        return try {
            val result = api.adminLogin(EmailAuthRequestDto(email = email.trim(), password = password)).requireData()
            val expiry = jwtExpiry(result.token) ?: (System.currentTimeMillis() + SESSION_FALLBACK_MS)
            session.save(result.token, expiry, result.refreshToken)
            val profile = AccountProfile(
                id = result.user.id,
                email = result.user.email,
                role = AccountRole.ADMIN,
                preferredLanguage = "en",
                accountStatus = "ACTIVE",
                verificationStatus = RecyclerVerificationStatus.VERIFIED,
                profileId = result.user.id,
                displayName = result.user.displayName,
                permissions = result.user.permissions.toSet()
            )
            storage?.saveAccount(profile)
            EmailAuthentication.Success(result.token, expiry, profile)
        } catch (error: Exception) {
            when {
                error is IOException -> EmailAuthentication.NetworkError
                errorCode(error) == "INVALID_CREDENTIALS" || error is RemoteApiException && error.httpCode == 401 -> EmailAuthentication.InvalidCredentials
                else -> EmailAuthentication.NetworkError
            }
        }
    }

    override suspend fun refreshAccessToken(): String? = refreshMutex.withLock {
        val current = storage?.get(SecureStorage.AUTH_TOKEN)
        // A concurrent caller may already have completed the rotation while
        // this caller was waiting for the mutex. Reuse that token; rotating
        // the refresh credential again would correctly be rejected by the
        // backend as token reuse.
        if (session.isSessionValid() && !current.isNullOrBlank()) return@withLock current

        runCatching {
            val refreshToken = storage?.get(SecureStorage.REFRESH_TOKEN) ?: return@runCatching null
            val refreshed = api.refreshSession(RefreshTokenRequestDto(refreshToken)).requireData()
            val expiry = jwtExpiry(refreshed.token) ?: (System.currentTimeMillis() + SESSION_FALLBACK_MS)
            session.save(refreshed.token, expiry, refreshed.refreshToken)
            refreshed.token
        }.getOrElse {
            // A rejected rotating token is not recoverable. Clear it so
            // repeated requests cannot create a refresh loop with stale
            // credentials.
            if (it is RemoteApiException && it.httpCode == 401) session.clear()
            null
        }
    }

    override suspend fun refreshAccount(): AccountProfile? = runCatching {
        if (!session.isSessionValid() && refreshAccessToken() == null) return@runCatching null
        // Admin tokens are issued by /auth/admin-login and intentionally do
        // not use the role-profile endpoint, which is reserved for the three
        // marketplace account roles. Keep the server-issued operator profile
        // from encrypted storage while the rotating session is refreshed.
        storage?.readAccount()?.takeIf { it.role == AccountRole.ADMIN }?.let { return@runCatching it }
        val remote = api.getAccountProfile().requireData().toDomain()
        // Role is an authorization result owned by the server. Never preserve
        // or synthesize a local presentation role across refreshes.
        storage?.saveAccount(remote)
        remote
    }.getOrNull()

    override suspend fun exportAccount(): JsonObject = api.exportAccount().requireData()

    override suspend fun deleteAccount(): Boolean {
        val result = api.deleteAccount(AccountDeletionRequestDto()).requireData()
        if (result.deleted) {
            session.clear()
            clearCachedAccount()
        }
        return result.deleted
    }

    override fun isSessionValid() = session.isSessionValid()

    override fun logout() {
        storage?.get(SecureStorage.REFRESH_TOKEN)?.let { refreshToken ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { api.logout(RefreshTokenRequestDto(refreshToken)) }
            }
        }
        session.clear()
    }

    private fun clearCachedAccount() {
        storage?.let {
            listOf(
                SecureStorage.ACCOUNT_EMAIL, SecureStorage.ACCOUNT_PHONE,
                SecureStorage.ACCOUNT_DISPLAY_NAME, SecureStorage.ACCOUNT_AREA_NAME,
                SecureStorage.ACCOUNT_ROLE, SecureStorage.ACCOUNT_VERIFICATION_STATUS,
                SecureStorage.ACCOUNT_LANGUAGE, SecureStorage.ACCOUNT_PROFILE_ID,
                SecureStorage.ACCOUNT_LATITUDE, SecureStorage.ACCOUNT_LONGITUDE,
                SecureStorage.ACCOUNT_PERMISSIONS, SecureStorage.COLLECTOR_ID,
                SecureStorage.SYNC_CURSOR, SecureStorage.ACTIVITY_CURSOR
            ).forEach(it::remove)
        }
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
        private const val TAG = "KcAuthentication"
        private const val OTP_TTL_MS = 10 * 60 * 1000L
        private const val RESEND_COOLDOWN_MS = 30 * 1000L
        private const val SESSION_FALLBACK_MS = 30L * 24L * 60L * 60L * 1000L
        private const val DEVELOPMENT_OTP_CODE = "123456"
    }
}

private fun com.irinteractivestudios.kabadiwalaconnect.data.remote.AccountProfileDto.toDomain() = AccountProfile(
    id = id,
    email = email.orEmpty(),
    role = when (role) { "RECYCLER" -> AccountRole.RECYCLER; "HOUSEHOLD" -> AccountRole.HOUSEHOLD; "ADMIN" -> AccountRole.ADMIN; else -> AccountRole.COLLECTOR },
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

private fun AccountRole.wireName(): String = when (this) { AccountRole.RECYCLER -> "RECYCLER"; AccountRole.HOUSEHOLD -> "HOUSEHOLD"; AccountRole.COLLECTOR -> "COLLECTOR"; AccountRole.ADMIN -> "ADMIN" }
