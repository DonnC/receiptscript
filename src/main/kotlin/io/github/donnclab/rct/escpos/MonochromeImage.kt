package io.github.donnclab.rct.escpos

/** Immutable row-major black/white pixels, independent of Android Bitmap or Java AWT. */
public class MonochromeImage private constructor(public val width: Int, public val height: Int, private val pixels: BooleanArray) {
    public fun isBlack(x: Int, y: Int): Boolean = pixels[y * width + x]

    internal fun fit(requestedWidth: Int?, requestedHeight: Int?, maxWidth: Int): MonochromeImage {
        val targetWidth = requestedWidth ?: width
        val targetHeight = requestedHeight ?: if (requestedWidth != null) {
            (height.toDouble() * targetWidth / width).toInt().coerceAtLeast(1)
        } else height
        require(targetWidth > 0 && targetHeight > 0)
        val scale = minOf(targetWidth.toDouble() / width, targetHeight.toDouble() / height, maxWidth.toDouble() / width)
        val resultWidth = (width * scale).toInt().coerceAtLeast(1)
        val resultHeight = (height * scale).toInt().coerceAtLeast(1)
        checkDimensions(resultWidth, resultHeight)
        return fromPixels(resultWidth, resultHeight) { x, y ->
            isBlack((x.toLong() * width / resultWidth).toInt(), (y.toLong() * height / resultHeight).toInt())
        }
    }

    public companion object {
        private fun checkDimensions(width: Int, height: Int) {
            require(width in 1..8192 && height in 1..32768 && width.toLong() * height <= 16_777_216) {
                "Image exceeds the supported pixel budget"
            }
        }

        public fun fromPixels(width: Int, height: Int, black: (x: Int, y: Int) -> Boolean): MonochromeImage {
            checkDimensions(width, height)
            return MonochromeImage(width, height, BooleanArray(width * height) { black(it % width, it / width) })
        }

        /** Alpha composites against white before luminance thresholding; transparent pixels stay white. */
        public fun fromArgb(width: Int, height: Int, argb: IntArray, threshold: Int = 160): MonochromeImage {
            checkDimensions(width, height)
            require(argb.size == width * height && threshold in 0..255)
            return fromPixels(width, height) { x, y ->
                val color = argb[y * width + x]
                val alpha = color ushr 24
                val red = (color ushr 16 and 255) * alpha + 255 * (255 - alpha)
                val green = (color ushr 8 and 255) * alpha + 255 * (255 - alpha)
                val blue = (color and 255) * alpha + 255 * (255 - alpha)
                (299L * red + 587L * green + 114L * blue) / 255000 < threshold
            }
        }
    }
}
