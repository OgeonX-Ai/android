import base64
import logging
import os
import secrets
import sys
import tempfile
import time
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Optional

import requests
import whisper
from dotenv import load_dotenv, set_key
from fastapi import Body, FastAPI, File, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from huggingface_hub import InferenceClient


# ------------------ Logging ------------------
LOG_DIR = Path(__file__).resolve().parent / "logs"
LOG_DIR.mkdir(parents=True, exist_ok=True)

LOG_FORMAT = "%(asctime)s [%(levelname)s] [%(request_id)s] %(message)s"


class RequestIdFilter(logging.Filter):
    """Ensures every record has a request_id attribute."""

    def __init__(self, default_request_id: str = "backend") -> None:
        super().__init__()
        self.default_request_id = default_request_id

    def filter(self, record: logging.LogRecord) -> bool:  # pragma: no cover - logging glue
        if not hasattr(record, "request_id"):
            record.request_id = self.default_request_id
        return True


def setup_logging() -> logging.Logger:
    logger = logging.getLogger("aitalk")
    logger.setLevel(logging.INFO)
    logger.propagate = False

    # Clear any default handlers from reloads
    logger.handlers.clear()

    formatter = logging.Formatter(LOG_FORMAT)
    request_filter = RequestIdFilter()

    stream_handler = logging.StreamHandler(sys.stdout)
    stream_handler.setFormatter(formatter)
    stream_handler.addFilter(request_filter)

    file_handler = RotatingFileHandler(
        LOG_DIR / "backend.log", maxBytes=1_000_000, backupCount=3
    )
    file_handler.setFormatter(formatter)
    file_handler.addFilter(request_filter)

    logger.addHandler(stream_handler)
    logger.addHandler(file_handler)

    return logger


logger = setup_logging()


def get_request_logger(request_id: str) -> logging.LoggerAdapter:
    return logging.LoggerAdapter(logger, {"request_id": request_id})


# ------------------ Env init ------------------
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
hf_client = InferenceClient(
    model=HF_MODEL, token=HF_TOKEN, base_url="https://router.huggingface.co"
)

# Whisper STT
logger.info("Loading Whisper model 'tiny' for STT (Finnish)...")
try:
    whisper_model = whisper.load_model("tiny")
    logger.info("Whisper loaded")
except Exception as exc:  # pragma: no cover - startup failure
    logger.exception("Failed to load Whisper model")
    raise RuntimeError("Whisper model failed to load") from exc

app = FastAPI(title="Empathy Phone Mobile Backend")


# ------------------ Helper functions ------------------


def stt_local(request_logger: logging.LoggerAdapter, path: str) -> str:
    request_logger.info("Transcribing file=%s", path)
    t0 = time.time()
    result = whisper_model.transcribe(path, language="fi")
    dt = time.time() - t0
    text = (result.get("text") or "").strip()
    request_logger.info("STT done in %.2fs chars=%s", dt, len(text))
    return text


def ask_llm(request_logger: logging.LoggerAdapter, prompt: str) -> str:
    request_logger.info("Calling LLM model=%s via router.huggingface.co", HF_MODEL)
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
    request_logger.info("LLM reply in %.2fs chars=%s", dt, len(answer))
    return answer


def tts_elevenlabs(request_logger: logging.LoggerAdapter, text: str, voice_id: str | None = None) -> bytes:
    chosen_voice = voice_id or VOICE_ID
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{chosen_voice}"
    request_logger.info("Calling ElevenLabs voice=%s url=%s", chosen_voice, url)
    t0 = time.time()
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
    request_logger.info("TTS status=%s", response.status_code)
    try:
        response.raise_for_status()
    except requests.HTTPError as http_err:  # pragma: no cover - depends on remote API
        raise HTTPException(
            status_code=response.status_code, detail=f"ElevenLabs error: {http_err}"
        ) from http_err
    dt = time.time() - t0
    request_logger.info("TTS done in %.2fs bytes=%s", dt, len(response.content))
    return response.content  # MP3 bytes


def read_body_text(payload: dict | None, key: str) -> Optional[str]:
    if not isinstance(payload, dict):
        return None
    value = payload.get(key)
    if not isinstance(value, str):
        return None
    return value.strip() or None


