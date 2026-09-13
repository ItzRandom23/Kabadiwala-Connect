package com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth

import android.Manifest
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcMinTouchHeight
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcBrandLogo
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcPrimaryButton
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import kotlinx.coroutines.delay

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel, onFinished: () -> Unit, onDemo: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onFinished() }
    OnboardingScreen(state, viewModel, onDemo)
}

@Composable
fun OnboardingScreen(state: OnboardingState, vm: OnboardingViewModel, onDemo: () -> Unit = {}) {
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.locationPermissionResult(granted)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.step != OnboardingStep.WELCOME && state.step != OnboardingStep.COMPLETE) {
            Progress(state)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = vm::goBack,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f).heightIn(min = KcMinTouchHeight).testTag("auth_back")
                ) { Text(stringResource(R.string.common_back)) }
                OutlinedButton(
                    onClick = vm::startOver,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f).heightIn(min = KcMinTouchHeight).testTag("auth_start_over")
                ) { Text(stringResource(R.string.auth_start_over)) }
            }
        }
        when (state.step) {
            OnboardingStep.WELCOME -> Welcome(vm, onDemo)
            OnboardingStep.EMAIL -> EmailEntry(state, vm)
            OnboardingStep.ROLE -> RoleEntry(state, vm)
            OnboardingStep.LANGUAGE -> LanguageEntry(state, vm)
            OnboardingStep.RECYCLER_DETAILS -> RecyclerDetailsEntry(state, vm)
            OnboardingStep.LOCATION_PERMISSION -> LocationPermission(state, vm, locationLauncher)
            OnboardingStep.AREA -> AreaEntry(state, vm)
            OnboardingStep.PHONE -> PhoneEntry(state, vm)
            OnboardingStep.OTP -> OtpEntry(state, vm)
            OnboardingStep.COMPLETE -> Complete(state)
        }
    }
}

@Composable private fun Progress(state: OnboardingState) {
    val total = if (state.role == AccountRole.RECYCLER) 6 else 5
    val number = when (state.step) {
        OnboardingStep.EMAIL, OnboardingStep.ROLE, OnboardingStep.LANGUAGE -> 1
        OnboardingStep.RECYCLER_DETAILS -> 2
        OnboardingStep.LOCATION_PERMISSION -> if (state.role == AccountRole.RECYCLER) 3 else 2
        OnboardingStep.AREA -> if (state.role == AccountRole.RECYCLER) 4 else 3
        OnboardingStep.PHONE -> if (state.role == AccountRole.RECYCLER) 5 else 4
        OnboardingStep.OTP -> if (state.role == AccountRole.RECYCLER) 6 else 5
        else -> 1
    }
    Text(stringResource(R.string.auth_step, number, total), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("onboarding_progress"))
}

@Composable private fun Welcome(vm: OnboardingViewModel, onDemo: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
            KcBrandLogo(contentDescription = stringResource(R.string.app_name), size = 88.dp)
            Text(stringResource(R.string.auth_welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.auth_welcome_detail), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WelcomeBenefit(Icons.Filled.Home, stringResource(R.string.auth_benefit_sell_title), stringResource(R.string.auth_benefit_sell_detail))
        WelcomeBenefit(Icons.Filled.AttachMoney, stringResource(R.string.auth_benefit_price_title), stringResource(R.string.auth_benefit_price_detail))
        WelcomeBenefit(Icons.Filled.Verified, stringResource(R.string.auth_benefit_trust_title), stringResource(R.string.auth_benefit_trust_detail))
    }
    KcPrimaryButton(stringResource(R.string.auth_get_started), vm::start, icon = Icons.Filled.Recycling, testTag = "auth_get_started")
    OutlinedButton(onClick = { vm.toggleReturning(); vm.start() }, modifier = Modifier.fillMaxWidth().heightIn(min = KcMinTouchHeight)) { Text(stringResource(R.string.auth_existing_account)) }
    // Demo is a development-only facility. It is absent from release builds;
    // the real-backend path still uses live authentication and catalogs.
    if (BuildConfig.DEBUG) {
        OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth().heightIn(min = KcMinTouchHeight).testTag("auth_demo")) { Text(stringResource(R.string.auth_demo_entry)) }
    }
}

