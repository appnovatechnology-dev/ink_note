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
 * Custom bespoke vector icon for the Lasso selection tool (looping lasso rope with knot & tail).
 * Uses Color.White so Jetpack Compose Icon tinting applies accurately in both active & inactive states.
 */
val LassoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LassoIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Lasso loop
        path(
            fill = SolidColor(Color.White.copy(alpha = 0.12f)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3.5f)
            curveTo(6.8f, 3.5f, 3.5f, 7.2f, 3.5f, 11.2f)
            curveTo(3.5f, 15.2f, 7.2f, 18.2f, 12f, 18.2f)
            curveTo(16.8f, 18.2f, 20.5f, 15.2f, 20.5f, 11.2f)
            curveTo(20.5f, 7.2f, 17.2f, 3.5f, 12f, 3.5f)
            close()
        }
        // Lasso knot
        path(
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(10.5f, 18.2f)
            curveTo(10.5f, 17.4f, 13.5f, 17.4f, 13.5f, 18.2f)
            curveTo(13.5f, 19.0f, 10.5f, 19.0f, 10.5f, 18.2f)
            close()
        }
        // Lasso rope tails
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11.2f, 18.6f)
            lineTo(8.5f, 21.8f)
            moveTo(12.8f, 18.6f)
            lineTo(15.2f, 21.8f)
        }
    }.build()
}

/**
 * Custom bespoke vector icon for the Shape tool (overlapping Circle and Square geometric shapes).
 */
val GeometricShapesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GeometricShapesIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Circle shape
        path(
            fill = SolidColor(Color.White.copy(alpha = 0.2f)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 9f)
            curveTo(14f, 11.76f, 11.76f, 14f, 9f, 14f)
            curveTo(6.24f, 14f, 4f, 11.76f, 4f, 9f)
            curveTo(4f, 6.24f, 6.24f, 4f, 9f, 4f)
            curveTo(11.76f, 4f, 14f, 6.24f, 14f, 9f)
            close()
        }
        // Square shape behind/overlapping
        path(
            fill = SolidColor(Color.White.copy(alpha = 0.35f)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(10.5f, 10.5f)
            lineTo(20f, 10.5f)
            lineTo(20f, 20f)
            lineTo(10.5f, 20f)
            close()
        }
    }.build()
}

/**
 * Custom bespoke vector icon for the Pen tool (classic fountain pen nib).
 */
val FountainPenIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FountainPenIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Pen nib barrel & tip
        path(
            fill = SolidColor(Color.White.copy(alpha = 0.35f)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 21f)
            lineTo(8.5f, 19.5f)
            lineTo(19f, 9f)
            lineTo(15f, 5f)
            lineTo(4.5f, 15.5f)
            close()
        }
        // Collar line & nib slit
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 8f)
            lineTo(16f, 12f)
            moveTo(3f, 21f)
            lineTo(7.5f, 16.5f)
        }
    }.build()
}

/**
 * Custom bespoke vector icon for the Highlighter tool (wide chisel marker tip).
 */
val HighlighterMarkerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HighlighterMarkerIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Marker barrel
        path(
            fill = SolidColor(Color.White.copy(alpha = 0.45f)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.5f, 3f)
            lineTo(15.5f, 3f)
            lineTo(16.5f, 12.5f)
            lineTo(7.5f, 12.5f)
            close()
        }
        // Chisel tip angled cut
        path(
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.5f, 12.5f)
            lineTo(10.5f, 21f)
            lineTo(14.5f, 18.2f)
            lineTo(15.5f, 12.5f)
            close()
        }
    }.build()
}

/**
 * Custom bespoke vector icon for the Laser Pointer tool (emitting laser stylus beam rays).
 */
val LaserPointerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LaserPointerIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Laser stylus body
        path(
            fill = SolidColor(Color.White.copy(alpha = 0.35f)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 20f)
            lineTo(12f, 12f)
            lineTo(14f, 14f)
            lineTo(6f, 22f)
            close()
        }
        // Laser emission rays & target spark
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13.5f, 11.5f)
            lineTo(15.5f, 9.5f)
            moveTo(18f, 7f)
            lineTo(22f, 3f)
            moveTo(18.5f, 11.5f)
            lineTo(22f, 11.5f)
            moveTo(12.5f, 5.5f)
            lineTo(12.5f, 2f)
        }
    }.build()
}
