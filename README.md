# AI Talk Demo (Android)

An Android sample that combines Jetpack Compose UI with microphone capture and a FastAPI backend to demonstrate text-to-speech and speech-to-text (via Whisper) style interactions. It uses OkHttp for networking and `MediaRecorder`/`MediaPlayer` for audio capture and playback. A reference FastAPI backend (with Whisper STT, Hugging Face LLM, and ElevenLabs TTS) is included under `backend/`.

## Project structure

- `app/src/main/java/com/example/aitalkdemo/MainActivity.kt` — entry activity that wires up Compose, microphone permissions, recording, and network calls.
- `app/src/main/java/com/example/aitalkdemo/ui/HomeScreen.kt` — Compose UI with text input, voice picker, and controls for speaking or recording.
- `app/src/main/java/com/example/aitalkdemo/ui/theme/` — theme, color, and typography definitions for the Material 3 setup.
- `app/src/main/res/values/` — strings, colors, and theme resources used by the activity theme and Compose.
- `backend/` — reference FastAPI service that powers STT → LLM → TTS using Whisper, Hugging Face Inference, and ElevenLabs.

## Features

- Compose-first UI with Material 3 styling and gradient background.
- Text input with voice/persona selector for backend TTS requests.
- Microphone recording with permission handling and status indicators.
- Multipart audio upload and JSON text requests to a configurable backend endpoint.
- Local playback of backend-generated MP3 responses.
- Optional backend with Whisper STT, Hugging Face LLM, and ElevenLabs TTS.

## Prerequisites

- Android Studio Hedgehog or newer.
- Android SDK 33+ with a device or emulator running Android 8.0 (API 26) or later.
- A reachable FastAPI backend with a `/talk` endpoint that accepts either:
  - `multipart/form-data` containing an `audio` file (`audio/mp4`), or
  - JSON payload with `prompt` and `voice` fields, responding with MP3 audio bytes.

## Configuration

- Update `backendUrl` in `MainActivity` if your backend is not reachable at `http://10.0.2.2:8000/talk` (the default for Android emulators talking to host localhost).
- Adjust the `voices` list in `MainActivity` to match the voice/persona names supported by your backend.
- If your backend expects different field names or content types, adapt the multipart form and JSON payloads in `sendToBackend` and `sendTextToBackend` respectively.
- Create a `.env` in `backend/` based on `.env.example` to supply `HF_API_TOKEN`, `ELEVENLABS_API_KEY`, and optionally `VOICE_ID`.

## Build and run

1. Open the project in Android Studio.
2. Ensure Compose and OkHttp dependencies remain in `app/build.gradle.kts` (they are included in the template).
3. Connect a device or start an emulator.
4. Run the **app** configuration. The app requests microphone permission on first launch.

### Backend (optional but recommended for end-to-end testing)

The included FastAPI backend accepts multipart audio uploads and returns MP3 audio replies after STT → LLM → TTS processing.

1. Install dependencies:

   ```bash
   cd backend
   python -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   ```

2. Create a `.env` file (see `.env.example`) with:
   - `HF_API_TOKEN` (Hugging Face Inference API token)
   - `ELEVENLABS_API_KEY` (for TTS)
   - optional `VOICE_ID` override

3. Start the server (default port 8000):

   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000
   ```

4. On Android emulator, the app will reach the host via `http://10.0.2.2:8000/talk`.

## Backend expectations

- **Audio upload**: POST multipart with field name `audio` and MIME type `audio/mp4`; returns raw MP3 bytes.
- **Text request**: POST JSON `{ "prompt": "<text>", "voice": "<voice>" }`; returns raw MP3 bytes.
- Responses should be relatively small MP3 files; long-running pipelines are supported by generous OkHttp timeouts.

## Usage tips

- Tap **Speak** after entering text and selecting a voice to trigger TTS.
- Tap **Record** to start/stop microphone capture; stopping will upload the clip automatically.
- The buttons reflect `Processing…` and `Stop` states while work is underway.

## Troubleshooting

- **Empty or error responses**: verify your backend returns MP3 bytes and the URL matches `backendUrl`.
- **Emulator cannot reach backend**: ensure the backend binds to `0.0.0.0` on the host so `10.0.2.2` is reachable.
- **Recording fails**: confirm microphone permission is granted in system settings; retry if a `RuntimeException` occurs during stop.
- **Slow responses/timeouts**: increase the OkHttp timeouts in `MainActivity` to accommodate your pipeline.

## Further customization

- Replace gradient colors or typography in `app/src/main/java/com/example/aitalkdemo/ui/theme/` to match your branding.
- Hook additional UI state (e.g., transcript display, error toasts) into `HomeScreen` by passing more state from `MainActivity`.
- Swap out `MediaPlayer` for ExoPlayer if you need streaming or more advanced playback controls.
