package com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.auth.AuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.CollectorProfileRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.EmailAccountRequest
import com.irinteractivestudios.kabadiwalaconnect.data.auth.EmailAuthentication
import com.irinteractivestudios.kabadiwalaconnect.data.auth.EmailValidator
import com.irinteractivestudios.kabadiwalaconnect.data.auth.IndianPhoneValidator
import com.irinteractivestudios.kabadiwalaconnect.data.auth.OtpChallenge
import com.irinteractivestudios.kabadiwalaconnect.data.auth.OtpVerification
import com.irinteractivestudios.kabadiwalaconnect.data.auth.PhoneAccountRequest
import com.irinteractivestudios.kabadiwalaconnect.data.auth.saveAccount
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.CollectorProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.LocationProvider
import com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class OnboardingStep { WELCOME, EMAIL, ROLE, LANGUAGE, RECYCLER_DETAILS, PHONE, OTP, LOCATION_PERMISSION, AREA, COMPLETE }
enum class LocationChoice { GPS, MANUAL }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val email: String = "",
    val password: String = "",
    val returningUser: Boolean = false,
    val role: AccountRole = AccountRole.COLLECTOR,
    val displayName: String = "",
    val businessName: String = "",
    val authorizationNumber: String = "",
    val materialsAccepted: Set<String> = emptySet(),
    val pickupAvailable: Boolean = false,
    val serviceRadiusKm: Int = 25,
    val phone: String = "",
    val otp: String = "",
    val language: String = LocaleManager.ENGLISH,
    val area: String = "",
    val locationChoice: LocationChoice = LocationChoice.MANUAL,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationError: Boolean = false,
    val isLocationBusy: Boolean = false,
    val challenge: OtpChallenge? = null,
    val otpError: OtpError? = null,
    val emailError: Boolean = false,
    val displayNameError: Boolean = false,
    val passwordError: Boolean = false,
    val phoneError: Boolean = false,
    val authError: Boolean = false,
    val isBusy: Boolean = false,
    val completed: Boolean = false
)

enum class OtpError { INCORRECT, EXPIRED, ATTEMPTS_EXCEEDED, ACCOUNT_CONFLICT, SERVER, NETWORK }

