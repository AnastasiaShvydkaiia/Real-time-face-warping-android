package com.example.reflekt.filters

import android.graphics.PointF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

fun babyFace(
    landmarks: List<NormalizedLandmark>,
    width: Int,
    height: Int
): Pair<List<PointF>, List<PointF>> {

    val src = mutableListOf<PointF>()
    val dst = mutableListOf<PointF>()

    fun p(i: Int): PointF =
        PointF(landmarks[i].x() * width, landmarks[i].y() * height)

    fun add(i: Int, dx: Float = 0f, dy: Float = 0f) {
        val s = p(i)
        src += s
        dst += PointF(s.x + dx * width, s.y + dy * height)
    }

    val faceHeight = (landmarks.maxOf { it.y() } - landmarks.minOf { it.y() }) * height
    val faceWidth = (landmarks.maxOf { it.x() } - landmarks.minOf { it.x() }) * width

    // --- Jaw and cheeks (rounder jaw) ---
    val jawLift = faceHeight * 0.09f      // raise jaw upward
    val jawInward = faceWidth * 0.05f     // move jaw inward

    // Key jaw/chin landmarks
    val jawPoints = listOf(
        152 to PointF(0f, -jawLift),           // chin
        127 to PointF(-jawInward, -jawLift),   // left jaw
        356 to PointF(jawInward, -jawLift),    // right jaw
    )
    jawPoints.forEach { (i, d) -> add(i, d.x / width, d.y / height) }

    // --- Eyes scaling  ---
    val leftEyeIndices = listOf(33, 133, 159, 145, 160, 144)
    val rightEyeIndices = listOf(362, 263, 386, 374, 387, 373)
    val eyeScale = 0.9f

    fun scaleEye(indices: List<Int>) {
        val cx = indices.map { p(it).x }.average().toFloat()
        val cy = indices.map { p(it).y }.average().toFloat()
        indices.forEach { i ->
            val original = p(i)
            src += original
            val dx = (original.x - cx) * (eyeScale - 1f)
            val dy = (original.y - cy) * (eyeScale - 1f)
            dst += PointF(original.x - dx, original.y - dy)
        }
    }

    scaleEye(leftEyeIndices)
    scaleEye(rightEyeIndices)

    // --- Nose ---
    val noseUp = faceHeight * 0.045f
    add(4, dy = -noseUp / height)
    add(1, dy = -noseUp * 0.6f / height)

    // --- Mouth ---
    val mouthIn = faceWidth * 0.03f
    val mouthUp = faceHeight * 0.02f
    add(61, dx = mouthIn / width, dy = -mouthUp / height)
    add(291, dx = -mouthIn / width, dy = -mouthUp / height)
    add(13, dy = -mouthUp * 0.6f / height)

    // --- Anchor points ---
    listOf(10, 9, 127, 356, 152).forEach { add(it) }

    return src to dst
}

