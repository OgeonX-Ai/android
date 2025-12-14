package com.example.aitalkdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A modern home screen for the AI Talk demo.
 *
 * This composable presents a radial gradient background, an input box for the user to
 * type a message, a voice picker and buttons to trigger text-to-speech or start
 * recording from the microphone. The styling draws inspiration from the GitHub Pages
 * demo of Kim’s AI voice project by using purple and blue gradients with white
 * typography.
 *
 * @param voices list of available voice/persona names to choose from
 * @param onSendText callback invoked when the user presses the Speak button
 * @param onRecordVoice callback invoked when the user toggles recording
 * @param isRecording current recording state
 * @param isProcessing whether the backend is processing a request
 */
@Composable
fun HomeScreen(
    voices: List<String>,
    onSendText: (String, String) -> Unit,
    onRecordVoice: (Boolean) -> Unit,
    isRecording: Boolean,
    isProcessing: Boolean
) {
    // Selected voice/persona state
    var selectedVoice by remember { mutableStateOf(voices.firstOrNull() ?: "") }
    // User's text input
    var message by remember { mutableStateOf("") }
    // Dropdown visibility state
    var dropdownExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Vertical gradient reminiscent of the web demo
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4e54c8), // deep purple
                        Color(0xFF8f94fb)  // light indigo
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "AI Talk Demo",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Message text field
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(text = "Type your message", color = Color.White) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                singleLine = false,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White,
                    cursorColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Voice picker
            Box {
                Button(
                    onClick = { dropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x665050B5))
                ) {
                    Text(
                        text = if (selectedVoice.isBlank()) "Select voice" else selectedVoice,
                        color = Color.White
                    )
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice) },
                            onClick = {
                                selectedVoice = voice
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Speak button triggers TTS on the backend
                Button(
                    onClick = {
                        if (message.isNotBlank() && selectedVoice.isNotBlank()) {
                            onSendText(message, selectedVoice)
                        }
                    },
                    enabled = message.isNotBlank() && !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF715AFF))
                ) {
                    Text(
                        text = if (isProcessing) "Processing…" else "Speak",
                        color = Color.White
                    )
                }
                // Record button toggles microphone recording
                Button(
                    onClick = { onRecordVoice(!isRecording) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFD32F2F) else Color(0xFF715AFF)
                    )
                ) {
                    Text(
                        text = if (isRecording) "Stop" else "Record",
                        color = Color.White
                    )
                }
            }
        }
    }
}
