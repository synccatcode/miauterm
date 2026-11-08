package org.cortex.terminal.emulator

import android.graphics.Color

object TerminalColor {

    data class ColorScheme(
        val id: String,
        val displayName: String,
        val fg: Int,
        val bg: Int,
        val cursor: Int,
        val ansi: IntArray
    )

    val SCHEMES = mapOf(
        "catppuccin" to ColorScheme(
            id = "catppuccin",
            displayName = "Catppuccin Mocha",
            fg = Color.rgb(0xcd, 0xd6, 0xf4),
            bg = Color.rgb(0x18, 0x18, 0x25),
            cursor = Color.rgb(0x89, 0xb4, 0xfa),
            ansi = intArrayOf(
                Color.rgb(0x1e, 0x1e, 0x2e),
                Color.rgb(0xf3, 0x8b, 0xa8),
                Color.rgb(0xa6, 0xe3, 0xa1),
                Color.rgb(0xf9, 0xe2, 0xaf),
                Color.rgb(0x89, 0xb4, 0xfa),
                Color.rgb(0xcb, 0xa6, 0xf7),
                Color.rgb(0x89, 0xdc, 0xeb),
                Color.rgb(0xba, 0xc2, 0xde),
                Color.rgb(0x7f, 0x84, 0x9c),
                Color.rgb(0xf3, 0x8b, 0xa8),
                Color.rgb(0xa6, 0xe3, 0xa1),
                Color.rgb(0xf9, 0xe2, 0xaf),
                Color.rgb(0x89, 0xb4, 0xfa),
                Color.rgb(0xcb, 0xa6, 0xf7),
                Color.rgb(0x94, 0xe2, 0xd5),
                Color.rgb(0xa6, 0xad, 0xc8)
            )
        ),

        "light" to ColorScheme(
            id = "light",
            displayName = "Light",
            fg = Color.rgb(0x1f, 0x23, 0x28),
            bg = Color.rgb(0xff, 0xff, 0xff),
            cursor = Color.rgb(0x09, 0x69, 0xda),
            ansi = intArrayOf(
                Color.rgb(0x24, 0x29, 0x2f),
                Color.rgb(0xcf, 0x22, 0x2e),
                Color.rgb(0x11, 0x63, 0x29),
                Color.rgb(0x9a, 0x67, 0x00),
                Color.rgb(0x09, 0x69, 0xda),
                Color.rgb(0x82, 0x50, 0xdf),
                Color.rgb(0x1b, 0x7c, 0x83),
                Color.rgb(0x6e, 0x77, 0x81),
                Color.rgb(0x57, 0x60, 0x6a),
                Color.rgb(0xa4, 0x0e, 0x26),
                Color.rgb(0x1a, 0x7f, 0x37),
                Color.rgb(0xbf, 0x87, 0x00),
                Color.rgb(0x09, 0x69, 0xda),
                Color.rgb(0x82, 0x50, 0xdf),
                Color.rgb(0x31, 0x92, 0xaa),
                Color.rgb(0x84, 0x8d, 0x97)
            )
        ),
        "tokyo_night" to ColorScheme(
            id = "tokyo_night",
            displayName = "Tokyo Night",
            fg = Color.rgb(0xc0, 0xca, 0xf5),
            bg = Color.rgb(0x1a, 0x1b, 0x26),
            cursor = Color.rgb(0xc0, 0xca, 0xf5),
            ansi = intArrayOf(
                Color.rgb(0x15, 0x16, 0x1e),
                Color.rgb(0xf7, 0x76, 0x8e),
                Color.rgb(0x9e, 0xce, 0x6a),
                Color.rgb(0xe0, 0xaf, 0x68),
                Color.rgb(0x7a, 0xa2, 0xf7),
                Color.rgb(0xbb, 0x9a, 0xf7),
                Color.rgb(0x7d, 0xcf, 0xff),
                Color.rgb(0xa9, 0xb1, 0xd6),
                Color.rgb(0x41, 0x48, 0x68),
                Color.rgb(0xf7, 0x76, 0x8e),
                Color.rgb(0x9e, 0xce, 0x6a),
                Color.rgb(0xe0, 0xaf, 0x68),
                Color.rgb(0x7a, 0xa2, 0xf7),
                Color.rgb(0xbb, 0x9a, 0xf7),
                Color.rgb(0x7d, 0xcf, 0xff),
                Color.rgb(0xc0, 0xca, 0xf5)
            )
        ),
        "dracula" to ColorScheme(
            id = "dracula",
            displayName = "Dracula",
            fg = Color.rgb(0xf8, 0xf8, 0xf2),
            bg = Color.rgb(0x28, 0x2a, 0x36),
            cursor = Color.rgb(0xf8, 0xf8, 0xf2),
            ansi = intArrayOf(
                Color.rgb(0x21, 0x22, 0x2c),
                Color.rgb(0xff, 0x55, 0x55),
                Color.rgb(0x50, 0xfa, 0x7b),
                Color.rgb(0xf1, 0xfa, 0x8c),
                Color.rgb(0xbd, 0x93, 0xf9),
                Color.rgb(0xff, 0x79, 0xc6),
                Color.rgb(0x8b, 0xe9, 0xfd),
                Color.rgb(0xf8, 0xf8, 0xf2),
                Color.rgb(0x62, 0x72, 0xa4),
                Color.rgb(0xff, 0x6e, 0x6e),
                Color.rgb(0x69, 0xff, 0x94),
                Color.rgb(0xff, 0xff, 0xa5),
                Color.rgb(0xd6, 0xac, 0xff),
                Color.rgb(0xff, 0x92, 0xdf),
                Color.rgb(0xa4, 0xff, 0xff),
                Color.rgb(0xff, 0xff, 0xff)
            )
        ),
        "gruvbox" to ColorScheme(
            id = "gruvbox",
            displayName = "Gruvbox Dark",
            fg = Color.rgb(0xeb, 0xdb, 0xb2),
            bg = Color.rgb(0x28, 0x28, 0x28),
            cursor = Color.rgb(0xeb, 0xdb, 0xb2),
            ansi = intArrayOf(
                Color.rgb(0x28, 0x28, 0x28),
                Color.rgb(0xcc, 0x24, 0x1d),
                Color.rgb(0x98, 0x97, 0x1a),
                Color.rgb(0xd7, 0x99, 0x21),
                Color.rgb(0x45, 0x85, 0x88),
                Color.rgb(0xb1, 0x62, 0x86),
                Color.rgb(0x68, 0x9d, 0x6a),
                Color.rgb(0xa8, 0x99, 0x84),
                Color.rgb(0x92, 0x83, 0x74),
                Color.rgb(0xfb, 0x49, 0x34),
                Color.rgb(0xb8, 0xbb, 0x26),
                Color.rgb(0xfa, 0xbd, 0x2f),
                Color.rgb(0x83, 0xa5, 0x98),
                Color.rgb(0xd3, 0x86, 0x9b),
                Color.rgb(0x8e, 0xc0, 0x7c),
                Color.rgb(0xeb, 0xdb, 0xb2)
            )
        ),
        "one_dark" to ColorScheme(
            id = "one_dark",
            displayName = "One Dark",
            fg = Color.rgb(0xab, 0xb2, 0xbf),
            bg = Color.rgb(0x1e, 0x22, 0x2a),
            cursor = Color.rgb(0x52, 0x8b, 0xff),
            ansi = intArrayOf(
                Color.rgb(0x1e, 0x22, 0x2a),
                Color.rgb(0xe0, 0x6c, 0x75),
                Color.rgb(0x98, 0xc3, 0x79),
                Color.rgb(0xe5, 0xc0, 0x7b),
                Color.rgb(0x61, 0xaf, 0xef),
                Color.rgb(0xc6, 0x78, 0xdd),
                Color.rgb(0x56, 0xb6, 0xc2),
                Color.rgb(0xab, 0xb2, 0xbf),
                Color.rgb(0x5c, 0x63, 0x70),
                Color.rgb(0xe0, 0x6c, 0x75),
                Color.rgb(0x98, 0xc3, 0x79),
                Color.rgb(0xe5, 0xc0, 0x7b),
                Color.rgb(0x61, 0xaf, 0xef),
                Color.rgb(0xc6, 0x78, 0xdd),
                Color.rgb(0x56, 0xb6, 0xc2),
                Color.rgb(0xff, 0xff, 0xff)
            )
        ),
        "nord" to ColorScheme(
            id = "nord",
            displayName = "Nord",
            fg = Color.rgb(0xd8, 0xde, 0xe9),
            bg = Color.rgb(0x2e, 0x34, 0x40),
            cursor = Color.rgb(0xd8, 0xde, 0xe9),
            ansi = intArrayOf(
                Color.rgb(0x3b, 0x42, 0x52),
                Color.rgb(0xbf, 0x61, 0x6a),
                Color.rgb(0xa3, 0xbe, 0x8c),
                Color.rgb(0xeb, 0xcb, 0x8b),
                Color.rgb(0x81, 0xa1, 0xc1),
                Color.rgb(0xb4, 0x8e, 0xad),
                Color.rgb(0x88, 0xc0, 0xd0),
                Color.rgb(0xe5, 0xe9, 0xf0),
                Color.rgb(0x4c, 0x56, 0x6a),
                Color.rgb(0xbf, 0x61, 0x6a),
                Color.rgb(0xa3, 0xbe, 0x8c),
                Color.rgb(0xeb, 0xcb, 0x8b),
                Color.rgb(0x81, 0xa1, 0xc1),
                Color.rgb(0xb4, 0x8e, 0xad),
                Color.rgb(0x8f, 0xbc, 0xbb),
                Color.rgb(0xec, 0xef, 0xf4)
            )
        ),
        "solarized_dark" to ColorScheme(
            id = "solarized_dark",
            displayName = "Solarized Dark",
            fg = Color.rgb(0x83, 0x94, 0x96),
            bg = Color.rgb(0x00, 0x2b, 0x36),
            cursor = Color.rgb(0x93, 0xa1, 0xa1),
            ansi = intArrayOf(
                Color.rgb(0x07, 0x36, 0x42),
                Color.rgb(0xdc, 0x32, 0x2f),
                Color.rgb(0x85, 0x99, 0x00),
                Color.rgb(0xb5, 0x89, 0x00),
                Color.rgb(0x26, 0x8b, 0xd2),
                Color.rgb(0xd3, 0x36, 0x82),
                Color.rgb(0x2a, 0xa1, 0x98),
                Color.rgb(0xee, 0xe8, 0xd5),
                Color.rgb(0x58, 0x6e, 0x75),
                Color.rgb(0xcb, 0x4b, 0x16),
                Color.rgb(0x85, 0x99, 0x00),
                Color.rgb(0xb5, 0x89, 0x00),
                Color.rgb(0x26, 0x8b, 0xd2),
                Color.rgb(0x6c, 0x71, 0xc4),
                Color.rgb(0x2a, 0xa1, 0x98),
                Color.rgb(0xfd, 0xf6, 0xe3)
            )
        ),
        "monokai" to ColorScheme(
            id = "monokai",
            displayName = "Monokai",
            fg = Color.rgb(0xf8, 0xf8, 0xf2),
            bg = Color.rgb(0x27, 0x28, 0x22),
            cursor = Color.rgb(0xf8, 0xf8, 0xf0),
            ansi = intArrayOf(
                Color.rgb(0x27, 0x28, 0x22),
                Color.rgb(0xf9, 0x26, 0x72),
                Color.rgb(0xa6, 0xe2, 0x2e),
                Color.rgb(0xf4, 0xbf, 0x75),
                Color.rgb(0x66, 0xd9, 0xef),
                Color.rgb(0xae, 0x81, 0xff),
                Color.rgb(0xa1, 0xef, 0xe4),
                Color.rgb(0xf8, 0xf8, 0xf2),
                Color.rgb(0x75, 0x71, 0x5e),
                Color.rgb(0xf9, 0x26, 0x72),
                Color.rgb(0xa6, 0xe2, 0x2e),
                Color.rgb(0xf4, 0xbf, 0x75),
                Color.rgb(0x66, 0xd9, 0xef),
                Color.rgb(0xae, 0x81, 0xff),
                Color.rgb(0xa1, 0xef, 0xe4),
                Color.rgb(0xf9, 0xf8, 0xf5)
            )
        ),
        "rose_pine" to ColorScheme(
            id = "rose_pine",
            displayName = "Rosé Pine",
            fg = Color.rgb(0xe0, 0xde, 0xf4),
            bg = Color.rgb(0x19, 0x17, 0x24),
            cursor = Color.rgb(0xe0, 0xde, 0xf4),
            ansi = intArrayOf(
                Color.rgb(0x26, 0x23, 0x3a),
                Color.rgb(0xeb, 0x6f, 0x92),
                Color.rgb(0x31, 0x74, 0x8f),
                Color.rgb(0xf6, 0xc1, 0x77),
                Color.rgb(0x9c, 0xcf, 0xd8),
                Color.rgb(0xc4, 0xa7, 0xe7),
                Color.rgb(0xeb, 0xbc, 0xba),
                Color.rgb(0xe0, 0xde, 0xf4),
                Color.rgb(0x6e, 0x6a, 0x86),
                Color.rgb(0xeb, 0x6f, 0x92),
                Color.rgb(0x31, 0x74, 0x8f),
                Color.rgb(0xf6, 0xc1, 0x77),
                Color.rgb(0x9c, 0xcf, 0xd8),
                Color.rgb(0xc4, 0xa7, 0xe7),
                Color.rgb(0xeb, 0xbc, 0xba),
                Color.rgb(0xe0, 0xde, 0xf4)
            )
        ),
        "everforest" to ColorScheme(
            id = "everforest",
            displayName = "Everforest",
            fg = Color.rgb(0xd3, 0xc6, 0xaa),
            bg = Color.rgb(0x2b, 0x33, 0x39),
            cursor = Color.rgb(0xd3, 0xc6, 0xaa),
            ansi = intArrayOf(
                Color.rgb(0x4b, 0x56, 0x5c),
                Color.rgb(0xe6, 0x7e, 0x80),
                Color.rgb(0xa7, 0xc0, 0x80),
                Color.rgb(0xdb, 0xbc, 0x7f),
                Color.rgb(0x7f, 0xbb, 0xb3),
                Color.rgb(0xd6, 0x99, 0xb6),
                Color.rgb(0x83, 0xc0, 0x92),
                Color.rgb(0xd3, 0xc6, 0xaa),
                Color.rgb(0x5c, 0x6a, 0x72),
                Color.rgb(0xe6, 0x7e, 0x80),
                Color.rgb(0xa7, 0xc0, 0x80),
                Color.rgb(0xdb, 0xbc, 0x7f),
                Color.rgb(0x7f, 0xbb, 0xb3),
                Color.rgb(0xd6, 0x99, 0xb6),
                Color.rgb(0x83, 0xc0, 0x92),
                Color.rgb(0xe6, 0xdf, 0xc6)
            )
        ),
        "github_dark" to ColorScheme(
            id = "github_dark",
            displayName = "GitHub Dark",
            fg = Color.rgb(0xc9, 0xd1, 0xd9),
            bg = Color.rgb(0x0d, 0x11, 0x17),
            cursor = Color.rgb(0x58, 0xa6, 0xff),
            ansi = intArrayOf(
                Color.rgb(0x48, 0x4f, 0x58),
                Color.rgb(0xff, 0x7b, 0x72),
                Color.rgb(0x3f, 0xb9, 0x50),
                Color.rgb(0xd2, 0x99, 0x22),
                Color.rgb(0x58, 0xa6, 0xff),
                Color.rgb(0xbc, 0x8c, 0xff),
                Color.rgb(0x39, 0xc5, 0xcf),
                Color.rgb(0xb1, 0xba, 0xc4),
                Color.rgb(0x6e, 0x76, 0x81),
                Color.rgb(0xff, 0xa1, 0x98),
                Color.rgb(0x56, 0xd3, 0x64),
                Color.rgb(0xe3, 0xb3, 0x41),
                Color.rgb(0x79, 0xc0, 0xff),
                Color.rgb(0xd2, 0xa8, 0xff),
                Color.rgb(0x56, 0xd4, 0xdd),
                Color.rgb(0xf0, 0xf6, 0xfc)
            )
        ),
        "ayu_mirage" to ColorScheme(
            id = "ayu_mirage",
            displayName = "Ayu Mirage",
            fg = Color.rgb(0xcb, 0xcc, 0xc6),
            bg = Color.rgb(0x1f, 0x24, 0x30),
            cursor = Color.rgb(0xff, 0xcc, 0x66),
            ansi = intArrayOf(
                Color.rgb(0x19, 0x1e, 0x2a),
                Color.rgb(0xed, 0x82, 0x74),
                Color.rgb(0xa6, 0xcc, 0x70),
                Color.rgb(0xfa, 0xd0, 0x7b),
                Color.rgb(0x6d, 0xcb, 0xfa),
                Color.rgb(0xcf, 0xba, 0xfa),
                Color.rgb(0x90, 0xe1, 0xc6),
                Color.rgb(0xc7, 0xc7, 0xc7),
                Color.rgb(0x68, 0x68, 0x68),
                Color.rgb(0xf2, 0x87, 0x79),
                Color.rgb(0xba, 0xe6, 0x7e),
                Color.rgb(0xff, 0xd5, 0x80),
                Color.rgb(0x73, 0xd0, 0xff),
                Color.rgb(0xd4, 0xbf, 0xff),
                Color.rgb(0x95, 0xe6, 0xcb),
                Color.rgb(0xff, 0xff, 0xff)
            )
        ),
        "amoled" to ColorScheme(
            id = "amoled",
            displayName = "Black (AMOLED)",
            fg = Color.rgb(0xcd, 0xd6, 0xf4),
            bg = Color.rgb(0x00, 0x00, 0x00),
            cursor = Color.rgb(0x89, 0xb4, 0xfa),
            ansi = intArrayOf(
                Color.rgb(0x1e, 0x1e, 0x2e),
                Color.rgb(0xf3, 0x8b, 0xa8),
                Color.rgb(0xa6, 0xe3, 0xa1),
                Color.rgb(0xf9, 0xe2, 0xaf),
                Color.rgb(0x89, 0xb4, 0xfa),
                Color.rgb(0xcb, 0xa6, 0xf7),
                Color.rgb(0x89, 0xdc, 0xeb),
                Color.rgb(0xba, 0xc2, 0xde),
                Color.rgb(0x7f, 0x84, 0x9c),
                Color.rgb(0xf3, 0x8b, 0xa8),
                Color.rgb(0xa6, 0xe3, 0xa1),
                Color.rgb(0xf9, 0xe2, 0xaf),
                Color.rgb(0x89, 0xb4, 0xfa),
                Color.rgb(0xcb, 0xa6, 0xf7),
                Color.rgb(0x94, 0xe2, 0xd5),
                Color.rgb(0xa6, 0xad, 0xc8)
            )
        )
    )

