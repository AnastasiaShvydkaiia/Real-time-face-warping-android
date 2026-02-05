package com.example.reflekt.warp.mls

import android.graphics.Bitmap

fun warpWithMLS(
    srcBitmap: Bitmap,
    controlSrc: List<Vec2>,
    controlDst: List<Vec2>
): Bitmap {
    val w = srcBitmap.width
    val h = srcBitmap.height
    val dstBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    val srcPixels = IntArray(w * h)
    val dstPixels = IntArray(w * h)

    srcBitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

    for (y in 0 until h) {
        for (x in 0 until w) {
            val srcPos = mlsRigidWarp(
                x.toFloat(),
                y.toFloat(),
                controlDst,   // inverse mapping
                controlSrc
            )

            val sx = srcPos.x.toInt()
            val sy = srcPos.y.toInt()

            val idx = y * w + x
            dstPixels[idx] =
                if (sx in 0 until w && sy in 0 until h)
                    srcPixels[sy * w + sx]
                else
                    0x00000000
        }
    }

    dstBitmap.setPixels(dstPixels, 0, w, 0, 0, w, h)
    return dstBitmap
}
fun mlsRigidWarp(
    x: Float,
    y: Float,
    src: List<Vec2>,
    dst: List<Vec2>,
    alpha: Float = 1.0f
): Vec2 {

    val v = Vec2(x, y)
    val n = src.size

    val weights = FloatArray(n)
    var wSum = 0f

    val pStar = Vec2(0f, 0f)
    val qStar = Vec2(0f, 0f)

    // --- 1. Weights ---
    for (i in 0 until n) {
        val d = norm2(src[i] - v).coerceAtLeast(1e-6f)
        weights[i] = 1f / Math.pow(d.toDouble(), alpha.toDouble()).toFloat()
        wSum += weights[i]
    }

    // --- 2. Centroids ---
    for (i in 0 until n) {
        val w = weights[i]
        pStar.x += src[i].x * w
        pStar.y += src[i].y * w
        qStar.x += dst[i].x * w
        qStar.y += dst[i].y * w
    }

    pStar.x /= wSum
    pStar.y /= wSum
    qStar.x /= wSum
    qStar.y /= wSum

    // --- 3. Compute rotation only ---
    var a = 0f
    var b = 0f

    for (i in 0 until n) {
        val pi = src[i] - pStar
        val qi = dst[i] - qStar
        val w = weights[i]

        a += w * dot(pi, qi)
        b += w * (pi.x * qi.y - pi.y * qi.x)
    }

    val vRel = v - pStar

    val norm = Math.sqrt((a * a + b * b).toDouble()).toFloat().coerceAtLeast(1e-6f)

    val cos = a / norm
    val sin = b / norm

    val newX = cos * vRel.x - sin * vRel.y + qStar.x
    val newY = sin * vRel.x + cos * vRel.y + qStar.y

    return Vec2(newX, newY)
}


data class Vec2(var x: Float, var y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
}

fun dot(a: Vec2, b: Vec2) = a.x * b.x + a.y * b.y
fun norm2(v: Vec2) = dot(v, v)
