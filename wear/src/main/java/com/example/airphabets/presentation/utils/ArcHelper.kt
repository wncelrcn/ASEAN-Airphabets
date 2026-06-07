package com.example.airphabets.presentation.utils

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Helper class for arc-related calculations
 */
object ArcHelper {

    /**
     * Determines which arc mode was tapped based on tap coordinates
     */
    fun getTappedMode(
        tapX: Float,
        tapY: Float,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): String? {
        // Calculate distance from center
        val dx = tapX - centerX
        val dy = tapY - centerY
        val distance = sqrt(dx * dx + dy * dy)

        // Check if tap is within the arc ring area
        val innerRadius = radius - (ArcConstants.ARC_STROKE_WIDTH * 2)
        val outerRadius = radius + (ArcConstants.ARC_STROKE_WIDTH * 2)

        if (distance < innerRadius || distance > outerRadius) {
            return null // Tap outside arc area
        }

        // Calculate angle of tap
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f

        // Check which arc was tapped based on angle ranges
        return when {
            isInArcRange(angle, ArcConstants.PRACTICE_MODE_ANGLE) -> "practice"
            isInArcRange(angle, ArcConstants.TUTORIAL_MODE_ANGLE) -> "tutorial"
            isInArcRangeWrapped(angle, ArcConstants.LEARN_MODE_ANGLE) -> "learn"
            else -> null
        }
    }

    private fun isInArcRange(angle: Float, centerAngle: Float): Boolean {
        val start = centerAngle - (ArcConstants.ARC_SWEEP_ANGLE / 2f)
        val end = centerAngle + (ArcConstants.ARC_SWEEP_ANGLE / 2f)
        return angle >= start && angle <= end
    }

    private fun isInArcRangeWrapped(angle: Float, centerAngle: Float): Boolean {
        val start = centerAngle - (ArcConstants.ARC_SWEEP_ANGLE / 2f)
        val end = centerAngle + (ArcConstants.ARC_SWEEP_ANGLE / 2f)
        return (angle >= 360f + start || angle <= end) || (angle >= start && angle <= end)
    }
}

