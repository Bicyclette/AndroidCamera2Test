package com.test.cam2

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureSessionCallback(
    val appViewModel: AppViewModel,
    val outputConfigList: MutableList<OutputConfiguration>,
    val cameraDevice: CameraDeviceState,
    val handler: Handler = Handler(Looper.getMainLooper())
) : CameraCaptureSession.StateCallback() {
    override fun onConfigureFailed(session: CameraCaptureSession) {
        Log.d("CaptureCallback", "CAPTURE SESSION CONFIGURATION FAILED !")
    }

    override fun onConfigured(session: CameraCaptureSession) {
        Log.d("CaptureCallback", "CAPTURE SESSION CONFIGURATION SUCCESS !")
        appViewModel.startCameraPreview.value = true

        // ==================== START THE VIDEO STREAMING ====================
        startRealtimePreviewCaptureSession(session)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startRealtimePreviewCaptureSession(session: CameraCaptureSession) {
        val camDevice = cameraDevice.cameraDevice as CameraDevice
        val previewReqBuilder = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        //previewReqBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
        //previewReqBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        //previewReqBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 200)
        previewReqBuilder.addTarget(outputConfigList[0].surfaces[0])

        session.setRepeatingRequest(
            previewReqBuilder.build(),
            object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    logMetadata(appViewModel, result)
                }
            },
            handler
        )
    }

    private fun startPhotoCaptureSession(session: CameraCaptureSession) {
        val camDevice = cameraDevice.cameraDevice as CameraDevice
        val takePhotoReqBuilder = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        takePhotoReqBuilder.addTarget(outputConfigList[1].surfaces[0])

        session.capture(
            takePhotoReqBuilder.build(),
            object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Log.d("CAPTURE", "A PHOTO HAS BEEN TAKEN !")
                }
            },
            handler
        )
    }

    private fun logMetadata(appViewModel: AppViewModel, result: CaptureResult) {
        Log.d("PREVIEW_METADATA", "==================== METADATA ====================")
        val sensor_exposure_time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) // temps de capture
        appViewModel.cameraPreviewMetadata.exposureTime.value = sensor_exposure_time.toString()
        Log.d("PREVIEW_METADATA", "SENSOR_EXPOSURE_TIME = $sensor_exposure_time")
        val sensor_sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY) // iso
        Log.d("PREVIEW_METADATA", "SENSOR_SENSITIVITY = $sensor_sensitivity")
        appViewModel.cameraPreviewMetadata.iso.value = sensor_sensitivity.toString()
        Log.d("PREVIEW_METADATA", "AUTO EXPOSURE MODE = ${result.toStr(CaptureResult.CONTROL_AE_MODE)}")
        appViewModel.cameraPreviewMetadata.autoExposureMode.value = result.toStr(CaptureResult.CONTROL_AE_MODE)
        Log.d("PREVIEW_METADATA", "AUTO WHITE BALANCE = ${result.toStr(CaptureResult.CONTROL_AWB_MODE)}") // AUTO WHITE BALANCE
        appViewModel.cameraPreviewMetadata.autoWhiteBalanceMode.value = result.toStr(CaptureResult.CONTROL_AWB_MODE)
        Log.d("PREVIEW_METADATA", "COLOR CORRECTION MODE = ${result.toStr(CaptureResult.COLOR_CORRECTION_MODE)}") // how the image data is converted from the sensor's native color into linear sRGB color
        appViewModel.cameraPreviewMetadata.colorCorrectionMode.value = result.toStr(CaptureResult.COLOR_CORRECTION_MODE)
        Log.d("PREVIEW_METADATA", "SCENE MODE = ${result.toStr(CaptureResult.CONTROL_SCENE_MODE)}")
        appViewModel.cameraPreviewMetadata.sceneMode.value = result.toStr(CaptureResult.CONTROL_SCENE_MODE)
        Log.d("PREVIEW_METADATA", "VIDEO STABILIZATION MODE = ${result.toStr(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)}")
        appViewModel.cameraPreviewMetadata.videoStabilizationMode.value = result.toStr(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)
    }
}

class CameraDeviceState(
    val appViewModel: AppViewModel,
    val cameraManager: CameraManager,
    var cameraDevice: CameraDevice? = null
) : CameraDevice.StateCallback() {
    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onOpened(camera: CameraDevice) {
        cameraDevice = camera
        Log.d("CAMERA_DEVICE_STATE", "CAMERA DEVICE ID: ${cameraDevice?.id} HAS BEEN OPENED !")
        prepareRealtimePreview(appViewModel)
    }

    override fun onClosed(camera: CameraDevice) {
        super.onClosed(camera)
        cameraDevice?.close()
        cameraDevice = null
    }

    override fun onDisconnected(camera: CameraDevice) {
        Log.d("CAMERA_DEVICE_STATE", "CAMERA DEVICE ID: ${cameraDevice?.id} HAS BEEN DISCONNECTED !")
        camera.close()
        cameraDevice = null
    }

    override fun onError(camera: CameraDevice, error: Int) {
        Log.d("CAMERA_DEVICE_STATE", "ERROR ON CAMERA DEVICE ID: ${cameraDevice?.id} !. ERROR NUMBER = $error")
        camera.close()
        cameraDevice = null
    }

    private fun prepareRealtimePreview(appViewModel: AppViewModel) {
        // ==================== GET CAMERA'S SUPPORTED OUTPUT FORMATS ====================
        Log.d("CAMERA_DEVICE_STATE", "PREPARE REALTIME PREVIEW")
        val rearCameraID = cameraDevice?.id
        if (rearCameraID != null) {
            val supportedOutputFormat =
                getCameraSupportedOutputFormat(cameraManager, rearCameraID)
            val supportedOutputSize =
                getCameraSupportedOutputSize(cameraManager, rearCameraID)

            if (supportedOutputFormat.isNotEmpty() && supportedOutputSize.isNotEmpty()) {
                val supportJPEG = ImageFormat.JPEG in supportedOutputFormat
                val supportYUV_420_888 = ImageFormat.YUV_420_888 in supportedOutputFormat
                if(supportJPEG && supportYUV_420_888) {
                    appViewModel.startBuildSurfaceView.value = true
                }
            }
        }
    }
}

