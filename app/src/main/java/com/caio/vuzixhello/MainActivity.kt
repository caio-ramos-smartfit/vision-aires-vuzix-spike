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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model.Image as RekImage
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest
import com.caio.vuzixhello.network.BioritmoApi
import com.caio.vuzixhello.network.Person
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            FaceRecognitionApp(
                activity = this,
                executor = cameraExecutor
            )
        }
    }
}

@Composable
fun FaceRecognitionApp(activity: ComponentActivity, executor: ExecutorService) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }

    var personInfo by remember { mutableStateOf<Person?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("ANALISANDO...") }
    var showCamera by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val imageCapture = ImageCapture.Builder().build()
        val detector = FaceDetection.getClient()

        var stableFaceFrames = 0
        var alreadyCaptured = false

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            if (alreadyCaptured) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return@setAnalyzer
            }

            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) stableFaceFrames++ else stableFaceFrames = 0

                    if (stableFaceFrames >= 30 && !alreadyCaptured) {
                        alreadyCaptured = true
                        isLoading = true
                        statusMessage = "Rosto capturado. Processando..."

                        capturePhoto(imageCapture, executor) { bmp ->
                            if (bmp != null) {
                                coroutineScope.launch {
                                    try {
                                        statusMessage = "Buscando na AWS..."
                                        val rekognitionId = searchFaceOnAws(context, bmp)

                                        if (rekognitionId != null) {
                                            statusMessage = "Buscando dados do aluno..."
                                            val response = BioritmoApi.retrofitService.getPersonInfo(rekognitionId)
                                            personInfo = response.person
                                            showCamera = false // Hide camera and show info
                                        } else {
                                            statusMessage = "Rosto não encontrado."
                                            // Reset to try again
                                            alreadyCaptured = false
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "Erro: ${e.message}"
                                        alreadyCaptured = false
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                statusMessage = "Erro ao capturar foto."
                                alreadyCaptured = false
                                isLoading = false
                            }
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis, imageCapture)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showCamera) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(text = statusMessage, fontSize = 24.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        } else {
            personInfo?.let { BioritmoInfoScreen(person = it) }
        }
    }
}

@Composable
fun BioritmoInfoScreen(person: Person) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = person.name, color = Color.Yellow, fontSize = 40.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Programa: ${person.program_name ?: "Não informado"}", color = Color.Yellow, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Unidade: ${person.location?.name ?: "Não informada"}", color = Color.Yellow, fontSize = 24.sp)
        }
    }
}

suspend fun searchFaceOnAws(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
    try {
        val provider = CognitoCachingCredentialsProvider(
            context,
            "us-east-1:119930ab-4067-4fbc-ab67-4a818ef4c00c", // ADICIONE SEU IDENTITY POOL ID
            Regions.US_EAST_1
        )
        val rekognition = AmazonRekognitionClient(provider).apply {
            setRegion(Region.getRegion(Regions.US_EAST_1))
        }
        val imgBytes = ByteArrayOutputStream().run { 
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, this)
            toByteArray()
        }
        val request = SearchFacesByImageRequest()
            .withCollectionId("faceid-vision-aires-dev") // ADICIONE SEU COLLECTION ID
            .withImage(RekImage().withBytes(ByteBuffer.wrap(imgBytes)))
            .withFaceMatchThreshold(80f)
            .withMaxFaces(1)

        val result = rekognition.searchFacesByImage(request)
        result.faceMatches.firstOrNull()?.face?.externalImageId
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun capturePhoto(capture: ImageCapture, executor: ExecutorService, onBitmapReady: (Bitmap?) -> Unit) {
    val stream = ByteArrayOutputStream()
    capture.takePicture(
        ImageCapture.OutputFileOptions.Builder(stream).build(),
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
                onBitmapReady(bmp)
            }
            override fun onError(exception: ImageCaptureException) {
                onBitmapReady(null)
            }
        }
    )
}
