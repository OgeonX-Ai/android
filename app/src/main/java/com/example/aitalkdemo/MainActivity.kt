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

/**
 * The entry activity for the AI talk demo mobile app.
 *
 * This activity wraps the Compose UI defined in [HomeScreen] and manages audio
 * recording as well as communication with the backend server for both audio and
 * text-to-speech requests. It reuses much of the logic from the initial
 * implementation but exposes state via Compose to keep the UI in sync.
 */
class MainActivity : ComponentActivity() {

    // MediaRecorder used for microphone capture
    private var recorder: MediaRecorder? = null
    // Compose state for whether we are currently recording
    private var isRecording by mutableStateOf(false)
    // Compose state for whether a backend call is in progress
    private var isProcessing by mutableStateOf(false)
    // Temporary file for the recorded audio
    private lateinit var audioFile: File

    // OkHttp client with generous timeouts for Whisper + LLM + TTS pipeline
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
        // Request mic permission up front
        requestMicPermission()
        // Compose UI
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

    /**
     * Starts recording audio to a temporary file. When finished, call
     * [stopRecordingAndSend] to stop and send the file to the backend.
     */
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
            Log.d(TAG, "Recording started: ${audioFile.absolutePath}")
        } catch (e: IOException) {
            handleRecordingStartFailure("Failed to start recording", e)
        } catch (e: Exception) {
            handleRecordingStartFailure("Unexpected error starting recording", e)
        }
    }

    /**
     * Stops the current recording (if any) and dispatches the recorded file to the backend.
     */
    private fun stopRecordingAndSend() {
        if (!isRecording) return
        isRecording = false
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            // Handle case where stop is called prematurely
            Log.e(TAG, "Error stopping recorder", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error stopping recorder", e)
        } finally {
            recorder?.release()
            recorder = null
        }
        if (audioFile.exists()) {
            // Send the recorded audio asynchronously
            sendToBackend(audioFile)
        } else {
            Log.w(TAG, "Recording file missing, skipping upload")
        }
    }

    /**
     * Sends the recorded audio file to the backend as multipart/form-data. Upon success
     * the backend is expected to return an MP3 file which will be stored and
     * played back on the device.
     */
    private fun sendToBackend(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            setProcessing(true)
            try {
                if (!file.exists()) {
                    Log.e(TAG, "Audio file does not exist: ${file.absolutePath}")
                    return@launch
                }
                Log.d(TAG, "Sending file: ${file.absolutePath}, size=${file.length()} bytes")
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        name = "audio",
                        filename = file.name,
                        body = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                    )
                    .build()
                val request = Request.Builder()
                    .url(backendUrl)
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "Backend response code: ${response.code}")
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Backend error: ${response.code} ${response.message}")
                    } else {
                        val responseBytes = response.body?.bytes()
                        if (responseBytes != null && responseBytes.isNotEmpty()) {
                            val mp3File = File(filesDir, "reply_${System.currentTimeMillis()}.mp3")
                            mp3File.writeBytes(responseBytes)
                            withContext(Dispatchers.Main) {
                                playAudio(mp3File)
                            }
                        } else {
                            Log.w(TAG, "Empty response body from backend")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending audio to backend", e)
            } finally {
                setProcessing(false)
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
        lifecycleScope.launch(Dispatchers.IO) {
            setProcessing(true)
            try {
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
                    } else {
                        val responseBytes = response.body?.bytes()
                        if (responseBytes != null && responseBytes.isNotEmpty()) {
                            val mp3File = File(filesDir, "reply_${System.currentTimeMillis()}.mp3")
                            mp3File.writeBytes(responseBytes)
                            withContext(Dispatchers.Main) {
                                playAudio(mp3File)
                            }
                        } else {
                            Log.w(TAG, "Empty response body from backend")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending text to backend", e)
            } finally {
                setProcessing(false)
            }
        }
    }

    /** Plays an MP3 file using [MediaPlayer] and releases the player once finished. */
    private fun playAudio(file: File) {
        if (!file.exists()) {
            Log.w(TAG, "Cannot play missing audio file: ${file.absolutePath}")
            return
        }
        try {
            Log.d(TAG, "Preparing MediaPlayer for: ${file.absolutePath}")
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer prepared, duration=${mp.duration} ms")
                mp.start()
            }
            player.setOnCompletionListener { mp ->
                Log.d(TAG, "Playback complete, releasing player")
                mp.release()
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                mp.release()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    /** Release recorder resources when start fails. */
    private fun handleRecordingStartFailure(message: String, error: Exception) {
        Log.e(TAG, message, error)
        recorder?.release()
        recorder = null
        isRecording = false
    }

    /**
     * Update processing state on the main thread to avoid state mutations off the UI thread.
     */
    private suspend fun setProcessing(value: Boolean) {
        withContext(Dispatchers.Main) {
            isProcessing = value
        }
    }

    companion object {
        private const val TAG = "AiTalkDemo"
    }
}
