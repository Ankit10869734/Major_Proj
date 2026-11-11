# MoodMix

**An AI-powered Android music application combining music discovery, generative audio, music intelligence, and personalized listening experiences.**

MoodMix is a native Android application built with Kotlin and Jetpack Compose. It brings together AI-powered music generation, catalogue discovery, natural-language search, audio signal analysis, lyrics intelligence, and music playback in a unified mobile experience.

The application follows a hybrid architecture: lightweight music analysis and application functionality run locally on Android, while computationally intensive AI inference is handled by independently hosted cloud services.

---

## Overview

MoodMix is designed around five primary workspaces:

| Workspace | Description |
|---|---|
| Discover | Music discovery, charts, releases, and regional experiences |
| Search | Catalogue search with natural-language and voice input |
| Create | AI-powered text-to-music generation |
| Extras | Audio analysis, lyrics intelligence, translation, and DJ transitions |
| Library | Generated music, liked tracks, listening history, and listening insights |

The system integrates two independent AI backends:

- **ACE-Step 1.5 Turbo** — generative audio inference and WAV music generation.
- **Qwen2.5-Omni-3B** — natural-language understanding and music-related text intelligence.

Both services are hosted independently on Kaggle GPU sessions and exposed through separate API tunnels.

---

## Features

### 1. Discover

A music discovery workspace combining catalogue data with contextual application experiences.

- Music discovery through Apple Music RSS and catalogue endpoints.
- Trending music, popular artists, albums, and new releases.
- Contextual greetings and recommendation prompts.
- Preserved navigation state to reduce unnecessary reloads when switching workspaces.
- Regional theme adaptation based on device locale or manual selection.
- Seasonal and festival-oriented themes, including a Diwali theme based on Indian Standard Time date calculations.

### 2. Search

A music search experience supporting both conventional and natural-language queries.

- Search across songs, artists, and albums.
- Category filters for search results.
- Integrated microphone-based voice search.
- Speech-to-text using Android SpeechRecognizer.
- Editable voice transcripts.
- Qwen-assisted natural-language query interpretation.
- Catalogue search using the interpreted query.
- Fallback to the original query when remote Qwen inference is unavailable.

### 3. Create

A dedicated AI music-generation workspace powered by ACE-Step 1.5 Turbo.

- Text-to-music generation from user-defined prompts.
- Configurable generation duration.
- Asynchronous generation workflow.
- Task submission and status polling.
- WAV audio retrieval.
- Generated-track playback.
- Persistent storage of generated tracks in the application library.
- Export to the device's `Music/MoodMix/` directory using Android MediaStore.

**Generation pipeline:**

1. The user submits a music-generation prompt.
2. The Android client sends a generation request to the ACE-Step API.
3. The backend queues and processes the generation task.
4. The application periodically checks the task status.
5. Once completed, the generated WAV file is retrieved.
6. The audio is stored locally and made available for playback and export.

### 4. Extras — Music Intelligence

A collection of music-analysis and audio-processing utilities.

**Audio Analysis**

The on-device `AudioAnalyzer` performs digital signal processing on local audio files to calculate:

- Tempo estimation (BPM).
- Musical key estimation.
- RMS-based loudness analysis.
- Energy distribution across audio segments.
- Spectral features used for onset and tempo estimation.

The documented implementation uses PCM decoding, spectral flux analysis, autocorrelation, chroma features, and Krumhansl-Schmuckler key profiles.

**Lyrics Intelligence**

- Lyrics structure analysis.
- Sentiment and emotional-tone analysis.
- Rhyme and thematic analysis.
- Qwen-assisted semantic interpretation.
- Local analysis through the lyrics-processing pipeline.

**Translation**

- On-device translation using Google ML Kit.
- Language identification.
- Line-by-line lyrics translation.
- Support for multiple target languages.

**AI DJ and Smart Transitions**

- BPM-based tempo matching.
- Transition compatibility assistance.
- Configurable transition auditioning.
- Smooth, linear, and quick-cut transition modes.
- Equal-power volume crossfading through ExoPlayer.

### 5. Library

A centralized workspace for personal music management.

- Generated music collection.
- Liked catalogue tracks.
- Listening history.
- Track details and playback controls.
- Persistent storage of generated WAV files.
- Qwen-assisted listening insights based on saved listening activity.

---

## System Architecture

MoodMix separates its Android application from its remote AI inference services.

```text
                         MOODMIX ANDROID APP
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│                    Jetpack Compose UI                        │
│                                                              │
│   Discover     Search      Create      Extras      Library   │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                       ViewModels                              │
│                                                              │
│              Kotlin Coroutines / StateFlow                   │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                   Repository / Service Layer                  │
│                                                              │
│   MusicRepository   QwenApi   AceStepApi   PlayerController  │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                    Local Processing                           │
│                                                              │
│       AudioAnalyzer    LyricsAnalyzer    ML Kit              │
│                                                              │
│                    Local Persistence                          │
│                                                              │
│                  SQLite / DataStore / Files                  │
└──────────────────────────────────────────────────────────────┘
              │                  │                  │
              ▼                  ▼                  ▼
        Apple Music         Qwen Service       ACE-Step Service
        Catalogue           Kaggle GPU         Kaggle GPU
                            Port 8000          Port 8001
                            /analyze           Generation API
```

