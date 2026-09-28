package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.RetrofitProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicEndpointPolicyTest {
    @Test
    fun materialCategoriesCanLoadBeforeSignIn() {
        assertTrue(RetrofitProvider.isPublicAuthEndpoint("/api/v1/materials/categories"))
        assertFalse(RetrofitProvider.isPublicAuthEndpoint("/api/v1/household/listings"))
        assertFalse(RetrofitProvider.isPublicAuthEndpoint("/api/v1/auth/profile"))
    }
}
