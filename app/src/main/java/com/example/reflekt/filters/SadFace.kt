package com.example.reflekt.filters

import android.graphics.PointF
import com.example.reflekt.warp.mls.Vec2
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

fun sadFace(
    landmarks: List<NormalizedLandmark>,
    width: Int,
    height: Int
): Pair<List<PointF>, List<PointF>> {

    val src = mutableListOf<PointF>()
    val dst = mutableListOf<PointF>()

    val faceHeight = (landmarks.maxOf { it.y() } - landmarks.minOf { it.y() }) * height

    val mouthCornerDown = faceHeight * 0.035f

    val innerBrowUp = faceHeight * 0.035f
    val outerBrowDown = faceHeight * 0.02f

    fun p(i: Int) = PointF(
        landmarks[i].x() * width,
        landmarks[i].y() * height
    )

    fun add(i: Int, dx: Float = 0f, dy: Float = 0f) {
        val s = p(i)
        src += s
        dst += PointF(s.x + dx, s.y + dy)
    }

    // Mouth corners DOWN
    listOf(61, 291).forEach {
        add(it, dy = mouthCornerDown)
    }

    // Inner brows UP
    listOf(107, 336).forEach {
        add(it, dy = -innerBrowUp)
    }

    // Outer brows DOWN
    listOf(70, 300).forEach {
        add(it, dy = outerBrowDown)
    }

    listOf(
        1,       // nose tip
        33, 133, //inner eye corners
    ).forEach {
        add(it)
    }

    return src to dst
}

fun sadFacePAT(
    landmarks: List<NormalizedLandmark>,
    width: Int,
    height: Int
): List<PointF> {

    val pts = landmarks.map {
        PointF(it.x() * width, it.y() * height)
    }.toMutableList()

    val faceHeight = (landmarks.maxOf { it.y() } - landmarks.minOf { it.y() }) * height

    val mouthDown = faceHeight * 0.035f
    val browUp = faceHeight * 0.035f

    fun move(i: Int, dx: Float = 0f, dy: Float = 0f) {
        pts[i] = PointF(pts[i].x + dx, pts[i].y + dy)
    }

    // Mouth corners down
    listOf(61, 291).forEach { move(it, dy = mouthDown) }

    // Mouth edges
    listOf(78, 308).forEach { move(it, dy = mouthDown * 0.7f) }

    // Inner brows up
    listOf(107, 336).forEach { move(it, dy = -browUp) }

    return pts
}

fun sadFaceMLS(
    landmarks: List<NormalizedLandmark>,
    width: Int,
    height: Int
): Pair<List<Vec2>, List<Vec2>> {

    val src = mutableListOf<Vec2>()
    val dst = mutableListOf<Vec2>()

    val faceHeight = (landmarks.maxOf { it.y() } - landmarks.minOf { it.y() }) * height

    val mouthCornerDown = faceHeight * 0.035f
    val innerBrowUp = faceHeight * 0.035f
    val outerBrowDown = faceHeight * 0.02f

    fun p(i: Int) = Vec2(
        landmarks[i].x() * width,
        landmarks[i].y() * height
    )

    fun add(i: Int, dx: Float = 0f, dy: Float = 0f) {
        val s = p(i)
        src += s
        dst += Vec2(s.x + dx, s.y + dy)
    }

    // Mouth corners DOWN
    listOf(61, 291).forEach { add(it, dy = mouthCornerDown) }

    // Mouth edges slightly down
    listOf(78, 308).forEach { add(it, dy = mouthCornerDown * 0.7f) }

    // Inner brows UP
    listOf(107, 336).forEach { add(it, dy = -innerBrowUp) }

    // Outer brows DOWN
    listOf(70, 300).forEach { add(it, dy = outerBrowDown) }

    // Anchor points
    listOf(
        1,       // nose tip
        33, 133, //inner eye corners
    ).forEach {
        add(it)
    }

    return src to dst
}