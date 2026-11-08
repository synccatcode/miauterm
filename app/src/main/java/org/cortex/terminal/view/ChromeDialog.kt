package org.cortex.terminal.view

import android.content.DialogInterface
import android.graphics.drawable.InsetDrawable
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

object ChromeDialog {

    private val BUTTONS = intArrayOf(
        DialogInterface.BUTTON_POSITIVE,
        DialogInterface.BUTTON_NEGATIVE,
        DialogInterface.BUTTON_NEUTRAL
    )

    fun style(dialog: AlertDialog, onShow: () -> Unit = {}): AlertDialog {
        val p = Chrome.palette
        val density = dialog.context.resources.displayMetrics.density
        val hairline = density.toInt().coerceAtLeast(1)
        dialog.window?.setBackgroundDrawable(
            InsetDrawable(Chrome.rounded(p.bar, 20 * density, p.outline, hairline), (16 * density).toInt())
        )
        val applyColors = {
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(p.text)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(p.textDim)
            for (which in BUTTONS) dialog.getButton(which)?.setTextColor(p.accent)
        }
        if (dialog.isShowing) {
            applyColors()
            onShow()
        } else {
            dialog.setOnShowListener {
                applyColors()
                onShow()
            }
        }
        return dialog
    }
}

fun AlertDialog.Builder.showInChrome(): AlertDialog = ChromeDialog.style(create()).also { it.show() }
