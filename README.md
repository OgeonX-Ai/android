# AI Talk Demo (Android)

An Android sample that combines Jetpack Compose UI with microphone capture and a FastAPI backend to demonstrate text-to-speech and speech-to-text (via Whisper) style interactions. It uses OkHttp for networking and `MediaRecorder`/`MediaPlayer` for audio capture and playback. A reference FastAPI backend (with Whisper STT, Hugging Face LLM, and ElevenLabs TTS) is included under `backend/`.

> If you want to try ElevenLabs voices, you can sign up via this referral link: https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs

## Project structure

- `app/src/main/java/com/example/aitalkdemo/MainActivity.kt` — entry activity that wires up Compose, microphone permissions, recording, and network calls.
- `app/src/main/java/com/example/aitalkdemo/ui/HomeScreen.kt` — Compose UI with text input, voice picker, and controls for speaking or recording.
- `app/src/main/java/com/example/aitalkdemo/ui/theme/` — theme, color, and typography definitions for the Material 3 setup.
- `app/src/main/res/values/` — strings, colors, and theme resources used by the activity theme and Compose.
- `backend/` — reference FastAPI service that powers STT → LLM → TTS using Whisper, Hugging Face Inference, and ElevenLabs (see the ElevenLabs onboarding link above).

## Documentation map

- Local setup: [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)
- Azure deployment guide: [docs/DEPLOY_AZURE.md](docs/DEPLOY_AZURE.md)
- Backend operations/runbook: [docs/BACKEND_OPERATIONS.md](docs/BACKEND_OPERATIONS.md)
- ElevenLabs signup/referral (for TTS keys): https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs
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
- ElevenLabs account for TTS: https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs

## Configuration

- Update `backendUrl` in `MainActivity` if your backend is not reachable at `http://10.0.2.2:8000/talk` (the default for Android emulators talking to host localhost).
- Adjust the `voices` list in `MainActivity` to match the voice/persona names supported by your backend.
- If your backend expects different field names or content types, adapt the multipart form and JSON payloads in `sendToBackend` and `sendTextToBackend` respectively.
- Create a `.env` in `backend/` based on `.env.example` to supply `HF_API_TOKEN`, `ELEVENLABS_API_KEY`, and optionally `VOICE_ID`. If you need an ElevenLabs API key, enroll here: https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs
- Create a `.env` in `backend/` based on `.env.example` to supply `HF_API_TOKEN`, `ELEVENLABS_API_KEY`, and optionally `VOICE_ID`.

## Build and run

If you want a step-by-step walkthrough (including cloning, backend setup, and customization tips), see [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md).

For cloud hosting of the backend (App Service, Container Apps, or a VM) and environment hardening, read [docs/DEPLOY_AZURE.md](docs/DEPLOY_AZURE.md).

1. Open the project in Android Studio.
2. Ensure Compose and OkHttp dependencies remain in `app/build.gradle.kts` (they are included in the template).
3. Connect a device or start an emulator.
4. Run the **app** configuration. The app requests microphone permission on first launch.

### Backend (optional but recommended for end-to-end testing)

The included FastAPI backend accepts multipart audio uploads and returns MP3 audio replies after STT → LLM → TTS processing.

1. Install dependencies (note: the Whisper dependency is installed from GitHub under the canonical package name `openai-whisper`; ensure `git` is available on your PATH). The server also needs `python-multipart` for handling uploads, which is bundled in `requirements.txt`:

   ```bash
   cd backend
   python -m venv .venv
   source .venv/bin/activate
   # Pre-pinning build tools avoids `KeyError: '__version__'` seen on Windows
   # when building the openai-whisper wheel.
   pip install --upgrade pip setuptools==68.2.2 wheel==0.41.3
   pip install -r requirements.txt
   ```