# ------------------ Routes ------------------


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/talk")
async def talk(
    request: Request,
    audio: UploadFile | None = File(default=None),
    text: str | None = Body(default=None),
    language: str | None = Body(default=None),
    system_prompt: str | None = Body(default=None),
    voice: str | None = Body(default=None),
):
    """Handle either audio uploads or raw text prompts."""

    request_id = secrets.token_hex(4)
    log = get_request_logger(request_id)
    request.state.request_id = request_id
    t0_all = time.time()
    tmp_path = None

    content_type = request.headers.get("content-type", "").lower()
    log.info("/talk start content-type=%s", content_type)

    try:
        user_text: str | None = None
        chosen_voice: str | None = (voice or "").strip() or None
        form_language: str | None = None
        form_system_prompt: str | None = None

        if "application/json" in content_type:
            try:
                payload = await request.json()
            except Exception:
                payload = None
            user_text = read_body_text(payload, "text") or read_body_text(payload, "prompt")
            chosen_voice = chosen_voice or read_body_text(payload, "voice")
            form_language = read_body_text(payload, "language")
            form_system_prompt = read_body_text(payload, "system_prompt")
        elif "multipart/form-data" in content_type:
            form = await request.form()
            user_text = (form.get("text") or form.get("prompt") or "").strip() or None
            chosen_voice = chosen_voice or (form.get("voice") or "").strip() or None
            form_language = (form.get("language") or "").strip() or None
            form_system_prompt = (form.get("system_prompt") or "").strip() or None
        elif content_type:
            log.warning("Unsupported content-type=%s", content_type)
            raise HTTPException(status_code=415, detail="Unsupported content type")

        if form_language:
            log.info("language field provided=%s", form_language)
        if form_system_prompt:
            log.info("system_prompt provided (len=%s)", len(form_system_prompt))

        if audio is not None:
            suffix = os.path.splitext(audio.filename or "")[1] or ".m4a"
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                raw = await audio.read()
                tmp.write(raw)
                tmp_path = tmp.name

            log.info("Received audio file=%s size=%sB", tmp_path, len(raw))

            if not user_text:
                user_text = stt_local(log, tmp_path)
        elif user_text is None:
            user_text = (text or "").strip() or None

        if not user_text:
            log.warning("No prompt text supplied or detected from audio")
            raise HTTPException(status_code=400, detail="Provide either text or audio input")

        # 2) LLM reply
        reply_text = ask_llm(log, user_text)

        # 3) TTS
        mp3_bytes = tts_elevenlabs(log, reply_text, voice_id=chosen_voice)

        total_time = time.time() - t0_all
        audio_b64 = base64.b64encode(mp3_bytes).decode("ascii")
        log.info(
            "/talk success total=%.2fs reply_chars=%s audio_bytes=%s",
            total_time,
            len(reply_text),
            len(mp3_bytes),
        )

        return JSONResponse(
            status_code=200,
            content={
                "request_id": request_id,
                "text": reply_text,
                "audio_base64": audio_b64,
                "audio_format": "mp3",
            },
        )

    except HTTPException as http_exc:
        log.warning("/talk handled error status=%s detail=%s", http_exc.status_code, http_exc.detail)
        raise http_exc
    except requests.HTTPError as http_err:
        status = getattr(http_err.response, "status_code", 502) or 502
        log.exception("Upstream HTTP error")
        raise HTTPException(status_code=status, detail=str(http_err)) from http_err
    except Exception as exc:  # pragma: no cover - catch-all for runtime issues
        log.exception("Unexpected error in /talk")
        raise HTTPException(status_code=500, detail="Internal server error") from exc
    finally:
        if tmp_path:
            try:
                os.remove(tmp_path)
            except OSError:
                log.warning("Failed to delete temp file %s", tmp_path)


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):  # pragma: no cover - routing glue
    request_id = getattr(request.state, "request_id", secrets.token_hex(4))
    log = get_request_logger(request_id)
    log.warning(
        "Returning HTTPException status=%s detail=%s path=%s",
        exc.status_code,
        exc.detail,
        request.url.path,
    )
    content = {"error": exc.detail, "request_id": request_id}
    return JSONResponse(status_code=exc.status_code, content=content)


if __name__ == "__main__":  # pragma: no cover - manual debugging
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
