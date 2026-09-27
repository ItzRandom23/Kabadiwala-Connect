package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import android.location.Geocoder
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val INDIA_SOUTH = 6.4
private const val INDIA_WEST = 68.0
private const val INDIA_NORTH = 37.2
private const val INDIA_EAST = 97.5
private const val INDIA_DEFAULT_LAT = 22.5
private const val INDIA_DEFAULT_LON = 82.0

private data class PickupPoint(val latitude: Double, val longitude: Double)

/** A tap-to-place map for the pickup address; it never reads the device GPS. */
@Composable
internal fun PickupPointPickerDialog(
    initialAddress: String,
    selectedLatitude: Double?,
    selectedLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchText by rememberSaveable(initialAddress) { mutableStateOf(initialAddress) }
    var selected by remember(selectedLatitude, selectedLongitude) {
        mutableStateOf(
            if (selectedLatitude != null && selectedLongitude != null) PickupPoint(selectedLatitude, selectedLongitude) else null
        )
    }
    var searching by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var mapView by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Set pickup point", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Search your pickup address or move the map, then tap the exact pickup spot. This pin is separate from your current phone location.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Pickup address or area in India") },
                    trailingIcon = {
                        TextButton(
                            enabled = searchText.isNotBlank() && !searching,
                            onClick = {
                                searching = true
                                message = null
                                scope.launch {
                                    val result = geocodeIndiaAddress(context, searchText.trim())
                                    searching = false
                                    if (result == null) {
                                        message = "Couldn't find that address in India. Try a nearby locality or move the map manually."
                                    } else {
                                        mapView?.evaluateJavascript("window.moveToArea(${result.latitude},${result.longitude});", null)
                                    }
                                }
                            }
                        ) {
                            if (searching) CircularProgressIndicator(Modifier.heightIn(max = 20.dp), strokeWidth = 2.dp) else Text("Find")
                        }
                    }
                )
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            mapView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.userAgentString = "${settings.userAgentString} KabadiwalaConnect/0.1.1"
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                            }
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onPin(latitude: Double, longitude: Double) {
                                    if (latitude.isFinite() && longitude.isFinite() && isWithinIndiaMapBounds(latitude, longitude)) {
                                        this@apply.post {
                                            selected = PickupPoint(latitude, longitude)
                                            message = null
                                        }
                                    }
                                }
                            }, "PickupPin")
                            setBackgroundColor(android.graphics.Color.rgb(232, 239, 231))
                            loadDataWithBaseURL("https://pickup-map.invalid/", indiaMapHtml(viewContext, selected), "text/html", "UTF-8", null)
                        }
                    },
                    update = { view -> mapView = view }
                )
                Text(
                    selected?.let { "Selected pin: %.5f, %.5f".format(Locale.US, it.latitude, it.longitude) }
                        ?: "Tap the map to place a pickup pin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Map data © OpenStreetMap contributors. Map tiles are provided on a best-effort basis; an internet connection is needed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, enabled = !confirming, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        enabled = selected != null && !confirming,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val point = selected ?: return@Button
                            confirming = true
                            message = null
                            scope.launch {
                                val inIndia = reverseGeocodeIsIndia(context, point.latitude, point.longitude)
                                confirming = false
                                if (inIndia) onConfirm(point.latitude, point.longitude)
                                else message = "That pin couldn't be verified as being in India. Choose a point inside India and try again."
                            }
                        }
                    ) {
                        if (confirming) CircularProgressIndicator(Modifier.heightIn(max = 20.dp), strokeWidth = 2.dp)
                        else Text("Use this pickup point")
                    }
                }
            }
        }
    }
}

private fun isWithinIndiaMapBounds(latitude: Double, longitude: Double): Boolean =
    latitude in INDIA_SOUTH..INDIA_NORTH && longitude in INDIA_WEST..INDIA_EAST

private suspend fun geocodeIndiaAddress(context: android.content.Context, query: String): PickupPoint? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale("en", "IN")).getFromLocationName(
            query, 5, INDIA_SOUTH, INDIA_WEST, INDIA_NORTH, INDIA_EAST
        ).orEmpty().firstOrNull { it.countryCode.equals("IN", ignoreCase = true) }
            ?.let { PickupPoint(it.latitude, it.longitude) }
    }.getOrNull()
}

private suspend fun reverseGeocodeIsIndia(context: android.content.Context, latitude: Double, longitude: Double): Boolean = withContext(Dispatchers.IO) {
    if (!isWithinIndiaMapBounds(latitude, longitude) || !Geocoder.isPresent()) return@withContext false
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale("en", "IN")).getFromLocation(latitude, longitude, 5).orEmpty()
            .firstOrNull()?.countryCode.equals("IN", ignoreCase = true)
    }.getOrDefault(false)
}

private fun indiaMapHtml(context: android.content.Context, initialPin: PickupPoint?): String {
    val bounds = "[[${INDIA_SOUTH},${INDIA_WEST}],[${INDIA_NORTH},${INDIA_EAST}]]"
    val leafletCss = context.assets.open("leaflet/leaflet.css").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    val leafletJs = context.assets.open("leaflet/leaflet.js").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    val initialMap = if (initialPin != null && isWithinIndiaMapBounds(initialPin.latitude, initialPin.longitude)) {
        "map.setView([${initialPin.latitude},${initialPin.longitude}],16);setPin(${initialPin.latitude},${initialPin.longitude});"
    } else ""
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>$leafletCss html,body,#map{height:100%;width:100%;margin:0;background:#e8efe7} .leaflet-control-attribution{font-size:10px} .pickup-pin{width:24px;height:24px;border-radius:50% 50% 50% 0;background:#2d6a46;border:3px solid white;transform:rotate(-45deg);box-shadow:0 2px 6px #0008}</style>
        <script>$leafletJs</script></head><body><div id="map"></div>
        <script>
          const bounds=L.latLngBounds($bounds);
          const map=L.map('map',{maxBounds:bounds,maxBoundsViscosity:1,minZoom:4,maxZoom:19,zoomControl:true}).setView([$INDIA_DEFAULT_LAT,$INDIA_DEFAULT_LON],5);
          L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'}).addTo(map);
          let pin=null;
          function setPin(lat,lon){ if(!bounds.contains([lat,lon])) return; if(pin)map.removeLayer(pin); const icon=L.divIcon({className:'',html:'<div class="pickup-pin"></div>',iconSize:[24,24],iconAnchor:[12,24]}); pin=L.marker([lat,lon],{icon:icon}).addTo(map); }
          map.on('click',function(e){ if(!bounds.contains(e.latlng))return; setPin(e.latlng.lat,e.latlng.lng); PickupPin.onPin(e.latlng.lat,e.latlng.lng); });
          window.moveToArea=function(lat,lon){ if(!bounds.contains([lat,lon]))return; map.setView([lat,lon],16); };
          $initialMap
        </script></body></html>
    """.trimIndent()
}
