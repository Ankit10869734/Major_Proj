package com.moodmix.app.domain.model

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.moodmix.app.ui.theme.MintPrimary

enum class AppRegion {
    AUTOMATIC,
    INDIA,
    UNITED_STATES,
    GERMANY,
    JAPAN,
    STANDARD_DARK
}

enum class OccasionType {
    DIWALI,
    NONE
}

@Immutable
data class Occasion(
    val type: OccasionType,
    val name: String,
    val regionCode: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val festiveMoods: List<Pair<String, String>> = emptyList()
)

@Immutable
data class RegionalThemeState(
    val isAutoThemeEnabled: Boolean = true,
    val selectedRegion: AppRegion = AppRegion.AUTOMATIC,
    val activeRegionCode: String = "US",
    val activeOccasion: Occasion? = null,
    val customColorScheme: ColorScheme? = null
) {
    val isFestiveActive: Boolean get() = activeOccasion != null
}
