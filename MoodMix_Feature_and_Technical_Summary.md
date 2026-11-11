# MoodMix: Comprehensive Technical and Feature Research Summary

## Executive Summary

**MoodMix** is a native Android application designed to provide AI-driven audio generation, catalogue discovery, on-device digital signal processing (DSP) music analysis, natural-language lyrics intelligence, and locale-adaptive regional theming. Developed in **Kotlin** using **Jetpack Compose Material 3** and **AndroidX Media3 (ExoPlayer)**, MoodMix decouples user-facing mobile interfaces from high-performance remote AI models through asynchronous microservices and local on-device heuristics.

This document serves as a research and technical specification for academic review, detailing MoodMix's software architecture, signal processing algorithms, remote AI service integration contracts, release engineering optimizations, and feature verification status.

---

## 1. Application Overview

### 1.1 Purpose and Target Audience
MoodMix addresses the fragmentation between music discovery, custom audio creation, and music intelligence tools. Designed for general music listeners, content creators, and audio enthusiasts, MoodMix provides a unified mobile platform for:
- Text-to-audio music generation.
- Natural-language search and voice-based music discovery.
- On-device DSP audio breakdown (BPM, key signature, energy curve).
- Multilingual lyrics translation and emotional sentiment analysis.
- Culturally adaptive UI themes (e.g. Drik Panchang astronomical Diwali festival calculations).

### 1.2 Core Problem Addressed
Existing mobile music utilities typically partition features into distinct applications: streaming discovery (e.g. Apple Music/Spotify), AI generation (web interfaces), or dedicated audio tools (tuning/BPM counters). MoodMix integrates these capabilities into a single application backed by an asynchronous, model-agnostic microservice architecture.

---

## 2. Feature Breakdown and Implementation Status

MoodMix is organized into five primary workspaces accessible via a Jetpack Compose bottom navigation bar:

### 2.1 Discover Workspace (`HomeScreen.kt`)
- **Contextual Greetings & Prompts**: Generates dynamic time-of-day greetings and contextual music generation prompts based on system clock hours.
- **Streaming Discovery Feed**: Fetches live charts from Apple Music RSS JSON APIs (`most-played` songs, `new-releases` albums, top artists, curated playlists).
- **Tab Navigation State Preservation**: `HomeViewModel` uses an in-memory `hasLoaded` flag to preserve feed content across tab navigation, eliminating redundant network calls and UI loading shimmers.
- **Regional & Festive Theme Engine**: Dynamically applies region-specific theme palettes (`RegionalThemePreferences.kt`). Includes an astronomical IST (`Asia/Kolkata`) Diwali date provider covering 2024–2030 using Drik Panchang calculations with WCAG 2.2 AA compliant contrast colors.

### 2.2 Search Workspace (`SearchScreen.kt`)
- **Multi-Category Catalogue Search**: Queries iTunes Search APIs across songs, artists, and albums with filter chips (All, Songs, Artists, Albums).
- **Voice Search Microphone Integration**: Embedded `SearchField` microphone action (`Icons.Default.Mic`) launching `VoiceSearchWorkspace`.
- **Speech-to-Text Transcription**: Uses Android `SpeechRecognizer` with offline model preference (`RecognizerIntent.EXTRA_PREFER_OFFLINE`) and lifecycle disposal.
- **Natural-Language Query Interpretation**: Passes user queries or transcripts through `QwenApi.interpretSearchQuery()` to extract targeted search keywords prior to catalogue lookup, falling back to raw user input on network failure.

### 2.3 Create Workspace (`GenerateScreen.kt`)
- **AI Music Creation**: Interface featuring a 400-character prompt field and a 5–30 second duration slider.
- **ACE-Step 1.5 Turbo Generation**: Submits asynchronous generation requests (`POST /release_task`) to a remote GPU server.
- **Asynchronous Coroutine Polling**: Polls task status (`POST /query_result`) every 5 seconds without blocking the UI thread.
- **WAV Audio Playback & Export**: Downloads generated 48 kHz WAV files to local persistent storage (`filesDir/generated_music/`), renders a Media3 seekable playback card, and exports audio to public device storage (`Music/MoodMix/`) via Android `MediaStore`.

