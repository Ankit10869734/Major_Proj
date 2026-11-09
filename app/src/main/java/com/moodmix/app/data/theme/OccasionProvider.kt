package com.moodmix.app.data.theme

import androidx.compose.ui.graphics.Color
import com.moodmix.app.domain.model.AppRegion
import com.moodmix.app.domain.model.Occasion
import com.moodmix.app.domain.model.OccasionType
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Regional occasion provider evaluating seasonal events and device/manual regions.
 *
 * DIWALI DATASET SOURCE & TIMEZONE CONTEXT:
 * Dates represent main Diwali (Laxmi Pujan / Amavasya Tithi) evaluated in Indian Standard Time
 * (IST / Asia/Kolkata, UTC+5:30) derived from official Drik Panchang astronomical calculations:
 * Source URL: https://www.drikpanchang.com/diwali/diwali-calendar.html
 *
 * Specific Date References (2024–2030):
 * - 2024: October 31, 2024 (Kartika Amavasya) -> Ref: https://www.drikpanchang.com/diwali/diwali-calendar.html?year=2024
 * - 2025: October 20, 2025 (Kartika Amavasya) -> Ref: https://www.drikpanchang.com/diwali/diwali-calendar.html?year=2025
 * - 2026: November 8, 2026 (Kartika Amavasya) -> Ref: https://www.drikpanchang.com/diwali/diwali-calendar.html?year=2026
 * - 2027: October 29, 2027 (Kartika Amavasya) -> Ref: https://www.drikpanchang.com/diwali/diwali-calendar.html?year=2027
 * - 2028: October 17, 2028 (Kartika Amavasya) -> Ref: https://www.drikpanchang.com/diwali/diwali-calendar.html?year=2028
 * - 2029: November 5, 2029 (Kartika Amavasya) -> Ref: https://www.drikpanchang.com/diwali/diwali-calendar.html?year=2029
 * - 2030: October 26, 2030 (Kartika Amavasya) -> Ref: https://www.drikpanchang.com/diwali/diwali-calendar.html?year=2030
 *
 * ARCHITECTURAL TRADE-OFF NOTE:
 * This dataset is a curated, deterministic local dataset. It is NOT a live network integration.
 * Using a local dataset eliminates network latency, offline failures, and API costs.
 * Maintenance requirement: Extending this dataset beyond 2030 requires updating the lookup map.
 * For years outside this verified dataset, null is safely returned (falling back to standard theme).
 */
object OccasionProvider {

    private val diwaliDatesIst = mapOf(
        2024 to (Calendar.OCTOBER to 31),
        2025 to (Calendar.OCTOBER to 20),
        2026 to (Calendar.NOVEMBER to 8),
        2027 to (Calendar.OCTOBER to 29),
        2028 to (Calendar.OCTOBER to 17),
        2029 to (Calendar.NOVEMBER to 5),
        2030 to (Calendar.OCTOBER to 26)
    )

    fun resolveCountryCode(selectedRegion: AppRegion, defaultLocale: Locale = Locale.getDefault()): String {
        return when (selectedRegion) {
            AppRegion.AUTOMATIC -> {
                val country = defaultLocale.country.uppercase()
                if (country.length == 2) country else "UNKNOWN"
            }
            AppRegion.INDIA -> "IN"
            AppRegion.UNITED_STATES -> "US"
            AppRegion.GERMANY -> "DE"
            AppRegion.JAPAN -> "JP"
            AppRegion.STANDARD_DARK -> "NONE"
        }
    }

    fun getActiveOccasion(
        countryCode: String,
        calendar: Calendar = Calendar.getInstance(),
        isAutoThemeEnabled: Boolean = true,
        selectedRegion: AppRegion = AppRegion.AUTOMATIC
    ): Occasion? {
        if (selectedRegion == AppRegion.STANDARD_DARK) return null
        if (!isAutoThemeEnabled && selectedRegion == AppRegion.AUTOMATIC) return null

        val targetCountry = if (selectedRegion != AppRegion.AUTOMATIC) {
            resolveCountryCode(selectedRegion)
        } else {
            val resolved = countryCode.uppercase()
            if (resolved.length == 2) resolved else "UNKNOWN"
        }

        if (targetCountry == "NONE" || targetCountry == "UNKNOWN") return null

        if (targetCountry == "IN") {
            if (isDiwaliDate(calendar)) {
                return Occasion(
                    type = OccasionType.DIWALI,
                    name = "Diwali Festival",
                    regionCode = "IN",
                    primaryColor = Color(0xFFFFD700), // Gold
                    secondaryColor = Color(0xFFFF8F00), // Festive Amber-Orange
                    accentColor = Color(0xFFFF6584), // Bright Festive Pink
                    festiveMoods = listOf(
                        "🪔 Diwali Beats" to "Upbeat Indian festive celebration instrumental music",
                        "✨ Festive Joy" to "Joyful festive folk pop rhythm",
                        "🌸 Devotional Flow" to "Peaceful devotional sitar ambient music"
                    )
                )
            }
        }
        return null
    }

    fun isDiwaliDate(calendar: Calendar): Boolean {
        val istCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            timeInMillis = calendar.timeInMillis
        }
        val year = istCalendar.get(Calendar.YEAR)
        val month = istCalendar.get(Calendar.MONTH)
        val day = istCalendar.get(Calendar.DAY_OF_MONTH)

        val expected = diwaliDatesIst[year] ?: return false
        return (month == expected.first && day == expected.second)
    }
}
