# 🎵 MoodMix — AI Music Intelligence & Regional Adaptation Android App

Welcome to the **MoodMix** repository! MoodMix is a modern native Android application built with **Kotlin**, **Jetpack Compose Material 3**, and **Media3 / ExoPlayer** designed for AI-driven music generation, streaming discovery, lyrics intelligence, audio DSP analysis, and seasonal regional theme adaptation.

This project includes the Android frontend, backend API server integration, model integration adapter framework, and Kaggle notebook Qwen LLM service.

---

## 📌 1. Project Overview & Features

### Core App Workspaces

1. **🌐 Discover (Home)**:
   - Dynamic greetings and time-of-day contextual AI music recommendation prompts.
   - Streaming discovery feed powered by Apple Music RSS APIs (Recently Played, Trending Songs, Popular Artists, New Releases, Featured Playlists).
   - **Tab Navigation State Preservation**: Prevents unnecessary page wipes, loading shimmers, and duplicate API requests when switching between tabs.
   - **Regional & Festive Theme Adaptation**: Automatically detects device locale or manual region selection (India, United States, Germany, Japan, Standard Dark).
   - **Lunisolar Diwali Festival Theme**: Features exact Indian Standard Time (`Asia/Kolkata`) Diwali dates (2024–2030) derived from official Drik Panchang astronomical calculations, dynamically applying WCAG 2.2 AA-compliant Gold (`#FFD700`), Amber-Orange (`#FF8F00`), and Festive Pink (`#FF6584`) color palettes.

2. **🔍 Search (Natural Language & Voice Integration)**:
   - Live music catalogue search across songs, artists, and albums with filter chips (All, Songs, Artists, Albums).
   - **Voice Search Microphone Integration**: Embedded microphone button (`mic`) inside the search bar.
   - **Integrated Speech-to-Text**: Transcribes spoken song or artist queries using Android `SpeechRecognizer` with offline model support (`RecognizerIntent.EXTRA_PREFER_OFFLINE`).
   - **Natural Language Query Interpretation**: Qwen LLM interprets complex search queries (e.g. *"can you find soft acoustic songs for studying"*) into targeted keywords before executing catalogue lookup.
   - **Editable Transcript**: Speech transcripts can be reviewed and edited in the query box before or after submitting.

3. **🤖 Create (AI Music Generation)**:
   - Full AI Music Creation Workspace available in both Debug and Release builds.
   - User text prompt input (up to 400 characters), duration slider (5–30 seconds), and AI lyrics generator toggle.
   - **Backend Integration**: Communicates with the MoodMix FastAPI backend server (`POST /generate` and `POST /api/v2/generate`).
   - **Teammate Model Integration Layer**: Supports model-agnostic adapters (`BaseMusicModelAdapter`) for Magenta, PyTorch, and custom teammate music generation models.
   - Audio export to Android device storage (`Music/MoodMix/`) via MediaStore APIs.

4. **💬 Extras (AI Tools & Intelligence)**:
   - **Song Analysis & Breakdown**: On-device audio DSP analyzer (`AudioAnalyzer`) computing tempo (BPM), key signature, energy curve, loudness (dBFS), and segment boundaries from local audio files, enriched with Qwen LLM musical interpretation.
   - **Lyrics Intelligence**: On-device lyrics structure, sentiment, and rhyme scheme analyzer paired with **On-Device ML Kit Translation** (12+ languages) and **Qwen LLM Lyrics Analysis**.
   - **AI DJ & Smart Transitions**: On-device BPM tempo matching, crossfade auditioning (Smooth, Linear, Quick Cut), and Qwen transition vibe commentary.
   - **Regional Theme & Occasions Selector**: Toggle automatic theme adaptation or force specific manual region overrides.

5. **📚 Library & Listening Insights**:
   - Organized tabs for All Tracks, Saved AI Creations, Liked Songs, and Recently Played History.
   - **🤖 Qwen Listening Insights Banner**: Analyzes actual saved titles and artists in user history to generate personalized listening habit summaries (*"Your recent activity shows a preference for lo-fi beats, ambient piano, and pop hits."*).
   - Track detail bottom sheet for playback, seeking, liking, and system file sharing.

6. **🎧 Media3 Playback & Service Lifecycle**:
   - Foreground `MediaSessionService` (`PlaybackService.kt`) with lock screen controls and system notification.
   - **Mini-Player Close (X) Button**: Dismisses playback, clears media queue, and removes system notifications immediately.
   - **Android `onTaskRemoved()` Recents Swipe Cleanup**: Swiping MoodMix away from Android Recent Apps cleanly stops playback, removes notifications, and terminates the service.

---

## 🏗️ 2. Architecture & Tech Stack

```
+-----------------------------------------------------------------------+
|                       Jetpack Compose UI Layer                        |
|   HomeScreen  |  SearchScreen  |  GenerateScreen  |  LibraryScreen   |
+-----------------------------------------------------------------------+
                                   | (StateFlow / Coroutines)
+-----------------------------------------------------------------------+
|                           ViewModels Layer                            |
| HomeViewModel | SearchViewModel | GenerateViewModel | LibraryViewModel|
+-----------------------------------------------------------------------+
                                   |
+-----------------------------------------------------------------------+
|                         Data & Service Layer                          |
| MusicRepository | QwenApi | RegionalThemePreferences | PlaybackService|
+-----------------------------------------------------------------------+
        |                          |                        |
        v                          v                        v
+---------------+        +------------------+     +------------------+
| Apple RSS API |        |  Qwen LLM API    |     |  FastAPI Server  |
| (Discover)    |        |  (Kaggle / ngrok)|     |  (Render ML)     |
+---------------+        +------------------+     +------------------+
```

