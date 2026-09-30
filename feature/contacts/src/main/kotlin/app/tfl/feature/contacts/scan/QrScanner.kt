package app.tfl.feature.contacts.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.qr.QrDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/**
 * The camera, reading QR codes. CAMERA is requested here and only here, after saying what the
 * camera is for; once it's refused for good, this offers the app's settings instead. Frames are
 * read in memory and dropped: nothing is saved or sent.
 *
 * [onCode] gets every code in view, on the main thread, many times a second while it stays in view.
 */
@Composable
internal fun QrScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    // Whether Android refuses without asking. Read as each request returns: after a second refusal
    // nothing else changes, so a check during composition would never see it.
    var blocked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        blocked = !ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }
    // Back from the system settings, the answer may have changed.
    LifecycleResumeEffect(context) {
        granted = context.hasCameraPermission()
        onPauseOrDispose {}
    }
    when {
        granted -> CameraViewfinder(onCode, modifier)
        blocked -> CameraBlocked(onOpenSettings = { context.openAppSettings() }, modifier)
        else -> CameraRationale(onAllow = { request.launch(Manifest.permission.CAMERA) }, modifier)
    }
}

/** Why TFL wants the camera, before Android asks. */
@Composable
internal fun CameraRationale(onAllow: () -> Unit, modifier: Modifier = Modifier) = CameraMessage(
    symbol = MaterialSymbols.PhotoCamera,
    title = stringResource(R.string.camera_rationale_title),
    body = stringResource(R.string.camera_rationale_body),
    action = stringResource(R.string.camera_rationale_allow),
    onAction = onAllow,
    modifier = modifier,
)

/** CAMERA was refused and Android won't ask again: only the app's settings can turn it on. */
@Composable
internal fun CameraBlocked(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) = CameraMessage(
    symbol = MaterialSymbols.NoPhotography,
    title = stringResource(R.string.camera_blocked_title),
    body = stringResource(R.string.camera_blocked_body),
    action = stringResource(R.string.camera_blocked_settings),
    onAction = onOpenSettings,
    modifier = modifier,
)

@Composable
private fun CameraMessage(
    symbol: String,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TflIcon(symbol, contentDescription = null, size = 36.dp, tint = colors.primary)
        Text(title, style = TflTheme.typography.headlineSm, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center)
        PrimaryButton(action, onClick = onAction, size = TflButtonSize.Medium)
    }
}

/** The live preview, with each frame passed to ZXing. Bound to the lifecycle: it stops in the background. */
@Composable
private fun CameraViewfinder(onCode: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unavailable = true
            return@LaunchedEffect
        }
        val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
            .firstOrNull { provider.hasCamera(it) }
        if (selector == null) {
            unavailable = true
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(ANALYSIS_RESOLUTION)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val analyzerThread = Executors.newSingleThreadExecutor()
        val mainThread = ContextCompat.getMainExecutor(context)
        analysis.setAnalyzer(analyzerThread, QrAnalyzer { text -> mainThread.execute { currentOnCode(text) } })
        try {
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            unavailable = true // this camera can't run both use cases
        } finally {
            provider.unbind(preview, analysis)
            analysis.clearAnalyzer()
            analyzerThread.shutdown()
        }
    }

    Box(modifier.background(TflTheme.colors.canvas), contentAlignment = Alignment.Center) {
        val request = surfaceRequest
        when {
            unavailable -> Text(
                stringResource(R.string.camera_unavailable),
                modifier = Modifier.padding(20.dp),
                style = TflTheme.typography.bodyMd,
                color = TflTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
            // Drawn inside TFL's window (not a separate surface), so FLAG_SECURE covers the preview.
            request != null -> CameraXViewfinder(request, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED)
        }
    }
}

/** Hands each frame's luminance (Y) plane to the decoder and closes the frame straight after. */
private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val decoder = QrDecoder()
    private var luminance = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            val plane = frame.planes[0]
            val buffer = plane.buffer.duplicate().apply { rewind() }
            if (luminance.size != buffer.remaining()) luminance = ByteArray(buffer.remaining())
            buffer.get(luminance)
            decoder.decode(luminance, plane.rowStride, frame.width, frame.height)?.let(onText)
        }
    }
}

/** 720p is enough for a dense code a hand's length away, and quick to decode. */
private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
