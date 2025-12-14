# Deploying the AI Talk backend to Azure

This guide walks through deploying the FastAPI backend to Azure so the Android app can reach it from anywhere. It assumes you already have an ElevenLabs account (sign up via https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs), a Hugging Face token, and an Azure subscription.

## Overview

- **Runtime**: FastAPI + Uvicorn
- **Recommended SKU**: Azure Container Apps or App Service (Linux) with at least 1 vCPU/1–2 GB RAM for the Whisper `tiny` model.
- **Artifacts**: Docker image or source-based deploy
- **Environment**: `.env` variables for `HF_API_TOKEN`, `ELEVENLABS_API_KEY`, and optional `VOICE_ID`

## 1) Prepare a production image

If you want a containerized deployment, build an image with Gunicorn + Uvicorn:

```Dockerfile
# backend/Dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir --upgrade pip setuptools==68.2.2 wheel==0.41.3 \
    && pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["gunicorn", "main:app", "-k", "uvicorn.workers.UvicornWorker", "--bind", "0.0.0.0:8000", "--timeout", "120"]
```

Build and push the image (replace registry/name with your own):

```bash
cd backend
az acr login --name <your-acr-name>
docker build -t <your-acr-name>.azurecr.io/ai-talk-backend:latest .
docker push <your-acr-name>.azurecr.io/ai-talk-backend:latest
```

## 2) Provision Azure resources

### Option A: Azure Container Apps (recommended)

```bash
RESOURCE_GROUP=ai-talk-demo
LOCATION=westeurope
IMAGE=<your-acr-name>.azurecr.io/ai-talk-backend:latest
ACA_ENV=ai-talk-env
APP_NAME=ai-talk-api

az group create -n $RESOURCE_GROUP -l $LOCATION
az containerapp env create -g $RESOURCE_GROUP -n $ACA_ENV -l $LOCATION
az containerapp create \
  -g $RESOURCE_GROUP \
  -n $APP_NAME \
  --environment $ACA_ENV \
  --image $IMAGE \
  --target-port 8000 \
  --ingress external \
  --min-replicas 1 \
  --max-replicas 2 \
  --cpu 1 --memory 2Gi \
  --env-vars HF_API_TOKEN=secretref:HF_API_TOKEN ELEVENLABS_API_KEY=secretref:ELEVENLABS_API_KEY VOICE_ID=EXAVITQu4vr4xnSDxMaL
```
Add the secrets (one-time):

```bash
az containerapp secret set -g $RESOURCE_GROUP -n $APP_NAME \
  --secrets HF_API_TOKEN=<huggingface_token> ELEVENLABS_API_KEY=<elevenlabs_key>
```

### Option B: App Service (Linux, source deploy)

If you prefer not to use containers, deploy the source directory and let App Service run Uvicorn directly:

```bash
RESOURCE_GROUP=ai-talk-demo
APP_PLAN=ai-talk-plan
APP_NAME=ai-talk-api

az group create -n $RESOURCE_GROUP -l westeurope
az appservice plan create -g $RESOURCE_GROUP -n $APP_PLAN --sku B1 --is-linux
az webapp create -g $RESOURCE_GROUP -p $APP_PLAN -n $APP_NAME --runtime "PYTHON|3.11"
az webapp config appsettings set -g $RESOURCE_GROUP -n $APP_NAME --settings \
  HF_API_TOKEN=<huggingface_token> \
  ELEVENLABS_API_KEY=<elevenlabs_key> \
  VOICE_ID=EXAVITQu4vr4xnSDxMaL \
  SCM_DO_BUILD_DURING_DEPLOYMENT=true
az webapp up -g $RESOURCE_GROUP -n $APP_NAME -l westeurope --src-path backend --runtime "PYTHON|3.11"
```

App Service will expose an HTTPS endpoint; set `backendUrl` in `MainActivity.kt` to `https://<APP_NAME>.azurewebsites.net/talk`.

## 3) Configure the Android app

After deployment, update the Android client:

- Set `backendUrl` in `app/src/main/java/com/example/aitalkdemo/MainActivity.kt` to your Azure hostname.
- Keep generous OkHttp timeouts (already configured) for first-call cold starts.
- Verify the backend with the included health check: `curl https://<APP_HOST>/health` should return `{ "status": "ok" }`.

## 4) Observability and scaling

- Enable Container Apps log streaming: `az containerapp logs show -g $RESOURCE_GROUP -n $APP_NAME --follow`.
- For App Service, enable Application Insights for basic request metrics.
- Start with 1–2 replicas; scale up if Whisper latency grows. The `tiny` model runs on CPU; for larger models consider GPU SKUs.

## 5) Cost and key management

- Store secrets with Azure Key Vault and reference them from Container Apps secrets where possible.
- Rotate ElevenLabs keys regularly. You can generate new keys from your dashboard: https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs (redirects to the official ElevenLabs signup portal).
- Restrict outbound internet if your organization requires it; the backend calls Hugging Face, ElevenLabs, and (optionally) PyTorch mirrors during install.

## 6) Post-deploy validation

1. Hit `/health` to confirm the container is reachable.
2. Run `python -m compileall backend` locally before pushing to avoid syntax surprises in CI/CD.
3. From the Android emulator/device, trigger **Speak** and **Record**; confirm audio responses play back.
4. Monitor logs for `Backend response code: 200` and `MP3 bytes=...` to confirm the full STT → LLM → TTS path.

## 7) Links and references

- ElevenLabs signup/referral: https://ogeonx-ai.github.io/kim-ai-voice-demo/elevenlabs
- Local setup guide: [docs/LOCAL_SETUP.md](LOCAL_SETUP.md)
- CI pipeline: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- Android configuration (backend URL/voices): `app/src/main/java/com/example/aitalkdemo/MainActivity.kt`
