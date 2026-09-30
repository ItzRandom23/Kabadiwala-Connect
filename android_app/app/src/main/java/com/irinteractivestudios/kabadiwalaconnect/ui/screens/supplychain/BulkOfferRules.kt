package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

internal fun isValidBulkOffer(rate: Double?, minimumRate: Double?): Boolean =
    rate != null && rate.isFinite() && rate > 0 && rate <= 1_000_000 &&
        (minimumRate == null || rate >= minimumRate)

internal fun canReviseBulkOffer(lotStatus: String, offerStatus: String?): Boolean =
    lotStatus == "LISTED" && offerStatus in setOf(null, "PENDING", "CANCELLED", "REJECTED")
