# 🎵 MoodMix — AI Music & Lyrics Generation Android App

Welcome to the **MoodMix** repository! MoodMix is a modern Android application built with **Kotlin** and **Jetpack Compose** designed for AI-driven music generation, lyrics creation, and audio intelligence.

This document serves as the **Backend API Integration Guide** for ML and backend team members connecting their server models (Flask, FastAPI, PyTorch, Cloudflare Tunnels, etc.) to the MoodMix frontend.

---

## 📌 1. Project Overview & Responsibilities

- **Frontend (Android)**:
  - Responsive Jetpack Compose Material 3 UI across 4 main workspaces (**Home**, **Generate**, **Tools**, **Library**).
  - Media3 ExoPlayer audio playback, progress seeking, local WAV file caching, downloading to `Music/MoodMix`, and system file sharing.
  - Runtime permission management (`RECORD_AUDIO`) and native device file picking (`audio/*`).
  - Network request execution off the main thread using Kotlin Coroutines and OkHttp 4.12.0.

- **Backend (ML & Model Hosting Team)**:
  - Hosting ML inference pipelines (e.g. Meta MusicGen / AudioCraft, Hugging Face Transformers, Whisper STT, Lyrics NLP models).
  - Exposing REST API HTTP endpoints returning raw audio binary streams (`audio/wav`) or structured JSON payloads.
  - Ensuring CORS/tunnel stability and low latency responses.

---

## 🏗️ 2. Architecture & Request/Response Data Flow

```
+------------------+         +-----------------------+         +-------------------------+
| Jetpack Compose  | ------> | MusicGenApi           | ------> | OkHttpClient            |
| UI Screens       |         | (Coroutines / IO)     |         | (Timeouts: 30s/10m)     |
+------------------+         +-----------------------+         +-------------------------+
         ^                                                                  |
         |                                                                  v
+------------------+         +-----------------------+         +-------------------------+
| ExoPlayer        | <------ | Local WAV Cache       | <------ | Backend HTTP Server     |
| Playback State   |         | (cacheDir/generated)  |         | (Flask/FastAPI/Tunnel)  |
+------------------+         +-----------------------+         +-------------------------+
```

---

## 🛠️ 3. Environment & Build Setup

- **IDE**: Android Studio Quail (2026.1.4) or newer
- **Gradle Runtime JDK**: JDK 17 (Required by Android Gradle Plugin to execute Gradle build tasks)
- **Java Language Compatibility**: Java 11 (`JavaVersion.VERSION_11` configured in `app/build.gradle.kts` for source & target compilation)
- **Compile SDK**: 37 | **Min SDK**: 29 | **Target SDK**: 37
- **Key Libraries**:
  - `androidx.compose.material3` (Jetpack Compose Material 3)
  - `com.squareup.okhttp3:okhttp:4.12.0`
  - `androidx.media3:media3-exoplayer:1.11.1`
  - `androidx.navigation:navigation-compose`

---

## 📡 4. Centralized Server Configuration

All backend networking parameters are centralized in `app/src/main/java/com/moodmix/app/network/ApiConfig.kt`:

```kotlin
object ApiConfig {

    /**
     * Set to 'true' during local frontend testing (returns mock responses).
     * Set to 'false' when connecting to your team's live backend server.
     */
    const val USE_MOCK_DATA = true

    /**
     * PASTE YOUR LIVE BACKEND SERVER URL HERE
     * Examples:
     * - Android Emulator local host: "http://10.0.2.2:8000"
     * - Physical Device LAN IP:      "http://192.168.1.50:8000"
     * - Cloudflare Quick Tunnel:    "https://YOUR-SUBDOMAIN.trycloudflare.com"
     */
    const val BASE_URL = "http://YOUR_TEAM_SERVER_IP:8000"

    val GENERATE_ENDPOINT            get() = "$BASE_URL/generate"
    val RECOMMENDATIONS_ENDPOINT     get() = "$BASE_URL/recommendations"
    val VOICE_SEARCH_ENDPOINT        get() = "$BASE_URL/voice-search"
    val SONG_ANALYSIS_ENDPOINT       get() = "$BASE_URL/analyze-song"
    val LYRICS_INTELLIGENCE_ENDPOINT get() = "$BASE_URL/lyrics-intelligence"
    val AI_DJ_ENDPOINT               get() = "$BASE_URL/ai-dj"
}
```

---

## 📋 5. Endpoint Inventory & Implementation Status

