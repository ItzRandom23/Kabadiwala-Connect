package com.irinteractivestudios.kabadiwalaconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.irinteractivestudios.kabadiwalaconnect.util.KeystoreSecureStorage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SecureStorageRotationTest {
    @Test
    fun readsTheLatestValueAfterRefreshTokenRotation() {
        val storage = KeystoreSecureStorage(InstrumentationRegistry.getInstrumentation().targetContext)
        val key = "refresh_rotation_test_${UUID.randomUUID()}"

        try {
            storage.put(key, "old-refresh-token")
            assertEquals("old-refresh-token", storage.get(key))

            storage.put(key, "rotated-refresh-token")
            assertEquals("rotated-refresh-token", storage.get(key))
        } finally {
            storage.remove(key)
        }
    }
}
