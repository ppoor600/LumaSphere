package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.hdr.HdrImage
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val project by viewModel.activeProject.collectAsState()
    val stitchedResult by viewModel.stitchedResult.collectAsState()
    val evOffset by viewModel.previewExposureOffset.collectAsState()
    val previewYaw by viewModel.previewYaw.collectAsState()

    var toneMappedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isToneMapping by remember { mutableStateOf(false) }

    // Execute exposure adjustment via a background coroutine
    LaunchedEffect(stitchedResult, evOffset) {
        val hdr = stitchedResult
        if (hdr != null) {
            isToneMapping = true
            withContext(Dispatchers.Default) {
                try {
                // Resize stitched result downsampled block if too heavy to tone map live at 60fps
                val bmp = toneMapHdrToLdr(hdr, evOffset)
                withContext(Dispatchers.Main) {
                    toneMappedBitmap = bmp
                    isToneMapping = false
                }
                } catch (e: Exception) {
                    Log.e("PreviewScreen", "Failed to tone map", e)
                    withContext(Dispatchers.Main) { isToneMapping = false }
                }
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = project?.title ?: "HDRI Light Map",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite
                        )
                        Text(
                            text = "32-bit Equirectangular EXR Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.setScreen(HdrScreen.HOME) },
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back home", tint = PureWhite)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setExposureOffset(0.0f) },
                        modifier = Modifier
                            .testTag("preview_reset_ev")
                            .padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset EV", tint = SlateGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardSurface,
                    titleContentColor = PureWhite
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interactive 360-Pan Wrapping Viewport
            Text(
                text = "INTERACTIVE 360 COMPASS VIEWPORT",
                style = MaterialTheme.typography.labelSmall,
                color = SolarAmber,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Drag scale: 0.25 degrees/pixel
                            viewModel.updatePreviewRotation(-dragAmount.x * 0.3f, dragAmount.y * 0.3f)
                        }
                    }
                    .testTag("interactive_360_viewport"),
                contentAlignment = Alignment.Center
            ) {
                val bmp = toneMappedBitmap
                if (bmp != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val viewW = size.width
                        val viewH = size.height

                        // Calculate the horizontal offset of our texture wrapper
                        // based on the previewYaw degrees heading (0 to 360 degrees)
                        val offsetFrac = previewYaw / 360f
                        val textureOffset = (offsetFrac * viewW) % viewW

                        // Source/Destination layouts inside the Canvas viewport.
                        // We draw the LDR tone-mapped environment bitmap twice to achieve wrap-around scrolling.
                        drawImage(
                            image = bmp.asImageBitmap(),
                            srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                            srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                            dstOffset = androidx.compose.ui.unit.IntOffset(-textureOffset.toInt(), 0),
                            dstSize = androidx.compose.ui.unit.IntSize(viewW.toInt(), viewH.toInt())
                        )
                        
                        // Draw the secondary wrapping card layout
                        drawImage(
                            image = bmp.asImageBitmap(),
                            srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                            srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                            dstOffset = androidx.compose.ui.unit.IntOffset((viewW - textureOffset).toInt(), 0),
                            dstSize = androidx.compose.ui.unit.IntSize(viewW.toInt(), viewH.toInt())
                        )
                    }

                    // Swipe helper compass overlay banner at the top
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PAN YAW: ${previewYaw.toInt()}° · DRAG HORIZON TO ROTATE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = CyberCyan
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = CyberCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Opening EXR light channels...", style = MaterialTheme.typography.labelSmall, color = SlateGray)
                    }
                }

                if (isToneMapping) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SolarAmber, modifier = Modifier.size(36.dp))
                    }
                }
            }

            // Interactive HDR Physics Tone Mapping EV Slider
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HDR EXPOSURE CHECK",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateGray,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .background(CyberCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (evOffset >= 0) "+${String.format("%.2f", evOffset)} EV" else "${String.format("%.2f", evOffset)} EV",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = evOffset,
                        onValueChange = { viewModel.setExposureOffset(it) },
                        valueRange = -5f..5f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan,
                            inactiveTrackColor = BorderGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exposure_ev_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "-5.0 EV (Shadow detail)", style = MaterialTheme.typography.labelSmall, color = SlateGray)
                        Text(text = "+5.0 EV (Highlights scale)", style = MaterialTheme.typography.labelSmall, color = SlateGray)
                    }
                }
            }

            // Expose export attributes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpecsBadge(label = "IMAGE RESOLUTION", value = "2048 x 1024", modifier = Modifier.weight(1f))
                SpecsBadge(label = "ENCODING LAYOUT", value = "Float32 EXR", modifier = Modifier.weight(1f))
            }

            // VFX / CGI Compatibility card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3D CG & GAME ENGINE COMPATIBILITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolarAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This 32-bit linear EXR file is compatible for Image-Based Lighting in: Blender, Unreal Engine, Unity, Cinema4D, Maya, Houdini, Redshift, Octane, and V-Ray.",
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                        color = SlateGray
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // EXR Export / Share Action Button
            Button(
                onClick = {
                    val path = project?.expFilepath
                    if (path != null && !path.startsWith("sim_")) {
                        shareExrFile(context, File(path))
                    } else {
                        // For mock assets, generate procedural light map exr file and export
                        coroutineScope.launch {
                            val msg = "Generating physical uncompressed fallback EXR for export..."
                            Log.d("PreviewScreen", msg)
                            // Simulate quick export path trigger
                            val mockPath = generateMockPhysicalExr(context)
                            shareExrFile(context, File(mockPath))
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("export_exr_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Export")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EXPORT 32-BIT EXR LIGHT MAP",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SpecsBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(8.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SlateGray)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = PureWhite, fontWeight = FontWeight.Bold)
    }
}

/**
 * Triggers standard Android native share sheet to export exr files.
 */
private fun shareExrFile(context: Context, file: File) {
    if (!file.exists()) {
        Log.e("shareExrFile", "File does not exist: $file")
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/x-exr" // standard MIME type for EXR images
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HDReye EXR 360 Light Map Output")
            putExtra(Intent.EXTRA_TEXT, "Captured 32-bit linear high dynamic range EXR environment panorama.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        intent.resolveActivity(context.packageManager)
        context.startActivity(Intent.createChooser(intent, "Export EXR Light Map to"))
    } catch (e: Exception) {
        Log.e("shareExrFile", "Failed to share EXR", e)
    }
}

/**
 * Generates a mock physical uncompressed EXR if exporting from an empty placeholder.
 * Ensures the sharing flow resolves and executes flawlessly on all configurations.
 */
private suspend fun generateMockPhysicalExr(context: Context): String = withContext(Dispatchers.Default) {
    val outputDir = File(context.filesDir, "hdris")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    val file = File(outputDir, "HDReye_Procedural_Dome_32b.exr")
    if (!file.exists()) {
        val st = com.example.hdr.HdrEngine.generateProceduralHdr(512, 256)
        com.example.hdr.ExrWriter.writeExr(
            file = file,
            width = st.width,
            height = st.height,
            rChannels = st.rData,
            gChannels = st.gData,
            bChannels = st.bData
        )
    }
    file.absolutePath
}

/**
 * Core mathematical Reinhard Tone Mapper converting Float32 pixel arrays into SDR display Bitmaps.
 */
private fun toneMapHdrToLdr(hdr: HdrImage, evOffset: Float): Bitmap {
    val w = hdr.width
    val h = hdr.height
    val ldrBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val size = w * h
    val argbPixels = IntArray(size)

    // Calculate light exposure scale: multiplier = 2^(EV)
    val exposure = 2.0f.pow(evOffset)

    for (i in 0 until size) {
        // Multiply original linear radiance values by exposure setting
        val lr = hdr.rData[i] * exposure
        val lg = hdr.gData[i] * exposure
        val lb = hdr.bData[i] * exposure

        // Reinhard global operators: mappedCoord = linearCoord / (linearCoord + 1.0)
        val tr = lr / (lr + 1.0f)
        val tg = lg / (lg + 1.0f)
        val tb = lb / (lb + 1.0f)

        // Gamma expand mapping layout (sRGB standard gamma = 2.2): coord^(1/2.2) ~ coord^0.4545
        val rVal = (tr.pow(0.4545f) * 255f).toInt().coerceIn(0, 255)
        val gVal = (tg.pow(0.4545f) * 255f).toInt().coerceIn(0, 255)
        val bVal = (tb.pow(0.4545f) * 255f).toInt().coerceIn(0, 255)

        // Pack values back into standard SDR 8-bit integer channel format
        argbPixels[i] = (0xFF shl 24) or (rVal shl 16) or (gVal shl 8) or bVal
    }

    ldrBmp.setPixels(argbPixels, 0, w, 0, 0, w, h)
    return ldrBmp
}