class OnboardingViewModel(
    private val auth: AuthenticationRepository,
    private val profiles: CollectorProfileRepository,
    private val secureStorage: SecureStorage? = null,
    initialLanguage: String = LocaleManager.ENGLISH,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val locationProvider: LocationProvider? = null
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnboardingState(language = LocaleManager.normalizeTag(initialLanguage))
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    private var authenticatedCollectorId: String? = null

    fun start() { _state.value = _state.value.copy(step = if (_state.value.returningUser) OnboardingStep.PHONE else OnboardingStep.ROLE) }
    fun useEmailSignIn() {
        _state.value = _state.value.copy(
            returningUser = true,
            step = OnboardingStep.EMAIL,
            authError = false,
            phoneError = false
        )
    }
    fun useAdminSignIn() {
        _state.value = _state.value.copy(
            returningUser = true,
            role = AccountRole.ADMIN,
            step = OnboardingStep.EMAIL,
            authError = false,
            phoneError = false
        )
    }
    fun toggleReturning() { _state.value = _state.value.copy(returningUser = !_state.value.returningUser, authError = false) }
    fun goBack() {
        val current = _state.value
        val previous = when (current.step) {
            OnboardingStep.EMAIL -> OnboardingStep.WELCOME
            OnboardingStep.ROLE -> OnboardingStep.WELCOME
            OnboardingStep.LANGUAGE -> OnboardingStep.ROLE
            OnboardingStep.RECYCLER_DETAILS -> OnboardingStep.ROLE
            OnboardingStep.LOCATION_PERMISSION -> if (current.role == AccountRole.RECYCLER) OnboardingStep.RECYCLER_DETAILS else OnboardingStep.ROLE
            OnboardingStep.AREA -> OnboardingStep.LOCATION_PERMISSION
            OnboardingStep.PHONE -> if (current.returningUser) OnboardingStep.WELCOME else OnboardingStep.AREA
            OnboardingStep.OTP -> OnboardingStep.PHONE
            OnboardingStep.WELCOME, OnboardingStep.COMPLETE -> current.step
        }
        _state.value = current.copy(
            step = previous,
            otp = if (previous == OnboardingStep.PHONE) "" else current.otp,
            challenge = if (previous == OnboardingStep.PHONE) null else current.challenge,
            otpError = null,
            phoneError = false,
            authError = false,
            isBusy = false
        )
    }
    fun startOver() {
        val language = _state.value.language
        authenticatedCollectorId = null
        _state.value = OnboardingState(language = language)
    }
    fun setEmail(value: String) { _state.value = _state.value.copy(email = value.trim(), emailError = false, authError = false) }
    fun setPassword(value: String) { _state.value = _state.value.copy(password = value, passwordError = false, authError = false) }
    fun continueEmail() {
        val current = _state.value
        val validEmail = EmailValidator.isValid(current.email)
        val validPassword = current.password.length >= 8
        _state.value = current.copy(emailError = !validEmail, passwordError = !validPassword)
        if (validEmail && validPassword) _state.value = _state.value.copy(step = if (current.returningUser) OnboardingStep.PHONE else OnboardingStep.ROLE)
    }
    fun signIn() {
        val current = _state.value
        if (!EmailValidator.isValid(current.email) || current.password.length < 8) { continueEmail(); return }
        viewModelScope.launch {
            _state.value = current.copy(isBusy = true, authError = false)
            when (val result = if (current.role == AccountRole.ADMIN) auth.authenticateAdmin(current.email, current.password) else auth.authenticateEmail(EmailAccountRequest(current.email, current.password, AccountRole.COLLECTOR, LocaleManager.ENGLISH, isReturning = true))) {
                is EmailAuthentication.Success -> { secureStorage?.saveAccount(result.profile); saveCollectorCacheIfNeeded(result.profile.profileId, current, result.profile.role); _state.value = current.copy(step = OnboardingStep.COMPLETE, completed = true, isBusy = false) }
                else -> _state.value = current.copy(isBusy = false, authError = true)
            }
        }
    }
    // The language is selected once on the first-run screen and persisted by
    // MainActivity. Registration reuses that choice instead of asking again.
    fun selectRole(role: AccountRole) {
        val next = if (role == AccountRole.RECYCLER) OnboardingStep.RECYCLER_DETAILS else OnboardingStep.LOCATION_PERMISSION
        _state.value = _state.value.copy(role = role, step = next)
    }
    fun setDisplayName(value: String) { _state.value = _state.value.copy(displayName = value, displayNameError = false) }
    fun setBusinessName(value: String) { _state.value = _state.value.copy(businessName = value) }
    fun setAuthorizationNumber(value: String) { _state.value = _state.value.copy(authorizationNumber = value) }
    fun toggleMaterial(value: String) { _state.value = _state.value.copy(materialsAccepted = _state.value.materialsAccepted.toMutableSet().also { if (!it.add(value)) it.remove(value) }) }
    fun setPickupAvailable(value: Boolean) { _state.value = _state.value.copy(pickupAvailable = value) }
    fun setServiceRadius(value: Int) { _state.value = _state.value.copy(serviceRadiusKm = value) }
    fun continueRecyclerDetails() {
        val current = _state.value
        if (!current.hasRequiredRecyclerDetails()) return
        val validEmail = current.email.isBlank() || EmailValidator.isValid(current.email)
        _state.value = current.copy(emailError = !validEmail)
        if (validEmail) _state.value = _state.value.copy(step = OnboardingStep.LOCATION_PERMISSION)
    }

    // Legacy phone OTP boundary remains available for older backend/dev flows.
    fun setPhone(value: String) {
        _state.value = _state.value.copy(
            // Keep the field itself strict: exactly the value the user can
            // submit is stored, with no punctuation, spaces, or country code.
            phone = value.filter(Char::isDigit).take(10),
            phoneError = false,
            authError = false
        )
    }
    fun requestOtp() {
        val phone = IndianPhoneValidator.normalize(_state.value.phone)
        _state.value = _state.value.copy(phone = phone)
        if (phone.length != 10 || !IndianPhoneValidator.isValid(phone)) { _state.value = _state.value.copy(phoneError = true); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, phoneError = false)
            try {
                val challenge = auth.requestOtp(phone)
                _state.value = _state.value.copy(
                    step = OnboardingStep.OTP,
                    challenge = challenge,
                    otp = challenge.developmentCodeHint.orEmpty(),
                    isBusy = false,
                    authError = false
                )
            } catch (_: Exception) { _state.value = _state.value.copy(isBusy = false, authError = true) }
        }
    }
    fun setOtp(value: String) { _state.value = _state.value.copy(otp = value.filter(Char::isDigit).take(6), otpError = null) }
    fun verifyOtp() {
        val current = _state.value
        if (current.otp.length != 6) return
        val useLegacyPhoneOnlyVerification = current.isPhoneOnlyVerification()
        if (!useLegacyPhoneOnlyVerification && !current.hasRequiredRegistrationFields()) return
        if (current.challenge == null) {
            _state.value = current.copy(otpError = OtpError.EXPIRED)
            return
        }
        val email = current.email.trim()
        val area = current.area.trim()
        val displayName = current.displayName.trim()
        val businessName = current.businessName.trim()
        val authorizationNumber = current.authorizationNumber.trim()
        val materialsAccepted = current.materialsAccepted.map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            _state.value = current.copy(isBusy = true)
            val registrationResult = try {
                if (useLegacyPhoneOnlyVerification) {
                    auth.verifyOtp(current.phone, current.otp)
                } else {
                    auth.verifyOtp(
                        current.phone,
                        current.otp,
                        PhoneAccountRequest(
                            phoneNumber = current.phone,
                            role = current.role,
                            preferredLanguage = current.language,
                            areaName = area,
                            displayName = displayName,
                            email = email,
                            businessName = businessName,
                            authorizationNumber = authorizationNumber,
                            materialsAccepted = materialsAccepted,
                            pickupAvailable = current.pickupAvailable,
                            serviceRadiusKm = current.serviceRadiusKm,
                            latitude = current.latitude,
                            longitude = current.longitude
                        )
                    )
                }
            } catch (_: Exception) { OtpVerification.NetworkError }
            // Older test deployments can have a phone profile without its
            // matching account identity. A verified phone is enough to safely
            // retry through the existing-phone sign-in path; this avoids
            // trapping a user on an erroneous duplicate-account message.
            val result = if (registrationResult == OtpVerification.AccountConflict && current.role == AccountRole.COLLECTOR) {
                try {
                    auth.verifyOtp(current.phone, current.otp)
                } catch (_: Exception) {
                    OtpVerification.NetworkError
                }
            } else {
                registrationResult
            }
            _state.value = when (result) {
                is OtpVerification.Success -> {
                    val profileId = result.collectorId.takeIf { it.isNotBlank() }
                        ?: result.profile?.profileId?.takeIf { it.isNotBlank() }
                        ?: "KC-${UUID.randomUUID().toString().take(8).uppercase()}"
                    authenticatedCollectorId = profileId
                    val profile = result.profile ?: AccountProfile(
                        id = profileId,
                        email = current.email,
                        role = current.role,
                        preferredLanguage = current.language,
                        verificationStatus = if (current.role == AccountRole.RECYCLER) RecyclerVerificationStatus.PENDING else RecyclerVerificationStatus.VERIFIED,
                        profileId = profileId,
                        businessName = current.businessName.ifBlank { null },
                        phoneNumber = current.phone,
                        displayName = current.displayName.ifBlank { null },
                        areaName = current.area
                    )
                    val selectedProfile = if (current.role == AccountRole.HOUSEHOLD && profile.role == AccountRole.COLLECTOR) profile.copy(role = AccountRole.HOUSEHOLD) else profile
                    secureStorage?.saveAccount(selectedProfile)
                    if (selectedProfile.role != AccountRole.RECYCLER) saveCollectorCacheIfNeeded(selectedProfile.profileId, current, selectedProfile.role)
                    current.copy(
                        step = OnboardingStep.COMPLETE,
                        completed = true,
                        isBusy = false,
                        otpError = null,
                        role = selectedProfile.role,
                        email = selectedProfile.email.ifBlank { current.email },
                        phone = selectedProfile.phoneNumber.ifBlank { current.phone },
                        displayName = selectedProfile.displayName.orEmpty(),
                        area = selectedProfile.areaName.orEmpty().ifBlank { current.area }
                    )
                }
                OtpVerification.Incorrect -> current.copy(isBusy = false, otpError = OtpError.INCORRECT)
                OtpVerification.Expired -> current.copy(isBusy = false, otpError = OtpError.EXPIRED)
                OtpVerification.AttemptsExceeded -> current.copy(isBusy = false, otpError = OtpError.ATTEMPTS_EXCEEDED)
                OtpVerification.AccountConflict -> current.copy(isBusy = false, otpError = OtpError.ACCOUNT_CONFLICT)
                OtpVerification.ServerError -> current.copy(isBusy = false, otpError = OtpError.SERVER)
                OtpVerification.NetworkError -> current.copy(isBusy = false, otpError = OtpError.NETWORK)
            }
        }
    }
    fun resetOtp() {
        _state.value = _state.value.copy(
            step = OnboardingStep.PHONE,
            otp = "",
            challenge = null,
            otpError = null,
            authError = false,
            isBusy = false
        )
    }

    fun resendOtp() { requestOtp() }

    fun selectLanguage(tag: String) {
        val next = if (_state.value.role == AccountRole.RECYCLER) OnboardingStep.RECYCLER_DETAILS else OnboardingStep.LOCATION_PERMISSION
        _state.value = _state.value.copy(language = LocaleManager.normalizeTag(tag), step = next)
    }
    fun locationPermissionResult(granted: Boolean) {
        if (!granted) {
            chooseManualLocation()
            return
        }
        val provider = locationProvider
        if (provider == null) {
            _state.value = _state.value.copy(locationChoice = LocationChoice.GPS, locationError = false, isLocationBusy = false, step = OnboardingStep.AREA)
            return
        }

        _state.value = _state.value.copy(isLocationBusy = true, locationError = false)
        viewModelScope.launch {
            val detected = runCatching { provider.current() }.getOrNull()
            val current = _state.value
            _state.value = current.copy(
                locationChoice = if (detected == null) LocationChoice.MANUAL else LocationChoice.GPS,
                latitude = detected?.latitude,
                longitude = detected?.longitude,
                area = detected?.areaName.orEmpty(),
                locationError = detected == null,
                isLocationBusy = false,
                step = OnboardingStep.AREA
            )
        }
    }

    fun chooseManualLocation() {
        _state.value = _state.value.copy(
            locationChoice = LocationChoice.MANUAL,
            latitude = null,
            longitude = null,
            locationError = false,
            isLocationBusy = false,
            step = OnboardingStep.AREA
        )
    }
    fun setArea(value: String) { _state.value = _state.value.copy(area = value) }
    fun continueToPhone() {
        val current = _state.value
        val validEmail = current.email.isBlank() || EmailValidator.isValid(current.email)
        val validDisplayName = current.role == AccountRole.RECYCLER || current.displayName.isNotBlank()
        _state.value = current.copy(emailError = !validEmail, displayNameError = !validDisplayName)
        if (current.area.isNotBlank() && validDisplayName && validEmail && (current.role != AccountRole.RECYCLER || current.hasRequiredRecyclerDetails())) {
            _state.value = _state.value.copy(step = OnboardingStep.PHONE)
        }
    }

    private fun OnboardingState.hasRequiredRecyclerDetails(): Boolean =
        role != AccountRole.RECYCLER || (businessName.isNotBlank() && materialsAccepted.isNotEmpty())

    private fun OnboardingState.hasRequiredRegistrationFields(): Boolean =
        phone.isNotBlank() &&
            area.isNotBlank() &&
            (role == AccountRole.RECYCLER || displayName.isNotBlank()) &&
            (email.isBlank() || EmailValidator.isValid(email)) &&
            hasRequiredRecyclerDetails()

    private fun OnboardingState.isPhoneOnlyVerification(): Boolean =
        role != AccountRole.RECYCLER &&
            area.isBlank() &&
            displayName.isBlank() &&
            email.isBlank() &&
            businessName.isBlank() &&
            authorizationNumber.isBlank() &&
            materialsAccepted.isEmpty() &&
            latitude == null &&
            longitude == null

    fun saveProfile() {
        val current = _state.value
        if (current.area.isBlank() && current.role != AccountRole.RECYCLER) return
        viewModelScope.launch {
            _state.value = current.copy(isBusy = true, authError = false)
            if (current.email.isNotBlank() && authenticatedCollectorId == null) {
                val request = EmailAccountRequest(email = current.email, password = current.password, role = current.role, preferredLanguage = current.language, areaName = current.area, businessName = current.businessName, authorizationNumber = current.authorizationNumber, materialsAccepted = current.materialsAccepted.toList(), pickupAvailable = current.pickupAvailable, serviceRadiusKm = current.serviceRadiusKm, isReturning = current.returningUser)
                when (val result = auth.authenticateEmail(request)) {
                    is EmailAuthentication.Success -> {
                        val selectedProfile = if (current.role == AccountRole.HOUSEHOLD && result.profile.role == AccountRole.COLLECTOR) result.profile.copy(role = AccountRole.HOUSEHOLD) else result.profile
                        secureStorage?.saveAccount(selectedProfile)
                        saveCollectorCacheIfNeeded(selectedProfile.profileId, current, selectedProfile.role)
                        _state.value = current.copy(step = OnboardingStep.COMPLETE, completed = true, isBusy = false, role = selectedProfile.role)
                    }
                    else -> _state.value = current.copy(isBusy = false, authError = true)
                }
            } else {
                val timestamp = now()
                val id = authenticatedCollectorId ?: "KC-${UUID.randomUUID().toString().take(8).uppercase()}"
                val profile = CollectorProfile(
                    id = id,
                    phoneNumber = current.phone,
                    preferredLanguage = current.language,
                    primaryLocation = current.area,
                    locationSource = current.locationChoice.name.lowercase(),
                    createdAtEpochMs = timestamp,
                    lastLoginEpochMs = timestamp,
                    latitude = current.latitude,
                    longitude = current.longitude
                )
                profiles.save(profile)
                try { auth.updateProfile(profile) } catch (_: Exception) { }
                secureStorage?.put(SecureStorage.COLLECTOR_ID, id)
                _state.value = current.copy(step = OnboardingStep.COMPLETE, completed = true, isBusy = false)
            }
        }
    }

    private suspend fun saveCollectorCacheIfNeeded(profileId: String, current: OnboardingState, role: AccountRole = current.role) {
        if (role != AccountRole.RECYCLER) {
            val timestamp = now()
            profiles.save(
                CollectorProfile(
                    id = profileId,
                    phoneNumber = current.phone,
                    preferredLanguage = current.language,
                    primaryLocation = current.area,
                    locationSource = current.locationChoice.name.lowercase(),
                    createdAtEpochMs = timestamp,
                    lastLoginEpochMs = timestamp,
                    latitude = current.latitude,
                    longitude = current.longitude
                )
            )
            secureStorage?.put(SecureStorage.COLLECTOR_ID, profileId)
        }
    }
}
