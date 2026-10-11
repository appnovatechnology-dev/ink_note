package com.example.inknote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Universally recognizable bespoke vector icon for the physical wedge block eraser.
 * Incorporates the classic beveled eraser block, sleeve wrapper collar,
 * and horizontal baseline stroke mark being erased.
 * All paths use Color.White so Jetpack Compose Icon tinting applies cleanly and accurately.
 */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EraserIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Base horizontal surface stroke line being erased under the wedge
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.5f, 20.5f)
            lineTo(21.5f, 20.5f)
        }

        // Main angled rectangular eraser rubber wedge (bottom tip resting on ground)
        path(
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(7.2f, 20.5f)
            lineTo(3.9f, 17.2f)
            curveTo(3.1f, 16.4f, 3.1f, 15.1f, 3.9f, 14.3f)
            lineTo(8.2f, 10.0f)
            lineTo(15.0f, 16.8f)
            lineTo(10.7f, 20.5f)
            close()
        }

        // Eraser holder sleeve wrapper (paper jacket covering upper block)
        path(
            fill = SolidColor(Color.White.copy(alpha = 0.55f)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.2f, 10.0f)
            lineTo(13.0f, 5.2f)
            curveTo(13.8f, 4.4f, 15.1f, 4.4f, 15.9f, 5.2f)
            lineTo(19.3f, 8.6f)
            curveTo(20.1f, 9.4f, 20.1f, 10.7f, 19.3f, 11.5f)
            lineTo(15.0f, 16.8f)
            close()
        }

        // Distinct sleeve edge band dividing line
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.2f, 10.0f)
            lineTo(15.0f, 16.8f)
        }
    }.build()
}
