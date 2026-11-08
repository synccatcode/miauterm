package org.cortex.terminal.view

import android.app.Activity
import android.content.DialogInterface
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import org.cortex.terminal.BootstrapDialog
import org.cortex.terminal.R
import org.cortex.terminal.ThemePicker
import org.cortex.terminal.databinding.ActivityMainBinding
import org.cortex.terminal.databinding.ItemDebianFileBinding
import org.cortex.terminal.databinding.ItemSessionBinding
import org.cortex.terminal.emulator.TerminalColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TerminalInterfaceTest {

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        TerminalColor.applyTheme("catppuccin")
        Chrome.apply(TerminalColor.SCHEMES.getValue("catppuccin"))
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
        activity.setTheme(R.style.Theme_Cortex)
    }

    private fun texts(view: View, into: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) into.add(view.text.toString())
        if (view is ViewGroup) for (i in 0 until view.childCount) texts(view.getChildAt(i), into)
        return into
    }

    private fun tabRows(tabs: SessionTabsView): ViewGroup {
        val scroll = tabs.getChildAt(0) as ViewGroup
        return scroll.getChildAt(0) as ViewGroup
    }

    @Test
    fun theTabsShowTheLinuxPromptAndCloseOnlyTheActiveTab() {
        val tabs = SessionTabsView(activity)
        tabs.update(listOf(SessionTabsView.Tab("root@localhost ~#"), SessionTabsView.Tab("mia@localhost ~$")), 1)
        assertEquals(2, tabRows(tabs).childCount)
        assertEquals(listOf("root@localhost ~#"), texts(tabRows(tabs).getChildAt(0)))
        assertEquals(listOf("mia@localhost ~$", "✕"), texts(tabRows(tabs).getChildAt(1)))
        assertEquals("root@localhost ~#", tabRows(tabs).getChildAt(0).contentDescription)
        assertEquals("mia@localhost ~$", tabRows(tabs).getChildAt(1).contentDescription)
    }

    @Test
    fun onlyTheTabOnScreenHasACloseButton() {
        val tabs = SessionTabsView(activity)
        tabs.update(listOf(SessionTabsView.Tab("a"), SessionTabsView.Tab("b")), 0)
        val closes = texts(tabs).count { it == "✕" }
        assertEquals(1, closes)
    }

    @Test
    fun tappingATabSelectsItAndThePlusStartsASession() {
        val tabs = SessionTabsView(activity)
        var selected = -1
        var started = 0
        tabs.onSelect = { selected = it }
        tabs.onNew = { started++ }
        tabs.update(listOf(SessionTabsView.Tab("a"), SessionTabsView.Tab("b")), 0)
        tabRows(tabs).getChildAt(1).performClick()
        assertEquals(1, selected)
        tabs.getChildAt(1).performClick()
        assertEquals(1, started)
    }

    @Test
    fun theCloseButtonClosesTheTabOnScreen() {
        val tabs = SessionTabsView(activity)
        var closed = -1
        tabs.onClose = { closed = it }
        tabs.update(listOf(SessionTabsView.Tab("a"), SessionTabsView.Tab("b")), 1)
        val row = tabRows(tabs).getChildAt(1) as ViewGroup
        val close = (0 until row.childCount).map { row.getChildAt(it) }.filterIsInstance<TextView>().first { it.text == "✕" }
        close.performClick()
        assertEquals(1, closed)
    }

    @Test
    fun theTabsFollowTheSchemeWhenItChanges() {
        val tabs = SessionTabsView(activity)
        tabs.update(listOf(SessionTabsView.Tab("a")), 0)
        Chrome.apply(TerminalColor.SCHEMES.getValue("nord"))
        tabs.applyPalette()
        assertEquals(1, tabRows(tabs).childCount)
        Chrome.apply(TerminalColor.SCHEMES.getValue("catppuccin"))
    }

    @Test
    fun theKeysBarHasItsRowsAndFollowsTheScheme() {
        val keys = ExtraKeysView(activity)
        assertEquals(2, keys.rowCount)
        Chrome.apply(TerminalColor.SCHEMES.getValue("monokai"))
        keys.applyPalette()
        assertEquals(2, keys.rowCount)
        Chrome.apply(TerminalColor.SCHEMES.getValue("catppuccin"))
    }

    @Test
    fun theKeysFillEachRowAndWideCustomRowsCanScroll() {
        val keys = ExtraKeysView(activity)
        val width = (360 * activity.resources.displayMetrics.density).toInt()

        fun layout() {
            keys.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            keys.layout(0, 0, width, keys.measuredHeight)
        }

        layout()
        val firstScroll = keys.getChildAt(0) as android.widget.HorizontalScrollView
        val firstRow = firstScroll.getChildAt(0) as ViewGroup
        val defaultWidths = (0 until firstRow.childCount).map { firstRow.getChildAt(it).width }
        assertTrue(defaultWidths.maxOrNull()!! - defaultWidths.minOrNull()!! <= 1)

        keys.setKeys(listOf(List(10) { ExtraKeysLayout.TextKey("x") }))
        layout()
        val customScroll = keys.getChildAt(0) as android.widget.HorizontalScrollView
        val customRow = customScroll.getChildAt(0)
        assertTrue(customRow.width > customScroll.width)
        assertTrue(customScroll.canScrollHorizontally(1))
    }

    @Test
    fun theSearchBarFollowsTheScheme() {
        val bar = TerminalSearchBar(activity)
        bar.applyPalette()
        assertNotNull(bar.background)
    }

    @Test
    fun aSessionCardInflates() {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(activity))

        assertNotNull(binding.sessionLabelText)
        assertNotNull(binding.btnSessionClose)
    }

    @Test
    fun aDebianFileRowHasDiscoverableActions() {
        val binding = ItemDebianFileBinding.inflate(LayoutInflater.from(activity))
        assertNotNull(binding.fileIcon)
        assertNotNull(binding.fileName)
        assertNotNull(binding.fileDetails)
        assertNotNull(binding.fileActions)
    }

    @Test
    fun theMainScreenInflatesWithItsTabsKeysAndDrawer() {
        val binding = ActivityMainBinding.inflate(LayoutInflater.from(activity))
        assertNotNull(binding.sessionTabs)
        assertNotNull(binding.terminalView)
        assertNotNull(binding.extraKeysView)
        assertNotNull(binding.btnDrawerFiles)
        assertNotNull(binding.btnDrawerNewSession)
        assertNotNull(binding.btnDrawerSettings)
        val drawerHeader = binding.navDrawer.getChildAt(0) as ViewGroup
        assertEquals(3, drawerHeader.childCount)
    }

    @Test
    fun theFirstStartDialogShowsTheProgress() {
        val dialog = BootstrapDialog(activity)
        dialog.show()
        assertTrue(dialog.isShowing)
        dialog.isIndeterminate = false
        dialog.progress = 42
        assertEquals(42, dialog.progress)
        dialog.dismiss()
    }

    @Test
    fun theSchemePickerListsEverySchemeAndChoosesOne() {
        val pref = ListPreference(activity).apply {
            title = "Color scheme"
            entries = TerminalColor.SCHEMES.values.map { it.displayName }.toTypedArray()
            entryValues = TerminalColor.SCHEMES.keys.toTypedArray()
            value = "catppuccin"
        }
        ThemePicker.show(activity as Activity, pref)
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(TerminalColor.SCHEMES.size, dialog.listView.adapter.count)
        assertNotNull(dialog.listView.adapter.getView(3, null, dialog.listView))
    }

    @Test
    fun aDialogIsInTheInterfacesColors() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Title")
            .setMessage("Message")
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .showInChrome()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dialog.isShowing)
        val p = Chrome.palette
        assertEquals(p.textDim, dialog.findViewById<TextView>(android.R.id.message)!!.currentTextColor)
        assertEquals(p.text, dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.currentTextColor)
        assertEquals(p.accent, dialog.getButton(DialogInterface.BUTTON_POSITIVE).currentTextColor)
        assertEquals(p.accent, dialog.getButton(DialogInterface.BUTTON_NEGATIVE).currentTextColor)
        dialog.dismiss()
    }

    @Test
    fun theKeyboardSeesALineWithSomethingToDelete() {

        val view = TerminalView(activity)
        val info = EditorInfo()
        val ic = view.onCreateInputConnection(info)
        assertEquals(1, info.initialSelStart)
        assertEquals(1, info.initialSelEnd)
        assertEquals(" ", ic.getTextBeforeCursor(100, 0).toString())
        assertEquals("", ic.getTextAfterCursor(100, 0).toString())

        assertTrue(ic.beginBatchEdit())
        assertTrue(ic.deleteSurroundingText(1, 0))
        assertFalse(ic.endBatchEdit())
        assertEquals(" ", ic.getTextBeforeCursor(1, 0).toString())
        val around = ic.getSurroundingText(10, 10, 0)!!
        assertEquals(" ", around.text.toString())
        assertEquals(1, around.selectionStart)
    }

    @Test
    fun theKeyboardsOwnSelectAllCopyAndPasteButtonsAreAnswered() {

        val view = TerminalView(activity)
        val ic = view.onCreateInputConnection(EditorInfo())
        assertFalse("nothing to copy without a selection", ic.performContextMenuAction(android.R.id.copy))
        assertFalse(view.isSelecting)
        assertTrue(ic.performContextMenuAction(android.R.id.selectAll))
        assertTrue("Select all selects", view.isSelecting)
        assertTrue(ic.performContextMenuAction(android.R.id.paste))
        assertTrue(ic.performContextMenuAction(android.R.id.pasteAsPlainText))
        assertFalse("another action is not ours", ic.performContextMenuAction(android.R.id.startSelectingText))

        view.selectAllText()
        assertFalse(ic.performContextMenuAction(android.R.id.cut))
        assertFalse(view.isSelecting)
    }

    @Test
    fun theClipboardKeysCopyAndPaste() {
        val view = TerminalView(activity)
        view.selectAllText()
        assertTrue(view.isSelecting)
        assertTrue(view.onKeyDown(KeyEvent.KEYCODE_COPY, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COPY)))
        assertFalse("copy leaves the selection (nothing on screen to copy without a session)", view.isSelecting)
        assertTrue(view.onKeyDown(KeyEvent.KEYCODE_PASTE, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PASTE)))
        view.selectAllText()
        assertTrue(view.onKeyDown(KeyEvent.KEYCODE_CUT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CUT)))
        assertFalse(view.isSelecting)
    }

    @Test
    fun aHardwareKeyboardsClipboardShortcutsAreAnswered() {
        val V = KeyEvent.KEYCODE_V
        val C = KeyEvent.KEYCODE_C
        val INS = KeyEvent.KEYCODE_INSERT

        assertEquals(ClipboardShortcut.PASTE, clipboardShortcut(V, ctrl = true, shift = true, alt = false))
        assertEquals(ClipboardShortcut.PASTE, clipboardShortcut(V, ctrl = true, shift = false, alt = true))
        assertEquals(ClipboardShortcut.PASTE, clipboardShortcut(INS, ctrl = false, shift = true, alt = false))

        assertEquals(ClipboardShortcut.COPY, clipboardShortcut(C, ctrl = true, shift = true, alt = false))
        assertEquals(ClipboardShortcut.COPY, clipboardShortcut(INS, ctrl = true, shift = false, alt = false))

        assertEquals(null, clipboardShortcut(V, ctrl = true, shift = false, alt = false))
        assertEquals(null, clipboardShortcut(C, ctrl = true, shift = false, alt = false))
        assertEquals(null, clipboardShortcut(V, ctrl = false, shift = false, alt = false))
        assertEquals(null, clipboardShortcut(V, ctrl = false, shift = true, alt = false))
        assertEquals(null, clipboardShortcut(INS, ctrl = false, shift = false, alt = false))
        assertEquals(null, clipboardShortcut(KeyEvent.KEYCODE_A, ctrl = true, shift = true, alt = false))

        val view = TerminalView(activity)
        val ctrlShift = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        fun key(code: Int, meta: Int) = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0, meta)
        assertTrue(view.onKeyDown(V, key(V, ctrlShift)))
        view.selectAllText()
        assertTrue(view.onKeyDown(C, key(C, ctrlShift)))
        assertFalse("Ctrl+Shift+C copies the selection and leaves it", view.isSelecting)
        view.selectAllText()
        assertTrue(view.onKeyDown(INS, key(INS, KeyEvent.META_CTRL_ON)))
        assertFalse("Ctrl+Insert copies the selection and leaves it", view.isSelecting)

        val repeat = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, V, 1, ctrlShift)
        assertTrue(view.onKeyDown(V, repeat))
        view.selectAllText()
        assertTrue(view.onKeyDown(C, KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, C, 1, ctrlShift)))
        assertTrue("a repeat of the copy key does not copy", view.isSelecting)
    }

    @Test
    fun deleteOverASelectionTakesTheSelectionAway() {
        val view = TerminalView(activity)
        val ic = view.onCreateInputConnection(EditorInfo())
        view.selectAllText()
        assertTrue(ic.deleteSurroundingText(1, 0))
        assertFalse(view.isSelecting)
        view.selectAllText()
        assertTrue(ic.commitText("", 1))
        assertFalse(view.isSelecting)
        view.selectAllText()

        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        assertFalse(view.isSelecting)
    }

    @Test
    fun theCursorIsInASelectionOnItsRowsAndBetweenItsEnds() {

        val norm = intArrayOf(2, 5, 4, 3)
        assertTrue(selectionHoldsCell(norm, 3, 0))
        assertTrue(selectionHoldsCell(norm, 3, 79))
        assertTrue(selectionHoldsCell(norm, 2, 5))
        assertFalse(selectionHoldsCell(norm, 2, 4))
        assertTrue(selectionHoldsCell(norm, 4, 3))
        assertFalse(selectionHoldsCell(norm, 4, 4))
        assertFalse(selectionHoldsCell(norm, 1, 40))
        assertFalse(selectionHoldsCell(norm, 5, 0))

        assertTrue(selectionHoldsCell(intArrayOf(-300, 0, 23, 79), 12, 6))

        assertTrue(selectionHoldsCell(intArrayOf(7, 2, 7, 2), 7, 2))
        assertFalse(selectionHoldsCell(intArrayOf(7, 2, 7, 2), 7, 3))
    }
}
