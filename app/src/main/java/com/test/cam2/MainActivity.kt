package com.test.cam2

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        setContent {
            val appViewModel = viewModel<AppViewModel>()
            val ctx = LocalContext.current
            AskCameraPermission(appViewModel, ctx)
            RunApp(ctx, appViewModel)
        }
    }
}

@Composable
fun RunApp(ctx: Context, appViewModel: AppViewModel) {
    if(appViewModel.allPermissionsGranted.value || ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        val cameraManager: CameraManager = getSystemService(ctx, CameraManager::class.java) as CameraManager
        var rearCameraDeviceState = remember { mutableStateOf<CameraDeviceState?>(null) }
        if(appViewModel.startBuildSurfaceView.value) {
            BuildSurfaceView(appViewModel = appViewModel, cameraManager = cameraManager, cameraDevice = rearCameraDeviceState.value)
        }
        else {
            rearCameraDeviceState.value = openCamera(appViewModel, cameraManager)
        }
    }
}

@Composable
fun openCamera(
    appViewModel: AppViewModel,
    cameraManager: CameraManager
): CameraDeviceState? {
    // ==================== OPEN REAR CAMERA ====================
    val cameraIDs = cameraManager.cameraIdList
    var rearCameraID = ""
    for(id in cameraIDs) {
        val cameraProperties = cameraManager.getCameraCharacteristics(id)
        val directionRelativeToScreen = cameraProperties.get(CameraCharacteristics.LENS_FACING)
        if(directionRelativeToScreen == 1){
            rearCameraID = id
            break
        }
    }
    if(rearCameraID.isNotEmpty()) {
        val rearCameraDeviceState = CameraDeviceState(
            appViewModel = appViewModel,
            cameraManager = cameraManager
        )
        try {
            cameraManager.openCamera(rearCameraID, rearCameraDeviceState, null)
            return rearCameraDeviceState
        } catch(e: CameraAccessException) {
            Log.d("EXCEPTION", e.message as String)
        } catch(e: IllegalArgumentException) {
            Log.d("EXCEPTION", e.message as String)
        } catch(e: SecurityException) {
            Log.d("EXCEPTION", e.message as String)
        }
    }
    return null
}

fun computeOptimalSurfaceSize(ctx: Context, camManager: CameraManager, camID: String?): Size {
    if(camID != null) {
        val supportedSizes = getCameraSupportedOutputSize(cameraManager = camManager, cameraID = camID)
        return supportedSizes.maxBy { it -> it.width * it.height }

    }
    return Size(0, 0)
}

@Composable
fun BuildSurfaceView(
    appViewModel: AppViewModel,
    cameraManager: CameraManager,
    cameraDevice: CameraDeviceState?
) {
    if(cameraDevice != null) {
        // ==================== ADD SURFACE VIEW TO APPLICATION'S MAIN UI ====================
        val scrollVertical = rememberScrollState(initial = 0)
        Column(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .scrollable(
                state = scrollVertical,
                orientation = Orientation.Vertical
            )) {
            AndroidView(factory = { ctx ->
                SurfaceView(ctx).apply {
                    val optimalSize = computeOptimalSurfaceSize(ctx, cameraManager, cameraDevice.cameraDevice?.id)
                    holder.setFixedSize(optimalSize.width, optimalSize.height)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Log.d("SURFACE_CALLBACK", "SURFACE CREATED")
                            setupCaptureSession(appViewModel, holder, cameraDevice)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            Log.d("SURFACE_CALLBACK", "SURFACE CHANGED")
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            Log.d("SURFACE_CALLBACK", "SURFACE DESTROYED")
                        }
                    })
                }
            })
            val isoStr = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color.Green))
                append("ISO: ")
                pushStyle(SpanStyle(color = Color.White))
                append("${appViewModel.cameraPreviewMetadata.iso.value}")
                pop()
            }
            val exposureTimeStr = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color.Green))
                append("EXPOSURE TIME: ")
                pushStyle(SpanStyle(color = Color.White))
                append("${appViewModel.cameraPreviewMetadata.exposureTime.value}")
                pop()
            }
            val autoExposureModeStr = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color.Green))
                append("AUTO EXPOSURE MODE: ")
                pushStyle(SpanStyle(color = Color.White))
                append("${appViewModel.cameraPreviewMetadata.autoExposureMode.value}")
                pop()
            }
            val autoWhiteBalanceStr = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color.Green))
                append("AUTO WHITE BALANCE: ")
                pushStyle(SpanStyle(color = Color.White))
                append("${appViewModel.cameraPreviewMetadata.autoWhiteBalanceMode.value}")
                pop()
            }
            val videoStabilizationModeStr = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color.Green))
                append("VIDEO STABILIZATION MODE: ")
                pushStyle(SpanStyle(color = Color.White))
                append("${appViewModel.cameraPreviewMetadata.videoStabilizationMode.value}")
                pop()
            }
            val colorCorrectionModeStr = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color.Green))
                append("COLOR CORRECTION MODE: ")
                pushStyle(SpanStyle(color = Color.White))
                append("${appViewModel.cameraPreviewMetadata.colorCorrectionMode.value}")
                pop()
            }
            val sceneModeStr = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color.Green))
                append("SCENE MODE: ")
                pushStyle(SpanStyle(color = Color.White))
                append("${appViewModel.cameraPreviewMetadata.sceneMode.value}")
                pop()
            }
            Text(text = isoStr)
            HorizontalDivider()
            Text(text = exposureTimeStr)
            HorizontalDivider()
            Text(text = autoExposureModeStr)
            HorizontalDivider()
            Text(text = autoWhiteBalanceStr)
            HorizontalDivider()
            Text(text = videoStabilizationModeStr)
            HorizontalDivider()
            Text(text = colorCorrectionModeStr)
            HorizontalDivider()
            Text(text = sceneModeStr)
            HorizontalDivider()
        }
    }
}

fun setupCaptureSession(
    appViewModel: AppViewModel,
    surfaceHolder: SurfaceHolder,
    cameraDevice: CameraDeviceState
) {
    Log.d("SETUP_CAPTURE_SESSION", "SETUP CAPTURE SESSION")

    val imageReader = ImageReader.newInstance(
        surfaceHolder.surfaceFrame.right,
        surfaceHolder.surfaceFrame.bottom,
        ImageFormat.JPEG, 1
    )
    val outputConfigurationRealTimePreview = OutputConfiguration(surfaceHolder.surface)
    val outputConfigurationCapturePhoto = OutputConfiguration(imageReader.surface)
    val outputConfigurationList = mutableListOf(outputConfigurationRealTimePreview, outputConfigurationCapturePhoto)

    // ==================== CREATE CAPTURE SESSION ====================
    val camCaptureSessionCallback = CameraCaptureSessionCallback(appViewModel, outputConfigurationList, cameraDevice)
    val sessionConfig = SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR,
        outputConfigurationList,
        cameraDevice.executor,
        camCaptureSessionCallback
    )
    cameraDevice.cameraDevice?.createCaptureSession(sessionConfig)
}