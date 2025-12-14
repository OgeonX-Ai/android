package com.example.aitalkdemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
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

class MainActivity : ComponentActivity() {

    // MediaRecorder used for microphone capture
    private var recorder: MediaRecorder? = null
    // Compose state for whether we are currently recording
    private var isProcessing by mutableStateOf(false)
    // Temporary file for the recorded audio
    private lateinit var audioFile: File

    // OkHttp client with generous timeouts for Whisper + LLM + TTS pipeline
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
    private val backendUrl = "http://10.0.2.2:8000/talk"

    /** List of example voices/personas. Replace with values supported by your API. */
    private val availableVoices = listOf("Kim", "Milla", "John", "Lily")
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
            sendAudioToBackend(audioFile)
        }
    }

    private fun sendAudioToBackend(file: File) {
        withProcessingIo {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    file.name,
                    file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()
            val request = Request.Builder()
                .url(talkEndpointUrl)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Backend response code: ${response.code}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Backend error: ${response.code} ${response.message}")
                    return@use
                }

                val responseBytes = response.body?.bytes()
                if (responseBytes == null || responseBytes.isEmpty()) {
                    Log.w(TAG, "Empty response body from backend")
                    return@use
                }

                val mp3File = File(filesDir, "reply_${System.currentTimeMillis()}.mp3")
                mp3File.writeBytes(responseBytes)
                withContext(Dispatchers.Main) {
                    playAudio(mp3File)
                }
            }
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
            val json = """{\"prompt\":\"$prompt\",\"voice\":\"$voice\"}"""
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(backendUrl)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Backend response code: ${response.code}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Backend error: ${response.code} ${response.message}")
                    return@use
                }

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use

                val responseBytes = response.body?.bytes().orEmpty()
                if (responseBytes.isEmpty()) return@use

                val mp3File = File(filesDir, "reply_${System.currentTimeMillis()}.mp3")
                mp3File.writeBytes(responseBytes)

                withContext(Dispatchers.Main) {
                    playAudio(mp3File)
                }
            }
        }
    }

    private fun sendTextToBackend(prompt: String, voice: String) {
        if (prompt.isBlank() || voice.isBlank()) return

        withProcessingIo {
            val json = """{"prompt":"$prompt","voice":"$voice"}"""
            val body = json.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(backendUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use

                val responseBytes = response.body?.bytes().orEmpty()
                if (responseBytes.isEmpty()) return@use

                val mp3File = File(filesDir, "reply_${System.currentTimeMillis()}.mp3")
                mp3File.writeBytes(responseBytes)

                withContext(Dispatchers.Main) {
                    playAudio(mp3File)
                }
            }
        }
    }

    private fun playAudio(file: File) {
        if (!file.exists()) return

        val player = MediaPlayer()
        player.setDataSource(file.absolutePath)
        player.setOnCompletionListener { it.release() }
        player.prepare()
        player.start()
    }

    private fun handleRecordingStartFailure(msg: String, e: Exception) {
        Log.e(TAG, msg, e)
        recorder?.release()
        recorder = null
        isRecording = false
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

    companion object {
        private const val TAG = "AiTalkDemo"
    }
}
