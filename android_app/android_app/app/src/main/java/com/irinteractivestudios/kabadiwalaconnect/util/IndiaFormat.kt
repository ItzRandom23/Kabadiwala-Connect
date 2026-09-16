package com.irinteractivestudios.kabadiwalaconnect.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Consistent business formatting for records created and settled in India. */
object IndiaFormat {
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val numberLocale: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun date(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("d MMM yyyy", locale).apply { timeZone = zone }.format(Date(epochMs))

    fun shortDate(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("dd/MM/yy", locale).apply { timeZone = zone }.format(Date(epochMs))

    fun dateTime(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("d MMM yyyy, h:mm a", locale).apply { timeZone = zone }.format(Date(epochMs))

    fun number(value: Double, decimals: Int = 0): String =
        NumberFormat.getNumberInstance(numberLocale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)
}
