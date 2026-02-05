package com.example.reflekt.filters

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.atan2
import kotlin.math.min

fun DrawScope.drawGlasses(
    landmarks: List<NormalizedLandmark>,
    glassesImageBitmap: ImageBitmap,
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Float,   // imageProxy.width
    imageHeight: Float,  // imageProxy.height
    isFrontCamera: Boolean,
    imageRotationDegrees: Int = 90
) {

    // ---- 1. Correct frame dimensions AFTER rotation ----
    val frameW: Float
    val frameH: Float

    if (imageRotationDegrees == 90 || imageRotationDegrees == 270) {
        frameW = imageHeight
        frameH = imageWidth
    } else {
        frameW = imageWidth
        frameH = imageHeight
    }

    // ---- 2. Scaling (same as PreviewView) ----
    val scale = min(canvasWidth / frameW, canvasHeight / frameH)
    val offsetX = (canvasWidth - frameW * scale) / 2f
    val offsetY = (canvasHeight - frameH * scale) / 2f

    // ---- 3. Landmark mapping  ----
    fun mapLandmark(lm: NormalizedLandmark): Offset {
        var x = lm.x() * frameW
        var y = lm.y() * frameH

        x = x * scale + offsetX
        y = y * scale + offsetY

        if (isFrontCamera) {
            x = canvasWidth - x
        }
        return Offset(x, y)
    }

    // ---- 4. FOR DEBUG: draw ALL landmarks ----
    //for (lm in landmarks) {
    //    val p = mapLandmark(lm)
    //    drawCircle(
    //        color = Color.Green,
    //        radius = 2f,
    //        center = p
    //    )
    //}

    // ---- 5. Use landmarks for glasses ----
    val leftEye = mapLandmark(landmarks[468]) //468 33
    val rightEye = mapLandmark(landmarks[473]) //473 263
    val nose = mapLandmark(landmarks[1])

    val eyeCenter = Offset(
        (leftEye.x + rightEye.x) / 2f,
        (leftEye.y + rightEye.y) / 2f
    )

    // ---- Angle between vertical line and (eyeCenter -> nose) ----
    val fx = nose.x - eyeCenter.x
    val fy = nose.y - eyeCenter.y

    // Vertical reference (pointing DOWN the face)
    val vx = 0f
    val vy = 1f

    val dot = vx * fx + vy * fy
    val cross = vx * fy - vy * fx

    var angleDeg = Math.toDegrees(atan2(cross.toDouble(), dot.toDouble())).toFloat()

    angleDeg *= 0.8f // dampen rotation slightly (looks more natural)

    val eyeDist = kotlin.math.hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
    val glassesWidth = eyeDist * 2.2f
    val glassesHeight = glassesWidth * glassesImageBitmap.height / glassesImageBitmap.width

    // ---- 6. Draw glasses ----
    drawContext.canvas.apply {
        save()
        translate(eyeCenter.x, eyeCenter.y)
        rotate(angleDeg)
        drawImage(
            glassesImageBitmap,
            dstOffset = IntOffset(
                (-glassesWidth / 2).toInt(),
                (-glassesHeight / 2).toInt()
            ),
            dstSize = IntSize(
                glassesWidth.toInt(),
                glassesHeight.toInt()
            )
        )
        restore()
    }
}