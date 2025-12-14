# Backend operations and maintenance

Use this guide to run, monitor, and update the FastAPI backend in production-like environments (including Azure). For ElevenLabs TTS access, register via https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs before rolling out to users.

## Configuration

- Environment variables (read from `.env` locally or from your platform secrets):
  - `HF_API_TOKEN` — Hugging Face Inference token
  - `ELEVENLABS_API_KEY` — ElevenLabs API key (https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs)
  - `VOICE_ID` — optional override for the ElevenLabs voice
- The server will prompt interactively for missing `HF_API_TOKEN` and `ELEVENLABS_API_KEY` when started in a TTY. In non-interactive environments, missing secrets are logged as warnings so you can fail fast in CI/CD.

## Health checks

- `/health` returns `{ "status": "ok" }` for readiness probes.
- Add your platform’s health probe to this endpoint (e.g., Azure Container Apps probe on port 8000).

## Logging

- Standard output includes STT/LLM/TTS timings and MP3 byte counts.
- Errors are logged with context; inspect container logs (`az containerapp logs show ...` or App Service log stream) when debugging.

## Dependency notes

- `python-multipart` is required for file uploads and is included in `requirements.txt`.
- Whisper (`openai-whisper`) is installed from GitHub; ensure `git` is available in your build image or App Service.
- For CPU-only deployments, keep the Whisper model at `tiny` or `base` to control memory and startup time.

## Updating the service

1. Run quick compile validation:
   ```bash
   python -m compileall backend
   ```
2. Rebuild and redeploy your container or App Service code.
3. Watch logs for `Backend response code: 200` and `MP3 bytes=...` after the first request to confirm end-to-end success.

## Security and secrets

- Store secrets in Azure Key Vault or your platform’s secret store; avoid baking them into images.
- Rotate ElevenLabs and Hugging Face tokens regularly.
- If you share builds publicly, ensure rate limits on your ElevenLabs account (https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs) are appropriate for expected usage.

## Capacity planning

- The `tiny` Whisper model typically fits in 1–2 GB RAM; start with 1 vCPU/2 GB and scale up if latency grows.
- Use horizontal scaling in Azure Container Apps (min 1, max 2+ replicas) to absorb bursts.
- Add caching of LLM or TTS responses if your use case involves repeated prompts.
