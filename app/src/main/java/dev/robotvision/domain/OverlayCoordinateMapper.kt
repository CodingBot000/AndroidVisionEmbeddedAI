package dev.robotvision.domain

import kotlin.math.max

object OverlayCoordinateMapper {
    /** Rotate a normalized sensor box clockwise; inference adapters return already rotated boxes. */
    fun rotate(box: Box, degrees: Int): Box = when ((degrees % 360 + 360) % 360) {
        0 -> box
        90 -> Box(1 - box.bottom, box.left, 1 - box.top, box.right)
        180 -> Box(1 - box.right, 1 - box.bottom, 1 - box.left, 1 - box.top)
        270 -> Box(box.top, 1 - box.right, box.bottom, 1 - box.left)
        else -> error("Rotation must be a multiple of 90")
    }
    fun fillCenter(box: Box, sourceWidth: Int, sourceHeight: Int, viewWidth: Float, viewHeight: Float): Box {
        require(sourceWidth > 0 && sourceHeight > 0 && viewWidth > 0 && viewHeight > 0)
        val scale = max(viewWidth / sourceWidth, viewHeight / sourceHeight)
        val w = sourceWidth * scale; val h = sourceHeight * scale
        val x = (viewWidth - w) / 2; val y = (viewHeight - h) / 2
        return Box(x + box.left * w, y + box.top * h, x + box.right * w, y + box.bottom * h)
    }
}
