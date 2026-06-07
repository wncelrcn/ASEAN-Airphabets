package com.example.airphabets.presentation.components

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airphabets.presentation.utils.ArcConstants
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a circular arc pill with curved text for a mode button
 */
fun DrawScope.drawModeArc(
    centerX: Float,
    centerY: Float,
    radius: Float,
    centerAngle: Float,
    text: String,
    color: Color,
    density: Density
) {
    val startAngle = centerAngle - (ArcConstants.ARC_SWEEP_ANGLE / 2f)

    // Draw the arc background
    val arcPath = Path()
    arcPath.addArc(
        oval = androidx.compose.ui.geometry.Rect(
            left = centerX - radius,
            top = centerY - radius,
            right = centerX + radius,
            bottom = centerY + radius
        ),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = ArcConstants.ARC_SWEEP_ANGLE
    )

    drawPath(
        path = arcPath,
        color = color,
        style = Stroke(
            width = ArcConstants.ARC_STROKE_WIDTH.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    )

    // Draw curved text along the arc
    drawCurvedText(
        text = text.reversed(),
        centerX = centerX,
        centerY = centerY,
        radius = radius,
        startAngle = startAngle,
        sweepAngle = ArcConstants.ARC_SWEEP_ANGLE,
        density = density
    )
}

/**
 * Draws text curved along a circular arc path
 */
private fun DrawScope.drawCurvedText(
    text: String,
    centerX: Float,
    centerY: Float,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    density: Density
) {
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = with(density) { ArcConstants.TEXT_SIZE_SP.sp.toPx() }
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    val arcStrokeWidth = ArcConstants.ARC_STROKE_WIDTH_DP.dp.toPx()
    val textRadius = radius + (arcStrokeWidth / 4f)

    // Calculate character widths and spacing
    val charWidths = FloatArray(text.length)
    textPaint.getTextWidths(text, charWidths)
    val totalTextWidth = charWidths.sum() * ArcConstants.LETTER_SPACING_FACTOR
    val textArcAngle = (totalTextWidth / (2f * PI.toFloat() * textRadius)) * 360f
    val textStartAngle = startAngle + sweepAngle - (sweepAngle - textArcAngle) / 2f

    // Draw each character along the curve
    var currentAngle = textStartAngle
    for (i in text.indices.reversed()) {
        val char = text[i].toString()
        val charWidth = charWidths[i] * ArcConstants.LETTER_SPACING_FACTOR
        val charAngle = (charWidth / (2f * PI.toFloat() * textRadius)) * 360f

        currentAngle -= charAngle / 2f

        val angleInRadians = Math.toRadians(currentAngle.toDouble())
        val x = centerX + textRadius * cos(angleInRadians).toFloat()
        val y = centerY + textRadius * sin(angleInRadians).toFloat()

        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.translate(x, y)
        drawContext.canvas.nativeCanvas.rotate(currentAngle - 90f)
        drawContext.canvas.nativeCanvas.drawText(char, 0f, 0f, textPaint)
        drawContext.canvas.nativeCanvas.restore()

        currentAngle -= charAngle / 2f
    }
}