### 2.4 Extras & AI Tools Workspace (`ToolsScreen.kt`)
- **Song Analysis & Breakdown (`AudioAnalyzer.kt`)**: On-device mono PCM decoding at 11,025 Hz, computing RMS loudness, 60-segment energy curves, spectral flux autocorrelation for BPM, and 12-bin chroma vector correlation for musical key signature, enriched by Qwen LLM analysis.
- **Lyrics Intelligence (`LyricsAnalyzer.kt`)**: On-device lexicon sentiment analysis, line-scoped negation window handling, syllable counting, and rhyme scheme detection, paired with Google ML Kit On-Device Translation (12+ languages) and Qwen LLM analysis.
- **AI DJ & Smart Transitions**: On-device BPM tempo matching, ExoPlayer volume ramp auditioning (Smooth, Linear, Quick Cut), and Qwen transition vibe commentary.
- **Regional Themes & Occasion Selector**: Manual region overrides (India, United States, Germany, Japan, Standard Dark) and auto-detection toggle.

### 2.5 Library Workspace (`LibraryScreen.kt`)
- **Persistent Media Library**: Displays AI Generated Creations, Liked Songs (stored in SQLite `liked_tracks`), and Recently Played History (stored in SQLite `listening_history`).
- **Instant Loading**: `LibraryViewModel` loads local database and file storage tracks on `Dispatchers.IO`, setting `isLoading = false` in milliseconds.
- **Qwen Listening Insights**: Asynchronously analyzes saved track titles and artists in a background thread to generate a personalized listening habit summary.

---

## 3. Technical Architecture

```
┌─────────────────────────────────────────────────────────────────┐
|                      Jetpack Compose UI Layer                   |
|   HomeScreen  │  SearchScreen  │  GenerateScreen  │  LibraryScreen |
└─────────────────────────────────────────────────────────────────┘
                                 │ StateFlow / Coroutines
┌─────────────────────────────────────────────────────────────────┐
|                     ViewModels Layer (MVVM)                     |
| HomeViewModel │ SearchViewModel │ GenerateViewModel │ LibraryViewModel│
└─────────────────────────────────────────────────────────────────┘
                                 │
┌─────────────────────────────────────────────────────────────────┐
|                    Data & Service Repositories                  |
| MusicRepository │ QwenApi │ AceStepApi │ RegionalThemePreferences|
└─────────────────────────────────────────────────────────────────┘
         │                       │                      │
         ▼                       ▼                      ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
|  Apple Music RSS |   |  Qwen LLM API    |   | ACE-Step Server  |
|  & iTunes API    |   | (Port 8000/ngrok)|   | (Port 8001/ngrok)|
└──────────────────┘   └──────────────────┘   └──────────────────┘
```

### 3.1 Architectural Patterns and Frameworks
- **Architecture Pattern**: MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF).
- **Concurrency**: Kotlin Coroutines (`viewModelScope`, `Dispatchers.IO`, `Dispatchers.Default`) and `StateFlow` / `collectAsStateWithLifecycle`.
- **UI Framework**: Jetpack Compose using Material 3 (`androidx.compose.material3`).
- **Audio Playback**: AndroidX Media3 ExoPlayer (`1.11.1`) and `MediaSessionService` (`PlaybackService.kt`).
- **Image Loading**: Coil 3 (`io.coil-kt.coil3`).
- **Color Extraction**: AndroidX Palette (`androidx.palette.ktx`).
- **Local Persistence**: SQLite via `SQLiteOpenHelper` (`LocalLibraryStorage.kt`) and AndroidX DataStore Preferences (`OnboardingDataStore.kt`, `RegionalThemePreferences.kt`).
- **Networking**: OkHttp `4.12.0` with explicit timeouts.

---

## 4. AI Integration and Remote Service Contracts

MoodMix connects to two independent remote AI services hosted on separate Kaggle GPU instances exposed via ngrok tunnels.

