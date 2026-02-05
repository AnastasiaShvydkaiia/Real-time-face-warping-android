package com.example.reflekt.warp.idw

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.pow

fun warpWithIDW(
    bitmap: Bitmap,
    src: List<PointF>,
    dst: List<PointF>,
    alpha: Float = 1.0f
): Bitmap {

    val w = bitmap.width
    val h = bitmap.height
    val out = Bitmap.createBitmap(w, h, bitmap.config)

    for (y in 0 until h) {
        for (x in 0 until w) {

            var sw = 0f
            var dx = 0f
            var dy = 0f

            for (i in src.indices) {
                val px = src[i].x
                val py = src[i].y
                val dist2 = (x - px) * (x - px) + (y - py) * (y - py)
                val wgt = 1f / (dist2 + 1f).pow(alpha)

                sw += wgt
                dx += wgt * (dst[i].x - px)
                dy += wgt * (dst[i].y - py)
            }

            val sx = (x - dx / sw).toInt().coerceIn(0, w - 1)
            val sy = (y - dy / sw).toInt().coerceIn(0, h - 1)

            out.setPixel(x, y, bitmap.getPixel(sx, sy))
        }
    }
    return out
}

