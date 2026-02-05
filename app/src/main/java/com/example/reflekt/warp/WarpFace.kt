package com.example.reflekt.warp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.reflekt.warp.idw.warpWithIDW
import com.example.reflekt.filters.*
import com.example.reflekt.warp.mls.warpWithMLS
import com.example.reflekt.warp.pat.FaceMeshTriangles
import com.example.reflekt.warp.pat.warpWithPAT
import kotlin.math.min
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
enum class WarpMethod { IDW, PAT, MLS }
enum class FilterType {SAD, BABY}

fun warpFace(
    bitmap: Bitmap,
    faceLandmarks: List<NormalizedLandmark>,
    method: WarpMethod,
    filter: FilterType

    ): Bitmap {

    return when (method) {
        WarpMethod.IDW -> {
            // 1. Compute warp points
            val (srcPoints, dstPoints) = when (filter) {
                FilterType.SAD -> {sadFace(faceLandmarks, bitmap.width, bitmap.height)}
                FilterType.BABY -> {babyFace(faceLandmarks, bitmap.width, bitmap.height)}
            }

            // 2. Crop the face region
            val minX = faceLandmarks.minOf { it.x() * bitmap.width }.toInt()
            val maxX = faceLandmarks.maxOf { it.x() * bitmap.width }.toInt()
            val minY = faceLandmarks.minOf { it.y() * bitmap.height }.toInt()
            val maxY = faceLandmarks.maxOf { it.y() * bitmap.height }.toInt()

            val faceHeight = maxY-minY+10 //padding
            val faceWidth = maxX - minX+10// padding
            val faceBitmap = Bitmap.createBitmap(bitmap, minX, minY, faceWidth, faceHeight)

            // 3. Adjust landmarks relative to cropped bitmap
            val croppedSrc = srcPoints.map { PointF(it.x - minX, it.y - minY) }
            val croppedDst = dstPoints.map { PointF(it.x - minX, it.y - minY) }

            // 4. Warp the face
            val warpedFace = warpWithIDW(faceBitmap, croppedSrc, croppedDst)

            // 5. Create and apply feather mask
            val mask = createFeatherMask(faceWidth, faceHeight)
            val featheredFace = applyFeatherMask(warpedFace, mask)
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)

            canvas.drawBitmap(
                featheredFace,
                minX.toFloat(),
                minY.toFloat(),
                null
            )
            result
        }
        WarpMethod.PAT -> {
            val srcPts = faceLandmarks.map {PointF(it.x() * bitmap.width, it.y() * bitmap.height)}
            val dstPts = sadFacePAT(faceLandmarks, bitmap.width, bitmap.height)
            warpWithPAT(bitmap, srcPts, dstPts, FaceMeshTriangles.TRIANGLES)
        }
        WarpMethod.MLS -> {
            val (srcPts, dstPts) = sadFaceMLS(faceLandmarks, bitmap.width, bitmap.height)
            warpWithMLS(bitmap, srcPts, dstPts)
        }
    }
}

fun createFeatherMask(
    width: Int,
    height: Int,
    feather: Float = 0.05f //blending
): Bitmap {
    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(mask)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val gradient = RadialGradient(
        width / 2f,
        height / 2f,
        min(width, height)/1f,
        intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
        floatArrayOf(1f - feather, 1f),
        Shader.TileMode.CLAMP
    )

    paint.shader = gradient
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    return mask
}

fun applyFeatherMask(
    warpedFace: Bitmap,
    mask: Bitmap
): Bitmap {

    val result = Bitmap.createBitmap(
        warpedFace.width,
        warpedFace.height,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // draw warped face
    canvas.drawBitmap(warpedFace, 0f, 0f, paint)

    // apply mask
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    canvas.drawBitmap(mask, 0f, 0f, paint)
    paint.xfermode = null

    return result
}

