package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.HouseholdListingDto
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.pickupDirectionsDestination
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.pickupDirectionsUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickupDirectionsTest {
    @Test
    fun savedAddressTakesPriorityOverPhoneCoordinates() {
        val listing = HouseholdListingDto(pickupAddress = " 5, Geeta Colony Road, Delhi ", latitude = 13.0, longitude = 80.0)
        assertEquals("5, Geeta Colony Road, Delhi", pickupDirectionsDestination(listing))
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=5%2C+Geeta+Colony+Road%2C+Delhi",
            pickupDirectionsUrl(pickupDirectionsDestination(listing)!!)
        )
    }

    @Test
    fun coordinatesWorkOnlyWhenSavedAddressIsMissing() {
        assertEquals("28.6,77.2", pickupDirectionsDestination(HouseholdListingDto(latitude = 28.6, longitude = 77.2)))
        assertNull(pickupDirectionsDestination(HouseholdListingDto(latitude = 0.0, longitude = 0.0)))
        assertNull(pickupDirectionsDestination(HouseholdListingDto(areaName = "Delhi")))
    }
}
