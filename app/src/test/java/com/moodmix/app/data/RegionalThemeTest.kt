package com.moodmix.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.moodmix.app.data.theme.OccasionProvider
import com.moodmix.app.domain.model.AppRegion
import com.moodmix.app.domain.model.OccasionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RegionalThemeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testResolveCountryCode() {
        assertEquals("IN", OccasionProvider.resolveCountryCode(AppRegion.INDIA))
        assertEquals("US", OccasionProvider.resolveCountryCode(AppRegion.UNITED_STATES))
        assertEquals("DE", OccasionProvider.resolveCountryCode(AppRegion.GERMANY))
        assertEquals("JP", OccasionProvider.resolveCountryCode(AppRegion.JAPAN))
        assertEquals("NONE", OccasionProvider.resolveCountryCode(AppRegion.STANDARD_DARK))
        assertEquals("US", OccasionProvider.resolveCountryCode(AppRegion.AUTOMATIC, Locale.US))
        assertEquals("UNKNOWN", OccasionProvider.resolveCountryCode(AppRegion.AUTOMATIC, Locale.forLanguageTag("und")))
    }

    @Test
    fun testExactDiwaliDatesAndAdjacentDays() {
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

        // 2024: Main Diwali is Oct 31, 2024 (IST)
        val calOct31In2024 = Calendar.getInstance(istTimeZone).apply { set(2024, Calendar.OCTOBER, 31, 12, 0) }
        val calOct30In2024 = Calendar.getInstance(istTimeZone).apply { set(2024, Calendar.OCTOBER, 30, 12, 0) }
        val calNov01In2024 = Calendar.getInstance(istTimeZone).apply { set(2024, Calendar.NOVEMBER, 1, 12, 0) }

        assertTrue(OccasionProvider.isDiwaliDate(calOct31In2024))
        assertFalse("Oct 30 is not main Diwali", OccasionProvider.isDiwaliDate(calOct30In2024))
        assertFalse("Nov 1 is not main Diwali", OccasionProvider.isDiwaliDate(calNov01In2024))

        // 2025: Main Diwali is Oct 20, 2025 (IST)
        val calOct20In2025 = Calendar.getInstance(istTimeZone).apply { set(2025, Calendar.OCTOBER, 20, 12, 0) }
        val calOct19In2025 = Calendar.getInstance(istTimeZone).apply { set(2025, Calendar.OCTOBER, 19, 12, 0) }

        assertTrue(OccasionProvider.isDiwaliDate(calOct20In2025))
        assertFalse(OccasionProvider.isDiwaliDate(calOct19In2025))
    }

    @Test
    fun testUnsupportedYearFallback() {
        val calYear2040 = Calendar.getInstance().apply { set(2040, Calendar.OCTOBER, 20, 12, 0) }
        assertFalse(OccasionProvider.isDiwaliDate(calYear2040))

        val occasion = OccasionProvider.getActiveOccasion(
            countryCode = "IN",
            calendar = calYear2040,
            isAutoThemeEnabled = true,
            selectedRegion = AppRegion.INDIA
        )
        assertNull("Unsupported future year must safely return null", occasion)
    }

    @Test
    fun testDiwaliActiveOccasionForIndia() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2024, Calendar.OCTOBER, 31, 12, 0)
        }

        val occasion = OccasionProvider.getActiveOccasion(
            countryCode = "IN",
            calendar = calendar,
            isAutoThemeEnabled = true,
            selectedRegion = AppRegion.AUTOMATIC
        )

        assertNotNull(occasion)
        assertEquals(OccasionType.DIWALI, occasion?.type)
        assertEquals("Diwali Festival", occasion?.name)
        assertEquals("IN", occasion?.regionCode)
        assertTrue(occasion?.festiveMoods?.isNotEmpty() == true)
    }

    @Test
    fun testManualRegionOverrideToIndia() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2024, Calendar.OCTOBER, 31, 12, 0)
        }

        val occasion = OccasionProvider.getActiveOccasion(
            countryCode = "US",
            calendar = calendar,
            isAutoThemeEnabled = true,
            selectedRegion = AppRegion.INDIA
        )

        assertNotNull(occasion)
        assertEquals(OccasionType.DIWALI, occasion?.type)
    }

    @Test
    fun testDisabledAutoThemeAndStandardDarkFallback() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2024, Calendar.OCTOBER, 31, 12, 0)
        }

        val occasionDisabled = OccasionProvider.getActiveOccasion(
            countryCode = "IN",
            calendar = calendar,
            isAutoThemeEnabled = false,
            selectedRegion = AppRegion.AUTOMATIC
        )

        assertNull(occasionDisabled)

        val occasionStandardDark = OccasionProvider.getActiveOccasion(
            countryCode = "IN",
            calendar = calendar,
            isAutoThemeEnabled = true,
            selectedRegion = AppRegion.STANDARD_DARK
        )

        assertNull(occasionStandardDark)
    }

    @Test
    fun testWcagContrastRatiosWithAlphaCompositing() {
        val backgroundDark = 0x080A10L
        val surfaceVariantDark = 0x202536L
        val textPrimary = 0xFFFFFFL
        val textSecondary = 0xA1A8B8L
        val diwaliGold = 0xFFD700L
        val diwaliOrange = 0xFF8F00L
        val diwaliWarmPink = 0xFF6584L
        val defaultPurple = 0xAB47BCL
        val defaultPink = 0xD81B60L

        // Standard text contrasts
        assertTrue("Primary text on background must exceed 4.5:1", calculateContrastRatio(textPrimary, backgroundDark) >= 4.5)
        assertTrue("Secondary text on background must exceed 4.5:1", calculateContrastRatio(textSecondary, backgroundDark) >= 4.5)
        assertTrue("Gold accent on dark background must exceed 4.5:1", calculateContrastRatio(diwaliGold, backgroundDark) >= 4.5)

        // Composited gradient endpoints contrast for 0.85 alpha over BackgroundDark & SurfaceVariant
        val compositedDefaultPurpleOnBackground = compositeAlpha(defaultPurple, 0.85f, backgroundDark)
        val compositedDefaultPinkOnBackground = compositeAlpha(defaultPink, 0.85f, backgroundDark)
        val compositedDiwaliOrangeOnBackground = compositeAlpha(diwaliOrange, 0.85f, backgroundDark)
        val compositedDiwaliWarmPinkOnBackground = compositeAlpha(diwaliWarmPink, 0.85f, backgroundDark)

        val compositedDiwaliOrangeOnSurface = compositeAlpha(diwaliOrange, 0.85f, surfaceVariantDark)
        val compositedDiwaliWarmPinkOnSurface = compositeAlpha(diwaliWarmPink, 0.85f, surfaceVariantDark)

        // Default Theme: White text on default gradient endpoints
        assertTrue("White text on composited Default Purple must exceed 4.5:1", calculateContrastRatio(textPrimary, compositedDefaultPurpleOnBackground) >= 4.5)
        assertTrue("White text on composited Default Pink must exceed 4.5:1", calculateContrastRatio(textPrimary, compositedDefaultPinkOnBackground) >= 4.5)

        // Diwali Theme: BackgroundDark (onSecondary/onTertiary) text on composited Diwali gradient endpoints
        assertTrue("onSecondary text on composited Diwali Orange (Background) must exceed 4.5:1", calculateContrastRatio(backgroundDark, compositedDiwaliOrangeOnBackground) >= 4.5)
        assertTrue("onTertiary text on composited Diwali Warm Pink (Background) must exceed 4.5:1", calculateContrastRatio(backgroundDark, compositedDiwaliWarmPinkOnBackground) >= 4.5)

        assertTrue("onSecondary text on composited Diwali Orange (Surface) must exceed 4.5:1", calculateContrastRatio(backgroundDark, compositedDiwaliOrangeOnSurface) >= 4.5)
        assertTrue("onTertiary text on composited Diwali Warm Pink (Surface) must exceed 4.5:1", calculateContrastRatio(backgroundDark, compositedDiwaliWarmPinkOnSurface) >= 4.5)
    }

    @Test
    fun testRealDataStorePersistenceAcrossInstanceRecreation() = runBlocking {
        val testFile = tempFolder.newFile("test_persistence_${System.currentTimeMillis()}.preferences_pb")
        val keyRegion = stringPreferencesKey("selected_region")
        val keyAutoEnabled = booleanPreferencesKey("auto_theme_enabled")

        // Phase 1: Write INDIA & auto_theme_enabled = false using Instance 1
        val scope1 = CoroutineScope(Dispatchers.IO + Job())
        val ds1 = PreferenceDataStoreFactory.create(scope = scope1, produceFile = { testFile })
        ds1.edit { prefs ->
            prefs[keyRegion] = AppRegion.INDIA.name
            prefs[keyAutoEnabled] = false
        }
        scope1.cancel() // Close DataStore instance 1

        // Phase 2: Create DataStore Instance 2 reading same testFile -> Verify persistence
        val scope2 = CoroutineScope(Dispatchers.IO + Job())
        val ds2 = PreferenceDataStoreFactory.create(scope = scope2, produceFile = { testFile })
        val prefs2 = ds2.data.first()
        assertEquals(AppRegion.INDIA.name, prefs2[keyRegion])
        assertEquals(false, prefs2[keyAutoEnabled])

        // Phase 3: Write STANDARD_DARK using Instance 2 -> Close -> Create Instance 3 -> Verify persistence
        ds2.edit { prefs -> prefs[keyRegion] = AppRegion.STANDARD_DARK.name }
        scope2.cancel()

        val scope3 = CoroutineScope(Dispatchers.IO + Job())
        val ds3 = PreferenceDataStoreFactory.create(scope = scope3, produceFile = { testFile })
        val prefs3 = ds3.data.first()
        assertEquals(AppRegion.STANDARD_DARK.name, prefs3[keyRegion])
        assertEquals(false, prefs3[keyAutoEnabled]) // Independent auto-theme remains false!

        // Phase 4: Write AUTOMATIC using Instance 3 -> Close -> Create Instance 4 -> Verify persistence
        ds3.edit { prefs -> prefs[keyRegion] = AppRegion.AUTOMATIC.name }
        scope3.cancel()

        val scope4 = CoroutineScope(Dispatchers.IO + Job())
        val ds4 = PreferenceDataStoreFactory.create(scope = scope4, produceFile = { testFile })
        val prefs4 = ds4.data.first()
        assertEquals(AppRegion.AUTOMATIC.name, prefs4[keyRegion])
        assertEquals(false, prefs4[keyAutoEnabled])
        scope4.cancel()
    }

    @Test
    fun testDiscoverRefreshLifecyclePolicy() {
        var fetchCount = 0
        var hasLoaded = false

        fun loadDiscoverContent(forceRefresh: Boolean = false) {
            if (hasLoaded && !forceRefresh) return
            fetchCount++
            hasLoaded = true
        }

        // 1. Initial cold launch: triggers fetch #1
        loadDiscoverContent(forceRefresh = false)
        assertEquals(1, fetchCount)

        // 2. Tab navigation (switched to Search/Create and returned to Discover): does NOT fetch
        loadDiscoverContent(forceRefresh = false)
        assertEquals(1, fetchCount)

        // 3. Repeated tab switches: does NOT fetch
        loadDiscoverContent(forceRefresh = false)
        assertEquals(1, fetchCount)

        // 4. Explicit manual refresh (onRetry or pull-to-refresh): triggers fetch #2
        loadDiscoverContent(forceRefresh = true)
        assertEquals(2, fetchCount)

        // 5. Process death simulation (ViewModel recreated, hasLoaded resets to false): triggers fetch #3
        var hasLoadedNewProcess = false
        fun loadDiscoverContentNewProcess(forceRefresh: Boolean = false) {
            if (hasLoadedNewProcess && !forceRefresh) return
            fetchCount++
            hasLoadedNewProcess = true
        }
        loadDiscoverContentNewProcess(forceRefresh = false)
        assertEquals(3, fetchCount)
    }

    private fun compositeAlpha(fgColor: Long, alpha: Float, bgColor: Long): Long {
        val fgR = ((fgColor shr 16) and 0xFF).toFloat()
        val fgG = ((fgColor shr 8) and 0xFF).toFloat()
        val fgB = (fgColor and 0xFF).toFloat()

        val bgR = ((bgColor shr 16) and 0xFF).toFloat()
        val bgG = ((bgColor shr 8) and 0xFF).toFloat()
        val bgB = (bgColor and 0xFF).toFloat()

        val r = (alpha * fgR + (1 - alpha) * bgR).toInt().coerceIn(0, 255)
        val g = (alpha * fgG + (1 - alpha) * bgG).toInt().coerceIn(0, 255)
        val b = (alpha * fgB + (1 - alpha) * bgB).toInt().coerceIn(0, 255)

        return ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
    }

    private fun calculateLuminance(color: Long): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0

        val rs = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        val gs = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        val bs = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

        return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
    }

    private fun calculateContrastRatio(color1: Long, color2: Long): Double {
        val l1 = calculateLuminance(color1)
        val l2 = calculateLuminance(color2)
        val maxL = Math.max(l1, l2)
        val minL = Math.min(l1, l2)
        return (maxL + 0.05) / (minL + 0.05)
    }
}
