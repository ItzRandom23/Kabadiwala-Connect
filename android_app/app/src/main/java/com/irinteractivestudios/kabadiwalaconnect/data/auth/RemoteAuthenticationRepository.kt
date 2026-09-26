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
import com.irinteractivestudios.kabadiwalaconnect.data.remote.AccountProfileUpdateRequestDto
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
                    address = account?.address.trimmedOrNull(),
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
                    areaName = collector.primaryLocation?.areaName,
                    address = collector.address
                )
            }
            // A token without a server-issued identity cannot safely choose a
            // role. Keep the previous session untouched if the response is
            // incomplete, so the UI never guesses from the onboarding form.
            if (profile == null) return OtpVerification.ServerError
            val expiry = jwtExpiry(auth.token) ?: (System.currentTimeMillis() + SESSION_FALLBACK_MS)
            session.save(auth.token, expiry, auth.refreshToken)
            storage?.saveAccount(profile)
            OtpVerification.Success(auth.token, expiry, profile.profileId, profile)
        } catch (error: Exception) {
            if ((error as? RemoteApiException)?.detailsCode == "ROLE_REQUIRED") return OtpVerification.RoleRequired
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
                address = request.address.trim().takeIf { it.isNotEmpty() },
                latitude = request.latitude,
                longitude = request.longitude,
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

    override suspend fun refreshAccessToken(force: Boolean, failedAccessToken: String?): String? = refreshMutex.withLock {
        val current = storage?.get(SecureStorage.AUTH_TOKEN)
        // A concurrent caller may already have completed the rotation while
        // this caller was waiting for the mutex. Compare against the access
        // token rejected by the server, even on a forced refresh. Rotating
        // twice can cause refresh-token reuse and revoke the whole family.
        if (!failedAccessToken.isNullOrBlank() && !current.isNullOrBlank() && current != failedAccessToken) return@withLock current
        if (!force && session.isSessionValid() && !current.isNullOrBlank()) return@withLock current

        val attemptedRefreshToken = storage?.get(SecureStorage.REFRESH_TOKEN)
        if (attemptedRefreshToken.isNullOrBlank()) {
            if (force && (failedAccessToken.isNullOrBlank() || current == failedAccessToken) && storage?.get(SecureStorage.REFRESH_TOKEN).isNullOrBlank()) session.clear()
            return@withLock null
        }
        runCatching {
            val refreshed = api.refreshSession(RefreshTokenRequestDto(attemptedRefreshToken)).requireData()
            val expiry = jwtExpiry(refreshed.token) ?: (System.currentTimeMillis() + SESSION_FALLBACK_MS)
            session.save(refreshed.token, expiry, refreshed.refreshToken)
            refreshed.token
        }.getOrElse {
            // A rejected rotating token is not recoverable. Clear it so
            // repeated requests cannot create a refresh loop with stale
            // credentials.
            val remote = it as? RemoteApiException
            Log.w(TAG, "Session refresh failed: type=${it::class.java.simpleName}, code=${remote?.code ?: "IO_OR_PARSE"}, http=${remote?.httpCode ?: "-"}")
            // Another login or refresh may have replaced the credential while
            // this request was in flight. Never erase that newer session.
            if (it is RemoteApiException && it.httpCode == 401 && storage.get(SecureStorage.REFRESH_TOKEN) == attemptedRefreshToken) session.clear()
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
    }.onFailure {
        val remote = it as? RemoteApiException
        Log.w(TAG, "Account restore failed: type=${it::class.java.simpleName}, code=${remote?.code ?: "IO_OR_PARSE"}, http=${remote?.httpCode ?: "-"}")
    }.getOrNull()

    override suspend fun updateAccountProfile(update: AccountProfileUpdate): AccountProfile {
        val result = api.updateAccountProfile(
            AccountProfileUpdateRequestDto(
                displayName = update.displayName?.trim()?.takeIf { it.isNotEmpty() },
                email = update.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                areaName = update.areaName?.trim()?.takeIf { it.isNotEmpty() },
                address = update.address?.trim(),
                latitude = update.latitude,
                longitude = update.longitude,
                preferredLanguage = update.preferredLanguage?.let(LocaleManager::toBackendName)
            )
        ).requireData().toDomain()
        storage?.saveAccount(result)
        return result
    }

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
                SecureStorage.ACCOUNT_ADDRESS,
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
        // The transport already parsed the structured API error. Falling back
        // to parsing the human-readable message made ACCOUNT_CONFLICT look
        // like a generic server failure and trapped returning phone users in
        // the registration flow.
        if (error.code.isNotBlank()) return error.code
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
        private const val RESEND_COOLDOWN_MS = 2 * 60 * 1000L
        private const val SESSION_FALLBACK_MS = 30L * 24L * 60L * 60L * 1000L
        private const val DEVELOPMENT_OTP_CODE = "123456"
    }
}

private fun com.irinteractivestudios.kabadiwalaconnect.data.remote.AccountProfileDto.toDomain() = AccountProfile(
    id = id,
    email = email.orEmpty(),
    role = when (role) {
        "RECYCLER" -> AccountRole.RECYCLER
        "HOUSEHOLD" -> AccountRole.HOUSEHOLD
        "COLLECTOR" -> AccountRole.COLLECTOR
        "ADMIN" -> AccountRole.ADMIN
        else -> throw IllegalArgumentException("Unrecognized account role")
    },
    preferredLanguage = LocaleManager.fromBackendName(preferredLanguage),
    accountStatus = accountStatus,
    verificationStatus = runCatching { RecyclerVerificationStatus.valueOf(verificationStatus) }.getOrDefault(RecyclerVerificationStatus.VERIFIED),
    profileId = profileId.ifBlank { id },
    businessName = profile?.name,
    phoneNumber = phone ?: profile?.contact?.phone.orEmpty(),
    displayName = displayName ?: profile?.name,
    areaName = areaName ?: profile?.facilityLocation?.areaName,
    address = address,
    latitude = latitude,
    longitude = longitude
)

private fun AccountRole.wireName(): String = when (this) { AccountRole.RECYCLER -> "RECYCLER"; AccountRole.HOUSEHOLD -> "HOUSEHOLD"; AccountRole.COLLECTOR -> "COLLECTOR"; AccountRole.ADMIN -> "ADMIN" }
