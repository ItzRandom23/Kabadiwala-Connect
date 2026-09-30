package com.irinteractivestudios.kabadiwalaconnect.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataChangeEventsTest {
    @Test fun onlyDataWritesInvalidateScreens() {
        assertTrue(DataChangeEvents.invalidatesData("POST", "/api/v1/kabadiwala/pickups/p1/complete"))
        assertTrue(DataChangeEvents.invalidatesData("PATCH", "/api/v1/recycler/profile"))
        assertTrue(DataChangeEvents.invalidatesData("DELETE", "/api/v1/household/listings/l1"))
        assertFalse(DataChangeEvents.invalidatesData("GET", "/api/v1/recycler/offers"))
        assertFalse(DataChangeEvents.invalidatesData("POST", "/api/v1/auth/refresh"))
        assertFalse(DataChangeEvents.invalidatesData("POST", "/api/v1/notifications/devices"))
        assertFalse(DataChangeEvents.invalidatesData("POST", "/api/v1/future/lots/material-suggestion"))
    }

    @Test fun hintsRetainTheOwnerAndSuppressUnauthenticatedWrites() = runTest {
        val events = mutableListOf<DataChangeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { DataChangeEvents.events.collect { events += it } }
        DataChangeEvents.publish(null, "/ignored")
        DataChangeEvents.publish("", "/ignored")
        DataChangeEvents.publish("collector-1", "/complete")
        DataChangeEvents.publish("recycler-2", "/offer")
        assertEquals(listOf(DataChangeEvent("collector-1", "/complete"), DataChangeEvent("recycler-2", "/offer")), events)
    }
}
