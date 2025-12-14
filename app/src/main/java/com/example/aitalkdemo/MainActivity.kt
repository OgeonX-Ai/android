package com.example.aitalkdemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aitalkdemo.ui.HomeScreen
import com.example.aitalkdemo.ui.theme.AiTalkDemoTheme
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private var recorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)
    private lateinit var audioFile: File

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Base URL of the FastAPI backend. You should point this to your own server.
     * For Android emulators, 10.0.2.2 maps to the host machine's localhost.
     */
    // Merge hint: prefer this single backend URL; do not reintroduce alternate or duplicate declarations.
    private val backendUrl = "http://10.0.2.2:8000/talk"

    /** List of example voices/personas. Replace with values supported by your API. */
    private val voices = listOf("Kim", "Milla", "John", "Lily")

    // Permission launcher for microphone access
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.w(TAG, "Microphone permission not granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMicPermission()
        setContent {
            AiTalkDemoTheme {
                HomeScreen(
                    voices = voices,
                    onSendText = { text, voice -> sendTextToBackend(text, voice) },
                    onRecordVoice = { start ->
                        if (start) startRecording() else stopRecordingAndSend()
                    },
                    isRecording = isRecording,
                    isProcessing = isProcessing
                )
            }
        }
    }

    /** Request microphone permission if not already granted. */
    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (isRecording) return
        // Create a temp file in the app's internal files directory
        audioFile = File(filesDir, "recording_${System.currentTimeMillis()}.m4a")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        try {
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(96_000)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            Log.i(TAG, "Recording started -> ${audioFile.absolutePath}")
            isRecording = true
        } catch (e: IOException) {
            handleRecordingStartFailure("Failed to start recording", e)
        } catch (e: Exception) {
            handleRecordingStartFailure("Unexpected error starting recording", e)
        }
    }

    private fun stopRecordingAndSend() {
        if (!isRecording) return
        isRecording = false
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            // Handle case where stop is called prematurely
            Log.e(TAG, "Error stopping recorder", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        } finally {
            recorder?.release()
            recorder = null
        }

        if (audioFile.exists()) {
            Log.i(TAG, "Recording stopped -> ${audioFile.absolutePath} size=${audioFile.length()}B")
            sendAudioToBackend(audioFile)
        } else {
            Log.w(TAG, "Recording file missing, skipping upload")
        }
    }

    private fun sendAudioToBackend(file: File) {
        withProcessingIo {
            Log.i(TAG, "Uploading audio -> ${file.absolutePath} (${file.length()}B)")
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    file.name,
                    file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()
            val request = Request.Builder()
                .url(backendUrl)
                .post(body)
                .build()

            val response = executeWithRetry(request)
            processTalkResponse(response, source = "audio-upload")
        }
    }

    /**
     * Sends a text prompt and selected voice to the backend. The API is expected to
     * return an MP3 file containing synthesized speech. Adjust the JSON payload
     * according to your API’s contract.
     *
     * @param prompt the text that should be spoken
     * @param voice the voice or persona name to use
     */
    private fun sendTextToBackend(prompt: String, voice: String) {
        if (prompt.isBlank() || voice.isBlank()) {
            Log.w(TAG, "Skipping TTS request because prompt or voice is blank")
            return
        }
        withProcessingIo {
            val json = JSONObject().apply {
                put("text", prompt)
                put("voice", voice)
            }
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(backendUrl)
                .post(body)
                .build()
            val response = executeWithRetry(request)
            processTalkResponse(response, source = "text")
        }
    }

    private suspend fun processTalkResponse(response: Response, source: String) {
        response.use { resp ->
            val bodyText = resp.body?.string()
            val requestId = kotlin.runCatching {
                if (bodyText.isNullOrBlank()) null else JSONObject(bodyText).optString("request_id")
            }.getOrNull()

            Log.i(
                TAG,
                "Backend response source=$source code=${resp.code} requestId=${requestId ?: "n/a"}"
            )

            if (!resp.isSuccessful) {
                Log.e(TAG, "Backend error code=${resp.code} body=$bodyText")
                showToast("Backend error (${resp.code}). Please try again.")
                return
            }

            if (bodyText.isNullOrBlank()) {
                Log.w(TAG, "Empty response body from backend")
                showToast("Empty response from server")
                return
            }

            try {
                val json = JSONObject(bodyText)
                val backendRequestId = json.optString("request_id", "unknown")
                val replyText = json.optString("text", "")
                val audioB64 = json.optString("audio_base64", "")
                val audioFormat = json.optString("audio_format", "mp3")

                val audioBytes = Base64.decode(audioB64, Base64.DEFAULT)
                val outFile = File(
                    cacheDir,
                    "reply_${backendRequestId}_${System.currentTimeMillis()}.${audioFormat}"
                )
                outFile.writeBytes(audioBytes)

                Log.i(
                    TAG,
                    "Decoded audio requestId=$backendRequestId bytes=${audioBytes.size} file=${outFile.absolutePath}"
                )
                if (replyText.isNotBlank()) {
                    Log.i(TAG, "Assistant reply (len=${replyText.length}): $replyText")
                }

                withContext(Dispatchers.Main) {
                    playAudio(outFile, backendRequestId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse backend response", e)
                showToast("Could not read server response")
            }
        }
    }

    private fun playAudio(file: File, requestId: String) {
        if (!file.exists()) {
            Log.w(TAG, "Audio file missing for playback requestId=$requestId")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        Log.i(TAG, "Playback complete requestId=$requestId")
                        it.release()
                    }
                    prepare()
                }
                Log.i(TAG, "Playback starting requestId=$requestId path=${file.absolutePath}")
                withContext(Dispatchers.Main) {
                    mediaPlayer?.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play audio requestId=$requestId", e)
                showToast("Playback failed")
            }
        }
    }

    private fun handleRecordingStartFailure(msg: String, e: Exception) {
        Log.e(TAG, msg, e)
        recorder?.release()
        recorder = null
        isRecording = false
        showToast(msg)
    }

    private suspend fun setProcessing(value: Boolean) {
        withContext(Dispatchers.Main) {
            isProcessing = value
        }
    }

    private fun withProcessingIo(block: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            setProcessing(true)
            try {
                block()
            } finally {
                setProcessing(false)
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeWithRetry(request: Request): Response {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < 2) {
            try {
                if (attempt > 0) {
                    Log.w(TAG, "Retrying request attempt=${attempt + 1}")
                }
                return client.newCall(request).execute()
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "Request attempt ${attempt + 1} failed", e)
                attempt++
                if (attempt >= 2) break
                Thread.sleep(300)
            }
        }
        throw lastError ?: IllegalStateException("Unknown network error")
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.release()
        recorder = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        // Merge hint: keep this TAG definition; remove any stray TAG declarations from other branches.
        private const val TAG = "AiTalkDemo"
    }
}
