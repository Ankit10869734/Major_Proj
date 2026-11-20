package com.moodmix.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moodmix.app.ui.theme.BrandPinkAccent
import com.moodmix.app.ui.theme.MintPrimary

private val NeonFieldColor = Color(0xFF0F121A)

/**
 * Draws a pill-shaped neon outline with a soft, gently "breathing" glow.
 * Apply BEFORE clip()/background() so the glow sits behind and the crisp line sits on top.
 * The glow is made of stacked translucent strokes, so it works on every API level (no blur filters).
 */
@Composable
fun Modifier.neonGlow(
    colors: List<Color> = listOf(MintPrimary, BrandPinkAccent),
    glowRadius: Dp = 10.dp,
    strokeWidth: Dp = 1.5.dp,
    intensity: Float = 1f
): Modifier {
    val transition = rememberInfiniteTransition(label = "neonGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonPulse"
    )

    return this.drawWithContent {
        val radius = size.height / 2f
        val brush = Brush.horizontalGradient(colors)

        val layers = 6
        val maxSpread = glowRadius.toPx()
        for (i in layers downTo 1) {
            val spread = maxSpread * i / layers
            drawRoundRect(
                brush = brush,
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = spread * 2f),
                alpha = (0.07f * intensity * pulse).coerceIn(0f, 1f)
            )
        }

        drawContent()

        val strokePx = strokeWidth.toPx()
        val inset = strokePx / 2f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokePx, size.height - strokePx),
            cornerRadius = CornerRadius(radius - inset),
            style = Stroke(width = strokePx),
            alpha = (0.55f + 0.45f * pulse).coerceIn(0f, 1f)
        )
    }
}

/**
 * Landing-page search bar. Not editable: tapping it opens the full-screen search page.
 */
@Composable
fun NeonSearchBar(
    placeholderText: String,
    onClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .neonGlow()
            .clip(CircleShape)
            .background(NeonFieldColor)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = placeholderText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onMicClick) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice search",
                tint = MintPrimary
            )
        }
    }
}

/**
 * Editable pill input used on the full-screen search page.
 */
@Composable
fun PillSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    placeholderText: String = "Search songs, artists, or albums"
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(48.dp)
            .neonGlow(glowRadius = 8.dp, intensity = 0.8f)
            .clip(CircleShape)
            .background(NeonFieldColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MintPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onMicClick) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice search",
                        tint = MintPrimary
                    )
                }
            }
        }
    )
}
