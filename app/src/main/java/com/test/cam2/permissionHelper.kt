package com.test.cam2

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat

@Composable
fun AskCameraPermission(viewModel: AppViewModel, ctx: Context) {
    val activity = ctx as Activity
    val permissions = viewModel.permissions
    val permissionToRequest = Manifest.permission.CAMERA

    val cameraPermissionResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            viewModel.onPermissionResult(permissionToRequest, isGranted)
        }
    )

    LaunchedEffect(Unit) {
        if(ContextCompat.checkSelfPermission(ctx, permissionToRequest) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionResultLauncher.launch(permissionToRequest)
        }
    }

    permissions.forEach { permission ->
        PermissionDialog(
            context = ctx,
            permissionTextProvider = CameraPermissionTextProvider(),
            isPermanentlyDeclined = !shouldShowRequestPermissionRationale(activity, permission),
            onDismiss = {
                viewModel.dismissDialog(permissionToRequest)
            },
            onOkClick = {
                viewModel.dismissDialog(permissionToRequest)
                cameraPermissionResultLauncher.launch(permissionToRequest)
            },
            onGoToAppSettingsClick = { activity.openAppSettings() }
        )
    }
}

fun Activity.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    startActivity(intent)
}

fun PermissionDialog(
    context: Context,
    permissionTextProvider: PermissionTextProvider,
    isPermanentlyDeclined: Boolean,
    onDismiss: () -> Unit,
    onOkClick: () -> Unit,
    onGoToAppSettingsClick: () -> Unit
) {
    val builder = AlertDialog.Builder(context)
    builder.setIcon(R.drawable.icon_exclamation_mark)
    builder.setTitle(permissionTextProvider.getTitle())
    builder.setMessage(permissionTextProvider.getDescription(isPermanentlyDeclined))
    builder.setPositiveButton("OK") { dialog, _ ->
        if(isPermanentlyDeclined) {
            onGoToAppSettingsClick()
        }
        else {
            onOkClick()
        }
        dialog.dismiss()
    }
    builder.setNegativeButton("NO") { dialog, _ ->
        onDismiss()
        dialog.dismiss()
    }
    val dialog = builder.create()
    dialog.show()
}

interface PermissionTextProvider {
    fun getTitle(): String
    fun getDescription(isPermanentlyDeclined: Boolean): String
}

class CameraPermissionTextProvider: PermissionTextProvider {
    override fun getTitle(): String {
        return "CAMERA PERMISSION WARNING !"
    }
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        if(isPermanentlyDeclined) {
            return "It seems you permanently declined the access of your camera device." +
                    " This application cannot run properly without it." +
                    " Please go to your application settings and grant the camera permission, thanks !"

        }
        return "This application obviously needs camera permission, please grant it !"
    }
}