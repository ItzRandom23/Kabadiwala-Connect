package com.irinteractivestudios.kabadiwalaconnect.data.auth

import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionState {
    RESTORING,
    UNAUTHENTICATED,
    AUTHENTICATED,
    EXPIRED
}

data class SessionSnapshot(
    val state: SessionState,
    val account: AccountProfile?
) {
    val restorable: Boolean get() = state == SessionState.AUTHENTICATED && account != null
}

/** Single process-local source of truth for session restoration and account scope. */
class SessionCoordinator {
    private val _snapshot = MutableStateFlow(SessionSnapshot(SessionState.RESTORING, null))
    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    fun beginRestoration() {
        _snapshot.value = SessionSnapshot(SessionState.RESTORING, null)
    }

    fun authenticated(account: AccountProfile) {
        _snapshot.value = SessionSnapshot(SessionState.AUTHENTICATED, account)
    }

    fun unauthenticated() {
        _snapshot.value = SessionSnapshot(SessionState.UNAUTHENTICATED, null)
    }

    fun expired() {
        _snapshot.value = SessionSnapshot(SessionState.EXPIRED, null)
    }
}