    var currentThemeId: String = "catppuccin"
        private set

    var DEFAULT_FG: Int = Color.rgb(0xcd, 0xd6, 0xf4)
    var DEFAULT_BG: Int = Color.rgb(0x18, 0x18, 0x25)
    var CURSOR_COLOR: Int = Color.rgb(0x89, 0xb4, 0xfa)

    val ANSI_COLORS = IntArray(16)
    val COLOR_PALETTE = IntArray(256)

    private var held = IntArray(18)

    init {
        applyTheme("catppuccin")
        held = inUse()
    }

    fun rebuildPalette() {
        for (i in 0 until 16) {
            COLOR_PALETTE[i] = ANSI_COLORS[i]
        }
        val steps = intArrayOf(0x00, 0x5f, 0x87, 0xaf, 0xd7, 0xff)
        var idx = 16
        for (r in steps) {
            for (g in steps) {
                for (b in steps) {
                    COLOR_PALETTE[idx++] = Color.rgb(r, g, b)
                }
            }
        }
        for (i in 0 until 24) {
            val gray = 8 + i * 10
            COLOR_PALETTE[idx++] = Color.rgb(gray, gray, gray)
        }
    }

    class Remap internal constructor(
        private val fgFrom: IntArray, private val fgTo: IntArray,
        private val bgFrom: IntArray, private val bgTo: IntArray
    ) {
        fun mapFg(color: Int): Int {
            for (i in fgFrom.indices) if (fgFrom[i] == color) return fgTo[i]
            return color
        }

        fun mapBg(color: Int): Int {
            for (i in bgFrom.indices) if (bgFrom[i] == color) return bgTo[i]
            return color
        }
    }

