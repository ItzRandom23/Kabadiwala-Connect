package com.irinteractivestudios.kabadiwalaconnect

import android.graphics.Bitmap
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainViewModel
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class CollectorPhotoLoadingTest {
    @Test
    fun successfulPhotoIsStoredAndRepeatTapDoesNotDownloadAgain() = runBlocking {
        val photo = validPng()
        val requests = AtomicInteger()
        val vm = viewModelFor(photo, requests)

        vm.loadKabadiwalaListingPhotos("listing-1", 1)
        val loaded = withTimeout(5_000) {
            vm.state.first { it.listingPhotos["listing-1"]?.isNotEmpty() == true }
        }
        assertArrayEquals(photo, loaded.listingPhotos["listing-1"]!!.single())
        assertEquals(null, loaded.listingPhotoErrors["listing-1"])

        vm.loadKabadiwalaListingPhotos("listing-1", 1)
        assertEquals(1, requests.get())
    }

    @Test
    fun unreadableHttp200PhotoShowsAnError() = runBlocking {
        val vm = viewModelFor("not an image".toByteArray(), AtomicInteger())

        vm.loadKabadiwalaListingPhotos("listing-2", 1)
        val failed = withTimeout(5_000) {
            vm.state.first { it.listingPhotoErrors["listing-2"] != null }
        }
        assertTrue(failed.listingPhotos["listing-2"].isNullOrEmpty())
        assertTrue(failed.listingPhotoErrors["listing-2"]!!.contains("unreadable photo"))
    }

    private fun viewModelFor(photo: ByteArray, requests: AtomicInteger): SupplyChainViewModel {
        val api = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getKabadiwalaListingPhoto" -> {
                    requests.incrementAndGet()
                    Response.success(photo.toResponseBody("image/png".toMediaType()))
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        } as ApiService
        return SupplyChainViewModel(
            api = api,
            roleProvider = { AccountRole.COLLECTOR },
            accountIdProvider = { "collector-1" },
            authenticatedSessionReady = { true }
        )
    }

    private fun validPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
