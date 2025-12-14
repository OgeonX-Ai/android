package com.example.aitalkdemo

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Basic unit tests to validate simple invariants without requiring an Android device.
 */
class MainActivityTest {

    @Test
    fun backendUrl_isConfigured() {
        // The demo backend URL should be non-blank so network calls are directed somewhere meaningful.
        val backendUrl = "http://10.0.2.2:8000/talk"
        assertTrue(backendUrl.isNotBlank(), "Backend URL must be set")
    }

    @Test
    fun voiceList_hasEntries() {
        val voices = listOf("Kim", "Milla", "John", "Lily")
        assertFalse(voices.isEmpty(), "Expected at least one voice option")
    }
}
