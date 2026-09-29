package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionCoordinator
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.AccountProfileViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.ProfileEditDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountProfileViewModelTest {
    private val recycler = AccountProfile("user-1", "old@example.com", AccountRole.RECYCLER,
        profileId = "recycler-1", businessName = "Old facility", displayName = "Old facility", address = "Old street")
    private val draft = ProfileEditDraft("New facility", "new@example.com", "", "New street")

    @Test fun saveShowsEditedDetailsBeforeNetworkCompletes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = SessionCoordinator().apply { authenticated(recycler) }
            val response = CompletableDeferred<AccountProfile>()
            val vm = AccountProfileViewModel(session.snapshot) { response.await() }
            vm.save(draft)
            assertEquals("New facility", vm.state.value.profile?.businessName)
            assertEquals("new@example.com", vm.state.value.profile?.email)
            assertTrue(vm.state.value.saving)
            runCurrent()
            response.complete(recycler.copy(businessName = "New facility", displayName = "New facility", email = "new@example.com", address = "New street"))
            runCurrent()
            assertFalse(vm.state.value.saving)
            assertEquals("New facility", vm.state.value.profile?.businessName)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedSaveRestoresConfirmedDetails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = SessionCoordinator().apply { authenticated(recycler) }
            val response = CompletableDeferred<AccountProfile>()
            val vm = AccountProfileViewModel(session.snapshot) { response.await() }
            vm.save(draft)
            runCurrent()
            response.completeExceptionally(IllegalStateException("offline"))
            runCurrent()
            assertEquals("Old facility", vm.state.value.profile?.businessName)
            assertFalse(vm.state.value.saving)
            assertTrue(vm.state.value.saveError?.contains("Could not save") == true)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun oldAccountResponseCannotReplaceNewAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = SessionCoordinator().apply { authenticated(recycler) }
            val response = CompletableDeferred<AccountProfile>()
            val vm = AccountProfileViewModel(session.snapshot) { response.await() }
            runCurrent()
            vm.save(draft)
            runCurrent()
            val next = recycler.copy(id = "user-2", profileId = "recycler-2", businessName = "Second facility")
            session.authenticated(next)
            runCurrent()
            response.complete(recycler.copy(businessName = "New facility"))
            runCurrent()
            assertEquals("user-2", vm.state.value.profile?.id)
            assertEquals("Second facility", vm.state.value.profile?.businessName)
        } finally { Dispatchers.resetMain() }
    }
}
