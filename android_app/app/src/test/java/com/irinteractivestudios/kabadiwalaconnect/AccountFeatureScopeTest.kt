package com.irinteractivestudios.kabadiwalaconnect.ui.util

import androidx.lifecycle.ViewModel
import org.junit.Assert.*
import org.junit.Test

class AccountFeatureScopeTest {
    private class Feature : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test fun sameAccountReusesStateButSwitchAndLogoutReleaseIt() {
        val scope = AccountFeatureScopeViewModel()
        scope.useAccount("collector-1:COLLECTOR")
        val first = Feature()
        scope.viewModelStore.put("feature", first)
        scope.useAccount("collector-1:COLLECTOR")
        assertSame(first, scope.viewModelStore.get("feature"))
        assertFalse(first.cleared)
        scope.useAccount("recycler-2:RECYCLER")
        assertTrue(first.cleared)
        assertNull(scope.viewModelStore.get("feature"))
        val second = Feature()
        scope.viewModelStore.put("feature", second)
        scope.useAccount(":HOUSEHOLD")
        assertTrue(second.cleared)
        assertNull(scope.viewModelStore.get("feature"))
    }
}
