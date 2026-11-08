package org.cortex.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalEmulatorUtf8Test {

    @Test
    fun testMultiByteSplitAcrossPackets() {
        val emulator = TerminalEmulator(rows = 5, cols = 20)

        val utf8Bytes = "ö".toByteArray(Charsets.UTF_8)
        assertEquals(2, utf8Bytes.size)

        emulator.processInput(byteArrayOf(utf8Bytes[0]), 0, 1)

        emulator.processInput(byteArrayOf(utf8Bytes[1]), 0, 1)

        val firstChar = emulator.buffer.screen[0].chars[0]
        assertEquals('ö', firstChar)
    }

    @Test
    fun testAsciiStringProcessing() {
        val emulator = TerminalEmulator(rows = 5, cols = 20)
        val text = "Cortex".toByteArray(Charsets.UTF_8)
        emulator.processInput(text, 0, text.size)
        assertEquals("Cortex", emulator.buffer.screen[0].getText())
    }
}
