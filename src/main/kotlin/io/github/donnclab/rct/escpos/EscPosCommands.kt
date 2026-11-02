package io.github.donnclab.rct.escpos

/**
 * The ESC/POS commands [EscPosEncoder] sends, named after the Epson ESC/POS command reference.
 *
 * A command is a prefix byte ([ESC] or [GS]), a command code, then parameters:
 * `ESC a 1` (centre alignment) is sent as `ESC, ALIGN, ALIGN_CENTER`, i.e. bytes `27 97 1`.
 * Encoder tests assert the raw byte values, so they double as a check on these constants.
 */
internal object EscPosCommands {
    // Control characters.

    /** `LF`: print the buffered line and advance one line. */
    const val LF: Int = 0x0A

    /** `ESC`: prefix for most printer commands. */
    const val ESC: Int = 0x1B

    /** `GS`: prefix for character size, cutting and graphics commands. */
    const val GS: Int = 0x1D

    // ESC commands. Each follows the ESC prefix.

    /** `ESC @`: reset the printer to its power-on settings. */
    const val INITIALIZE: Int = 0x40

    /** `ESC t n`: select character code table `n` (see [EscPosProfile.codeTable]). */
    const val SELECT_CODE_TABLE: Int = 0x74

    /** `ESC a n`: justify following lines. `n` is [ALIGN_LEFT], [ALIGN_CENTER] or [ALIGN_RIGHT]. */
    const val ALIGN: Int = 0x61

    /** `ESC M n`: select character font. `n` is [FONT_A] or [FONT_B]. */
    const val SELECT_FONT: Int = 0x4D

    /** `ESC E n`: emphasized (bold) mode. `n` is [OFF] or [ON]. */
    const val BOLD: Int = 0x45

    /** `ESC - n`: underline mode. `n` is [OFF] or [ON] (one-dot underline). */
    const val UNDERLINE: Int = 0x2D

    /** `ESC d n`: print the buffer and feed `n` lines, at most [MAX_FEED_LINES]. */
    const val FEED_LINES: Int = 0x64

    // GS commands. Each follows the GS prefix.

    /** `GS ! n`: character size. High nibble is width multiplier − 1, low nibble is height multiplier − 1. */
    const val CHARACTER_SIZE: Int = 0x21

    /** `GS V m`: cut the paper. `m` is [CUT_PARTIAL]. */
    const val CUT: Int = 0x56

    /**
     * `GS v 0 m xL xH yL yH d…`: print a raster bit image. `0` is [RASTER_FUNCTION], `m` is
     * [RASTER_NORMAL_DENSITY], `x` is the width in bytes and `y` the height in dots, both
     * little-endian; each data byte holds 8 horizontal pixels, most significant bit leftmost, 1 = black.
     */
    const val RASTER_IMAGE: Int = 0x76

    // Parameter values.

    const val OFF: Int = 0
    const val ON: Int = 1

    const val ALIGN_LEFT: Int = 0
    const val ALIGN_CENTER: Int = 1
    const val ALIGN_RIGHT: Int = 2

    /** Font A: 12 × 24 dots on most printers. */
    const val FONT_A: Int = 0

    /** Font B: 9 × 17 dots on most printers; used for `small` text. */
    const val FONT_B: Int = 1

    /** Partial cut, leaving one point uncut, for `GS V`. */
    const val CUT_PARTIAL: Int = 1

    /** The `0` that selects the raster function of `GS v`. */
    const val RASTER_FUNCTION: Int = 0x30

    /** Normal width and height for `GS v 0`. */
    const val RASTER_NORMAL_DENSITY: Int = 0

    /** The largest single-byte parameter, which limits `ESC d n` per command. */
    const val MAX_FEED_LINES: Int = 255
}