### Application Architecture

The Android application uses an MVVM-oriented architecture with Clean Architecture principles and unidirectional data flow.

| Layer | Responsibility |
|---|---|
| Presentation | Jetpack Compose screens and UI components |
| ViewModels | Application state, event handling, and business-flow coordination |
| Repository / Services | Data access, networking, and service coordination |
| Domain / Processing | Music analysis and application-specific logic |
| Data | Local persistence and remote data integration |

### Data and Playback

- SQLite for local music metadata and listening history.
- DataStore Preferences for application settings.
- Internal application storage for generated audio.
- Android MediaStore for public audio export.
- AndroidX Media3 ExoPlayer for audio playback.
- MediaSessionService for background playback and system media controls.

---

## AI Backend Services

MoodMix uses two independent AI services. They have different responsibilities and are not interchangeable.

### ACE-Step 1.5 Turbo — Music Generation

**Model components:**
- `acestep-v15-turbo`
- `acestep-5Hz-lm-0.6B`

**Purpose:** Text-to-music generation.

**Deployment:** Kaggle GPU environment, exposed through ngrok.

**API workflow:**

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/release_task` | Submit a music-generation task |
| POST | `/query_result` | Retrieve task status and results |
| GET | `/v1/audio` | Retrieve generated audio |

The Android integration is implemented through `AceStepApi.kt`.

The client submits a generation request, extracts the task identifier, polls for completion, retrieves the generated WAV file, and makes the result available to the application.

### Qwen2.5-Omni-3B — Language Intelligence

**Model:** `Qwen/Qwen2.5-Omni-3B`

**Purpose:** Natural-language understanding and music-related text analysis.

**Deployment:** Kaggle GPU environment, exposed through ngrok.

**Primary API endpoint:**

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/` | Service status and supported tasks |
| POST | `/analyze` | Task-based language inference |

**Request format:**

```json
{
  "task": "lyrics_analysis",
  "content": "Text or task-specific input"
}
```

The service uses task-specific prompt templates to route requests to the appropriate language-processing workflow.

The documented task categories are:

- `lyrics_analysis`
- `search_understanding`
- `voice_query_understanding`
- `prompt_enhancement`
- `recommendation_explanation`
- `discover_personalization`
- `music_analysis`
- `listening_preference_analysis`
- `ai_dj_transition`

The Android integration is implemented through `QwenApi.kt`.

Remote inference is used where semantic interpretation is beneficial. Supported workflows also have local fallback behaviour when the remote service is unavailable.

**Important deployment distinction:** Qwen and ACE-Step are separate services. They use different API ports, independent model environments, and separate public tunnel URLs.

---

## Technology Stack

### Android Development

| Technology | Purpose |
|---|---|
| Kotlin | Primary programming language |
| Jetpack Compose | Declarative UI |
| Material 3 | Design system |
| Android Architecture Components | Application architecture |
| Kotlin Coroutines | Asynchronous operations |
| StateFlow | Reactive state management |
| OkHttp | HTTP networking |
| AndroidX Media3 | Audio playback |
| ExoPlayer | Media playback engine |
| MediaSessionService | Background playback |
| SQLite | Local database |
| DataStore | Preference persistence |
| Coil 3 | Image loading |
| AndroidX Palette | Color extraction |

### Artificial Intelligence and Processing

| Technology | Purpose |
|---|---|
| ACE-Step 1.5 Turbo | Generative music inference |
| Qwen2.5-Omni-3B | Language-model inference |
| Google ML Kit Translation | On-device translation |
| Google ML Kit Language Identification | Language detection |
| Digital Signal Processing | Audio feature extraction |
| Spectral Flux and Autocorrelation | Tempo estimation |
| Chroma Features | Musical key estimation |

### Backend and Infrastructure

| Technology | Purpose |
|---|---|
| Python | AI service implementation |
| FastAPI | REST API services |
| Uvicorn | ASGI server |
| Kaggle GPU Notebooks | Remote model execution |
| ngrok | Public API tunnelling |

---

## Project Structure

The following is a logical overview of the application modules and responsibilities.

