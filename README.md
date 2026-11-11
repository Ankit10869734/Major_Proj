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
- Tab navigation state preservation preventing unnecessary re-fetches.
- Regional theme adaptation and astronomical lunisolar Diwali festival date engine.

### 2. Search

A search interface supporting structured and unstructured input.

- Song, artist, and album catalogue search.
- Filter chips (All, Songs, Artists, Albums).
- Voice search using Android SpeechRecognizer.
- Qwen natural-language search query interpretation.

### 3. Create

An AI music creation workspace for generating custom instrumental audio.

- Prompt input (up to 400 characters).
- Duration selection (5 to 30 seconds).
- Asynchronous music generation powered by ACE-Step 1.5 Turbo.
- Task ID tracking, status polling, and WAV download.
- Local persistent storage and MediaStore public export.

### 4. Extras (Tools)

A suite of audio, language, and playback tools.

- On-device Audio DSP Analysis (BPM, key signature, energy curve, loudness).
- Lyrics Intelligence (on-device NLP sentiment, syllable counting, rhyme schemes).
- Google ML Kit On-Device Translation (12+ languages).
- AI DJ transition auditioning with BPM matching and volume crossfades.

### 5. Library

A central location for user-saved and generated content.

- Generated AI Creations, Liked Songs, and Listening History.
- Reactive real-time updates when songs are generated, played, or liked.
- Qwen Personal Listening Insights.
