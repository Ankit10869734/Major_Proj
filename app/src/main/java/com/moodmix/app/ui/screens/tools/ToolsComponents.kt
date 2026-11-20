package com.moodmix.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodmix.app.audio.SongAnalysis
import com.moodmix.app.ui.theme.MintPrimary
import kotlin.math.abs

@Composable
internal fun ToolsHeader() {
    Column {
        Text(
            "MOODMIX",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.SemiBold,
            color = MintPrimary.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "AI Tools",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Analyze, translate and blend your music",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(primary.copy(alpha = 0.12f))
                .border(1.dp, primary.copy(alpha = 0.25f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small label showing where a result came from. */
@Composable
internal fun SourceTag(cloud: Boolean) {
    val c = if (cloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(if (cloud) "Qwen AI" else "On-device", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = c)
    }
}

@Composable
internal fun InsightBlock(title: String, text: String, cloud: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            SourceTag(cloud)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** On-device written summary, used when the cloud is unreachable. */
internal fun localSongSummary(a: SongAnalysis): String {
    val energy = when {
        a.energy >= 0.58f -> "high"
        a.energy >= 0.30f -> "moderate"
        else -> "low"
    }
    val bits = mutableListOf("This track feels ${a.mood.lowercase()}")
    a.bpm?.let { bits += "runs at around $it BPM" }
    a.key?.let { bits += "sits in $it" }
    bits += "carries $energy energy"
    return bits.joinToString(", ") + "."
}

private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun parseKey(k: String?): Pair<Int, Boolean>? {
    if (k == null) return null
    val parts = k.split(" ")
    if (parts.size < 2) return null
    val i = noteNames.indexOf(parts[0])
    return if (i < 0) null else i to parts[1].equals("major", ignoreCase = true)
}

private fun keysCompatible(a: Pair<Int, Boolean>, b: Pair<Int, Boolean>): Boolean {
    val (ra, majA) = a
    val (rb, majB) = b
    if (majA == majB) {
        val d = ((ra - rb) + 12) % 12
        return d == 0 || d == 5 || d == 7
    }
    val major = if (majA) ra else rb
    val minor = if (majA) rb else ra
    return minor == (major + 9) % 12 // relative minor
}

/** Plain-language mixing advice from tempo, key and energy. Runs fully on-device. */
internal fun djAdvice(a: SongAnalysis, b: SongAnalysis): String {
    val lines = mutableListOf<String>()
    val bpmA = a.bpm
    val bpmB = b.bpm
    if (bpmA != null && bpmB != null && a.bpmConfidence >= 0.35f && b.bpmConfidence >= 0.35f) {
        val diff = abs(bpmA - bpmB)
        lines += when {
            diff <= 3 -> "Tempos are almost identical ($bpmA and $bpmB BPM). A long, smooth blend will work well."
            diff <= 8 -> "Tempos are close ($bpmA and $bpmB BPM). Turn on Match tempo for a tight blend."
            else -> "Tempos differ by $diff BPM, so a shorter crossfade will sound cleaner."
        }
    } else {
        lines += "Tempo couldn't be detected reliably, so a smooth crossfade is the safest choice."
    }
    val ka = parseKey(a.key)
    val kb = parseKey(b.key)
    if (ka != null && kb != null) {
        lines += if (keysCompatible(ka, kb)) "Keys (${a.key} and ${b.key}) are harmonically compatible."
        else "Keys (${a.key} and ${b.key}) may clash during the overlap. Keep the crossfade short."
    }
    if (abs(a.energy - b.energy) > 0.3f) {
        lines += if (b.energy > a.energy) "Track B is noticeably more energetic, which builds the mood."
        else "Track B is noticeably calmer, which eases the mood down."
    }
    return lines.joinToString("\n")
}