### Core Technologies

- **Language**: Kotlin 100%
- **UI Toolkit**: Jetpack Compose with Material 3 Design System
- **Architecture Pattern**: MVVM (Model-View-ViewModel) + Unidirectional Data Flow
- **State Management**: Kotlin Coroutines & `StateFlow`
- **Audio Playback Engine**: AndroidX `Media3` ExoPlayer 1.11.1 & `MediaSessionService`
- **Networking**: OkHttp 4.12.0
- **On-Device ML**: Google ML Kit Translation (`com.google.mlkit:translate`) & Language Identification (`com.google.mlkit:language-id`)
- **Remote LLM**: Qwen2.5-Omni-3B hosted on Kaggle GPU / ngrok
- **Image Loading**: Coil 3 (`io.coil-kt.coil3`)
- **Color Extraction**: AndroidX Palette (`androidx.palette:palette-ktx`)
- **Persistence**: AndroidX DataStore Preferences (`androidx.datastore:datastore-preferences`)

---

## 📦 3. APK & Build Optimizations

The application underwent rigorous build and packaging optimization:

| Metric | Debug APK | Release APK | Release AAB | Size Reduction | Reduction % |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Total Size** | **142.59 MB** | **24.19 MB** | **16.13 MB** | **-118.40 MB** | **-83.0%** |
| **Native Libraries (`lib/`)** | 63.90 MB | **16.59 MB** | ~16.59 MB | **-47.31 MB** | **-74.0%** |
| **Compiled DEX Files** | 71.90 MB | **5.26 MB** | ~5.26 MB | **-66.64 MB** | **-92.7%** |
| **Packaged ABIs** | 4 ABIs | **1 (`arm64-v8a` only)** | **1 (`arm64-v8a` only)** | **-3 ABIs** | **-75.0%** |

### Release Build Configurations (`app/build.gradle.kts`)
- **Production ABI Filter**: `ndk { abiFilters.add("arm64-v8a") }` filters out `x86`, `x86_64`, and `armeabi-v7a` native libraries in production releases.
- **R8 Code & Resource Shrinking**: `isMinifyEnabled = true` and `isShrinkResources = true` strip unused classes and resources.
- **Refined ProGuard Rules**: Preserved serialization DTOs (`com.moodmix.app.domain.model.**`, `com.moodmix.app.network.**`) and Media3 session interfaces while enabling full R8 minification across UI screens.

---

## 📡 4. Server & Qwen LLM API Configuration

All networking endpoints are centralized in [`ApiConfig.kt`](file:///C:/Users/Blade/AndroidStudioProjects/MoodMix/app/src/main/java/com/moodmix/app/network/ApiConfig.kt):

```kotlin
object ApiConfig {

    // FastAPI Production Backend
    const val BASE_URL = "https://moodmix-backend.onrender.com"

    // Qwen LLM Backend (Kaggle / ngrok)
    const val QWEN_BASE_URL = "https://valiant-program-parched.ngrok-free.dev"

    val QWEN_ANALYZE_ENDPOINT get() = "$QWEN_BASE_URL/analyze"
    val GENERATE_ENDPOINT get() = "$BASE_URL/generate"
    val STATUS_ENDPOINT get() = "$BASE_URL/status"
}
```

### Qwen LLM Integration Contract (`QwenApi.kt`)
Communicates with `POST /analyze` passing task-based payloads:
```json
{
  "task": "lyrics_analysis",
  "content": "your_lyrics_or_prompt_here"
}
```
Includes automatic markdown code fence stripping (`stripMarkdownFences()`) and JSON attribute parsing with **100% on-device fallback resilience** if the remote Qwen server is offline or times out.

---

## 🔒 5. Kaggle Notebook Security & Setup

The Qwen LLM backend is hosted via a Kaggle notebook (`MoodMix_Qwen_Kaggle_Notebook.ipynb`).

### Kaggle Secrets Setup Instructions
1. Open your notebook in Kaggle.
2. Go to **Add-ons** $\rightarrow$ **Secrets**.
3. Click **+ Add a new secret**.
4. Set **Label**: `NGROK_AUTHTOKEN`, **Value**: Your ngrok token from [dashboard.ngrok.com](https://dashboard.ngrok.com/get-started/your-authtoken).
5. Check the box to attach `NGROK_AUTHTOKEN` to the notebook session.

See [`NOTEBOOK_SECURITY_CHANGELOG.md`](file:///C:/Users/Blade/AndroidStudioProjects/MoodMix/NOTEBOOK_SECURITY_CHANGELOG.md) for full security documentation.

---

## 🤝 6. Teammate Model Integration Guide

For teammate developers connecting custom PyTorch / Magenta music generation models to MoodMix, see [`MODEL_INTEGRATION.md`](file:///C:/Users/Blade/mood-music/melody-craft/MODEL_INTEGRATION.md).

FastAPI V2 Endpoint: `POST /api/v2/generate`
- Request: `ModelGenerationRequest` (`prompt`, `genre`, `mood`, `duration`, `model_id`, `user_id`, `extra_params`)
- Response: `ModelGenerationResponse` (`success`, `track_id`, `audio_url`, `duration`, `format`, `model_id`, `message`)

---

## 🧪 7. Build & Verification Instructions

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Build Release APK & AAB
```bash
./gradlew assembleRelease
./gradlew bundleRelease
```
Output artifacts:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`
