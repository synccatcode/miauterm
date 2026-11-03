package org.cortex.terminal.view

import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtraKeysViewTest {

    @Test
    fun testExtraKeysButtonsWireClickListenerWithTouchDeduplication() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        var clicks = 0
        view.onMenuClick = { clicks++ }
        view.setKeys(
            listOf(
                listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.MENU))
            )
        )
        val row = (view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout
        val button = row.getChildAt(0) as Button

        assertTrue(button.performClick())
        assertEquals(1, clicks)

        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 1f, 1f, 0)
        try {
            button.dispatchTouchEvent(down)
            button.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }

        assertEquals(2, clicks)
    }

    @Test
    fun setKeysRendersNewKeysFromTheUpdatedLayout() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val layout = ExtraKeysLayout.parse(
            """
                [
                  ["ESC", "/"],
                  ["TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN", "NEW"]
                ]
            """.trimIndent()
        )

        view.setKeys(layout.rows)

        assertEquals(2, view.rowCount)
        val lastRow = view.getChildAt(1) as HorizontalScrollView
        val row = lastRow.getChildAt(0) as LinearLayout
        assertEquals(8, row.childCount)
        assertEquals("NEW", (row.getChildAt(7) as Button).text.toString())
    }

    @Test
    fun overflowingExtraKeysRowCanBeSwipedHorizontally() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val keys = (1..16).map { ExtraKeysLayout.TextKey("key$it", "K$it") }
        view.setKeys(listOf(keys))

        val density = context.resources.displayMetrics.density
        val width = (280 * density).toInt()
        val height = (42 * density).toInt()
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val row = view.getChildAt(0) as HorizontalScrollView
        assertTrue(row.getChildAt(0).width > row.width)
        assertTrue(row.canScrollHorizontally(1))

        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, width - 8f, height / 2f, 0)
        val move = MotionEvent.obtain(0, 40, MotionEvent.ACTION_MOVE, 8f, height / 2f, 0)
        val up = MotionEvent.obtain(0, 60, MotionEvent.ACTION_UP, 8f, height / 2f, 0)
        try {
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(move)
            view.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        assertTrue(row.scrollX > 0)
    }

    /** Extracting the color of the state the background currently shows. */
    private fun shownColor(button: Button): Int? {
        val holder = button.background as? android.graphics.drawable.StateListDrawable ?: return null
        return (holder.current as? android.graphics.drawable.GradientDrawable)?.color?.defaultColor
    }

    /** A touch sequence over a key: down, up, and the events recycled in one place. */
    private fun tapButton(button: Button, action: (Button) -> Unit) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 1f, 1f, 0)
        try {
            button.dispatchTouchEvent(down)
            action(button)
            button.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    /** Latches a function key with two taps; nothing is sent by the F key itself. */
    private fun latchOneAndOff(view: ExtraKeysView) {
        val row = (view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout
        val button = row.getChildAt(0) as Button
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 50)
        tapButton(button) { pressed ->
            assertTrue(pressed.isPressed)
            assertEquals(Chrome.mix(Chrome.palette.accent, Chrome.palette.fg, 0.25f), shownColor(pressed))
        }
        assertEquals(Chrome.palette.accent, shownColor(button))
        tapButton(button) {}
        assertEquals(Chrome.palette.raised, shownColor(button))
    }

    @Test
    fun anFTapHoldsTheAccentUntilItIsTappedAgainAndSendsNothing() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val sent = mutableListOf<Int>()
        view.keySequenceSink = { sent.add(it) }
        view.setKeys(listOf(listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.F2))))
        latchOneAndOff(view)
        assertTrue("an F key tap only latches, it never sends on its own", sent.isEmpty())
    }

    @Test
    fun aLatchedFunctionKeySendsItsSequenceWithTheNextKeyThenStopsHolding() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val sent = mutableListOf<Int>()
        view.keySequenceSink = { sent.add(it) }
        view.setKeys(
            listOf(listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.F2), ExtraKeysLayout.TextKey("x")))
        )
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 50)
        val keys = (view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout
        val f2 = keys.getChildAt(0) as Button
        val otherKey = keys.getChildAt(1) as Button

        tapButton(f2) {}
        assertEquals(Chrome.palette.accent, shownColor(f2))

        tapButton(otherKey) {}
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_F2), sent)
        assertEquals(Chrome.palette.raised, shownColor(f2))
    }

    @Test
    fun repeatedNavigationKeysKeepTheirRestingColorWhileHeld() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        view.setKeys(listOf(listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.UP))))
        val row = (view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout
        val button = row.getChildAt(0) as Button
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 50)

        tapButton(button) { pressed ->
            assertTrue(pressed.isPressed)
            // The pressed tone (raisedHigh) shows while held; the accent never comes on
            assertEquals(Chrome.palette.raisedHigh, shownColor(pressed))
        }
        assertEquals(Chrome.palette.raised, shownColor(button))
    }

    @Test
    fun ctrlStaysPaintedWithTheAccentAfterItIsToggledOn() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val terminalView = TerminalView(context)
        view.terminalView = terminalView
        view.setKeys(listOf(listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.CTRL))))
        val row = (view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout
        val button = row.getChildAt(0) as Button
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 50)

        tapButton(button) {}
        assertTrue(terminalView.isCtrlPressed)
        assertEquals(Chrome.palette.accent, shownColor(button))
    }
}
