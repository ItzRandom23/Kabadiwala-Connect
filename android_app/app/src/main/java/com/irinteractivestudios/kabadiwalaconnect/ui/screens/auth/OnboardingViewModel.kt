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
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalDate

enum class OnboardingStep { WELCOME, SIGN_IN, EMAIL, ROLE, LANGUAGE, RECYCLER_DETAILS, PHONE, OTP, LOCATION_PERMISSION, AREA, COMPLETE }
enum class LocationChoice { GPS, MANUAL }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val email: String = "",
    val password: String = "",
    val returningUser: Boolean = false,
    val role: AccountRole = AccountRole.COLLECTOR,
    val roleSelected: Boolean = false,
    val roleRequiredAfterSignIn: Boolean = false,
    val displayName: String = "",
    val businessName: String = "",
    val authorizationNumber: String = "",
    val materialsAccepted: Set<String> = emptySet(),
    val pickupAvailable: Boolean = false,
    val serviceRadiusKm: Int = 25,
    val materialCategories: List<String> = emptyList(), val materialCategoriesError: Boolean = false,
    val authorizationAuthority: String = "", val authorizationType: String = "", val authorizationEvidenceReference: String = "", val authorizationValidUntil: String = "", val alternatePhone: String = "",
    val pickupAvailability: String = "FLEXIBLE", val pickupPricing: String = "INCLUDED", val pickupFeeText: String = "", val logisticsCostText: String = "", val operatingHours: String = "",
    val recyclerDetailsError: String? = null,
    val phone: String = "",
    val otp: String = "",
    val language: String = LocaleManager.ENGLISH,
    val area: String = "",
    val address: String = "",
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
    val authError: AuthError? = null,
    val otpRetryAfterSeconds: Long? = null,
    val isBusy: Boolean = false,
    val completed: Boolean = false
)

enum class OtpError { INCORRECT, EXPIRED, ATTEMPTS_EXCEEDED, ACCOUNT_CONFLICT, MISSING_DETAILS, SERVER, NETWORK }
enum class AuthError { INVALID_CREDENTIALS, OTP_RATE_LIMITED, NETWORK }

