package com.irinteractivestudios.kabadiwalaconnect.util

/**
 * Shared screen-state model for the app's cache-backed screens.
 *
 * Offline-first rule: screens always render from local state first.
 * Server-dependent actions are represented as [Syncing] (queued) or
 * [Offline] (unavailable until connectivity returns); the sync worker
 * reconciles queued mutations when the network is available.
 */
sealed interface UiState<out T> {
    /** Initial load from the local database / cache. */
    data object Loading : UiState<Nothing>

    /** No cached rows to show yet (normal for a fresh install). */
    data object Empty : UiState<Nothing>

    /** Device is offline; [cached] holds whatever was last saved. */
    data class Offline<T>(val cached: T? = null) : UiState<T>

    /** Local data loaded successfully. */
    data class Success<T>(val data: T) : UiState<T>

    /** Work was queued and will sync when connectivity returns. */
    data class Syncing<T>(val cached: T? = null) : UiState<T>

    /** Something went wrong locally (DB error, etc.). Retry is offered. */
    data class Error(val message: String? = null) : UiState<Nothing>
}
