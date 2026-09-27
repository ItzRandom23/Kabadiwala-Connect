package com.irinteractivestudios.kabadiwalaconnect.data.sync

import com.google.gson.JsonParser

/** Rebind a queued pickup request to the server ID assigned to its offline listing. */
internal fun remapHouseholdPickupListingReference(
    payloadJson: String,
    localListingId: String,
    serverListingId: String
): String? {
    val payload = runCatching { JsonParser.parseString(payloadJson).asJsonObject }.getOrNull() ?: return null
    if (payload.get("listingId")?.takeUnless { it.isJsonNull }?.asString != localListingId) return null
    payload.addProperty("listingId", serverListingId)
    return payload.toString()
}
