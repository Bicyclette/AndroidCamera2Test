package com.test.cam2

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

data class CameraPreviewMetadata(
    var iso: MutableState<String> = mutableStateOf<String>(""),
    var exposureTime: MutableState<String> = mutableStateOf<String>(""),
    var autoExposureMode: MutableState<String> = mutableStateOf<String>(""),
    var autoWhiteBalanceMode: MutableState<String> = mutableStateOf<String>(""),
    var colorCorrectionMode: MutableState<String> = mutableStateOf<String>(""),
    var sceneMode: MutableState<String> = mutableStateOf<String>(""),
    var videoStabilizationMode: MutableState<String> = mutableStateOf<String>("")
)

class AppViewModel: ViewModel() {
    val permissions = mutableStateListOf<String>()
    var allPermissionsGranted = mutableStateOf<Boolean>(false)
    var startCameraPreview = mutableStateOf(false)
    var startBuildSurfaceView = mutableStateOf(false)
    val cameraPreviewMetadata = CameraPreviewMetadata()

    fun dismissDialog(permission: String) {
        if(permissions.isNotEmpty()) {
            permissions.remove(permission)
        }
    }

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        if(!isGranted && !permissions.contains(permission)) {
            permissions.add(permission)
        } else {
            allPermissionsGranted.value = true
        }
    }
}