    private fun inUse() = intArrayOf(DEFAULT_FG, DEFAULT_BG, *ANSI_COLORS)

    fun takeRemap(): Remap? {
        val now = inUse()
        if (now.indices.all { held[it] == now[it] }) {
            held = now
            return null
        }

        val fgOrder = (listOf(0) + (2..17) + listOf(1)).filter { held[it] != now[it] }
        val bgOrder = (listOf(1) + (2..17) + listOf(0)).filter { held[it] != now[it] }
        val remap = Remap(
            IntArray(fgOrder.size) { held[fgOrder[it]] }, IntArray(fgOrder.size) { now[fgOrder[it]] },
            IntArray(bgOrder.size) { held[bgOrder[it]] }, IntArray(bgOrder.size) { now[bgOrder[it]] }
        )
        held = now
        return remap
    }

    fun applyTheme(themeId: String) {
        val scheme = SCHEMES[themeId] ?: SCHEMES["catppuccin"] ?: return
        currentThemeId = scheme.id
        DEFAULT_FG = scheme.fg
        DEFAULT_BG = scheme.bg
        CURSOR_COLOR = scheme.cursor
        for (i in 0 until 16) {
            ANSI_COLORS[i] = scheme.ansi[i]
        }
        rebuildPalette()
    }
}
