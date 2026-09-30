package com.irinteractivestudios.kabadiwalaconnect.notifications

import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole

/** Defense in depth for role-specific events, including delayed pushes. */
internal fun pushTypeMatchesRole(type: String?, role: AccountRole?): Boolean {
    if (role == null) return false
    val intended = when (type) {
        "PICKUP_REQUESTED", "PICKUP_REASSIGNED_TO_COLLECTOR", "PICKUP_WAITING_FOR_PICKUP",
        "PICKUP_SETTLEMENT_ACCEPTED", "PICKUP_SETTLEMENT_DISPUTED", "PICKUP_PAYMENT_RECEIVED",
        "BULK_OFFER_SUBMITTED", "BULK_OFFER_WITHDRAWN", "SUPPLY_HANDOVER_RECEIVED", "SUPPLY_HANDOVER_REVIEW_REQUIRED" -> AccountRole.COLLECTOR
        "PICKUP_REQUEST_SENT", "PICKUP_ACCEPTED", "PICKUP_SCHEDULED", "PICKUP_REASSIGNMENT_REQUIRED",
        "PICKUP_ARRIVED", "PICKUP_IN_TRANSIT", "PICKUP_WEIGHED", "PICKUP_PAYMENT_RECORDED" -> AccountRole.HOUSEHOLD
        "BULK_OFFER_REJECTED", "BULK_OFFER_COUNTERED", "BULK_OFFER_ACCEPTED", "HANDOVER_COLLECTOR_CONFIRMED" -> AccountRole.RECYCLER
        else -> null // Shared types remain account-scoped rather than inferred.
    }
    return intended == null || intended == role
}

/** A shared device must not display push for a previous or different account. */
internal fun mayDisplayPush(recipientAccountId: String?, activeAccountId: String?, sessionValid: Boolean): Boolean =
    sessionValid && !recipientAccountId.isNullOrBlank() && recipientAccountId == activeAccountId

internal fun pickupPushCopy(type: String?): Pair<String, String>? = when (type) {
    "PICKUP_REQUESTED", "PICKUP_REASSIGNED_TO_COLLECTOR" -> "New pickup request" to "A household selected you for a pickup. Tap to review it."
    "PICKUP_REQUEST_SENT" -> "Pickup request sent" to "Your request was sent to the Kabadiwala. Tap to view it."
    "PICKUP_WAITING_FOR_PICKUP" -> "Pickup needed nearby" to "A household is waiting for a Kabadiwala. Tap to view pickups."
    else -> null
}
