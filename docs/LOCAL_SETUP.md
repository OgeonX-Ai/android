# Local setup and testing guide

This guide walks through installing the Android client, running the bundled FastAPI backend locally, and customizing the app to point at your own services.

## 1) Prerequisites
- **Android**: Android Studio Hedgehog+, Android SDK 33+, and an emulator or device running Android 8.0 (API 26) or newer.
- **Backend**: Python 3.10+ (tested with 3.10/3.11) and the ability to create a virtual environment.
- **Accounts**: Hugging Face Inference token and ElevenLabs API key for the reference backend TTS/STT/LLM pipeline.

## 2) Clone the repo
```bash
git clone https://github.com/<your-org>/android.git
cd android
```

## 3) Backend installation (recommended for end-to-end testing)
1. Create and activate a virtual environment:
   ```bash
   cd backend
   python -m venv .venv
   source .venv/bin/activate
   ```
2. Install dependencies (the Whisper package installs from GitHub under the canonical name `openai-whisper`; ensure `git` is available on your PATH). The upload handler depends on `python-multipart`, which is bundled in `requirements.txt`:
   ```bash
   pip install --upgrade pip
   # Pre-pinning build tools avoids `KeyError: '__version__'` on Windows when
   # building the openai-whisper wheel from source.
   pip install setuptools==68.2.2 wheel==0.41.3
   pip install -r requirements.txt
   ```
   If you hit PyTorch download issues on Linux CI, add `--extra-index-url https://download.pytorch.org/whl/cpu` to the install command.
3. Configure environment variables by copying the template and filling in keys:
   ```bash
   cp .env.example .env
   # edit .env to add HF_API_TOKEN, ELEVENLABS_API_KEY, VOICE_ID (optional)
   ```
   If these values are missing when you start the server and the terminal is interactive, the backend will prompt once for them and save to `.env` automatically.
4. Start the API server:
   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000
   ```
5. Keep this terminal running; the Android app will call `http://10.0.2.2:8000/talk` when using an emulator.

## 4) Android app setup
1. Open the project root (`android/`) in **Android Studio**.
2. Let Gradle sync; ensure Compose and OkHttp dependencies resolve (they are already declared in `app/build.gradle.kts`).
3. Update the backend URL if you are not using the default host:
   - In `app/src/main/java/com/example/aitalkdemo/MainActivity.kt`, change `backendUrl` to your server (e.g., LAN IP or tunneled URL).
4. (Optional) Adjust the available voices in `MainActivity.kt` to match your backend.
5. If Gradle reports a missing Android SDK, copy `local.properties.example` to `local.properties` and set `sdk.dir` to your SDK install (Android Studio usually writes this automatically once an SDK is configured).

## 5) Run the Android app
1. Start an emulator (or connect a device with USB debugging).
2. Click **Run** in Android Studio. Grant microphone permission on first launch.
3. Interact with the UI:
   - **Speak**: type text, pick a voice, and send to backend for TTS.
   - **Record**: tap to start/stop recording; audio uploads on stop.
4. Watch `logcat` for networking and playback logs if troubleshooting.
5. To run tests from the command line without downloading a new wrapper distribution, start with the bundled helper (it skips
   Android tests automatically if your SDK is not configured). If you need an SDK locally, run `./scripts/bootstrap_android_sdk.sh`
   once; it installs platform-tools, platform 34, and build-tools 34.0.0 under `$HOME/android-sdk` by default. The helper falls
   back to a GitHub mirror if Google downloads are blocked and will reuse a pre-downloaded command-line tools ZIP if you place it
   in `$HOME/android-sdk/` or point `CMDLINE_ZIP_PATH=/absolute/path/to/commandlinetools-linux-11076708_latest.zip`. When it
   succeeds, it writes `local.properties` with the SDK path for you (overwriting if present):
   ```bash
   ./scripts/run_tests.sh
   ```
   Or use the preinstalled `gradle` binary directly when your SDK is set:
   ```bash
   gradle test                 # unit tests
   gradle connectedAndroidTest # instrumentation (emulator/device + backend required)
   ```
   Unit tests stay local to the Android codebase; they do not require the backend to be running.
   For a fast backend sanity check (no external API calls), run:
   ```bash
   cd backend
   python -m compileall .
   ```

## 6) Customizing for your own app
- Swap the gradient/colors/typography under `app/src/main/java/com/example/aitalkdemo/ui/theme/` to match your brand.
- Replace the `voices` list and JSON payload in `sendTextToBackend` if your API expects different parameters.
- If your backend returns additional metadata (transcripts, error codes), extend the response handling in `MainActivity` and surface it in `HomeScreen`.
- For streaming audio or richer playback controls, consider migrating from `MediaPlayer` to ExoPlayer.

## 7) Common issues
- **Emulator cannot reach backend**: ensure the backend binds to `0.0.0.0`; use `10.0.2.2` from emulator or the host IP from a physical device.
- **Timeouts or empty audio**: confirm your backend returns MP3 bytes and increase OkHttp timeouts in `MainActivity` if your pipeline is slow.
- **Recording errors**: verify microphone permission and that no other app holds exclusive audio focus.

## 8) Next steps
- CI is configured in `.github/workflows/ci.yml` to run Android unit tests and a backend compile check on every push/PR.
- Deploy the FastAPI backend to a cloud VM or container platform and update `backendUrl` accordingly.
- Lock API keys with a secrets manager when publishing builds.
