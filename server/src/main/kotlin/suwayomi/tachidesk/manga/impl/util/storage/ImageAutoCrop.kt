package suwayomi.tachidesk.manga.impl.util.storage

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

object ImageAutoCrop {
    private const val FILLED_RATIO_LIMIT = 0.0025
    private const val THRESHOLD = 0.5
    private const val MARGIN = 4
    private val BORDER_COLOR_CONFIG = BorderColorConfig(scanSteps = 10, pixelCount = 5)

    fun autoCropBorders(inputStream: InputStream): InputStream {
        val originalImage = ImageIO.read(inputStream) ?: throw IllegalArgumentException("Invalid image input")
        val width = originalImage.width
        val height = originalImage.height

        val borderColorByDirection = detectBorderColors(originalImage, width, height)
        val (left, top, right, bottom) = findBorderBoundaries(originalImage, width, height, borderColorByDirection)

        if (left >= right || top >= bottom) return inputStream

        val croppedImage =
            originalImage.getSubimage(
                maxOf(0, left - MARGIN),
                maxOf(0, top - MARGIN),
                minOf(width, right + MARGIN) - maxOf(0, left - MARGIN),
                minOf(height, bottom + MARGIN) - maxOf(0, top - MARGIN),
            )

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(croppedImage, "png", outputStream)
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    private fun detectBorderColors(
        image: BufferedImage,
        width: Int,
        height: Int,
    ): Map<BorderDirection, Int> {
        val stepY = height / BORDER_COLOR_CONFIG.scanSteps // Steps along height
        val stepX = width / BORDER_COLOR_CONFIG.scanSteps // Steps along width

        val samples = BorderDirection.entries.associateWith { mutableListOf<Int>() }.toMutableMap()

        for (i in 0 until BORDER_COLOR_CONFIG.scanSteps) {
            val y = i * stepY
            val x = i * stepX

            // Sample multiple pixels across the fixed coordinate for better accuracy
            for (offset in 0 until BORDER_COLOR_CONFIG.pixelCount) {
                samples[BorderDirection.LEFT]!!.add(image.getRGB(minOf(offset, width - 1), y))
                samples[BorderDirection.RIGHT]!!.add(image.getRGB(maxOf(width - 1 - offset, 0), y))
                samples[BorderDirection.TOP]!!.add(image.getRGB(x, minOf(offset, height - 1)))
                samples[BorderDirection.BOTTOM]!!.add(image.getRGB(x, maxOf(height - 1 - offset, 0)))
            }
        }

        return samples.mapValues { (_, values) ->
            values
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: 0xFFFFFF
        }
    }

    private fun findBorderBoundaries(
        image: BufferedImage,
        width: Int,
        height: Int,
        bgColorMap: Map<BorderDirection, Int>,
    ): BorderBoundaries {
        val top = findBorder(image, width, height, bgColor = bgColorMap[BorderDirection.TOP] ?: 0xFFFFFF, direction = BorderDirection.TOP)
        val bottom =
            findBorder(image, width, height, bgColor = bgColorMap[BorderDirection.BOTTOM] ?: 0xFFFFFF, direction = BorderDirection.BOTTOM)
        val left = findBorder(image, width, height, top, bottom, bgColorMap[BorderDirection.LEFT] ?: 0xFFFFFF, BorderDirection.LEFT)
        val right = findBorder(image, width, height, top, bottom, bgColorMap[BorderDirection.RIGHT] ?: 0xFFFFFF, BorderDirection.RIGHT)

        return BorderBoundaries(left, top, right, bottom)
    }

    private fun findBorder(
        image: BufferedImage,
        width: Int,
        height: Int,
        top: Int = 0,
        bottom: Int = height,
        bgColor: Int,
        direction: BorderDirection,
    ): Int =
        when (direction) {
            BorderDirection.LEFT -> (0 until width).firstOrNull { hasSignificantContent(image, it, top, bottom, bgColor) } ?: 0
            BorderDirection.RIGHT ->
                (width - 1 downTo 0).firstOrNull { hasSignificantContent(image, it, top, bottom, bgColor) }?.plus(1)
                    ?: width
            BorderDirection.TOP ->
                (0 until height).firstOrNull { hasSignificantContent(image, it, 0, width, bgColor, horizontal = true) }
                    ?: 0
            BorderDirection.BOTTOM ->
                (height - 1 downTo 0).firstOrNull { hasSignificantContent(image, it, 0, width, bgColor, horizontal = true) }?.plus(1)
                    ?: height
        }

    private fun hasSignificantContent(
        image: BufferedImage,
        pos: Int,
        start: Int,
        end: Int,
        bgColor: Int,
        horizontal: Boolean = false,
    ): Boolean {
        val filledLimit = ((end - start) * FILLED_RATIO_LIMIT).roundToInt()
        var count = 0
        for (i in start until end step 2) {
            val pixel = if (horizontal) image.getRGB(i, pos) else image.getRGB(pos, i)
            if (!isSimilar(pixel, bgColor)) count++
            if (count > filledLimit) return true
        }
        return false
    }

    private fun isSimilar(
        color1: Int,
        color2: Int,
    ): Boolean {
        val diff =
            abs((color1 and 0xFF) - (color2 and 0xFF)) +
                abs(((color1 shr 8) and 0xFF) - ((color2 shr 8) and 0xFF)) +
                abs(((color1 shr 16) and 0xFF) - ((color2 shr 16) and 0xFF))
        return diff < (255 * THRESHOLD).toInt()
    }

    data class BorderBoundaries(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    /**
     * Configuration for border color detection.
     *
     * This will scan a total of `scanSteps * pixelCount` pixels per side.
     * - `scanSteps`: Number of evenly spaced scan lines.
     * - `pixelCount`: Number of adjacent pixels sampled per scan line.
     *
     * Example: If `scanSteps = 10` and `pixelCount = 5`, then `10 × 5 = 50` pixels
     * will be considered per border side.
     */
    private data class BorderColorConfig(
        val scanSteps: Int,
        val pixelCount: Int,
    )

    enum class BorderDirection {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
    }
}
