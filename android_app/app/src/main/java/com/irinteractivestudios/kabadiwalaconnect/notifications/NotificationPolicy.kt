package com.irinteractivestudios.kabadiwalaconnect.notifications

/** A shared device must not display push for a previous or different account. */
internal fun mayDisplayPush(recipientAccountId: String?, activeAccountId: String?, sessionValid: Boolean): Boolean =
    sessionValid && !recipientAccountId.isNullOrBlank() && recipientAccountId == activeAccountId

internal fun pickupPushCopy(type: String?): Pair<String, String>? = when (type) {
    "PICKUP_REQUESTED", "PICKUP_REASSIGNED_TO_COLLECTOR" -> "New pickup request" to "A household selected you for a pickup. Tap to review it."
    "PICKUP_REQUEST_SENT" -> "Pickup request sent" to "Your request was sent to the Kabadiwala. Tap to view it."
    "PICKUP_WAITING_FOR_PICKUP" -> "Pickup needed nearby" to "A household is waiting for a Kabadiwala. Tap to view pickups."
    else -> null
}