fun getCameraSupportedOutputFormat(cameraManager: CameraManager, cameraID: String): Array<Int> {
    val characteristics = cameraManager.getCameraCharacteristics(cameraID)
    val supportedOutputs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    if(supportedOutputs != null) {
        return supportedOutputs.outputFormats.toTypedArray()
    }
    else {
        return emptyArray<Int>()
    }
}

fun getCameraSupportedOutputSize(cameraManager: CameraManager, cameraID: String): Array<Size> {
    val characteristics = cameraManager.getCameraCharacteristics(cameraID)
    val supportedSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    if(supportedSizes != null) {
        return supportedSizes.getOutputSizes(ImageFormat.YUV_420_888)
    }
    else {
        return emptyArray<Size>()
    }
}

fun CaptureResult.toStr(key: CaptureResult.Key<Int>): String {
    return when(key) {
        CaptureResult.CONTROL_AWB_MODE -> when(this.get(key)) {
            CaptureResult.CONTROL_AWB_MODE_OFF -> "OFF"
            CaptureResult.CONTROL_AWB_MODE_AUTO -> "AUTO"
            CaptureResult.CONTROL_AWB_MODE_SHADE -> "SHADE"
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"
            CaptureResult.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY DAYLIGHT"
            CaptureResult.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"
            CaptureResult.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WARM FLUORESCENT"
            else -> "ERROR"
        }
        CaptureResult.CONTROL_AE_MODE -> when(this.get(key)) {
            CaptureResult.CONTROL_AE_MODE_OFF -> "OFF"
            CaptureResult.CONTROL_AE_MODE_ON -> "ON"
            CaptureResult.CONTROL_AE_MODE_ON_AUTO_FLASH -> "ON AUTO FLASH"
            CaptureResult.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "ON ALWAYS FLASH"
            CaptureResult.CONTROL_AE_MODE_ON_EXTERNAL_FLASH -> "ON EXTERNAL FLASH"
            CaptureResult.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "ON AUTO FLASH RED EYE"
            CaptureResult.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY -> "ON LOW LIGHT BOOST BRIGHTNESS PRIORITY"
            else -> "ERROR"
        }
        CaptureResult.COLOR_CORRECTION_MODE -> when(this.get(key)) {
            CaptureResult.COLOR_CORRECTION_MODE_FAST -> "FAST"
            CaptureResult.COLOR_CORRECTION_MODE_HIGH_QUALITY -> "HIGH QUALITY"
            CaptureResult.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX -> "TRANSFORM MATRIX"
            else -> "ERROR"
        }
        CaptureResult.CONTROL_SCENE_MODE -> when(this.get(key)) {
            CaptureResult.CONTROL_SCENE_MODE_HDR -> "HDR"
            CaptureResult.CONTROL_SCENE_MODE_SNOW -> "SNOW"
            CaptureResult.CONTROL_SCENE_MODE_BEACH -> "BEACH"
            CaptureResult.CONTROL_SCENE_MODE_NIGHT -> "NIGHT"
            CaptureResult.CONTROL_SCENE_MODE_PARTY -> "PARTY"
            CaptureResult.CONTROL_SCENE_MODE_ACTION -> "ACTION"
            CaptureResult.CONTROL_SCENE_MODE_BARCODE -> "BARCODE"
            CaptureResult.CONTROL_SCENE_MODE_CANDLELIGHT -> "CANDLELIGHT"
            CaptureResult.CONTROL_SCENE_MODE_DISABLED -> "DISABLED"
            CaptureResult.CONTROL_SCENE_MODE_FACE_PRIORITY -> "FACE PRIORITY"
            CaptureResult.CONTROL_SCENE_MODE_FIREWORKS -> "FIREWORKS"
            CaptureResult.CONTROL_SCENE_MODE_LANDSCAPE -> "LANDSCAPE"
            CaptureResult.CONTROL_SCENE_MODE_NIGHT_PORTRAIT -> "NIGHT PORTRAIT"
            CaptureResult.CONTROL_SCENE_MODE_PORTRAIT -> "PORTRAIT"
            CaptureResult.CONTROL_SCENE_MODE_SPORTS -> "SPORTS"
            CaptureResult.CONTROL_SCENE_MODE_STEADYPHOTO -> "STEADY PHOTO"
            CaptureResult.CONTROL_SCENE_MODE_SUNSET -> "SUNSET"
            CaptureResult.CONTROL_SCENE_MODE_THEATRE -> "THEATRE"
            else -> "ERROR"
        }
        CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE -> when(this.get(key)) {
            CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OFF"
            CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "ON"
            CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION -> "PREVIEW STABILIZATION"
            else -> "ERROR"
        }
        else -> "KEY NOT YET SUPPORTED BY TO_STRING(...) FUNCTION !"
    }
}