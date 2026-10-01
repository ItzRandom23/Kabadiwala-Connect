package com.irinteractivestudios.kabadiwalaconnect.util

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

/** Both handover scanners use a portrait camera surface. */
class PortraitQrCaptureActivity : CaptureActivity()

fun portraitQrScanOptions(prompt: String): ScanOptions = ScanOptions()
    .setCaptureActivity(PortraitQrCaptureActivity::class.java)
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    // The manifest owns orientation; do not let CaptureManager lock to a
    // device rotation captured while the camera activity was opening.
    .setOrientationLocked(false)
    .setBeepEnabled(false)
    .setPrompt(prompt)
