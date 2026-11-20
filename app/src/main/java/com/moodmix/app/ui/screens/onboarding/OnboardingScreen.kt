package com.moodmix.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodmix.app.R
import com.moodmix.app.ui.theme.MintPrimary
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class TutorialStepData(
    val stepIndex: Int,
    val tabName: String,
    val icon: ImageVector,
    val title: String,
    val explanation: String
)

val tutorialSteps = listOf(
    TutorialStepData(
        stepIndex = 0,
        tabName = "1. Discover",
        icon = Icons.Default.Explore,
        title = "Find your sound",
        explanation = "Your home feed greets you with mood chips and featured prompts, so there's always somewhere to start."
    ),
    TutorialStepData(
        stepIndex = 1,
        tabName = "2. Search",
        icon = Icons.Default.Search,
        title = "Search styles and genres",
        explanation = "Look up a genre or browse style cards to get ready-made prompts for lo-fi, synthwave, ambient and more."
    ),
    TutorialStepData(
        stepIndex = 2,
        tabName = "3. Create",
        icon = Icons.Default.AutoAwesome,
        title = "Create through conversation",
        explanation = "Describe the music you want, choose a length up to 2 minutes and send. Your track arrives right in the chat."
    ),
    TutorialStepData(
        stepIndex = 3,
        tabName = "4. Extras",
        icon = Icons.Default.GraphicEq,
        title = "Smart audio tools",
        explanation = "Analyze any song, break down and translate lyrics, or preview a DJ-style crossfade between two tracks."
    ),
    TutorialStepData(
        stepIndex = 4,
        tabName = "5. Library",
        icon = Icons.Default.LibraryMusic,
        title = "Everything you've made",
        explanation = "Your generated tracks, liked songs and listening history live together in one place."
    )
)

// Index 0 = welcome page, 1..5 = the five tabs.
private val pageAccents = listOf(
    MintPrimary,
    Color(0xFF7DD3FC),
    Color(0xFF5EEAD4),
    Color(0xFFA78BFA),
    Color(0xFFFBBF24),
    Color(0xFFF472B6)
)

private val pagePoints = listOf(
    listOf("Create original tracks from a text prompt", "Analyze songs and lyrics", "Keep everything in your library"),
    listOf("Mood chips for every time of day", "Featured prompt ideas", "One tap to start creating"),
    listOf("Genre search and style presets", "Browse prompt inspiration", "Send any idea straight to Create"),
    listOf("Chat-style prompts with full history", "Stop a generation at any time", "Download tracks to your Music folder"),
    listOf("Song analysis: tempo, key and energy", "Lyrics breakdown and translation", "AI DJ crossfade previews"),
    listOf("Your AI-generated tracks", "Liked songs and recent plays", "Share audio files with anyone")
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val pageCount = tutorialSteps.size + 1
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pageCount - 1

    val accent by animateColorAsState(
        targetValue = pageAccents[pagerState.currentPage],
        animationSpec = tween(500),
        label = "accent"
    )

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.20f),
                        radius = size.width * 1.1f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.width * 0.9f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isLast) {
                    TextButton(onClick = onFinishOnboarding) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - distance * 0.65f
                            val s = 1f - distance * 0.08f
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    OnboardPageContent(page = page, accent = pageAccents[page])
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(top = 8.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { i ->
                        val selected = i == pagerState.currentPage
                        val w by animateDpAsState(if (selected) 24.dp else 7.dp, label = "dot_w")
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(w)
                                .clip(CircleShape)
                                .background(if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (isLast) onFinishOnboarding()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF0D0B14)
                    )
                ) {
                    Text(
                        text = if (isLast) "Start creating" else "Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardPageContent(page: Int, accent: Color) {
    val isWelcome = page == 0
    val step = if (isWelcome) null else tutorialSteps[page - 1]

    val label = if (isWelcome) "AI MUSIC DISCOVERY & CREATION" else step!!.tabName.substringAfter(". ").uppercase()
    val title = if (isWelcome) "MoodMix" else step!!.title
    val body = if (isWelcome) {
        "Your personal AI sound lab. Create custom tracks, analyze lyrics and explore music made for your mood. Swipe to take a quick tour."
    } else step!!.explanation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        HeroOrb(accent = accent) {
            if (isWelcome) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "MoodMix logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = step!!.icon,
                    contentDescription = null,
                    tint = Color(0xFF0D0B14),
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        PointsCard(points = pagePoints[page], accent = accent)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HeroOrb(
    accent: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.38f), Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r * pulse,
                center = center
            )
            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = r * 0.78f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = accent.copy(alpha = 0.32f),
                radius = r * 0.58f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
private fun PointsCard(points: List<String>, accent: Color) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        points.forEach { text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
