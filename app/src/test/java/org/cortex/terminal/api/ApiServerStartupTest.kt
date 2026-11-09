package org.cortex.terminal.api

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.cortex.terminal.runtime.ShadowJavaFilesOs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowJavaFilesOs::class])
class ApiServerStartupTest {
    @Test
    fun applicationStartupCreatesConnectionFileAndStartsLocalServer() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val connection = ApiServer.connectionFile(application)
        try {
            assertTrue("command connection file was not created", connection.isFile)
            val fields = connection.readText().trim().split(Regex("\\s+"))
            assertEquals("connection file must contain a port and token", 2, fields.size)
            val port = fields[0].toInt()
            assertTrue("command server port is invalid", port in 1..65535)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 2_000)
            }
        } finally {
            ApiServer.stop(application)
        }
    }
}
