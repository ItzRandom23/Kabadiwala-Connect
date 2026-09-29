package com.irinteractivestudios.kabadiwalaconnect.data.auth

import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole

/** This grants cached viewing only; it never makes a server session valid. */
object OfflineAccessPolicy {
    const val WINDOW_MS = 7L * 24L * 60L * 60L * 1000L

    fun eligible(account: AccountProfile?, lastValidatedAtEpochMs: Long?, nowEpochMs: Long): Boolean {
        val validatedAt = lastValidatedAtEpochMs ?: return false
        return account != null && account.role != AccountRole.ADMIN &&
            account.accountStatus == "ACTIVE" && validatedAt > 0L &&
            nowEpochMs >= validatedAt && nowEpochMs - validatedAt <= WINDOW_MS
    }
}