| Endpoint Route | HTTP Method | Purpose | Request Type | Response Type | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/generate` | `POST` | Core Music & Lyrics Generation | `application/json` | Binary Stream (`audio/wav`) OR `application/json` | **Implemented & Wired** |
| `/recommendations` | `POST` | Song Recommendations | `application/json` | `application/json` | **Scaffolding Implemented** (Proposed) |
| `/voice-search` | `POST` | Speech-to-Text Voice Search | `multipart/form-data` | `application/json` | **Scaffolding Implemented** (Proposed) |
| `/analyze-song` | `POST` | Audio Feature Extraction | `multipart/form-data` | `application/json` | **Scaffolding Implemented** (Proposed) |
| `/lyrics-intelligence` | `POST` | Lyrics Sentiment & NLP | `application/json` | `application/json` | **Scaffolding Implemented** (Proposed) |
| `/ai-dj` | `POST` | Smart Crossfade Transitions | `application/json` | Binary Stream OR `application/json` | **Scaffolding Implemented** (Proposed) |

---

## 📖 6. Detailed API Contracts & Request/Response Examples

### 6.1 Music & Lyrics Generation (`POST /generate`)
- **UI Screen**: `GenerateScreen.kt`
- **Request Body**:
  ```json
  {
    "prompt": "peaceful lo-fi beat for studying at night",
    "max_new_tokens": 750,
    "generate_lyrics": true,
    "genre": "Lo-Fi"
  }
  ```
- **Response Format Option A (Direct Audio Stream)**:
  - Header: `Content-Type: audio/wav`
  - Body: Binary WAV audio bytes.
- **Response Format Option B (JSON Response)**:
  ```json
  {
    "status": "success",
    "song_title": "MoodMix - Peaceful Lo-Fi",
    "audio_url": "https://your-server.com/audio/output_123.wav",
    "lyrics": "[Verse 1]\nWalking under neon lights...\n[Chorus]\nFeel the rhythm low...",
    "message": "Generation completed"
  }
  ```

---

### 6.2 Song Recommendations (`POST /recommendations`)
- **UI Screen**: `HomeScreen.kt`
- **Request Body**:
  ```json
  {
    "genre": "Lo-Fi"
  }
  ```
- **Response Body**:
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

---

### 6.3 Voice Search Speech-to-Text (`POST /voice-search`)
- **UI Screen**: `ToolsScreen.kt` (Voice Search Workspace)
- **Request Body**: `multipart/form-data`
  - Field: `audio` (file binary, WAV or AAC)
- **Response Body**:
  ```json
  {
    "status": "success",
    "recognized_text": "chill late night lo-fi beat"
  }
  ```

---

### 6.4 Song Analysis (`POST /analyze-song`)
- **UI Screen**: `ToolsScreen.kt` (Song Analysis Workspace)
- **Request Body**: `multipart/form-data`
  - Field: `audio` (uploaded user audio file)
- **Response Body**:
  ```json
  {
    "status": "success",
    "bpm": 120,
    "key_signature": "C Major",
    "energy_level": 0.84,
    "primary_mood": "Energetic & Upbeat"
  }
  ```

---

### 6.5 Lyrics Intelligence (`POST /lyrics-intelligence`)
- **UI Screen**: `ToolsScreen.kt` (Lyrics Intelligence Workspace)
- **Request Body**:
  ```json
  {
    "lyrics_text": "Walking through the quiet neon lights...",
    "action": "analyze"
  }
  ```
- **Response Body**:
  ```json
  {
    "status": "success",
    "sentiment": "Highly Positive & Uplifting",
    "rhyme_scheme": "AABB AA",
    "summary": "Themes of music, freedom, and reflection."
  }
  ```

---

### 6.6 AI DJ Smart Transitions (`POST /ai-dj`)
- **UI Screen**: `ToolsScreen.kt` (AI DJ Workspace)
- **Request Body**:
  ```json
  {
    "track_a": "https://server.com/audio/a.mp3",
    "track_b": "https://server.com/audio/b.mp3",
    "crossfade_seconds": 4
  }
  ```
- **Response Body**:
  ```json
  {
    "status": "success",
    "transition_audio_url": "https://server.com/audio/dj_mix_123.wav"
  }
  ```

---

## ⚡ 7. Networking & Device Testing Guidelines

When testing your ML server against a physical phone or emulator:

1. **Android Emulator**: Use `http://10.0.2.2:8000` to refer to `localhost:8000` on your host PC.
2. **Physical Device on Wi-Fi**: Use your PC's local LAN address (e.g., `http://192.168.1.X:8000`). Make sure your PC firewall allows inbound connections on port 8000.
3. **Cloudflare Quick Tunnel**: Run `cloudflared tunnel --url http://localhost:8000` and paste the generated HTTPS URL in `ApiConfig.BASE_URL`.

---

## 🧪 8. Build & Verification Instructions

To build and verify the app:
1. Open terminal in project root and run:
   ```bash
   ./gradlew assembleDebug
   ```
2. Deploy to device or emulator using Android Studio Run button (`Shift + F10`).
