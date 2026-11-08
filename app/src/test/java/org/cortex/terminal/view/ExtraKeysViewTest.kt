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
import org.robolectric.shadows.ShadowLooper
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

    private fun shownColor(button: Button): Int? {
        val holder = button.background as? android.graphics.drawable.StateListDrawable ?: return null
        return (holder.current as? android.graphics.drawable.GradientDrawable)?.color?.defaultColor
    }

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

    private fun holdButton(button: Button, held: (Button) -> Unit) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 1f, 1f, 0)
        try {
            button.dispatchTouchEvent(down)
            held(button)
            button.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun fireHolds() {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun anFTapSendsItsSequenceOnceAndDoesNotLatch() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val sent = mutableListOf<Int>()
        view.keySequenceSink = { sent.add(it) }
        view.setKeys(listOf(listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.F2))))
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 50)
        val button = ((view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout).getChildAt(0) as Button
        tapButton(button) {}
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_F2), sent)
        assertEquals(Chrome.palette.raised, shownColor(button))
    }

    @Test
    fun holdingAnFLatchesItBlueUntilTheSameKeyIsTappedOrTheNextKeyAppliesIt() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val sent = mutableListOf<Int>()
        view.keySequenceSink = { sent.add(it) }
        view.setKeys(listOf(listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.F2))))
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 50)
        val button = ((view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout).getChildAt(0) as Button

        holdButton(button) {
            fireHolds()
            assertTrue(sent.isEmpty())
            assertEquals(Chrome.palette.onAccent, button.currentTextColor)
        }
        assertEquals(Chrome.palette.accent, shownColor(button))
        assertTrue(sent.isEmpty())

        tapButton(button) {}
        assertEquals(Chrome.palette.raised, shownColor(button))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aLatchedKeyAppliesItsSequenceWithTheNextKeyThenStopsHolding() {
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

        holdButton(f2) { fireHolds() }
        assertEquals(Chrome.palette.accent, shownColor(f2))

        tapButton(otherKey) {}
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_F2), sent)
        assertEquals(Chrome.palette.raised, shownColor(f2))
    }

    @Test
    fun holdingAnotherLatchableKeySwapsTheLatchWithoutSendingTheLastOne() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ExtraKeysView(context)
        val sent = mutableListOf<Int>()
        view.keySequenceSink = { sent.add(it) }
        view.setKeys(
            listOf(listOf(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.F2), ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.F5)))
        )
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(50, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 50)
        val keys = (view.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout
        val f2 = keys.getChildAt(0) as Button
        val f5 = keys.getChildAt(1) as Button

        holdButton(f2) { fireHolds() }
        assertEquals(Chrome.palette.accent, shownColor(f2))

        tapButton(f5) {}
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_F5), sent)
        assertEquals(Chrome.palette.raised, shownColor(f5))
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