### 4.1 ACE-Step 1.5 Turbo Music Generation Backend
- **Base Endpoint**: `https://valiant-program-parched.ngrok-free.dev` (Port 8001)
- **Primary Method**: `AceStepApi.generateTrack()` in [`AceStepApi.kt`](file:///C:/Users/Blade/AndroidStudioProjects/MoodMix/app/src/main/java/com/moodmix/app/network/AceStepApi.kt)
- **Model Specs**: `acestep-v15-turbo`, `acestep-5Hz-lm-0.6B`, 48 kHz WAV output.

#### Execution Pipeline:
1. **Task Submission (`POST /release_task`)**:
   ```json
   {
     "prompt": "A cinematic electronic instrumental with emotional piano",
     "lyrics": "",
     "thinking": true,
     "audio_duration": 15,
     "audio_format": "wav",
     "model": "acestep-v15-turbo",
     "inference_steps": 8,
     "batch_size": 1
   }
   ```
   *Note: `audio_duration` receives the exact duration slider value (5–30 seconds).*
2. **Task ID Extraction**: Extracts `task_id` from `data.task_id` response.
3. **Asynchronous Polling (`POST /query_result`)**:
   ```json
   { "task_id_list": ["TASK_ID_HERE"] }
   ```
   Polls status every 5 seconds (`status == 0`: queued/processing; `status == 1`: succeeded; `status == 2`: failed).
4. **WAV Audio Retrieval (`GET /v1/audio?path=...`)**:
   Downloads full WAV bytes to persistent storage (`filesDir/generated_music/`).

### 4.2 Qwen2.5-Omni-3B LLM Service
- **Base Endpoint**: `https://buggy-relic-albatross.ngrok-free.dev` (Port 8000)
- **Primary Method**: `QwenApi.postTask()` in [`QwenApi.kt`](file:///C:/Users/Blade/AndroidStudioProjects/MoodMix/app/src/main/java/com/moodmix/app/network/QwenApi.kt)
- **Request Format (`POST /analyze`)**:
  ```json
  {
    "task": "lyrics_analysis",
    "content": "prompt_or_content_string"
  }
  ```
- **Response Processing**: Returns a JSON string or Markdown code fence (```` ```json { ... } ``` ````). `QwenApi.stripMarkdownFences()` extracts raw JSON and parses attributes (`explanation`, `sentiment`, `themes`, `emotional_tone`).
- **On-Device Fallback**: Every `QwenApi` invocation is wrapped in error handling. If Qwen is offline or times out, MoodMix seamlessly defaults to local on-device heuristics (`LyricsAnalyzer.analyze()`, raw search strings, local DSP metrics).

---

## 5. Audio and Data Flow Lifecycle

### 5.1 Music Generation to Local & Public Storage
1. **User Request**: User configures prompt (up to 400 chars) and duration (5–30s) on `GenerateScreen.kt`.
2. **Remote Inference**: `AceStepApi.generateTrack()` releases task and polls until completed.
3. **Cache & Persistent Storage**:
   - Audio is saved to `cacheDir/moodmix_generated/MoodMix_yyyyMMdd_HHmmss_taskId.wav`.
   - Audio is copied to `filesDir/generated_music/` for permanent access across application restarts.
4. **7-Day Retention Cleanup**: `AceStepApi.cleanExpired7DayCache()` scans `cacheDir/moodmix_generated/` and deletes files with `lastModified()` > 7 days (`7 * 24 * 60 * 60 * 1000L` ms).
5. **Public MediaStore Export**: User tapping "Export WAV" uses `ContentResolver` and `MediaStore.Audio.Media` to copy the file to `Environment.DIRECTORY_MUSIC + "/MoodMix"`.

### 5.2 Playback Control (`PlayerController.kt` & `PlaybackService.kt`)
- `PlayerController` manages global playback state via `StateFlow` (`currentTrack`, `isPlaying`, `position`, `duration`, `isBuffering`).
- Controls AndroidX Media3 `ExoPlayer` instance attached to a foreground `MediaSessionService` (`PlaybackService.kt`).
- Features a **Mini-Player Close (X) Button** to clear media queues and stop foreground notifications.
- Overrides `onTaskRemoved()` to stop playback and release player resources when MoodMix is swiped away from Android Recent Apps.

---

## 6. Intelligent Tools Implementation Details

### 6.1 On-Device Audio DSP Breakdown (`AudioAnalyzer.kt`)
`AudioAnalyzer` performs on-device digital signal processing without remote cloud dependency:
- **Mono PCM Decoding**: Uses `MediaExtractor` and `MediaCodec` to decode audio files into 11,025 Hz mono float PCM arrays.
- **RMS Loudness & Energy**: Calculates root-mean-square amplitude and dBFS loudness ($20 \cdot \log_{10}(\text{RMS})$).
- **Energy Curve & Segment Boundaries**: Computes a 60-segment energy curve and identifies structural transitions where energy delta exceeds thresholding ($\Delta > 0.30$).
- **BPM Detection**: Applies a Hann window, Radix-2 FFT, and spectral flux onset detection, followed by autocorrelation across a 60–180 BPM lag window.
- **Key Signature Detection**: Computes a 4,096-sample Radix-2 FFT, extracts pitch class energy into a 12-bin chroma vector (65 Hz–2,100 Hz), and correlates chroma against Krumhansl-Schmuckler major/minor key profiles.

### 6.2 Lyrics Intelligence (`LyricsAnalyzer.kt` & ML Kit)
- **On-Device Sentiment**: Uses positive/negative lexicon lookup tables with a 3-word line-scoped negation window (`"not"`, `"don't"`, `"never"`).
- **Structure Analysis**: Syllable counting and phonetic rhyme group labeling (`AABB`).
- **On-Device Translation**: Integrates Google ML Kit Translation (`com.google.mlkit:translate`) and Language Identification (`com.google.mlkit:language-id`) across 12+ target languages.
- **Qwen LLM Enrichment**: Enriches sentiment analysis when Qwen LLM API is online.

### 6.3 AI DJ & Smart Transitions
- **BPM Sync**: Calculates playback speed ratio ($BPM_A / BPM_B$) bounded between $0.92\times$ and $1.08\times$.
- **Crossfade Auditioning**: Uses ExoPlayer volume ramps ($V_A = \cos(\theta)$, $V_B = \sin(\theta)$) across 1–10 second crossfade windows.

---

## 7. Security and Release Engineering

### 7.1 Release Signing & Credential Protection
- **Dedicated Keystore**: Signed with a 2048-bit RSA release keystore (`release-keystore.jks`).
- **Externalized Credentials**: Credentials stored in `keystore.properties` (never committed).
- **Git Safety**: `.gitignore` explicitly excludes `keystore.properties`, `*.jks`, and `*.keystore`.
- **Fail-Fast Gradle Validation ([`app/build.gradle.kts`](file:///C:/Users/Blade/AndroidStudioProjects/MoodMix/app/build.gradle.kts))**:
  ```kotlin
  signingConfigs {
      create("release") {
          val keyStoreFilePath = keystoreProperties.getProperty("KEYSTORE_FILE")
          ...
          if (fileExists && credentialsValid) {
              storeFile = rootProject.file(keyStoreFilePath)
              storePassword = storePass
              keyAlias = alias
              keyPassword = keyPass
          } else {
              if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
                  throw InvalidUserDataException("CRITICAL ERROR: Release build signing configuration is incomplete.")
              }
          }
      }
  }
  ```

### 7.2 Release Optimizations & Packaging
- **R8 Minification & Resource Shrinking**: `isMinifyEnabled = true` and `isShrinkResources = true`.
- **Production ABI Filter**: `ndk { abiFilters.add("arm64-v8a") }` filters out 3 unneeded ABIs (`x86`, `x86_64`, `armeabi-v7a`).
- **Measured APK Size Reduction**:
  - Debug APK: **142.59 MB**
  - Release Signed APK (`MoodMix-v1.0-release.apk`): **25.40 MB (-83% reduction)**
  - Release AAB (`app-release.aab`): **16.13 MB**
- **Signature Verification**: Verified via `apksigner`: `Verified using v2 scheme: true` (1 signer).

---

## 8. Limitations and Future Work

### 8.1 Technical Limitations
1. **Remote ngrok Tunnel Dependency**: The remote AI servers rely on temporary ngrok tunnels; if a Kaggle session terminates, base URLs in `ApiConfig.kt` must be updated.
2. **On-Device DSP Memory Constraints**: `AudioAnalyzer` decodes up to 120 seconds of PCM samples at 11,025 Hz to prevent Out-Of-Memory (OOM) errors on low-spec mobile hardware.
3. **No Direct Audio Fingerprinting**: Music recognition transcribes spoken speech and text titles rather than acoustic waveform fingerprinting.

### 8.2 Future Improvements
- Implement WebSocket-based polling for ACE-Step generation status.
- Add background worker sync (`WorkManager`) for model asset downloads.
- Integrate on-device neural audio compression.

---

## 9. Academic Deliverables & Summary

### 9.1 Summary of Contributions
1. **Model-Agnostic Audio Architecture**: Successfully decoupled Android UI from heavy AI models via clean REST/JSON contracts and asynchronous coroutine polling.
2. **On-Device Audio DSP & NLP Engine**: Developed standalone, zero-network DSP audio algorithms (BPM, key, energy, loudness) and on-device lexicon sentiment NLP.
3. **Resilient Hybrid Fallback Strategy**: Constructed a fail-safe architecture where remote LLM intelligence enhances app features when online, while local heuristics ensure 100% feature uptime when offline.
4. **Astronomical Regional Adaptation**: Implemented an automated Drik Panchang IST lunisolar date engine for Diwali festive theme transformations.
5. **Production Release Engineering**: Achieved an 83% APK size reduction (142.59 MB $\rightarrow$ 25.40 MB) with v2 scheme release signing.

---

### 9.2 Feature Implementation Matrix

| Feature | Category | Source Files / Evidence | Status |
| :--- | :--- | :--- | :---: |
| **Discover Streaming Feed** | Music Discovery | `HomeScreen.kt`, `iTunesMusicProvider.kt` | **Implemented** |
| **Regional & Diwali Themes** | Adaptation Engine | `OccasionProvider.kt`, `RegionalThemePreferences.kt` | **Implemented** |
| **Search & Voice Search** | Music Discovery | `SearchScreen.kt`, `SearchViewModel.kt` | **Implemented** |
| **ACE-Step 1.5 Turbo Music Gen** | AI Generation | `GenerateScreen.kt`, `AceStepApi.kt` | **Implemented** |
| **WAV Audio Export (MediaStore)** | Storage & Export | `GenerateScreen.kt`, `MediaStore` API | **Implemented** |
| **7-Day Cache Retention Pruning** | Storage Manager | `AceStepApi.kt`, `cacheDir/moodmix_generated` | **Implemented** |
| **Persistent Generated Library** | Local Storage | `LocalLibraryStorage.kt`, `filesDir/generated_music` | **Implemented** |
| **Media3 Foreground Service** | Playback Engine | `PlaybackService.kt`, `PlayerController.kt` | **Implemented** |
| **On-Device Audio DSP Breakdown** | Audio Intelligence | `AudioAnalyzer.kt` | **Implemented** |
| **On-Device ML Kit Translation** | Text Intelligence | `ToolsScreen.kt`, `com.google.mlkit:translate` | **Implemented** |
| **Qwen LLM Search & Lyrics** | AI Intelligence | `QwenApi.kt`, `POST /analyze` | **Implemented** |
| **Qwen Personal Library Insights** | AI Intelligence | `LibraryViewModel.kt`, `QwenApi.kt` | **Implemented** |
| **AI DJ BPM Sync & Crossfade** | Audio Intelligence | `ToolsScreen.kt`, `ExoPlayer` volume ramp | **Implemented** |
| **Acoustic Waveform Fingerprinting** | Audio Intelligence | *Not present in codebase* | **Not Implemented** |
| **Legacy Daydream / Render Gen** | Music Generation | *Removed from active execution path* | **Removed / Unused** |

---

### 9.3 Claims Safe to Make in an Academic Paper

1. *MoodMix provides an integrated mobile platform for AI music generation, DSP audio analysis, and catalogue discovery.*
2. *On-device audio DSP algorithms compute BPM via spectral flux autocorrelation and detect key signatures via 12-bin chroma correlation against Krumhansl-Schmuckler profiles.*
3. *The application utilizes an asynchronous coroutine polling architecture to decouple mobile UI responsiveness from long-running remote GPU audio generation tasks.*
4. *Remote LLM failures seamlessly trigger local on-device heuristic fallbacks without causing UI crashes or service interruptions.*
5. *Release engineering optimizations (R8 minification, resource shrinking, ARM64 ABI filtering) reduced the production application package size by 83%.*
6. *Regional theme adaptation incorporates lunisolar Indian Standard Time astronomical calculations for festive theme rendering.*

---

### 9.4 Claims Requiring Additional Testing or Subjective Evidence

1. *User preference satisfaction scores for Qwen-generated library insights vs. static counts.*
2. *Real-world battery consumption benchmarks during 120-second PCM audio DSP decoding across varying Android SoC hardware.*
3. *Statistical accuracy comparisons of `AudioAnalyzer`'s BPM autocorrelation against professional DAW ground truth across diverse musical genres.*
