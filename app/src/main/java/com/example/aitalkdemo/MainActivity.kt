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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var audioFile: File

    // Longer timeouts because Whisper + LLM + TTS can be slow
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // 👉 Emulator → PC backend:
    private val backendUrl = "http://10.0.2.2:8000/talk"
    // 👉 Real phone on same Wi-Fi → use LAN IP instead:
    // private val backendUrl = "http://192.168.100.5:8000/talk"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.w("AiTalkDemo", "Microphone permission not granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestMicPermission()

        setContent {
            AiTalkDemoUI(
                startRecording = { startRecording() },
                stopRecording = { stopRecordingAndSend() }
            )
        }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // -----------  RECORD AUDIO -------------
    private fun startRecording() {
        if (isRecording) return

        audioFile = File(filesDir, "recording_${System.currentTimeMillis()}.m4a")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this)
        else
            MediaRecorder()

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
            Log.d("AiTalkDemo", "Recording started: ${audioFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AiTalkDemo", "Failed to start recording", e)
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    // ----------- STOP + SEND TO BACKEND ------------
    private fun stopRecordingAndSend() {
        if (!isRecording) return
        isRecording = false

        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e("AiTalkDemo", "Error stopping recorder", e)
        } finally {
            recorder?.release()
            recorder = null
        }

        sendToBackend(audioFile)
    }

    // ----------- SEND FILE TO FASTAPI -------------
    private fun sendToBackend(file: File) {
        CoroutineScope(Dispatchers.IO).launch {

            Log.d("AiTalkDemo", "Sending file: ${file.absolutePath}, size=${file.length()} bytes")

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",    // MUST match FastAPI: audio: UploadFile = File(...)
                    file.name,
                    file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(backendUrl)
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    Log.d("AiTalkDemo", "Backend response code: ${response.code}")

                    if (!response.isSuccessful) {
                        Log.e(
                            "AiTalkDemo",
                            "Backend error: ${response.code} ${response.message}"
                        )
                        return@use
                    }

                    val responseBodyBytes = response.body?.bytes()
                    if (responseBodyBytes == null || responseBodyBytes.isEmpty()) {
                        Log.e("AiTalkDemo", "Empty response body from backend")
                        return@use
                    }

                    val mp3File = File(filesDir, "reply_${System.currentTimeMillis()}.mp3")
                    mp3File.writeBytes(responseBodyBytes)
                    Log.d(
                        "AiTalkDemo",
                        "Saved reply to: ${mp3File.absolutePath} size=${mp3File.length()}"
                    )

                    withContext(Dispatchers.Main) {
                        playAudio(mp3File)
                    }
                }

            } catch (e: Exception) {
                Log.e("AiTalkDemo", "Error sending audio to backend", e)
            }
        }
    }

    // ----------- PLAY THE MP3 REPLY ---------------
    private fun playAudio(file: File) {
        try {
            Log.d("AiTalkDemo", "Preparing MediaPlayer for: ${file.absolutePath}")
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)

            player.setOnPreparedListener { mp ->
                Log.d("AiTalkDemo", "MediaPlayer prepared, duration=${mp.duration} ms")
                mp.start()
            }

            player.setOnCompletionListener { mp ->
                Log.d("AiTalkDemo", "Playback complete, releasing player")
                mp.release()
            }

            player.setOnErrorListener { mp, what, extra ->
                Log.e("AiTalkDemo", "MediaPlayer error what=$what extra=$extra")
                mp.release()
                true
            }

            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("AiTalkDemo", "Error playing audio", e)
        }
    }
}

@Composable
fun AiTalkDemoUI(
    startRecording: () -> Unit,
    stopRecording: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = if (isRecording) "🎙 Kuuntelen…" else "Paina puhuaksesi",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    if (isRecording) stopRecording() else startRecording()
                    isRecording = !isRecording
                },
                modifier = Modifier.size(160.dp)
            ) {
                Text(if (isRecording) "Stop" else "Talk")
            }
        }
    }
}
