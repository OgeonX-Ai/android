import logging
import os
import tempfile
import time
from typing import Optional

import requests
import whisper
from dotenv import load_dotenv
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse, Response
from huggingface_hub import InferenceClient

# ------------------ Init ------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)

load_dotenv()

HF_TOKEN: Optional[str] = os.getenv("HF_API_TOKEN")
ELEVEN_KEY: Optional[str] = os.getenv("ELEVENLABS_API_KEY")
VOICE_ID: str = os.getenv("VOICE_ID", "EXAVITQu4vr4xnSDxMaL")

if not HF_TOKEN:
    logger.warning("HF_API_TOKEN missing in .env")
if not ELEVEN_KEY:
    logger.warning("ELEVENLABS_API_KEY missing in .env")

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


def tts_elevenlabs(text: str) -> bytes:
    """Generate MP3 with ElevenLabs for given text."""
    logger.info("Generating TTS with ElevenLabs...")
    t0 = time.time()
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{VOICE_ID}"
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
async def talk(audio: UploadFile = File(...)):
    """
    Android sends microphone recording here as form-data:
    field name 'audio', file type audio/mp4 (m4a).
    We return MP3 bytes with AI reply.
    """
    t0_all = time.time()
    tmp_path = None

    try:
        suffix = os.path.splitext(audio.filename or "")[1] or ".m4a"
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            raw = await audio.read()
            tmp.write(raw)
            tmp_path = tmp.name

        logger.info("Received file: %s, size=%s bytes", tmp_path, len(raw))

        # 1) STT
        user_text = stt_local(tmp_path)
        if not user_text:
            logger.warning("No speech detected from %s", tmp_path)
            return JSONResponse(status_code=400, content={"error": "No speech detected"})

        # 2) LLM reply
        reply_text = ask_llm(user_text)

        # 3) TTS
        mp3_bytes = tts_elevenlabs(reply_text)

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
