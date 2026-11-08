package org.cortex.terminal.view

object Sextants {

    const val FIRST = 0x1FB00
    const val LAST = 0x1FB3B

    fun isSextant(cp: Int): Boolean = cp in FIRST..LAST

    fun mask(cp: Int): Int {
        var m = cp - FIRST + 1
        if (m >= 21) m++
        if (m >= 42) m++
        return m
    }
}
