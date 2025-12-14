import logging
import os
import sys
import tempfile
import time
from pathlib import Path
from typing import Optional

import requests
import whisper
from dotenv import load_dotenv, set_key
from fastapi import Body, FastAPI, File, Request, UploadFile
from fastapi.responses import JSONResponse, Response
from huggingface_hub import InferenceClient

# ------------------ Init ------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)

ENV_PATH = Path(__file__).resolve().parent / ".env"
load_dotenv(dotenv_path=ENV_PATH, override=False)


def _ensure_env(name: str, prompt: str) -> Optional[str]:
    """Prompt for a missing env var and persist it into .env when interactive."""

    current = os.getenv(name)
    if current:
        return current

    if not sys.stdin.isatty():
        logger.warning("%s missing in .env and stdin is not interactive; skipping prompt", name)
        return None

    try:
        user_value = input(f"{prompt}: ").strip()
    except EOFError:
        logger.warning("Input unavailable while prompting for %s", name)
        return None

    if not user_value:
        logger.warning("%s not provided; backend may fail to start", name)
        return None

    set_key(str(ENV_PATH), name, user_value)
    os.environ[name] = user_value
    logger.info("Saved %s to %s", name, ENV_PATH)
    return user_value


HF_TOKEN: Optional[str] = _ensure_env("HF_API_TOKEN", "Enter your Hugging Face token (HF_API_TOKEN)")
ELEVEN_KEY: Optional[str] = _ensure_env("ELEVENLABS_API_KEY", "Enter your ElevenLabs API key (ELEVENLABS_API_KEY)")
VOICE_ID: str = os.getenv("VOICE_ID", "EXAVITQu4vr4xnSDxMaL")

if not HF_TOKEN:
    logger.warning("HF_API_TOKEN missing; LLM calls will fail")
if not ELEVEN_KEY:
    logger.warning("ELEVENLABS_API_KEY missing; TTS will fail")

# Hugging Face LLM (chat model)
HF_MODEL = "meta-llama/Llama-3.1-8B-Instruct"
hf_client = InferenceClient(model=HF_MODEL, token=HF_TOKEN)

# Whisper STT
logger.info("Loading Whisper model 'tiny' for STT (Finnish)...")
try:
    # If you want better Finnish, change to "base" or "small" (slower but more accurate):
    # whisper_model = whisper.load_model("base")
    whisper_model = whisper.load_model("tiny")
    logger.info("Whisper loaded")
except Exception as exc:  # pragma: no cover - startup failure
    logger.exception("Failed to load Whisper model")
    raise RuntimeError("Whisper model failed to load") from exc

app = FastAPI(title="Empathy Phone Mobile Backend")


# ------------------ Helper functions ------------------


def stt_local(path: str) -> str:
    """Transcribe audio file to text using local Whisper."""
    logger.info("Transcribing %s ...", path)
    t0 = time.time()
    result = whisper_model.transcribe(path, language="fi")
    dt = time.time() - t0
    text = (result.get("text") or "").strip()
    logger.info("STT text (%.1fs): %s", dt, text)
    return text


def ask_llm(prompt: str) -> str:
    """Call HF chat completion."""
    logger.info("Asking LLM...")
    t0 = time.time()
    response = hf_client.chat_completion(
        messages=[
            {
                "role": "system",
                "content": (
                    "You are a warm, empathetic assistant. "
                    "If user speaks Finnish, answer in Finnish. "
                    "Otherwise answer in English. Keep replies short (1–3 sentences)."
                ),
            },
            {"role": "user", "content": prompt},
        ],
        max_tokens=120,
        temperature=0.7,
    )
    dt = time.time() - t0
    answer = response.choices[0].message["content"]
    logger.info("LLM reply (%.1fs): %s", dt, answer)
    return answer


