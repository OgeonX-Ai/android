package com.example.aitalkdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    voices: List<String>,
    onSendText: (String, String) -> Unit,
    onRecordVoice: (Boolean) -> Unit,
    isRecording: Boolean,
    isProcessing: Boolean
) {
    var selectedVoice by remember { mutableStateOf(voices.firstOrNull() ?: "") }
    var message by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF4e54c8), Color(0xFF8f94fb))
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI Talk Demo",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Type your message", color = Color.White) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(color = Color.White),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White,
                    cursorColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box {
                Button(onClick = { dropdownExpanded = true }) {
                    Text(if (selectedVoice.isBlank()) "Select voice" else selectedVoice)
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    voices.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                selectedVoice = it
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSendText(message, selectedVoice) },
                    enabled = message.isNotBlank() && !isProcessing
                ) {
                    Text(if (isProcessing) "Processing…" else "Speak")
                }

                Button(
                    onClick = { onRecordVoice(!isRecording) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isRecording) "Stop" else "Record")
                }
            }
        }
    }
}
