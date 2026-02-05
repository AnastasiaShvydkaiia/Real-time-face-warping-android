package com.example.reflekt

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.mediapipe.framework.image.MPImage
import java.util.Collections
import com.example.reflekt.filters.drawGlasses

// Filter types
enum class FilterType {NONE, GLASSES, SAD_FACE, BABY_FACE }

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var faceLandmarkerResult by remember { mutableStateOf<FaceLandmarkerResult?>(null) }
    val previewView = remember { PreviewView(context) }.apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    var imageAnalysisWidth by remember { mutableStateOf(0) }
    var imageAnalysisHeight by remember { mutableStateOf(0) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf(FilterType.NONE) }
    var currentFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFlash by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    //  Performance metrics
    var lastFrameTimestamp by remember { mutableStateOf(0L) }
    var frameTimeMs by remember { mutableStateOf(0.0) }
    var fps by remember { mutableStateOf(0.0) }
    var memoryUsageMB by remember { mutableStateOf(0L) }
    val runtime = Runtime.getRuntime()
    fun getUsedMemoryMB(): Long {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
    val mpStartTimes = remember {Collections.synchronizedMap(mutableMapOf<Long, Long>())}

    var mpLatencyMs by remember { mutableStateOf(0.0) }

    // MediaPipe Face Landmarker initialization
    val faceLandmarker = remember {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result: FaceLandmarkerResult, _: MPImage ->
                    val timestampMs = result.timestampMs()

                    val startNs = mpStartTimes.remove(timestampMs)
                    if (startNs != null) {
                        mpLatencyMs = (System.nanoTime() - startNs) / 1_000_000.0
                    }


                    val faceCount = result.faceLandmarks().size
                    Log.d("CameraScreen", "Face landmarks detected: $faceCount faces")
                    if (faceCount > 0) {
                        val landmarkCount = result.faceLandmarks()[0].size
                        Log.d("CameraScreen", "First face has $landmarkCount landmarks")
                    }

                    faceLandmarkerResult = result
                }
                .build()

            FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e("CameraScreen", "Failed to initialize FaceLandmarker", e)
            null
        }
    }

    // Cleanup face landmarker on dispose
    DisposableEffect(faceLandmarker) {
        onDispose {
            faceLandmarker?.close()
        }
    }

    // Initialize camera when PreviewView is ready or camera changes
    LaunchedEffect(previewView, isFrontCamera) {
        val cameraProvider = ProcessCameraProvider
            .getInstance(context)
            .get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                @androidx.camera.core.ExperimentalGetImage
                it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    imageAnalysisWidth = imageProxy.width
                    imageAnalysisHeight = imageProxy.height
                    processImageProxy(imageProxy, faceLandmarker,mpStartTimes) { bitmap ->
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            currentFrameBitmap = bitmap
                        }
                    }
                }
            }

        imageCapture = ImageCapture.Builder().build()

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
                imageCapture)
            while (true) {
                memoryUsageMB = getUsedMemoryMB()
                delay(1000)
            }
        } catch (e: Exception) {
            Log.e("CameraScreen", "Use case binding failed", e)
        }
    }

    // Load glasses image bitmap
    val glassesBitmap = remember {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.glasses)?.asImageBitmap()
        } catch (e: Exception) {
            Log.e("CameraScreen", "Failed to load glasses image", e)
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Face landmarks and filters overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageAnalysisWidth == 0 || imageAnalysisHeight == 0) return@Canvas

            val now = System.nanoTime()
            if (lastFrameTimestamp != 0L) {
                frameTimeMs = (now - lastFrameTimestamp) / 1_000_000.0
                fps = 1000.0 / frameTimeMs
            }
            lastFrameTimestamp = now


            val frameAspect = imageAnalysisWidth.toFloat() / imageAnalysisHeight
            val viewAspect = size.width / size.height

            val previewWidth: Float
            val previewHeight: Float

            if (viewAspect > frameAspect) {
                previewHeight = size.height
                previewWidth = frameAspect * previewHeight
            } else {
                previewWidth = size.width
                previewHeight = previewWidth / frameAspect
            }

            faceLandmarkerResult?.let { result ->
                result.faceLandmarks().forEach { faceLandmarks ->
                    if (faceLandmarks.size >= 468) {

                        // Apply filters
                        when (selectedFilter) {
                            FilterType.GLASSES -> {
                                if (
                                    glassesBitmap != null &&
                                    imageAnalysisWidth > 0 &&
                                    imageAnalysisHeight > 0
                                ) {
                                    drawGlasses(
                                        landmarks = faceLandmarks,
                                        glassesImageBitmap = glassesBitmap,
                                        canvasWidth = size.width,
                                        canvasHeight = size.height,
                                        imageWidth = imageAnalysisWidth.toFloat(),
                                        imageHeight = imageAnalysisHeight.toFloat(),
                                        isFrontCamera = isFrontCamera
                                    )
                                }
                            }
                            FilterType.SAD_FACE -> {
                                currentFrameBitmap?.let { bmp ->
                                    val warped= com.example.reflekt.warp.warpFace(
                                        bmp,
                                        faceLandmarks,
                                        com.example.reflekt.warp.WarpMethod.MLS,  // Change warping method here
                                        com.example.reflekt.warp.FilterType.SAD
                                    )

                                    val imageBitmap = warped.asImageBitmap()

                                    drawContext.canvas.apply {
                                        save()

                                        val scale = size.width / bmp.width.toFloat()
                                        val scaledHeight = bmp.height * scale
                                        val offsetY = (size.height - scaledHeight) / 2f

                                        if (isFrontCamera) {
                                            translate(size.width, offsetY)
                                            scale(-scale, scale)
                                        } else {
                                            translate(0f, offsetY)
                                            scale(scale, scale)
                                        }

                                        // Draw frame
                                        drawImage(imageBitmap, Offset.Zero)

                                        restore()
                                    }
                                }
                            }
                            FilterType.BABY_FACE -> {
                                currentFrameBitmap?.let { bmp ->

                                    val warped= com.example.reflekt.warp.warpFace(
                                        bmp,
                                        faceLandmarks,
                                        com.example.reflekt.warp.WarpMethod.IDW,
                                        com.example.reflekt.warp.FilterType.BABY
                                    )

                                    val imageBitmap = warped.asImageBitmap()


                                    drawContext.canvas.apply {
                                        save()

                                        val scale = size.width / bmp.width.toFloat()
                                        val scaledHeight = bmp.height * scale
                                        val offsetY = (size.height - scaledHeight) / 2f

                                        if (isFrontCamera) {
                                            translate(size.width, offsetY)
                                            scale(-scale, scale)
                                        } else {
                                            translate(0f, offsetY)
                                            scale(scale, scale)
                                        }

                                        // Draw original frame
                                        drawImage(imageBitmap, Offset.Zero)

                                        restore()
                                    }
                                }
                            }
                            FilterType.NONE -> {  }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(8.dp)
        ) {
            Text(
                text = "FPS: ${"%.1f".format(fps)}",
                color = Color.Green,
                fontSize = 18.sp
            )
            Text(
                text = "ms/frame: ${"%.1f".format(frameTimeMs)}",
                color = Color.Yellow,
                fontSize = 18.sp
            )
            Text(
                text = "Memory: $memoryUsageMB MB",
                color = Color.Cyan,
                fontSize = 18.sp
            )
            Text(
                text = "MP Latency: ${"%.1f".format(mpLatencyMs)} ms",
                color = Color.Magenta,
                fontSize = 18.sp
            )

        }

        // Camera flip button (top right corner)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            FloatingActionButton(
                onClick = {
                    isFrontCamera = !isFrontCamera
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera_flip),
                    contentDescription = "Switch Camera",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }

        // Filter buttons (above the camera button)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filter selection buttons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glasses filter button
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selectedFilter == FilterType.GLASSES)
                                    Color.White.copy(alpha = 0.3f)
                                else
                                    Color.Black.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = if (selectedFilter == FilterType.GLASSES) 2.dp else 1.dp,
                                color = if (selectedFilter == FilterType.GLASSES)
                                    Color.White.copy(alpha = 0.8f)
                                else
                                    Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedFilter = if (selectedFilter == FilterType.GLASSES) FilterType.NONE else FilterType.GLASSES
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "👓",
                            fontSize = 24.sp
                        )
                    }

                    // Sad face filter button
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selectedFilter == FilterType.SAD_FACE)
                                    Color.White.copy(alpha = 0.3f)
                                else
                                    Color.Black.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = if (selectedFilter == FilterType.SAD_FACE) 2.dp else 1.dp,
                                color = if (selectedFilter == FilterType.SAD_FACE)
                                    Color.White.copy(alpha = 0.8f)
                                else
                                    Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedFilter = if (selectedFilter == FilterType.SAD_FACE) FilterType.NONE else FilterType.SAD_FACE
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "😟",
                            fontSize = 24.sp
                        )
                    }

                    // Baby face filter button
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selectedFilter == FilterType.BABY_FACE)
                                    Color.White.copy(alpha = 0.3f)
                                else
                                    Color.Black.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = if (selectedFilter == FilterType.BABY_FACE) 2.dp else 1.dp,
                                color = if (selectedFilter == FilterType.BABY_FACE)
                                    Color.White.copy(alpha = 0.8f)
                                else
                                    Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedFilter = if (selectedFilter == FilterType.BABY_FACE) FilterType.NONE else FilterType.BABY_FACE
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "👶",
                            fontSize = 24.sp
                        )
                    }
                }

                // Camera and gallery buttons row
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery button
                    FloatingActionButton(
                        onClick = {
                            openGallery(context)
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_gallery),
                            contentDescription = "Open Gallery",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }

                    // Capture button
                    FloatingActionButton(
                        onClick = {
                            currentFrameBitmap?.let { bmp ->
                                // Trigger flash
                                triggerFlash(scope) { showFlash = it }
                                // Save photo
                                saveBitmapToMediaStore(context, bmp) { success ->
                                    if (success) Log.d("Camera", "Saved photo with filter!")
                                    else Log.d("Camera", "Failed to save photo")
                                }
                            }
                        }
                    )
                    {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera_capture),
                            contentDescription = "Capture Photo",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (showFlash) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawRect(Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

//--------------------------------------------------------------
@androidx.camera.core.ExperimentalGetImage
private fun processImageProxy(
    imageProxy: ImageProxy,
    faceLandmarker: FaceLandmarker?,
    mpStartTimes: MutableMap<Long, Long>,
    updateCurrentBitmap: (Bitmap) -> Unit
) {
    if (faceLandmarker == null) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage != null && mediaImage.format == ImageFormat.YUV_420_888) {
        try {
            val bitmap = yuv420888ToBitmap(mediaImage)
            if (bitmap != null) {
                val rotation = imageProxy.imageInfo.rotationDegrees
                val rotatedBitmap = if (rotation != 0) rotateBitmap(bitmap, rotation.toFloat()) else bitmap
                updateCurrentBitmap(rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true))
                val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                // MediaPipe metrics
                val timestampMs = System.currentTimeMillis()
                val startNs = System.nanoTime()
                mpStartTimes[timestampMs] = startNs
                //faceLandmarker.detectAsync(mpImage, timestampMs)

                try { faceLandmarker.detectAsync(mpImage, System.currentTimeMillis()) }
                catch (e: Exception) { Log.e("CameraScreen", "Error in detectAsync: ${e.message}", e) }
            }
        } catch (e: Exception) { Log.e("CameraScreen", "Error processing image", e) }
        finally { imageProxy.close() }
    } else { imageProxy.close() }
}

private fun yuv420888ToBitmap(mediaImage: android.media.Image): Bitmap? {
    try {
        val yBuffer = mediaImage.planes[0].buffer
        val uBuffer = mediaImage.planes[1].buffer
        val vBuffer = mediaImage.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error converting YUV to Bitmap", e)
        return null
    }
}

private fun saveBitmapToMediaStore(
    context: Context,
    bitmap: Bitmap,
    onSaved: (Boolean) -> Unit
) {
    try {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Reflekt")
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let { imageUri ->
            context.contentResolver.openOutputStream(imageUri)?.use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
                outStream.flush()
            }
            onSaved(true)
        } ?: run { onSaved(false) }

    } catch (e: Exception) {
        Log.e("CameraScreen", "Failed to save photo: ${e.message}", e)
        onSaved(false)
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply {
        postRotate(degrees)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun openGallery(context: Context) {
    val intent = Intent(
        Intent.ACTION_PICK,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    )
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

private fun triggerFlash(
    scope: kotlinx.coroutines.CoroutineScope,
    setFlash: (Boolean) -> Unit
) {
    scope.launch {
        setFlash(true)
        delay(60) // blink duration
        setFlash(false)
    }
}




