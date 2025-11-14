package com.caio.vuzixhello

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection

import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model.Image as RekImage
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startApp()
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startApp()
        }
    }

    private fun startApp() {
        setContent {
            FaceDetectionCameraScreen(
                activity = this,
                executor = cameraExecutor
            )
        }
    }
}

@Composable
fun FaceDetectionCameraScreen(
    activity: ComponentActivity,
    executor: ExecutorService
) {
    val context = LocalContext.current

    var faceDetected by remember { mutableStateOf(false) }
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var awsResult by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context) }

    // frames para estabilizar detecção
    var stableFaceFrames by remember { mutableStateOf(0) }
    var missingFaceFrames by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageCapture = ImageCapture.Builder().build()
        val detector = FaceDetection.getClient()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        var alreadyCaptured = false

        imageAnalysis.setAnalyzer(executor) { imageProxy ->

            if (alreadyCaptured) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return@setAnalyzer
            }

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(inputImage)
                .addOnSuccessListener { faces ->

                    if (faces.isNotEmpty()) {
                        stableFaceFrames++
                        missingFaceFrames = 0
                    } else {
                        missingFaceFrames++
                        stableFaceFrames = 0
                    }

                    if (stableFaceFrames >= 30 && !alreadyCaptured) {
                        alreadyCaptured = true
                        faceDetected = true

                        capturePhoto(
                            context = context,
                            capture = imageCapture,
                            executor = executor
                        ) { bmp ->

                            capturedPhoto = bmp

                            if (bmp != null) {
                                awsResult = "Enviando para AWS…"

                                searchFaceOnAws(
                                    context = context,
                                    bitmap = bmp
                                ) { result ->
                                    awsResult = result
                                }
                            } else {
                                awsResult = "Erro ao capturar foto"
                            }
                        }
                    }

                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            activity,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis,
            imageCapture
        )
    }

    // UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        if (capturedPhoto != null) {
            Box(modifier = Modifier.fillMaxSize()) {

                Image(
                    bitmap = capturedPhoto!!.asImageBitmap(),
                    contentDescription = "Foto capturada",
                    modifier = Modifier.fillMaxSize()
                )

                Text(
                    text = awsResult ?: "Processando…",
                    fontSize = 42.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(20.dp)
                )
            }
            return@Box
        }

        // preview da câmera
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (!faceDetected) {
            Text(
                text = "ANALISANDO…",
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

/* ----------------- FUNÇÕES AUXILIARES ------------------- */

fun capturePhoto(
    context: Context,
    capture: ImageCapture,
    executor: ExecutorService,
    onBitmapReady: (Bitmap?) -> Unit
) {
    val file = File(context.externalMediaDirs.first(), "face_capture.jpg")

    val output = ImageCapture.OutputFileOptions.Builder(file).build()

    capture.takePicture(
        output,
        executor,
        object : ImageCapture.OnImageSavedCallback {

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                onBitmapReady(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                onBitmapReady(null)
            }
        }
    )
}

fun bitmapToJpegByteArray(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return stream.toByteArray()
}

fun searchFaceOnAws(
    context: Context,
    bitmap: Bitmap,
    onResult: (String) -> Unit
) {
    Thread {

        try {
            val provider = CognitoCachingCredentialsProvider(
                context,
                "us-east-1:119930ab-4067-4fbc-ab67-4a818ef4c00c",
                Regions.US_EAST_1
            )

            val rekognition = AmazonRekognitionClient(provider)
            rekognition.setRegion(Region.getRegion(Regions.US_EAST_1))

            val imgBytes = bitmapToJpegByteArray(bitmap)

            val request = SearchFacesByImageRequest()
                .withCollectionId("faceid-vision-aires-dev")
                .withImage(
                    RekImage().withBytes(
                        ByteBuffer.wrap(imgBytes)
                    )
                )
                .withFaceMatchThreshold(80f)
                .withMaxFaces(1)

            val result = rekognition.searchFacesByImage(request)
            val match = result.faceMatches.firstOrNull()

            if (match != null) {
                val external = match.face.externalImageId ?: "Sem External ID"
                onResult("ID: $external")
            } else {
                onResult("Nenhuma correspondência encontrada")
            }

        } catch (e: Exception) {
            onResult("Erro AWS: ${e.message}")
        }

    }.start()
}
