package com.caio.vuzixhello

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // launcher para pedir permissão da câmera
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                runCameraApp()
            }
        }

        // verifica permissão
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            runCameraApp()
        }
    }

    private fun runCameraApp() {
        setContent {
            CameraCaptureScreen(
                cameraExecutor = cameraExecutor,
                activity = this
            )
        }
    }
}

@Composable
fun CameraCaptureScreen(
    cameraExecutor: ExecutorService,
    activity: ComponentActivity
) {
    val context = LocalContext.current

    var status by remember { mutableStateOf("Iniciando...") }
    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(true) {
        status = "Carregando câmera..."

        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val capture = ImageCapture.Builder().build()
        imageCapture = capture

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // BIND TO ACTIVITY — CORRETO
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            activity,
            cameraSelector,
            preview,
            capture
        )

        // Espera 3 segundos e tira a foto
        status = "Aguardando 3 segundos..."
        delay(3000)

        status = "Capturando foto..."
        takePhoto(
            context = context,
            executor = cameraExecutor,
            imageCapture = capture,
            onPhotoCaptured = { bitmap ->
                capturedBitmap = bitmap
                status = "Foto capturada!"
            },
            onError = {
                status = "Erro ao capturar foto."
            }
        )
    }

    // UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (capturedBitmap != null) {
            Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = "Foto",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Text(
                    text = status,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(16.dp)
                )
            }
        }
    }
}

fun takePhoto(
    context: android.content.Context,
    executor: ExecutorService,
    imageCapture: ImageCapture,
    onPhotoCaptured: (android.graphics.Bitmap) -> Unit,
    onError: (Exception) -> Unit
) {
    val photoFile = File(context.externalMediaDirs.first(), "vuzix_poc.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                onError(exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                bitmap?.let { onPhotoCaptured(it) }
            }
        }
    )
}
