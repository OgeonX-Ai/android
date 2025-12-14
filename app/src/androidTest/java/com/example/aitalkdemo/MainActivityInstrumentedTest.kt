package com.example.aitalkdemo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Simple instrumented test to ensure the app context loads with the expected package name.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @Test
    fun appContext_hasCorrectPackageName() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.example.aitalkdemo", context.packageName)
    }
}
