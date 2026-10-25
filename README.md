# MoodMix

MoodMix is an Android application built with Kotlin and Jetpack Compose for music generation, lyrics generation, and audio-related tools.

The project includes the Android frontend and API client used to connect the app to backend services running ML inference pipelines. This document covers the app architecture, development setup, server configuration, and API contracts for backend integration.

## Overview

The application is organized into four main sections:

- **Home** — music recommendations and discovery.
- **Generate** — music and lyrics generation.
- **Tools** — voice search, song analysis, lyrics analysis, and AI DJ transitions.
- **Library** — access to generated and saved audio.

The Android application handles the UI, network requests, audio playback, local audio caching, downloads, and file sharing. The backend is responsible for model inference and the API endpoints that expose those capabilities.

## Architecture

```text
+------------------+       +-----------------------+       +----------------------+
| Jetpack Compose  | ----> | MusicGenApi            | ----> | OkHttpClient         |
| UI screens       |       | Coroutines / IO        |       | HTTP requests        |
+------------------+       +-----------------------+       +----------------------+
         ^                                                         |
         |                                                         v
+------------------+       +-----------------------+       +----------------------+
| ExoPlayer        | <---- | Local audio cache      | <---- | Backend HTTP server  |
| Playback         |       | Generated audio files |       | Flask / FastAPI      |
+------------------+       +-----------------------+       +----------------------+
```

The frontend uses Kotlin Coroutines and OkHttp to perform network operations off the main thread. Generated audio is cached locally and played through AndroidX Media3 ExoPlayer.

### Responsibilities

**Android frontend**
- Jetpack Compose UI with Material 3.
- Audio playback and progress seeking through Media3 ExoPlayer.
- Local WAV caching, downloads to `Music/MoodMix`, and system file sharing.
- Runtime permission handling for `RECORD_AUDIO`.
- Native file selection for audio files using `audio/*`.
- HTTP communication with the backend.

**Backend**
- Hosting and running ML inference pipelines, including MusicGen, speech recognition, and lyrics-related models.
- Exposing REST endpoints for generation, recommendations, speech recognition, audio analysis, lyrics intelligence, and AI DJ transitions.
- Returning audio streams or structured JSON responses.
- Maintaining API availability, tunnel configuration, and response latency.

## Development setup

### Requirements

| Component | Version / configuration |
|---|---|
| IDE | Android Studio Quail (2026.1.4) or newer |
| Gradle runtime JDK | JDK 17 |
| Java source/target compatibility | Java 11 (`JavaVersion.VERSION_11`) |
| Compile SDK | 37 |
| Minimum SDK | 29 |
| Target SDK | 37 |

The Java source and target compatibility settings are configured in `app/build.gradle.kts`. The Gradle runtime JDK is a separate requirement.

### Main dependencies

- Jetpack Compose and Material 3
- OkHttp 4.12.0
- AndroidX Media3 ExoPlayer 1.11.1
- AndroidX Navigation Compose
- Kotlin Coroutines

### Build and run

From the project root, build the debug variant:

```bash
./gradlew assembleDebug
```

On Windows, the equivalent command is:

```powershell
.\gradlew.bat assembleDebug
```

To run the app, open the project in Android Studio, sync Gradle, select a device or emulator, and use **Run** (`Shift + F10`).

## Backend configuration

Backend networking settings are defined in:

`app/src/main/java/com/moodmix/app/network/ApiConfig.kt`

The current configuration supports mock data while the backend is under development.

```kotlin
object ApiConfig {
    const val USE_MOCK_DATA = true

    const val BASE_URL = "http://YOUR_TEAM_SERVER_IP:8000"

    val GENERATE_ENDPOINT get() = "$BASE_URL/generate"
    val RECOMMENDATIONS_ENDPOINT get() = "$BASE_URL/recommendations"
    val VOICE_SEARCH_ENDPOINT get() = "$BASE_URL/voice-search"
    val SONG_ANALYSIS_ENDPOINT get() = "$BASE_URL/analyze-song"
    val LYRICS_INTELLIGENCE_ENDPOINT get() = "$BASE_URL/lyrics-intelligence"
    val AI_DJ_ENDPOINT get() = "$BASE_URL/ai-dj"
}
```

Replace `BASE_URL` with the backend server's base URL when a server is available. Set `USE_MOCK_DATA` to `false` when testing against a compatible live backend.

Example base URLs:

| Environment | Base URL |
|---|---|
| Android Emulator | `http://10.0.2.2:8000` |
| Physical device on the same network | `http://192.168.1.50:8000` |
| Cloudflare Quick Tunnel | `https://YOUR-SUBDOMAIN.trycloudflare.com` |

