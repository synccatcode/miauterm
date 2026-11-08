package org.cortex.terminal.view

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ActionPopupPositionTest {

    private fun place(left: Int, top: Int, bottom: Int, popupWidth: Int = 600, popupHeight: Int = 120) =
        actionPopupPosition(
            viewLeft = 0, viewTop = 150, viewWidth = 1000, viewHeight = 1800,
            popupWidth = popupWidth, popupHeight = popupHeight,
            selectionLeft = left, selectionTop = top, selectionBottom = bottom,
            gap = 20, handles = 90
        )

    @Test
    fun itGoesAboveTheSelectionInTheWindowsCoordinates() {

        assertArrayEquals(intArrayOf(100, 910), place(left = 100, top = 900, bottom = 950))
    }

    @Test
    fun onTheFirstRowsItGoesUnderTheHandlesNotUnderTheTabs() {

        assertArrayEquals(intArrayOf(0, 150 + 50 + 90), place(left = 0, top = 0, bottom = 50))
    }

    @Test
    fun aSelectionOfTheWholeScreenKeepsItInsideTheTerminal() {
        assertArrayEquals(intArrayOf(0, 150 + 1800 - 120), place(left = 0, top = 0, bottom = 1800))
    }

    @Test
    fun itStaysInsideTheTerminalSideways() {
        assertArrayEquals("at the right edge", intArrayOf(400, 910), place(left = 900, top = 900, bottom = 950))
        assertArrayEquals("wider than the terminal", intArrayOf(0, 910), place(left = 300, top = 900, bottom = 950, popupWidth = 1200))
    }
}