class OnboardingViewModel(
    private val auth: AuthenticationRepository,
    private val profiles: CollectorProfileRepository,
    private val secureStorage: SecureStorage? = null,
    initialLanguage: String = LocaleManager.ENGLISH,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val locationProvider: LocationProvider? = null,
    private val apiService: ApiService? = null
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnboardingState(language = LocaleManager.normalizeTag(initialLanguage))
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    private var authenticatedCollectorId: String? = null
    private var signInGeneration = 0L
    private var signInJob: Job? = null
    private var otpFlowGeneration = 0L
    private var otpFlowJob: Job? = null
    private var locationGeneration = 0L
    private var locationJob: Job? = null
    private var profileGeneration = 0L
    private var profileJob: Job? = null

    private fun invalidateSignIn() {
        signInGeneration++
        signInJob?.cancel()
        signInJob = null
        if (_state.value.isBusy) _state.value = _state.value.copy(isBusy = false)
    }

    private fun invalidateOtpFlow() {
        otpFlowGeneration++
        otpFlowJob?.cancel()
        otpFlowJob = null
        if (_state.value.isBusy) _state.value = _state.value.copy(isBusy = false)
    }

    private fun invalidateLocationLookup() {
        locationGeneration++
        locationJob?.cancel()
        locationJob = null
        if (_state.value.isLocationBusy) _state.value = _state.value.copy(isLocationBusy = false)
    }

    private fun invalidateProfileSave() {
        profileGeneration++
        profileJob?.cancel()
        profileJob = null
        if (_state.value.isBusy) _state.value = _state.value.copy(isBusy = false)
    }

    fun start() { _state.value = _state.value.copy(returningUser = false, step = OnboardingStep.ROLE, roleRequiredAfterSignIn = false, authError = null) }
    fun startSignIn() {
        invalidateSignIn()
        _state.value = _state.value.copy(returningUser = true, step = OnboardingStep.SIGN_IN, roleRequiredAfterSignIn = false, authError = null)
    }
    fun usePhoneSignIn() { _state.value = _state.value.copy(returningUser = true, step = OnboardingStep.PHONE, authError = null) }
    fun useEmailSignIn() {
        invalidateSignIn()
        invalidateOtpFlow()
        invalidateLocationLookup()
        _state.value = _state.value.copy(
            returningUser = true,
            // Operator sign-in is a separate credential endpoint. Going back
            // to ordinary email sign-in must clear that transient choice;
            // the server resolves the actual marketplace role after login.
            role = AccountRole.COLLECTOR,
            roleSelected = false,
            step = OnboardingStep.EMAIL,
            roleRequiredAfterSignIn = false,
            authError = null,
            otpRetryAfterSeconds = null,
            phoneError = false
        )
    }
    fun useAdminSignIn() {
        invalidateSignIn()
        invalidateOtpFlow()
        invalidateLocationLookup()
        _state.value = _state.value.copy(
            returningUser = true,
            role = AccountRole.ADMIN,
            step = OnboardingStep.EMAIL,
            authError = null,
            otpRetryAfterSeconds = null,
            phoneError = false
        )
    }
    fun toggleReturning() { invalidateSignIn(); _state.value = _state.value.copy(returningUser = !_state.value.returningUser, roleRequiredAfterSignIn = false, authError = null) }
    fun goBack() {
        invalidateSignIn()
        invalidateOtpFlow()
        invalidateLocationLookup()
        invalidateProfileSave()
        val current = _state.value
        val previous = when (current.step) {
            OnboardingStep.EMAIL -> if (current.returningUser && current.role != AccountRole.ADMIN) OnboardingStep.SIGN_IN else OnboardingStep.WELCOME
            OnboardingStep.SIGN_IN -> OnboardingStep.WELCOME
            OnboardingStep.ROLE -> OnboardingStep.WELCOME
            OnboardingStep.LANGUAGE -> OnboardingStep.ROLE
            OnboardingStep.RECYCLER_DETAILS -> OnboardingStep.ROLE
            OnboardingStep.LOCATION_PERMISSION -> if (current.role == AccountRole.RECYCLER) OnboardingStep.RECYCLER_DETAILS else OnboardingStep.ROLE
            OnboardingStep.AREA -> OnboardingStep.LOCATION_PERMISSION
            OnboardingStep.PHONE -> if (current.returningUser) OnboardingStep.SIGN_IN else OnboardingStep.AREA
            OnboardingStep.OTP -> OnboardingStep.PHONE
            OnboardingStep.WELCOME, OnboardingStep.COMPLETE -> current.step
        }
        _state.value = current.copy(
            step = previous,
            otp = if (previous == OnboardingStep.PHONE) "" else current.otp,
            challenge = if (previous == OnboardingStep.PHONE) null else current.challenge,
            otpError = null,
            phoneError = false,
            authError = null,
            otpRetryAfterSeconds = null,
            isBusy = false
        )
    }
    fun startOver() {
        invalidateSignIn()
        invalidateOtpFlow()
        invalidateLocationLookup()
        invalidateProfileSave()
        val language = _state.value.language
        authenticatedCollectorId = null
        _state.value = OnboardingState(language = language)
    }
    fun setEmail(value: String) { invalidateSignIn(); _state.value = _state.value.copy(email = value.trim(), emailError = false, authError = null) }
    fun setPassword(value: String) { invalidateSignIn(); _state.value = _state.value.copy(password = value, passwordError = false, authError = null) }
    fun continueEmail() {
        val current = _state.value
        val validEmail = EmailValidator.isValid(current.email)
        val validPassword = current.password.length >= 8
        _state.value = current.copy(emailError = !validEmail, passwordError = !validPassword)
        if (validEmail && validPassword) _state.value = _state.value.copy(step = if (current.returningUser) OnboardingStep.PHONE else OnboardingStep.ROLE)
    }
    fun signIn() {
        val current = _state.value
        if (current.isBusy) return
        if (!EmailValidator.isValid(current.email) || current.password.length < 8) { continueEmail(); return }
        val generation = ++signInGeneration
        _state.value = current.copy(isBusy = true, authError = null)
        signInJob = viewModelScope.launch {
            try {
                val result = try {
                    if (current.role == AccountRole.ADMIN) auth.authenticateAdmin(current.email, current.password)
                    else auth.authenticateEmail(EmailAccountRequest(current.email, current.password, current.role, current.language, isReturning = true))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    EmailAuthentication.NetworkError
                }
                if (generation != signInGeneration) return@launch
                when (result) {
                    is EmailAuthentication.Success -> {
                        secureStorage?.saveAccount(result.profile)
                        saveCollectorCacheIfNeeded(result.profile.profileId, current, result.profile.role)
                        if (generation == signInGeneration) _state.value = current.copy(step = OnboardingStep.COMPLETE, completed = true, isBusy = false, role = result.profile.role, roleSelected = true)
                    }
                    is EmailAuthentication.InvalidCredentials -> _state.value = _state.value.copy(isBusy = false, authError = AuthError.INVALID_CREDENTIALS)
                    else -> _state.value = _state.value.copy(isBusy = false, authError = AuthError.NETWORK)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == signInGeneration) _state.value = _state.value.copy(isBusy = false, authError = AuthError.NETWORK)
            } finally {
                if (generation == signInGeneration) {
                    if (_state.value.isBusy) _state.value = _state.value.copy(isBusy = false)
                    signInJob = null
                }
            }
        }
    }
    // The language is selected once on the first-run screen and persisted by
    // MainActivity. Registration reuses that choice instead of asking again.
    fun selectRole(role: AccountRole) {
        _state.value = _state.value.copy(role = role, roleSelected = true, roleRequiredAfterSignIn = false)
    }
    fun continueRole() {
        val current = _state.value
        if (current.step != OnboardingStep.ROLE || !current.roleSelected) return
        val next = if (current.role == AccountRole.RECYCLER) OnboardingStep.RECYCLER_DETAILS else OnboardingStep.LOCATION_PERMISSION
        _state.value = current.copy(step = next)
        if (current.role == AccountRole.RECYCLER) loadMaterialCategories()
    }
    fun canContinueRecyclerDetails(): Boolean {
        val current = _state.value
        return current.businessName.trim().length >= 2 &&
            current.materialCategories.isNotEmpty() &&
            current.materialsAccepted.isNotEmpty()
    }
    fun canContinueArea(): Boolean {
        val current = _state.value
        return current.address.isNotBlank() &&
            (current.role == AccountRole.RECYCLER || current.displayName.isNotBlank()) &&
            (current.email.isBlank() || EmailValidator.isValid(current.email))
    }
    fun loadMaterialCategories() {
        val api = apiService ?: return
        viewModelScope.launch {
            try {
                val categories = api.materialCategories().requireData()
                _state.value = _state.value.copy(materialCategories = categories, materialCategoriesError = categories.isEmpty())
            } catch (error: Exception) {
                android.util.Log.w("RecyclerMaterials", "Could not load canonical material categories", error)
                _state.value = _state.value.copy(materialCategoriesError = true)
            }
        }
    }
    fun setDisplayName(value: String) { _state.value = _state.value.copy(displayName = value, displayNameError = false) }
    fun setBusinessName(value: String) { _state.value = _state.value.copy(businessName = value) }
    fun setAuthorizationNumber(value: String) { _state.value = _state.value.copy(authorizationNumber = value) }
    fun setAuthorizationAuthority(value: String) { _state.value = _state.value.copy(authorizationAuthority = value) }
    fun setAuthorizationType(value: String) { _state.value = _state.value.copy(authorizationType = value) }
    fun setAuthorizationEvidenceReference(value: String) { _state.value = _state.value.copy(authorizationEvidenceReference = value) }
    fun setAuthorizationValidUntil(value: String) { _state.value = _state.value.copy(authorizationValidUntil = value) }
    fun setAlternatePhone(value: String) { _state.value = _state.value.copy(alternatePhone = value) }
    fun setPickupAvailability(value: String) { _state.value = _state.value.copy(pickupAvailability = value) }
    fun setPickupPricing(value: String) { _state.value = _state.value.copy(pickupPricing = value) }
    fun setPickupFeeText(value: String) { _state.value = _state.value.copy(pickupFeeText = value) }
    fun setLogisticsCostText(value: String) { _state.value = _state.value.copy(logisticsCostText = value) }
    fun setOperatingHours(value: String) { _state.value = _state.value.copy(operatingHours = value) }
    fun toggleMaterial(value: String) { _state.value = _state.value.copy(materialsAccepted = _state.value.materialsAccepted.toMutableSet().also { if (!it.add(value)) it.remove(value) }) }
    fun setPickupAvailable(value: Boolean) { _state.value = _state.value.copy(pickupAvailable = value, pickupPricing = if (value) _state.value.pickupPricing else "INCLUDED", pickupFeeText = if (value) _state.value.pickupFeeText else "", logisticsCostText = if (value) _state.value.logisticsCostText else "") }
    fun setServiceRadius(value: Int) { _state.value = _state.value.copy(serviceRadiusKm = value) }
    fun continueRecyclerDetails() {
        val current = _state.value
        val issue = current.recyclerIssue()
        if (issue != null) { _state.value = current.copy(recyclerDetailsError = issue); return }
        val validEmail = current.email.isBlank() || EmailValidator.isValid(current.email)
        _state.value = current.copy(emailError = !validEmail)
        if (validEmail) _state.value = _state.value.copy(step = OnboardingStep.LOCATION_PERMISSION)
    }
    private fun OnboardingState.recyclerIssue(): String? = when {
        businessName.trim().length < 2 -> "Enter the facility name (at least 2 characters)."
        authorizationNumber.isNotBlank() && !authorizationNumber.trim().matches(Regex("[A-Za-z0-9][A-Za-z0-9/.-]{3,79}")) -> "Check the registration number format."
        materialCategories.isEmpty() -> "Load supported materials before continuing."
        materialsAccepted.isEmpty() || !materialCategories.containsAll(materialsAccepted) -> "Select at least one supported material."
        authorizationValidUntil.isNotBlank() && runCatching { LocalDate.parse(authorizationValidUntil) }.getOrNull()?.isAfter(LocalDate.now()) != true -> "Choose a future authorization validity date."
        alternatePhone.isNotBlank() && !IndianPhoneValidator.isValid(alternatePhone) -> "Enter a valid 10-digit alternate phone."
        pickupAvailable && serviceRadiusKm !in 1..200 -> "Set a pickup distance between 1 and 200 km."
        pickupAvailable && pickupPricing == "FIXED" && (pickupFeeText.toDoubleOrNull()?.let { it >= 0 && it <= 100000 } != true) -> "Enter a valid fixed pickup fee."
        pickupAvailable && pickupPricing == "PER_KM" && (logisticsCostText.toDoubleOrNull()?.let { it >= 0 && it <= 100000 } != true) -> "Enter a valid cost per km."
        else -> null
    }

    // Legacy phone OTP boundary remains available for older backend/dev flows.
    fun setPhone(value: String) {
        invalidateOtpFlow()
        _state.value = _state.value.copy(
            // Keep the field itself strict: exactly the value the user can
            // submit is stored, with no punctuation, spaces, or country code.
            phone = value.filter(Char::isDigit).take(10),
            phoneError = false,
            authError = null,
            otpRetryAfterSeconds = null
        )
    }
    fun requestOtp() {
        val current = _state.value
        // Compose disables the button after recomposition, but a rapid double
        // tap can reach the ViewModel before that recomposition happens.
        // Claim the busy state synchronously so only one request is launched.
        if (current.isBusy) return
        val phone = IndianPhoneValidator.normalize(current.phone)
        _state.value = _state.value.copy(phone = phone)
        if (phone.length != 10 || !IndianPhoneValidator.isValid(phone)) { _state.value = _state.value.copy(phoneError = true); return }
        val generation = ++otpFlowGeneration
        _state.value = _state.value.copy(isBusy = true, phoneError = false)
        otpFlowJob = viewModelScope.launch {
            try {
                val challenge = auth.requestOtp(phone)
                if (generation == otpFlowGeneration) {
                    _state.value = _state.value.copy(
                        step = OnboardingStep.OTP,
                        challenge = challenge,
                        otp = challenge.developmentCodeHint.orEmpty(),
                        isBusy = false,
                        authError = null,
                        otpRetryAfterSeconds = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val remote = error as? RemoteApiException
                if (generation == otpFlowGeneration) _state.value = _state.value.copy(
                    isBusy = false,
                    authError = if (remote?.code == "OTP_RATE_LIMITED" || remote?.code == "OTP_COOLDOWN") AuthError.OTP_RATE_LIMITED else AuthError.NETWORK,
                    otpRetryAfterSeconds = remote?.retryAfterSeconds
                )
            } finally {
                if (generation == otpFlowGeneration) {
                    if (_state.value.isBusy) _state.value = _state.value.copy(isBusy = false)
                    otpFlowJob = null
                }
            }
        }
    }
    fun setOtp(value: String) { invalidateOtpFlow(); _state.value = _state.value.copy(otp = value.filter(Char::isDigit).take(6), otpError = null) }
    fun verifyOtp() {
        val current = _state.value
        if (current.isBusy) return
        if (current.otp.length != 6) return
        val useLegacyPhoneOnlyVerification = current.isPhoneOnlyVerification()
        if (!useLegacyPhoneOnlyVerification && !current.hasRequiredRegistrationFields()) {
            // Never leave a valid-looking Verify button with no visible result.
            // This can happen after process recreation or when a signup step was
            // skipped by a deep link/back-stack restore.
            _state.value = current.copy(otpError = OtpError.MISSING_DETAILS)
            return
        }
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
        val generation = ++otpFlowGeneration
        _state.value = current.copy(isBusy = true)
        otpFlowJob = viewModelScope.launch {
            try {
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
                                address = current.address.trim(),
                                displayName = displayName,
                                email = email,
                                businessName = businessName,
                                authorizationNumber = authorizationNumber,
                                materialsAccepted = materialsAccepted,
                                pickupAvailable = current.pickupAvailable,
                                serviceRadiusKm = current.serviceRadiusKm,
                                authorizationAuthority = current.authorizationAuthority, authorizationType = current.authorizationType, authorizationEvidenceReference = current.authorizationEvidenceReference, authorizationValidUntil = current.authorizationValidUntil, alternatePhone = current.alternatePhone, pickupAvailability = current.pickupAvailability, pickupIncluded = current.pickupAvailable && current.pickupPricing == "INCLUDED", pickupFee = current.pickupFeeText.toDoubleOrNull().takeIf { current.pickupAvailable && current.pickupPricing == "FIXED" }, logisticsCostPerKm = current.logisticsCostText.toDoubleOrNull().takeIf { current.pickupAvailable && current.pickupPricing == "PER_KM" }, operatingHours = current.operatingHours,
                                latitude = current.latitude,
                                longitude = current.longitude
                            )
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    OtpVerification.NetworkError
                }
                if (generation != otpFlowGeneration) return@launch
                // Do not silently convert a signup conflict into sign-in:
                // that can open an established account with another role.
                val result = registrationResult
                if (generation != otpFlowGeneration) return@launch
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
                        areaName = current.area,
                        address = current.address.trim().ifBlank { null }
                    )
                    // The backend owns the authenticated role. The client may
                    // request a role during signup, but must not rewrite the
                    // returned authorization result for presentation.
                    secureStorage?.saveAccount(profile)
                    if (profile.role != AccountRole.RECYCLER) saveCollectorCacheIfNeeded(profile.profileId, current, profile.role)
                    current.copy(
                        step = OnboardingStep.COMPLETE,
                        completed = true,
                        isBusy = false,
                        otpError = null,
                        role = profile.role,
                        roleSelected = true,
                        email = profile.email.ifBlank { current.email },
                        phone = profile.phoneNumber.ifBlank { current.phone },
                        displayName = profile.displayName.orEmpty(),
                        area = profile.areaName.orEmpty().ifBlank { current.area }
                    )
                }
                OtpVerification.Incorrect -> current.copy(isBusy = false, otpError = OtpError.INCORRECT)
                OtpVerification.Expired -> current.copy(isBusy = false, otpError = OtpError.EXPIRED)
                OtpVerification.AttemptsExceeded -> current.copy(isBusy = false, otpError = OtpError.ATTEMPTS_EXCEEDED)
                OtpVerification.AccountConflict -> current.copy(isBusy = false, otpError = OtpError.ACCOUNT_CONFLICT)
                OtpVerification.RoleRequired -> current.copy(
                    step = OnboardingStep.ROLE,
                    returningUser = false,
                    roleSelected = false,
                    roleRequiredAfterSignIn = true,
                    challenge = null,
                    otp = "",
                    otpError = null,
                    isBusy = false
                )
                OtpVerification.ServerError -> current.copy(isBusy = false, otpError = OtpError.SERVER)
                OtpVerification.NetworkError -> current.copy(isBusy = false, otpError = OtpError.NETWORK)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == otpFlowGeneration) _state.value = _state.value.copy(isBusy = false, otpError = OtpError.SERVER)
            } finally {
                if (generation == otpFlowGeneration) {
                    if (_state.value.isBusy) _state.value = _state.value.copy(isBusy = false)
                    otpFlowJob = null
                }
            }
        }
    }
    fun resetOtp() {
        invalidateOtpFlow()
        _state.value = _state.value.copy(
            step = OnboardingStep.PHONE,
            otp = "",
            challenge = null,
            otpError = null,
            authError = null,
            otpRetryAfterSeconds = null,
            isBusy = false
        )
    }

    fun resendOtp() { requestOtp() }

    fun selectLanguage(tag: String) {
        val next = if (_state.value.role == AccountRole.RECYCLER) OnboardingStep.RECYCLER_DETAILS else OnboardingStep.LOCATION_PERMISSION
        _state.value = _state.value.copy(language = LocaleManager.normalizeTag(tag), step = next)
    }
    fun locationPermissionResult(granted: Boolean) {
        invalidateLocationLookup()
        if (!granted) {
            chooseManualLocation()
            return
        }
        val provider = locationProvider
        if (provider == null) {
            _state.value = _state.value.copy(locationChoice = LocationChoice.GPS, locationError = false, isLocationBusy = false, step = OnboardingStep.AREA)
            return
        }

        val generation = ++locationGeneration
        _state.value = _state.value.copy(isLocationBusy = true, locationError = false)
        locationJob = viewModelScope.launch {
            try {
                val detected = try {
                    provider.current()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (generation != locationGeneration) return@launch
                val current = _state.value
                _state.value = current.copy(
                    locationChoice = if (detected == null) LocationChoice.MANUAL else LocationChoice.GPS,
                    latitude = detected?.latitude,
                    longitude = detected?.longitude,
                    area = detected?.areaName.orEmpty().ifBlank { detected?.formattedAddress.orEmpty() },
                    address = detected?.formattedAddress.orEmpty(),
                    locationError = detected == null,
                    isLocationBusy = false,
                    step = OnboardingStep.AREA
                )
            } catch (cancelled: CancellationException) {
                if (generation == locationGeneration && _state.value.isLocationBusy) _state.value = _state.value.copy(isLocationBusy = false)
                throw cancelled
            } finally {
                if (generation == locationGeneration) {
                    if (_state.value.isLocationBusy) _state.value = _state.value.copy(isLocationBusy = false)
                    locationJob = null
                }
            }
        }
    }

    fun chooseManualLocation() {
        invalidateLocationLookup()
        _state.value = _state.value.copy(
            locationChoice = LocationChoice.MANUAL,
            latitude = null,
            longitude = null,
            area = "",
            address = "",
            locationError = false,
            isLocationBusy = false,
            step = OnboardingStep.AREA
        )
    }
    fun setAddress(value: String) {
        val address = value.take(240)
        val current = _state.value
        val addressChanged = address.trim() != current.address.trim()
        _state.value = current.copy(
            address = address,
            // Coordinates and address must describe the same place. If the
            // user edits the detected address, stop treating the old GPS fix
            // as coordinates for that manually entered address.
            locationChoice = if (addressChanged) LocationChoice.MANUAL else current.locationChoice,
            latitude = if (addressChanged) null else current.latitude,
            longitude = if (addressChanged) null else current.longitude,
            area = if (addressChanged) address.trim() else current.area
        )
    }
    fun continueToPhone() {
        val current = _state.value
        if (!current.returningUser && !current.roleSelected) {
            _state.value = current.copy(step = OnboardingStep.ROLE)
            return
        }
        val validEmail = current.email.isBlank() || EmailValidator.isValid(current.email)
        val validDisplayName = current.role == AccountRole.RECYCLER || current.displayName.isNotBlank()
        _state.value = current.copy(emailError = !validEmail, displayNameError = !validDisplayName)
        if (current.address.isNotBlank() && validDisplayName && validEmail && (current.role != AccountRole.RECYCLER || current.hasRequiredRecyclerDetails())) {
            _state.value = _state.value.copy(step = OnboardingStep.PHONE)
        }
    }

    private fun OnboardingState.hasRequiredRecyclerDetails(): Boolean =
        role != AccountRole.RECYCLER || (businessName.isNotBlank() && materialsAccepted.isNotEmpty())

    private fun OnboardingState.hasRequiredRegistrationFields(): Boolean =
        roleSelected &&
            phone.isNotBlank() &&
            address.isNotBlank() &&
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
        if (current.isBusy) return
        if (!current.returningUser && !current.roleSelected) {
            _state.value = current.copy(step = OnboardingStep.ROLE)
            return
        }
        if (current.address.isBlank() && current.role != AccountRole.RECYCLER) return
        val generation = ++profileGeneration
        _state.value = current.copy(isBusy = true, authError = null)
        profileJob = viewModelScope.launch {
            try {
                if (current.email.isNotBlank() && authenticatedCollectorId == null) {
                    val request = EmailAccountRequest(email = current.email, password = current.password, role = current.role, preferredLanguage = current.language, areaName = current.area, address = current.address.trim(), latitude = current.latitude, longitude = current.longitude, businessName = current.businessName, authorizationNumber = current.authorizationNumber, materialsAccepted = current.materialsAccepted.toList(), pickupAvailable = current.pickupAvailable, serviceRadiusKm = current.serviceRadiusKm, authorizationAuthority = current.authorizationAuthority, authorizationType = current.authorizationType, authorizationEvidenceReference = current.authorizationEvidenceReference, authorizationValidUntil = current.authorizationValidUntil, alternatePhone = current.alternatePhone, pickupAvailability = current.pickupAvailability, pickupIncluded = current.pickupAvailable && current.pickupPricing == "INCLUDED", pickupFee = current.pickupFeeText.toDoubleOrNull().takeIf { current.pickupAvailable && current.pickupPricing == "FIXED" }, logisticsCostPerKm = current.logisticsCostText.toDoubleOrNull().takeIf { current.pickupAvailable && current.pickupPricing == "PER_KM" }, operatingHours = current.operatingHours, isReturning = current.returningUser)
                    when (val result = auth.authenticateEmail(request)) {
                        is EmailAuthentication.Success -> {
                            secureStorage?.saveAccount(result.profile)
                            saveCollectorCacheIfNeeded(result.profile.profileId, current, result.profile.role)
                            if (generation == profileGeneration) _state.value = current.copy(step = OnboardingStep.COMPLETE, completed = true, isBusy = false, role = result.profile.role, roleSelected = true)
                        }
                        is EmailAuthentication.InvalidCredentials -> _state.value = _state.value.copy(isBusy = false, authError = AuthError.INVALID_CREDENTIALS)
                        else -> _state.value = _state.value.copy(isBusy = false, authError = AuthError.NETWORK)
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
                    try {
                        auth.updateProfile(profile)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Profile updates are best effort for the legacy
                        // offline phone-only registration path.
                    }
                    secureStorage?.put(SecureStorage.COLLECTOR_ID, id)
                    if (generation == profileGeneration) _state.value = current.copy(step = OnboardingStep.COMPLETE, completed = true, isBusy = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == profileGeneration) _state.value = _state.value.copy(isBusy = false, authError = AuthError.NETWORK)
            } finally {
                if (generation == profileGeneration) {
                    if (_state.value.isBusy) _state.value = _state.value.copy(isBusy = false)
                    profileJob = null
                }
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