```text
MoodMix/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/com/moodmix/app/
│           │   ├── navigation/
│           │   ├── ui/
│           │   │   ├── screens/
│           │   │   │   ├── home/
│           │   │   │   ├── search/
│           │   │   │   ├── generate/
│           │   │   │   ├── tools/
│           │   │   │   └── library/
│           │   │   └── theme/
│           │   ├── network/
│           │   │   ├── ApiConfig.kt
│           │   │   ├── QwenApi.kt
│           │   │   └── AceStepApi.kt
│           │   ├── audio/
│           │   │   └── AudioAnalyzer.kt
│           │   ├── data/
│           │   ├── domain/
│           │   └── playback/
│           │
│           └── res/
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

This illustrates the main architectural areas rather than an exhaustive file listing.

---

## Getting Started

### Prerequisites

- Android Studio.
- Android SDK compatible with the project's Gradle configuration.
- JDK compatible with the project's Gradle and Android Gradle Plugin versions.
- A physical Android device or compatible emulator.
- Kaggle account with access to GPU notebook sessions.
- ngrok account and authentication token for exposing the backend services.

### 1. Clone the Repository

```bash
git clone https://github.com/Ankit10869734/MoodMix.git
cd MoodMix
```

### 2. Open the Android Project

Open the cloned project in Android Studio and allow Gradle synchronization to complete.

### 3. Configure Backend Endpoints

The Android application centralizes endpoint configuration in `ApiConfig.kt`.

Configure the current service URLs for:

- ACE-Step API.
- Qwen API.
- Any other external service required by the application.

Do not commit temporary ngrok URLs or private deployment configuration to source control.

### 4. Configure Kaggle Services

The two AI backends must be deployed independently.

**Qwen service**
1. Open the Qwen Kaggle notebook.
2. Configure the required GPU environment and dependencies.
3. Add `NGROK_AUTHTOKEN` through Kaggle Secrets.
4. Execute the notebook cells.
5. Confirm that the FastAPI service starts on port `8000`.
6. Record the active public tunnel URL.

**ACE-Step service**
1. Open the ACE-Step 1.5 Kaggle notebook.
2. Configure the GPU environment and install dependencies.
3. Initialise the required ACE-Step model components.
4. Add `NGROK_AUTHTOKEN` through Kaggle Secrets.
5. Start the API on port `8001`.
6. Confirm the health endpoint and model initialisation.
7. Record the active public tunnel URL.

Update the Android endpoint configuration when either tunnel URL changes.

### 5. Build and Run

Run the application through Android Studio or use Gradle:

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Build a release APK:

```bash
./gradlew assembleRelease
```

Build an Android App Bundle:

```bash
./gradlew bundleRelease
```

---

## Build and Release Engineering

The project documentation reports the following package measurements:

| Artifact | Reported size |
|---|---:|
| Debug APK | 142.59 MB |
| Release APK | 25.40 MB |
| Release AAB | 16.13 MB |

Documented release optimisations include:

- R8 code minification.
- Resource shrinking.
- ARM64-only production ABI packaging.
- Release signing configuration.
- APK signature verification.

These are project-reported measurements and may vary with subsequent builds and dependency changes.

---

## Limitations and Deployment Considerations

MoodMix is a hybrid Android and cloud-inference application. Its current architecture has several operational constraints.

**Remote AI availability**

The Qwen and ACE-Step services depend on active Kaggle sessions and ngrok tunnels. If a session terminates or a tunnel URL changes, the Android endpoint configuration must be updated.

**Network dependency**

AI music generation requires a connection to the remote ACE-Step service. Remote Qwen functionality also requires the language service to be reachable. Local processing remains available for supported workflows.

**Audio recognition**

Voice search uses speech recognition and catalogue queries. It does not implement Shazam-style acoustic waveform fingerprinting.

**Audio analysis**

On-device DSP provides estimated musical features. BPM and key estimates should not be treated as professional studio measurements without independent benchmarking.

**Backend deployment**

Kaggle and ngrok are development and demonstration infrastructure. They are not a substitute for a production deployment with persistent hosting, authentication, monitoring, and managed compute resources.

---

## Security

The following practices should be maintained when developing or deploying MoodMix:

- Store ngrok authentication tokens in Kaggle Secrets.
- Never commit API credentials, keystore passwords, or signing keys.
- Keep signing configuration outside the public repository.
- Avoid publishing active temporary tunnel URLs.
- Do not include private user listening data in public logs or screenshots.
- Use authenticated and access-controlled endpoints before wider public deployment.

---

## Future Development

Potential areas for further engineering include:

- Persistent cloud hosting for AI inference services.
- Authenticated API access and request-level security.
- WebSocket-based generation progress updates.
- More robust background task handling.
- Improved audio-analysis accuracy through benchmark datasets.
- Acoustic waveform fingerprinting for direct music recognition.
- Further optimisation of remote inference latency and GPU memory consumption.

---

## Project Status

MoodMix is an actively developed Android application integrating music discovery, AI-assisted music generation, audio analysis, and personal music management.

Its architecture combines native Android functionality with independently hosted AI inference services.

The application is intended as a demonstration of integrating mobile development, machine learning inference, digital signal processing, REST API design, and multimedia playback into a unified software system.

---

## Author

**Ankit Das**

B.Tech — Computer Science and Engineering  
KIIT University, Bhubaneswar

**Repository:** [MoodMix](https://github.com/Ankit10869734/Major_Proj)


---

## License

No license has been specified yet. All rights are reserved by the author unless a license is added to this repository.

If you intend to make the source code reusable or open source, add an explicit `LICENSE` file and review the licenses of the included models, dependencies, and third-party services before distribution.
