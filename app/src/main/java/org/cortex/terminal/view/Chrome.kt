package org.cortex.terminal.view

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import org.cortex.terminal.emulator.TerminalColor
import kotlin.math.pow
import kotlin.math.roundToInt

object Chrome {

    class Palette(

        val bg: Int,
        val fg: Int,

        val bar: Int,

        val raised: Int,
        val raisedHigh: Int,
        val outline: Int,
        val text: Int,
        val textDim: Int,
        val textFaint: Int,
        val accent: Int,

        val accentSoft: Int,

        val onAccent: Int,
        val danger: Int,
        val dangerSoft: Int,

        val dark: Boolean
    )

    @Volatile
    var palette: Palette = from(TerminalColor.SCHEMES.getValue("catppuccin"))
        private set

    fun apply(scheme: TerminalColor.ColorScheme) {
        palette = from(scheme)
    }

    fun from(scheme: TerminalColor.ColorScheme): Palette = from(scheme.bg, scheme.fg, scheme.ansi)

    fun from(bg: Int, fg: Int, ansi: IntArray): Palette {
        val dark = luminance(bg) < 0.5f
        val bar = if (dark) mix(bg, BLACK, 0.22f) else mix(bg, BLACK, 0.05f)
        val raised = mix(bar, fg, 0.075f)
        val raisedHigh = mix(bar, fg, 0.17f)
        val accentSoft = mix(bg, ansi[12], 0.20f)
        val dangerSoft = mix(bg, ansi[9], 0.18f)

        val away = if (dark) WHITE else BLACK
        val surfaces = listOf(bg, bar, raised, raisedHigh, accentSoft, dangerSoft)
        val worst = if (dark) (surfaces.maxByOrNull { luminance(it) } ?: bg) else (surfaces.minByOrNull { luminance(it) } ?: bg)
        val accent = readable(ansi[12], worst, 3f, away)
        return Palette(
            bg = bg,
            fg = fg,
            bar = bar,
            raised = raised,
            raisedHigh = raisedHigh,
            outline = mix(bar, fg, 0.14f),
            text = readable(fg, worst, 4.5f, away),
            textDim = readable(mix(bg, fg, 0.68f), worst, 4.5f, away),
            textFaint = readable(mix(bg, fg, 0.42f), worst, 3f, away),
            accent = accent,
            accentSoft = accentSoft,
            onAccent = onColor(accent),
            danger = readable(ansi[9], worst, 3f, away),
            dangerSoft = dangerSoft,
            dark = dark
        )
    }

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    private fun red(c: Int) = (c shr 16) and 0xff
    private fun green(c: Int) = (c shr 8) and 0xff
    private fun blue(c: Int) = c and 0xff

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xff shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return rgb(
            (red(a) + (red(b) - red(a)) * f).roundToInt(),
            (green(a) + (green(b) - green(a)) * f).roundToInt(),
            (blue(a) + (blue(b) - blue(a)) * f).roundToInt()
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xffffff)

    fun luminance(color: Int): Float {
        fun channel(v: Int): Float {
            val s = v / 255f
            return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
        return 0.2126f * channel(red(color)) + 0.7152f * channel(green(color)) + 0.0722f * channel(blue(color))
    }

    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    fun readable(color: Int, against: Int, minContrast: Float, toward: Int): Int {
        if (contrast(color, against) >= minContrast) return color
        var t = 0.05f
        while (t < 1f) {
            val c = mix(color, toward, t)
            if (contrast(c, against) >= minContrast) return c
            t += 0.05f
        }
        return toward
    }

    fun onColor(background: Int): Int =
        if (contrast(background, BLACK) >= contrast(background, WHITE)) BLACK else WHITE

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun pressable(normal: Drawable, pressed: Drawable): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }

    fun ripple(content: Drawable, rippleColor: Int, radiusPx: Float): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, rounded(WHITE, radiusPx))

    fun keyBackground(p: Palette, radiusPx: Float, strokePx: Int): Drawable =
        pressable(
            rounded(p.raised, radiusPx, p.outline, strokePx),
            rounded(p.raisedHigh, radiusPx, p.outline, strokePx)
        )

    fun activeKeyBackground(p: Palette, radiusPx: Float, strokePx: Int): Drawable =
        pressable(
            rounded(p.accent, radiusPx, p.accent, strokePx),
            rounded(mix(p.accent, p.fg, 0.25f), radiusPx, p.accent, strokePx)
        )

    fun buttonBackground(p: Palette, radiusPx: Float, strokePx: Int, filled: Boolean): Drawable =
        if (filled) {
            ripple(rounded(p.accent, radiusPx), withAlpha(p.onAccent, 70), radiusPx)
        } else {
            ripple(rounded(p.raised, radiusPx, p.outline, strokePx), withAlpha(p.accent, 70), radiusPx)
        }
}