def tts_elevenlabs(text: str, voice_id: str | None = None) -> bytes:
    """Generate MP3 with ElevenLabs for given text.

    :param voice_id: Optional voice ID override; defaults to VOICE_ID.
    """
    chosen_voice = voice_id or VOICE_ID
    logger.info("Generating TTS with ElevenLabs...")
    t0 = time.time()
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{chosen_voice}"
    headers = {
        "xi-api-key": ELEVEN_KEY or "",
        "Content-Type": "application/json",
    }
    data = {
        "text": text,
        "model_id": "eleven_multilingual_v2",
        "voice_settings": {
            "stability": 0.5,
            "similarity_boost": 0.8,
        },
    }
    response = requests.post(url, json=data, headers=headers, timeout=120)
    logger.info("TTS status: %s", response.status_code)
    response.raise_for_status()
    dt = time.time() - t0
    logger.info("TTS done in %.1fs, bytes=%s", dt, len(response.content))
    return response.content  # MP3 bytes


# ------------------ Routes ------------------


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/talk")
async def talk(
    request: Request,
    audio: UploadFile | None = File(default=None),
    prompt: str | None = Body(default=None),
    voice: str | None = Body(default=None),
):
    """Handle either audio uploads or raw text prompts.

    Android sends microphone recording here as form-data (field name 'audio', file type
    audio/mp4). When users type text instead, the app posts JSON `{ "prompt": "...",
    "voice": "optional_voice_id" }`. We return MP3 bytes with the AI reply in both
    cases.
    """
    t0_all = time.time()
    tmp_path = None

    try:
        # Accept prompt/voice from JSON, multipart form, or explicit body parameters.
        user_text: str | None = None
        chosen_voice: str | None = None

        content_type = request.headers.get("content-type", "")
        if "application/json" in content_type:
            try:
                payload = await request.json()
            except Exception:
                payload = None
            if isinstance(payload, dict):
                user_text = (payload.get("prompt") or "").strip() or None
                chosen_voice = (payload.get("voice") or "").strip() or None
        elif "multipart/form-data" in content_type:
            form = await request.form()
            user_text = (form.get("prompt") or "").strip() or None
            chosen_voice = (form.get("voice") or "").strip() or None

        # Body parameters as fallback (keeps compatibility with prior clients)
        if user_text is None:
            user_text = (prompt or "").strip() or None
        if chosen_voice is None:
            chosen_voice = (voice or "").strip() or None

        if audio is not None:
            suffix = os.path.splitext(audio.filename or "")[1] or ".m4a"
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                raw = await audio.read()
                tmp.write(raw)
                tmp_path = tmp.name

            logger.info("Received file: %s, size=%s bytes", tmp_path, len(raw))

            if not user_text:
                user_text = stt_local(tmp_path)

        if not user_text:
            logger.warning(
                "No prompt text supplied or detected from audio. Content-Type=%s", content_type
            )
            return JSONResponse(
                status_code=400,
                content={
                    "error": "Provide either text prompt or valid audio",
                    "hint": "Send JSON {prompt, voice} or multipart/form-data with 'audio'",
                },
            )

        # 2) LLM reply
        reply_text = ask_llm(user_text)

        # 3) TTS
        mp3_bytes = tts_elevenlabs(reply_text, voice_id=chosen_voice)

        total = time.time() - t0_all
        logger.info("/talk total time: %.1fs, MP3 bytes=%s", total, len(mp3_bytes))

        return Response(content=mp3_bytes, media_type="audio/mpeg")

    except requests.HTTPError as http_err:
        logger.exception("TTS HTTP error")
        return JSONResponse(status_code=http_err.response.status_code, content={"error": str(http_err)})
    except Exception as exc:  # pragma: no cover - catch-all for runtime issues
        logger.exception("Error in /talk")
        return JSONResponse(status_code=500, content={"error": str(exc)})
    finally:
        if tmp_path:
            try:
                os.remove(tmp_path)
            except OSError:
                logger.warning("Failed to delete temp file %s", tmp_path)


if __name__ == "__main__":  # pragma: no cover - manual debugging
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
