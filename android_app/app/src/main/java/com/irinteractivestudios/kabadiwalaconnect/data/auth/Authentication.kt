package com.irinteractivestudios.kabadiwalaconnect.data.auth

import com.irinteractivestudios.kabadiwalaconnect.data.local.CollectorProfileDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.CollectorProfileEntity
import com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class OtpChallenge(
    val phoneNumber: String,
    val expiresAtEpochMs: Long,
    val resendAtEpochMs: Long,
    val developmentCodeHint: String? = null
)

data class AuthenticatedCollector(
    val collectorId: String,
    val token: String,
    val expiresAtEpochMs: Long
)

data class EmailAccountRequest(
    val email: String,
    val password: String,
    val role: AccountRole,
    val preferredLanguage: String,
    val areaName: String = "",
    val businessName: String = "",
    val authorizationNumber: String = "",
    val materialsAccepted: List<String> = emptyList(),
    val pickupAvailable: Boolean = false,
    val serviceRadiusKm: Int = 25,
    val isReturning: Boolean = false
)

data class PhoneAccountRequest(
    val phoneNumber: String,
    val role: AccountRole,
    val preferredLanguage: String,
    val areaName: String = "",
    val displayName: String = "",
    val email: String = "",
    val businessName: String = "",
    val authorizationNumber: String = "",
    val materialsAccepted: List<String> = emptyList(),
    val pickupAvailable: Boolean = false,
    val serviceRadiusKm: Int = 25,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class AccountProfileUpdate(
    val displayName: String?,
    val email: String?,
    val areaName: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val preferredLanguage: String? = null
)

sealed interface EmailAuthentication {
    data class Success(val token: String, val expiresAtEpochMs: Long, val profile: AccountProfile) : EmailAuthentication
    data object InvalidCredentials : EmailAuthentication
    data object EmailInUse : EmailAuthentication
    data object NetworkError : EmailAuthentication
    data object InvalidInput : EmailAuthentication
}

object EmailValidator {
    private val pattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    fun isValid(value: String): Boolean = pattern.matches(value.trim())
}

object IndianPhoneValidator {
    private val pattern = Regex("^[6-9]\\d{9}$")

    /** Return the canonical ten-digit Indian mobile number used by the API. */
    fun normalize(value: String): String {
        val digits = value.filter { it in '0'..'9' }
        return when {
            digits.length == 14 && digits.startsWith("0091") -> digits.drop(4)
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith('0') -> digits.drop(1)
            else -> digits
        }
    }

    fun isValid(value: String): Boolean = pattern.matches(normalize(value))
}

interface OtpService {
    suspend fun send(phoneNumber: String, nowEpochMs: Long = System.currentTimeMillis()): OtpChallenge
    suspend fun verify(phoneNumber: String, code: String, nowEpochMs: Long = System.currentTimeMillis()): OtpVerification
}

sealed interface OtpVerification {
    data class Success(val token: String, val expiresAtEpochMs: Long, val collectorId: String = "", val profile: AccountProfile? = null) : OtpVerification
    data object Incorrect : OtpVerification
    data object Expired : OtpVerification
    data object AttemptsExceeded : OtpVerification
    data object AccountConflict : OtpVerification
    data object ServerError : OtpVerification
    data object NetworkError : OtpVerification
}

/** Development-only OTP boundary used only by the offline debug configuration. */
class MockOtpService(
    private val code: String = "123456",
    private val ttlMs: Long = 60_000L,
    private val resendCooldownMs: Long = 30_000L,
    private val maxAttempts: Int = 3
) : OtpService {
    private data class Pending(val phone: String, val expiresAt: Long, val resendAt: Long, var attempts: Int)
    private var pending: Pending? = null

    override suspend fun send(phoneNumber: String, nowEpochMs: Long): OtpChallenge {
        val current = pending
        if (current != null && current.phone == phoneNumber && nowEpochMs < current.resendAt) {
            return OtpChallenge(phoneNumber, current.expiresAt, current.resendAt, code)
        }
        val next = Pending(phoneNumber, nowEpochMs + ttlMs, nowEpochMs + resendCooldownMs, 0)
        pending = next
        return OtpChallenge(phoneNumber, next.expiresAt, next.resendAt, code)
    }

    override suspend fun verify(phoneNumber: String, code: String, nowEpochMs: Long): OtpVerification {
        val current = pending ?: return OtpVerification.Expired
        if (current.phone != phoneNumber || nowEpochMs >= current.expiresAt) return OtpVerification.Expired
        if (current.attempts >= maxAttempts) return OtpVerification.AttemptsExceeded
        current.attempts++
        return if (code == this.code) {
            pending = null
            OtpVerification.Success("mock-${UUID.randomUUID()}", nowEpochMs + 30L * 24L * 60L * 60L * 1000L)
        } else OtpVerification.Incorrect
    }
}

interface SessionRepository {
    fun isSessionValid(nowEpochMs: Long = System.currentTimeMillis()): Boolean
    fun save(token: String, expiresAtEpochMs: Long, refreshToken: String? = null)
    fun clear()
}

class SecureSessionRepository(
    private val storage: SecureStorage,
    /**
     * Real backend access tokens are JWTs. The debug placeholder backend uses
     * opaque mock tokens, so the app injects that exception only for the
     * placeholder environment instead of weakening production validation.
     */
    private val tokenValidator: (String) -> Boolean = { true }
) : SessionRepository {
    override fun isSessionValid(nowEpochMs: Long): Boolean {
        val token = storage.get(SecureStorage.AUTH_TOKEN)
        val expiry = storage.get(SecureStorage.SESSION_EXPIRY)?.toLongOrNull()
        return !token.isNullOrBlank() && tokenValidator(token) && expiry != null && expiry > nowEpochMs
    }

    override fun save(token: String, expiresAtEpochMs: Long, refreshToken: String?) {
        storage.put(SecureStorage.AUTH_TOKEN, token)
        storage.put(SecureStorage.SESSION_EXPIRY, expiresAtEpochMs.toString())
        if (!refreshToken.isNullOrBlank()) storage.put(SecureStorage.REFRESH_TOKEN, refreshToken)
        else storage.remove(SecureStorage.REFRESH_TOKEN)
    }

    override fun clear() {
        storage.remove(SecureStorage.AUTH_TOKEN)
        storage.remove(SecureStorage.REFRESH_TOKEN)
        storage.remove(SecureStorage.SESSION_EXPIRY)
    }
}

interface CollectorProfileRepository {
    fun observe(): Flow<com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile?>
    suspend fun save(profile: com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile)
    suspend fun clear()
}

class RoomCollectorProfileRepository(private val dao: CollectorProfileDao) : CollectorProfileRepository {
    override fun observe(): Flow<com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile?> =
        dao.observe().map { entity -> entity?.let { it.toDomain() } }

    override suspend fun save(profile: com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile) {
        dao.save(
            CollectorProfileEntity(
                collectorId = profile.id,
                phoneNumber = profile.phoneNumber,
                preferredLanguage = profile.preferredLanguage,
                primaryLocation = profile.primaryLocation,
                locationSource = profile.locationSource,
                createdAtEpochMs = profile.createdAtEpochMs,
                lastLoginEpochMs = profile.lastLoginEpochMs,
                latitude = profile.latitude,
                longitude = profile.longitude
            )
        )
    }

    override suspend fun clear() = dao.clear()
}

private fun CollectorProfileEntity.toDomain() = com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile(
    id = collectorId,
    phoneNumber = phoneNumber,
    preferredLanguage = preferredLanguage,
    primaryLocation = primaryLocation,
    locationSource = locationSource,
    createdAtEpochMs = createdAtEpochMs,
    lastLoginEpochMs = lastLoginEpochMs,
    latitude = latitude,
    longitude = longitude
)

interface AuthenticationRepository {
    suspend fun requestOtp(phoneNumber: String): OtpChallenge
    suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification
    suspend fun verifyOtp(phoneNumber: String, code: String, account: PhoneAccountRequest): OtpVerification = verifyOtp(phoneNumber, code)
    /** Persists language/location remotely when a real backend is configured. */
    suspend fun updateProfile(profile: com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile) = Unit
    suspend fun authenticateEmail(request: EmailAccountRequest): EmailAuthentication = EmailAuthentication.NetworkError
    suspend fun authenticateAdmin(email: String, password: String): EmailAuthentication = EmailAuthentication.NetworkError
    suspend fun refreshAccount(): AccountProfile? = null
    suspend fun updateAccountProfile(update: AccountProfileUpdate): AccountProfile? = null
    /** Returns the authenticated account export without exposing credentials. */
    suspend fun exportAccount(): JsonObject? = null
    /** Performs the server-side privacy deletion and returns whether it completed. */
    suspend fun deleteAccount(): Boolean = false
    /** Refreshes credentials without loading profile data; used by the HTTP 401 authenticator. */
    suspend fun refreshAccessToken(): String? = null
    fun isSessionValid(): Boolean
    fun logout()
}

class MockAuthenticationRepository(
    private val otpService: OtpService,
    private val session: SessionRepository,
    private val secureStorage: SecureStorage? = null
) : AuthenticationRepository {
    override suspend fun requestOtp(phoneNumber: String) = otpService.send(phoneNumber)
    override suspend fun verifyOtp(phoneNumber: String, code: String): OtpVerification {
        val result = otpService.verify(phoneNumber, code)
        if (result is OtpVerification.Success) session.save(result.token, result.expiresAtEpochMs)
        return result
    }

    override suspend fun verifyOtp(phoneNumber: String, code: String, account: PhoneAccountRequest): OtpVerification {
        val result = otpService.verify(phoneNumber, code)
        if (result !is OtpVerification.Success) return result
        session.save(result.token, result.expiresAtEpochMs)
        val id = "mock-phone-${UUID.randomUUID()}"
        val profile = AccountProfile(
            id = id,
            email = account.email.trim().lowercase(),
            role = account.role,
            preferredLanguage = account.preferredLanguage,
            verificationStatus = if (account.role == AccountRole.RECYCLER) RecyclerVerificationStatus.PENDING else RecyclerVerificationStatus.VERIFIED,
            profileId = id,
            businessName = account.businessName.ifBlank { null },
            phoneNumber = account.phoneNumber,
            displayName = account.displayName.ifBlank { account.businessName.ifBlank { null } },
            areaName = account.areaName.ifBlank { null }
        )
        secureStorage?.saveAccount(profile)
        return result.copy(collectorId = profile.profileId, profile = profile)
    }
    override suspend fun authenticateEmail(request: EmailAccountRequest): EmailAuthentication {
        if (!EmailValidator.isValid(request.email) || request.password.length < 8) return EmailAuthentication.InvalidInput
        val id = "mock-${UUID.randomUUID()}"
        val profile = AccountProfile(
            id = id,
            email = request.email.trim().lowercase(),
            role = request.role,
            preferredLanguage = request.preferredLanguage,
            verificationStatus = if (request.role == AccountRole.RECYCLER) RecyclerVerificationStatus.PENDING else RecyclerVerificationStatus.VERIFIED,
            profileId = id,
            businessName = request.businessName.ifBlank { null }
        )
        val expiry = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L
        session.save("mock-email-${UUID.randomUUID()}", expiry)
        secureStorage?.saveAccount(profile)
        return EmailAuthentication.Success("mock-email", expiry, profile)
    }
    override suspend fun refreshAccount(): AccountProfile? = secureStorage?.readAccount()
    override suspend fun exportAccount(): JsonObject? = null
    override suspend fun deleteAccount(): Boolean = false
    override fun isSessionValid() = session.isSessionValid()
    override fun logout() = session.clear()
}

fun SecureStorage.saveAccount(profile: AccountProfile) {
    put(SecureStorage.ACCOUNT_EMAIL, profile.email)
    put(SecureStorage.ACCOUNT_PHONE, profile.phoneNumber)
    put(SecureStorage.ACCOUNT_DISPLAY_NAME, profile.displayName ?: profile.businessName.orEmpty())
    put(SecureStorage.ACCOUNT_AREA_NAME, profile.areaName.orEmpty())
    put(SecureStorage.ACCOUNT_ROLE, profile.role.name)
    put(SecureStorage.ACCOUNT_VERIFICATION_STATUS, profile.verificationStatus.name)
    put(SecureStorage.ACCOUNT_LANGUAGE, profile.preferredLanguage)
    put(SecureStorage.ACCOUNT_PROFILE_ID, profile.profileId)
    put(SecureStorage.ACCOUNT_PERMISSIONS, profile.permissions.joinToString(","))
    profile.latitude?.let { put(SecureStorage.ACCOUNT_LATITUDE, it.toString()) } ?: remove(SecureStorage.ACCOUNT_LATITUDE)
    profile.longitude?.let { put(SecureStorage.ACCOUNT_LONGITUDE, it.toString()) } ?: remove(SecureStorage.ACCOUNT_LONGITUDE)
}

fun SecureStorage.readAccount(): AccountProfile? {
    val role = get(SecureStorage.ACCOUNT_ROLE)?.let { runCatching { AccountRole.valueOf(it) }.getOrNull() } ?: return null
    val profileId = get(SecureStorage.ACCOUNT_PROFILE_ID) ?: return null
    val status = get(SecureStorage.ACCOUNT_VERIFICATION_STATUS)?.let { runCatching { RecyclerVerificationStatus.valueOf(it) }.getOrNull() } ?: RecyclerVerificationStatus.VERIFIED
    return AccountProfile(
        id = profileId,
        email = get(SecureStorage.ACCOUNT_EMAIL).orEmpty(),
        role = role,
        preferredLanguage = get(SecureStorage.ACCOUNT_LANGUAGE) ?: "en",
        verificationStatus = status,
        profileId = profileId,
        phoneNumber = get(SecureStorage.ACCOUNT_PHONE).orEmpty(),
        displayName = get(SecureStorage.ACCOUNT_DISPLAY_NAME)?.takeIf { it.isNotBlank() },
        areaName = get(SecureStorage.ACCOUNT_AREA_NAME)?.takeIf { it.isNotBlank() },
        latitude = get(SecureStorage.ACCOUNT_LATITUDE)?.toDoubleOrNull(),
        longitude = get(SecureStorage.ACCOUNT_LONGITUDE)?.toDoubleOrNull(),
        permissions = get(SecureStorage.ACCOUNT_PERMISSIONS).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    )
}
