package com.example.reflekt.warp.pat

import android.graphics.Bitmap
import android.graphics.PointF

private fun barycentric(
    p: PointF,
    a: PointF,
    b: PointF,
    c: PointF
): Triple<Float, Float, Float> {
    val denom = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
    val w1 = ((b.y - c.y) * (p.x - c.x) + (c.x - b.x) * (p.y - c.y)) / denom
    val w2 = ((c.y - a.y) * (p.x - c.x) + (a.x - c.x) * (p.y - c.y)) / denom
    val w3 = 1f - w1 - w2
    return Triple(w1, w2, w3)
}
private fun insideTriangle(w1: Float, w2: Float, w3: Float): Boolean {
    return w1 >= 0f && w2 >= 0f && w3 >= 0f
}

fun warpWithPAT(
    bitmap: Bitmap,
    srcPts: List<PointF>,
    dstPts: List<PointF>,
    triangles: IntArray
): Bitmap {

    val w = bitmap.width
    val h = bitmap.height

    val out = Bitmap.createBitmap(w, h, bitmap.config)

    for (t in triangles.indices step 3) {
        val i0 = triangles[t]
        val i1 = triangles[t + 1]
        val i2 = triangles[t + 2]

        val s0 = srcPts[i0]
        val s1 = srcPts[i1]
        val s2 = srcPts[i2]

        val d0 = dstPts[i0]
        val d1 = dstPts[i1]
        val d2 = dstPts[i2]

        val minX = maxOf(
            0,
            minOf(d0.x, d1.x, d2.x).toInt()
        )
        val maxX = minOf(
            w - 1,
            maxOf(d0.x, d1.x, d2.x).toInt()
        )
        val minY = maxOf(
            0,
            minOf(d0.y, d1.y, d2.y).toInt()
        )
        val maxY = minOf(
            h - 1,
            maxOf(d0.y, d1.y, d2.y).toInt()
        )

        for (y in minY..maxY) {
            for (x in minX..maxX) {

                val p = PointF(x.toFloat(), y.toFloat())
                val (w1, w2, w3) = barycentric(p, d0, d1, d2)

                if (!insideTriangle(w1, w2, w3)) continue

                val sx = w1 * s0.x + w2 * s1.x + w3 * s2.x

                val sy = w1 * s0.y + w2 * s1.y + w3 * s2.y

                val ix = sx.toInt().coerceIn(0, w - 1)
                val iy = sy.toInt().coerceIn(0, h - 1)

                out.setPixel(x, y, bitmap.getPixel(ix, iy))
            }
        }
    }

    return out
}