The addresses above are examples. Use the actual host, port, and tunnel URL for the server being tested.

For a Cloudflare Quick Tunnel, start the tunnel against the local backend server:

```bash
cloudflared tunnel --url http://localhost:8000
```

Use the HTTPS URL generated by Cloudflare as `BASE_URL`.

## API reference

The following contracts describe the frontend's expected backend interface. Routes marked as scaffolding are proposed integration points and should not be treated as confirmed, working backend endpoints.

| Endpoint | Method | Purpose | Request | Response | Frontend status |
|---|---|---|---|---|---|
| `/generate` | POST | Music and lyrics generation | JSON | WAV stream or JSON | Implemented and wired |
| `/recommendations` | POST | Music recommendations | JSON | JSON | Scaffolding |
| `/voice-search` | POST | Speech-to-text | Multipart form data | JSON | Scaffolding |
| `/analyze-song` | POST | Audio feature extraction | Multipart form data | JSON | Scaffolding |
| `/lyrics-intelligence` | POST | Lyrics analysis | JSON | JSON | Scaffolding |
| `/ai-dj` | POST | Audio transitions | JSON | Audio URL or JSON | Scaffolding |

### Music generation — `POST /generate`

**Frontend:** `GenerateScreen.kt`

Request body:

```json
{
  "prompt": "peaceful lo-fi beat for studying at night",
  "max_new_tokens": 750,
  "generate_lyrics": true,
  "genre": "Lo-Fi"
}
```

The backend can return audio in either of the following formats.

**Option A: Audio stream**

- Content type: `audio/wav`
- Response body: WAV audio bytes.

**Option B: JSON response**

```json
{
  "status": "success",
  "song_title": "MoodMix - Peaceful Lo-Fi",
  "audio_url": "https://your-server.com/audio/output_123.wav",
  "lyrics": "[Verse 1]\nWalking under neon lights...\n[Chorus]\nFeel the rhythm low...",
  "message": "Generation completed"
}
```

### Recommendations — `POST /recommendations`

**Frontend:** `HomeScreen.kt`

Request body:

```json
{
  "genre": "Lo-Fi"
}
```

Response:

```json
{
  "status": "success",
  "tracks": [
    {
      "title": "Daily Mix 1",
      "artist": "MoodMix AI",
      "audio_url": "https://your-server.com/audio/rec1.mp3"
    }
  ]
}
```

### Voice search — `POST /voice-search`

**Frontend:** `ToolsScreen.kt`

Request content type: `multipart/form-data`

| Field | Description |
|---|---|
| `audio` | Audio file, such as WAV or AAC |

Response:

```json
{
  "status": "success",
  "recognized_text": "chill late night lo-fi beat"
}
```

### Song analysis — `POST /analyze-song`

**Frontend:** `ToolsScreen.kt`

Request content type: `multipart/form-data`

| Field | Description |
|---|---|
| `audio` | Audio file to analyze |

Response:

```json
{
  "status": "success",
  "bpm": 120,
  "key_signature": "C Major",
  "energy_level": 0.84,
  "primary_mood": "Energetic & Upbeat"
}
```

### Lyrics intelligence — `POST /lyrics-intelligence`

**Frontend:** `ToolsScreen.kt`

Request body:

```json
{
  "lyrics_text": "Walking through the quiet neon lights...",
  "action": "analyze"
}
```

Response:

```json
{
  "status": "success",
  "sentiment": "Highly Positive & Uplifting",
  "rhyme_scheme": "AABB AA",
  "summary": "Themes of music, freedom, and reflection."
}
```

### AI DJ transitions — `POST /ai-dj`

**Frontend:** `ToolsScreen.kt`

Request body:

```json
{
  "track_a": "https://server.com/audio/a.mp3",
  "track_b": "https://server.com/audio/b.mp3",
  "crossfade_seconds": 4
}
```

Response:

```json
{
  "status": "success",
  "transition_audio_url": "https://server.com/audio/dj_mix_123.wav"
}
```

## Network testing

When testing against a local backend:

- **Android Emulator:** Use `10.0.2.2` to reach the development machine's localhost.
- **Physical device:** Use the development machine's LAN IP and ensure the firewall allows connections to the backend port.
- **Cloudflare Tunnel:** Use the generated HTTPS tunnel URL as the base URL.

The backend port in these examples is `8000`. Use the port on which your server actually listens.

## Project structure

```text
MoodMix/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/moodmix/app/
│   │   │   │   ├── navigation/
│   │   │   │   ├── network/
│   │   │   │   └── ui/screens/
│   │   │   │       ├── generate/
│   │   │   │       ├── home/
│   │   │   │       ├── library/
│   │   │   │       └── tools/
│   │   │   └── res/
│   │   └── test/
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

