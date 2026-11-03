package org.cortex.terminal.api

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiCommandsTest {
    @Test
    fun keysreloadFailsClearlyWhenTheTerminalScreenIsUnavailable() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        val result = ApiCommands.run(application, ApiRequest("token", "keysreload", emptyList(), null))

        assertEquals(1, result.status)
        assertTrue(result.output.contains("terminal screen is not available"))
    }
}
