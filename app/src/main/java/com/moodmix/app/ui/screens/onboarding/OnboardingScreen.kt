package com.moodmix.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moodmix.app.R
import com.moodmix.app.ui.components.PrimaryButton
import com.moodmix.app.ui.components.SecondaryButton
import com.moodmix.app.ui.theme.BrandPinkAccent
import com.moodmix.app.ui.theme.BrandPurpleAccent
import com.moodmix.app.ui.theme.MintPrimary

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    var onboardingStage by remember { mutableIntStateOf(0) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = onboardingStage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding_transition"
        ) { stage ->
            when (stage) {
                0 -> WelcomeStage(
                    onContinue = { onboardingStage = 1 }
                )
                1 -> ProductIntroStage(
                    onStartTutorial = {
                        onboardingStage = 2
                        tutorialStep = 0
                    },
                    onSkip = onFinishOnboarding
                )
                2 -> InteractiveTutorialStage(
                    currentStep = tutorialStep,
                    onNextStep = {
                        if (tutorialStep < 4) {
                            tutorialStep++
                        } else {
                            onFinishOnboarding()
                        }
                    },
                    onPrevStep = {
                        if (tutorialStep > 0) {
                            tutorialStep--
                        } else {
                            onboardingStage = 1
                        }
                    },
                    onSkip = onFinishOnboarding,
                    onFinish = onFinishOnboarding
                )
            }
        }
    }
}

@Composable
private fun WelcomeStage(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "MoodMix App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MoodMix",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "AI Music Discovery & Creation",
                style = MaterialTheme.typography.titleMedium,
                color = MintPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome to your personal AI sound lab. Create custom tracks, analyze lyrics, and explore music tailored to your mood.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        PrimaryButton(
            text = "Continue →",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProductIntroStage(
    onStartTutorial: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Capabilities",
                    style = MaterialTheme.typography.titleMedium,
                    color = MintPrimary,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onSkip) {
                    Text("Skip to App", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Discover What MoodMix Can Do",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            CapabilityCard(
                icon = Icons.Default.Explore,
                title = "Discover Music",
                description = "Explore mood playlists and creative prompt suggestions."
            )

            Spacer(modifier = Modifier.height(12.dp))

            CapabilityCard(
                icon = Icons.Default.Search,
                title = "Smart Search",
                description = "Browse genres, styles, and popular AI prompt inspirations."
            )

            Spacer(modifier = Modifier.height(12.dp))

            CapabilityCard(
                icon = Icons.Default.AddCircle,
                title = "AI Music Creation",
                description = "Generate original instrumental tracks and lyrics from simple prompts."
            )

            Spacer(modifier = Modifier.height(12.dp))

            CapabilityCard(
                icon = Icons.Default.GraphicEq,
                title = "Audio Extras",
                description = "Analyze audio files, explore lyrics intelligence, and audition AI DJ crossfades."
            )

            Spacer(modifier = Modifier.height(12.dp))

            CapabilityCard(
                icon = Icons.Default.LibraryMusic,
                title = "Personal Library",
                description = "Manage your generated audio tracks and export them anytime."
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        PrimaryButton(
            text = "Start Interactive Tutorial →",
            onClick = onStartTutorial,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CapabilityCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(BrandPurpleAccent.copy(alpha = 0.8f), BrandPinkAccent.copy(alpha = 0.8f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
        title = "Discover Music & Prompts",
        explanation = "Explore dynamic greetings, mood chips, and featured AI prompts on your Home feed to spark creative inspiration."
    ),
    TutorialStepData(
        stepIndex = 1,
        tabName = "2. Search",
        icon = Icons.Default.Search,
        title = "Search Styles & Categories",
        explanation = "Search for specific musical genres or browse style cards to discover preset prompts for lo-fi, synthwave, ambient, and more."
    ),
    TutorialStepData(
        stepIndex = 2,
        tabName = "3. Create",
        icon = Icons.Default.AddCircle,
        title = "Generate Music with AI",
        explanation = "Type your prompt, adjust duration, toggle matching lyrics, and generate custom audio tracks in real-time."
    ),
    TutorialStepData(
        stepIndex = 3,
        tabName = "4. Extras",
        icon = Icons.Default.GraphicEq,
        title = "Audio Intelligence Tools",
        explanation = "Use Voice Search, Song Analysis, Lyrics Intelligence & Translation, and AI DJ crossfading tools."
    ),
    TutorialStepData(
        stepIndex = 4,
        tabName = "5. Library",
        icon = Icons.Default.LibraryMusic,
        title = "Your Personal Library",
        explanation = "Access all your generated tracks in one place, play them back, or export them to your device's Music folder."
    )
)

@Composable
private fun InteractiveTutorialStage(
    currentStep: Int,
    onNextStep: () -> Unit,
    onPrevStep: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val step = tutorialSteps[currentStep]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TUTORIAL (${currentStep + 1}/5)",
                style = MaterialTheme.typography.labelMedium,
                color = MintPrimary,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onSkip) {
                Text("Skip Tutorial", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Highlight Card showing actual tab interface representation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MintPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = step.tabName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = step.explanation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Step Navigation Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) {
                SecondaryButton(
                    text = "Back",
                    onClick = onPrevStep,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.ArrowBack
                )
            }

            if (currentStep < 4) {
                PrimaryButton(
                    text = "Next →",
                    onClick = onNextStep,
                    modifier = Modifier.weight(1f)
                )
            } else {
                PrimaryButton(
                    text = "Finish & Launch 🚀",
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.Check
                )
            }
        }
    }
}
