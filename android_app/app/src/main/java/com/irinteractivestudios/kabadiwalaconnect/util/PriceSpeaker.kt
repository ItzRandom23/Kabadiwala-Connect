package com.irinteractivestudios.kabadiwalaconnect.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface PriceSpeaker { fun speak(material: String, rate: Double, languageTag: String); fun shutdown() }
class AndroidPriceSpeaker(context: Context) : PriceSpeaker {
    private val tts = TextToSpeech(context.applicationContext) { }
    override fun speak(material: String, rate: Double, languageTag: String) {
        val requested = Locale.forLanguageTag(LocaleManager.normalizeTag(languageTag) + "-IN")
        val result = tts.setLanguage(requested)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.ENGLISH
        }
        tts.speak("$material. Rupees ${rate.toInt()} per kilogram.", TextToSpeech.QUEUE_FLUSH, null, "kc-price")
    }
    override fun shutdown() { tts.stop(); tts.shutdown() }
}
class RecordingPriceSpeaker : PriceSpeaker {
    var lastMessage: String? = null
    override fun speak(material: String, rate: Double, languageTag: String) { lastMessage = "$material:$rate:$languageTag" }
    override fun shutdown() = Unit
}