@Composable
private fun WelcomeBenefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(10.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun EmailEntry(state: OnboardingState, vm: OnboardingViewModel) {
    Text(if (state.returningUser) stringResource(R.string.auth_sign_in_title) else stringResource(R.string.auth_email_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_email_detail), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(state.email, vm::setEmail, label = { Text(stringResource(R.string.auth_email_label)) }, leadingIcon = { Icon(Icons.Filled.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, isError = state.emailError, supportingText = { if (state.emailError) Text(stringResource(R.string.auth_email_error)) }, modifier = Modifier.fillMaxWidth().testTag("auth_email"))
    OutlinedTextField(state.password, vm::setPassword, label = { Text(stringResource(R.string.auth_password_label)) }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, isError = state.passwordError, supportingText = { if (state.passwordError) Text(stringResource(R.string.auth_password_error)) }, modifier = Modifier.fillMaxWidth().testTag("auth_password"))
    if (state.authError) Text(stringResource(R.string.auth_network_error), color = MaterialTheme.colorScheme.error)
    KcPrimaryButton(if (state.returningUser) stringResource(R.string.auth_sign_in) else stringResource(R.string.auth_continue), if (state.returningUser) vm::signIn else vm::continueEmail, icon = Icons.Filled.CheckCircle, enabled = !state.isBusy, testTag = "auth_email_continue")
}

@Composable private fun RoleEntry(state: OnboardingState, vm: OnboardingViewModel) {
    Text(stringResource(R.string.auth_role_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_role_detail), style = MaterialTheme.typography.bodyLarge)
    RoleCard(AccountRole.HOUSEHOLD, stringResource(R.string.auth_role_household), stringResource(R.string.auth_role_household_detail), Icons.Filled.Home, state.role == AccountRole.HOUSEHOLD) { vm.selectRole(AccountRole.HOUSEHOLD) }
    RoleCard(AccountRole.COLLECTOR, stringResource(R.string.auth_role_collector), stringResource(R.string.auth_role_collector_detail), Icons.Filled.Recycling, state.role == AccountRole.COLLECTOR) { vm.selectRole(AccountRole.COLLECTOR) }
    RoleCard(AccountRole.RECYCLER, stringResource(R.string.auth_role_recycler), stringResource(R.string.auth_role_recycler_detail), Icons.Filled.Storefront, state.role == AccountRole.RECYCLER) { vm.selectRole(AccountRole.RECYCLER) }
}

@Composable private fun RoleCard(role: AccountRole, title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(48.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected, onClick)
        }
    }
}

@Composable private fun LanguageEntry(state: OnboardingState, vm: OnboardingViewModel) {
    Text(stringResource(R.string.auth_language_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_language_detail), style = MaterialTheme.typography.bodyLarge)
    LocaleManager.SUPPORTED.forEach { tag ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable { vm.selectLanguage(tag) }
                .testTag("auth_lang_$tag")
                .padding(vertical = 4.dp)
        ) {
            RadioButton(state.language == tag, { vm.selectLanguage(tag) })
            Spacer(Modifier.width(8.dp))
            Text(LocaleManager.LABELS[tag] ?: tag, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable private fun RecyclerDetailsEntry(state: OnboardingState, vm: OnboardingViewModel) {
    Text(stringResource(R.string.auth_recycler_details_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_recycler_details_detail), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(state.businessName, vm::setBusinessName, label = { Text(stringResource(R.string.auth_business_name)) }, leadingIcon = { Icon(Icons.Filled.Business, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.email, vm::setEmail, label = { Text(stringResource(R.string.auth_email_optional)) }, leadingIcon = { Icon(Icons.Filled.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, isError = state.emailError, supportingText = { if (state.emailError) Text(stringResource(R.string.auth_email_optional_error)) }, modifier = Modifier.fillMaxWidth().testTag("auth_email_optional"))
    OutlinedTextField(state.authorizationNumber, vm::setAuthorizationNumber, label = { Text(stringResource(R.string.auth_authorization_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.auth_materials_accepted), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { listOf("PCB", "CABLE", "BATTERY").forEach { material -> FilterChip(selected = material in state.materialsAccepted, onClick = { vm.toggleMaterial(material) }, label = { Text(material) }) } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.auth_pickup_available), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Switch(state.pickupAvailable, vm::setPickupAvailable) }
    KcPrimaryButton(stringResource(R.string.auth_continue), vm::continueRecyclerDetails, icon = Icons.Filled.CheckCircle, enabled = state.businessName.isNotBlank() && state.materialsAccepted.isNotEmpty())
}

@Composable private fun LocationPermission(state: OnboardingState, vm: OnboardingViewModel, launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
    Text(stringResource(R.string.auth_location_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_location_detail), style = MaterialTheme.typography.bodyLarge)
    if (state.isLocationBusy) {
        Text(stringResource(R.string.auth_location_detecting), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.locationError) {
        Text(stringResource(R.string.auth_location_unavailable), color = MaterialTheme.colorScheme.error)
    }
    KcPrimaryButton(
        stringResource(R.string.auth_use_gps),
        { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
        icon = Icons.Filled.LocationOn,
        enabled = !state.isLocationBusy,
        testTag = "auth_use_gps"
    )
    OutlinedButton(onClick = vm::chooseManualLocation, enabled = !state.isLocationBusy, modifier = Modifier.fillMaxWidth().heightIn(min = KcMinTouchHeight).testTag("auth_manual_location")) { Text(stringResource(R.string.auth_enter_manually)) }
}

@Composable private fun AreaEntry(state: OnboardingState, vm: OnboardingViewModel) {
    val title = when (state.role) {
        AccountRole.HOUSEHOLD -> R.string.auth_area_household_title
        AccountRole.RECYCLER -> R.string.auth_area_recycler_title
        AccountRole.COLLECTOR -> R.string.auth_area_title
    }
    val detail = when (state.role) {
        AccountRole.HOUSEHOLD -> R.string.auth_area_household_detail
        AccountRole.RECYCLER -> R.string.auth_area_recycler_detail
        AccountRole.COLLECTOR -> R.string.auth_area_detail
    }
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(detail), style = MaterialTheme.typography.bodyLarge)
    if (state.locationChoice == LocationChoice.GPS && state.area.isNotBlank()) {
        Text(stringResource(R.string.auth_location_detected, state.area), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (state.locationChoice == LocationChoice.GPS && !state.locationError) {
        Text(stringResource(R.string.auth_location_coordinates_saved), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.locationError) {
        Text(stringResource(R.string.auth_location_unavailable), color = MaterialTheme.colorScheme.error)
    }
    OutlinedTextField(state.area, vm::setArea, label = { Text(stringResource(R.string.auth_area_label)) }, leadingIcon = { Icon(Icons.Filled.LocationOn, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("auth_area"))
    if (state.role != AccountRole.RECYCLER) {
        OutlinedTextField(
            state.displayName,
            vm::setDisplayName,
            label = { Text(stringResource(R.string.auth_name_label)) },
            leadingIcon = { Icon(Icons.Filled.Person, null) },
            singleLine = true,
            isError = state.displayNameError,
            supportingText = {
                Text(stringResource(if (state.displayNameError) R.string.auth_name_required_error else R.string.auth_required_field))
            },
            modifier = Modifier.fillMaxWidth().testTag("auth_name")
        )
        OutlinedTextField(state.email, vm::setEmail, label = { Text(stringResource(R.string.auth_email_optional)) }, leadingIcon = { Icon(Icons.Filled.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, isError = state.emailError, supportingText = { if (state.emailError) Text(stringResource(R.string.auth_email_optional_error)) }, modifier = Modifier.fillMaxWidth().testTag("auth_email_optional"))
        Text(stringResource(R.string.auth_email_optional_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    KcPrimaryButton(
        stringResource(R.string.auth_continue_to_phone),
        vm::continueToPhone,
        icon = Icons.Filled.Phone,
        enabled = state.area.isNotBlank() && (state.role == AccountRole.RECYCLER || state.displayName.isNotBlank()) && !state.isBusy,
        testTag = "auth_area_next"
    )
}

@Composable private fun Complete(state: OnboardingState) {
    Spacer(Modifier.height(24.dp)); Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(84.dp))
    Text(stringResource(if (state.role == AccountRole.RECYCLER) R.string.auth_recycler_complete_title else R.string.auth_complete_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (state.role == AccountRole.RECYCLER) R.string.auth_recycler_complete_detail else R.string.auth_complete_detail), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.auth_complete_phone, state.phone), style = MaterialTheme.typography.titleMedium)
            state.displayName.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            state.email.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// Kept as a compatibility boundary for legacy phone-only deployments.
@Composable private fun PhoneEntry(state: OnboardingState, vm: OnboardingViewModel) {
    Text(stringResource(R.string.auth_phone_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_phone_detail), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = state.phone,
        onValueChange = vm::setPhone,
        label = { Text(stringResource(R.string.auth_phone_label)) },
        leadingIcon = { Icon(Icons.Filled.Phone, null) },
        prefix = {
            Text(
                text = "+91",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        placeholder = { Text("10-digit mobile number") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = state.phoneError,
        supportingText = { if (state.phoneError) Text(stringResource(R.string.auth_phone_error)) },
        modifier = Modifier.fillMaxWidth().testTag("auth_phone")
    )
    if (state.authError) Text(stringResource(R.string.auth_network_error), color = MaterialTheme.colorScheme.error)
    KcPrimaryButton(stringResource(if (state.isBusy) R.string.auth_sending_otp else R.string.auth_send_otp), vm::requestOtp, icon = Icons.Filled.Sms, enabled = !state.isBusy, testTag = "auth_send_otp")
    if (state.returningUser) {
        TextButton(
            onClick = vm::useEmailSignIn,
            modifier = Modifier.fillMaxWidth().heightIn(min = KcMinTouchHeight).testTag("auth_use_email")
        ) {
            Text(stringResource(R.string.auth_use_email_instead))
        }
    }
}

@Composable private fun OtpEntry(state: OnboardingState, vm: OnboardingViewModel) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Text(stringResource(R.string.auth_otp_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_otp_detail, state.phone), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(state.otp, vm::setOtp, label = { Text(stringResource(R.string.auth_otp_label)) }, leadingIcon = { Icon(Icons.Filled.Sms, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = state.otpError != null, supportingText = { if (state.otpError != null) Text(stringResource(when (state.otpError) { OtpError.INCORRECT -> R.string.auth_otp_incorrect; OtpError.EXPIRED -> R.string.auth_otp_expired; OtpError.ATTEMPTS_EXCEEDED -> R.string.auth_otp_attempts; OtpError.ACCOUNT_CONFLICT -> R.string.auth_otp_account_conflict; OtpError.SERVER -> R.string.auth_server_error; OtpError.NETWORK -> R.string.auth_network_error })) }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("auth_otp"))
    state.challenge?.developmentCodeHint?.let { Text(stringResource(R.string.auth_dev_otp, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    KcPrimaryButton(stringResource(if (state.isBusy) R.string.auth_verifying else R.string.auth_verify), vm::verifyOtp, icon = Icons.Filled.CheckCircle, enabled = state.otp.length == 6 && !state.isBusy, testTag = "auth_verify")
    OutlinedButton(
        onClick = vm::resetOtp,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().heightIn(min = KcMinTouchHeight).testTag("auth_reset_otp")
    ) {
        Text(stringResource(R.string.auth_change_phone))
    }
}