2. Create a `.env` file (see `.env.example`) with:
   - `HF_API_TOKEN` (Hugging Face Inference API token)
   - `ELEVENLABS_API_KEY` (for TTS)
   - optional `VOICE_ID` override
   If these variables are missing when you start the server **and your terminal is interactive**, the backend will prompt for the values once and persist them to `.env` automatically.

3. Start the server (default port 8000):

   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000
   ```

4. On Android emulator, the app will reach the host via `http://10.0.2.2:8000/talk`.
   Launching the Android app without the backend running is fine for UI exploration, but the **Speak** and **Record** actions require a live server to return audio.

## Testing

- Create a `local.properties` file pointing to your Android SDK (copy [`local.properties.example`](local.properties.example) and adjust `sdk.dir`). Android Studio writes this file automatically when an SDK is configured. If you do not have an SDK yet, run `./scripts/bootstrap_android_sdk.sh` to download a minimal command-line install (platform 34/build-tools 34.0.0) under `$HOME/android-sdk` and re-run the tests. The helper falls back to a GitHub mirror if the Google download is blocked and will reuse a pre-downloaded ZIP placed in `$HOME/android-sdk/` or a custom path via `CMDLINE_ZIP_PATH=/path/to/commandlinetools-linux-11076708_latest.zip`.
- Prefer the bundled test runner script for local checks; it avoids Gradle distribution downloads and skips Android tests if your SDK is missing:
  ```bash
  ./scripts/run_tests.sh
  ```
- If you want to run individual steps manually:
  - JVM/unit tests (requires Android SDK configured):
    ```bash
    gradle test
    ```
    These unit tests do **not** call the backend; they only validate Android-side logic. If the Gradle wrapper download is blocked in your environment, the preinstalled `gradle` command avoids fetching the distribution.
  - Instrumentation tests on an emulator/device with the backend running (for end-to-end coverage):
    ```bash
    gradle connectedAndroidTest
    ```
    Ensure `adb devices` lists at least one online target and that the backend is reachable from the device.
  - Quick backend sanity check (no external services required):
    ```bash
    cd backend
    python -m compileall .
    ```
  This verifies the backend sources parse correctly in CI and local environments before hitting external APIs.

Automated CI runs for both Android unit tests and the backend compile check are defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Pushes and pull requests will execute the same commands shown above.

If you plan to deploy the backend to Azure (recommended for sharing with testers), see [docs/DEPLOY_AZURE.md](docs/DEPLOY_AZURE.md) for steps and cost/scale notes.

## Backend expectations

- **Audio upload**: POST multipart with field name `audio` and MIME type `audio/mp4`; returns raw MP3 bytes.
- **Text request**: POST JSON `{ "prompt": "<text>", "voice": "<voice>" }`; returns raw MP3 bytes.
- Responses should be relatively small MP3 files; long-running pipelines are supported by generous OkHttp timeouts.

## Usage tips

- Tap **Speak** after entering text and selecting a voice to trigger TTS.
- Tap **Record** to start/stop microphone capture; stopping will upload the clip automatically.
- The buttons reflect `Processing…` and `Stop` states while work is underway.
- Make sure your ElevenLabs account (https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs) is funded or on a free tier before load testing.

## Troubleshooting

- **Empty or error responses**: verify your backend returns MP3 bytes and the URL matches `backendUrl`.
- **Emulator cannot reach backend**: ensure the backend binds to `0.0.0.0` on the host so `10.0.2.2` is reachable.
- **Recording fails**: confirm microphone permission is granted in system settings; retry if a `RuntimeException` occurs during stop.
- **Slow responses/timeouts**: increase the OkHttp timeouts in `MainActivity` to accommodate your pipeline.

## Further customization

- Replace gradient colors or typography in `app/src/main/java/com/example/aitalkdemo/ui/theme/` to match your branding.
- Hook additional UI state (e.g., transcript display, error toasts) into `HomeScreen` by passing more state from `MainActivity`.
- Swap out `MediaPlayer` for ExoPlayer if you need streaming or more advanced playback controls.
