package com.irinteractivestudios.kabadiwalaconnect.ui.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel

/** Share a feature across routes while keeping different accounts isolated. */
@Composable
internal inline fun <reified T : ViewModel> accountFeatureViewModel(
    owner: ViewModelStoreOwner,
    accountId: String,
    factory: ViewModelProvider.Factory
): T = viewModel(viewModelStoreOwner = owner, key = "${T::class.java.name}:$accountId", factory = factory)

/** Survives activity recreation, but releases all feature state on account/role changes. */
internal class AccountFeatureScopeViewModel : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var accountKey: String? = null

    fun useAccount(key: String) {
        if (key != accountKey) {
            viewModelStore.clear()
            accountKey = key
        }
    }

    override fun onCleared() { viewModelStore.clear() }
}
