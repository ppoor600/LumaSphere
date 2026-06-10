package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sensor.PhoneOrientation
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val orientation by viewModel.currentOrientation.collectAsState()
    val capturePoints by viewModel.capturePoints.collectAsState()
    val isTripodMode by viewModel.isTripodMode.collectAsState()
    val stabilityScore by viewModel.stabilityScore.collectAsState()
    
    val isCapturingBurst by viewModel.isCapturingBurst.collectAsState()
    val burstIndex by viewModel.burstIndex.collectAsState()
    val selectedBracket by viewModel.selectedBracketLevel.collectAsState()

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val totalPoints = capturePoints.size
    val capturedCount = capturePoints.count { it.isCaptured }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Camera live preview view or fallback simulated grid
        if (cameraPermissionGranted) {
            AndroidCameraXView(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onErrorFallback = {
                    SimulatedMatrixFinder(modifier = Modifier.fillMaxSize())
                }
            )
        } else {
            SimulatedMatrixFinder(modifier = Modifier.fillMaxSize())
        }

        // Intermittent strobe flare when capturing bracketed photos to visualize EV shifts
        AnimatedVisibility(
            visible = isCapturingBurst,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            val isDarken = burstIndex % 2 == 0
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDarken) Color.Black.copy(alpha = 0.55f)
                        else Color.White.copy(alpha = 0.25f)
                    )
            )
        }

        // 2. Translucent Ambient Scaffolding Overlays
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left element: Close Button (X)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.setScreen(HdrScreen.HOME) }
                            .testTag("capture_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = PureWhite,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            fontSize = 18.sp
                        )
                    }

                    // Center element: Progress Ring Badge "3/7 EV" or "N/18 EV"
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val progressRingColor = if (isCapturingBurst) SolarAmber else CyberCyan
                        val stepText = if (isCapturingBurst) {
                            val maxShots = when (selectedBracket) {
                                "Low" -> 3
                                "Medium" -> 5
                                else -> 7
                            }
                            "$burstIndex/$maxShots"
                        } else {
                            "$capturedCount/$totalPoints"
                        }
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw ambient track
                            drawCircle(
                                color = BorderGray.copy(alpha = 0.2f),
                                radius = size.minDimension / 2f - 2f * density,
                                style = Stroke(width = 2f * density)
                            )
                            // Draw active arc based on progress fraction
                            val sweepFrac = if (isCapturingBurst) {
                                val maxShots = when (selectedBracket) {
                                    "Low" -> 3f
                                    "Medium" -> 5f
                                    else -> 7f
                                }
                                burstIndex.toFloat() / maxShots
                            } else {
                                capturedCount.toFloat() / totalPoints.toFloat()
                            }
                            drawArc(
                                color = progressRingColor,
                                startAngle = -90f,
                                sweepAngle = sweepFrac * 360f,
                                useCenter = false,
                                topLeft = Offset(4f, 4f),
                                size = Size(size.width - 8f, size.height - 8f),
                                style = Stroke(width = 2.5f * density)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stepText,
                                color = PureWhite,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            )
                            Text(
                                text = "EV",
                                color = SlateGray,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Right element: Padlock + Switch camera
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Padlock icon box
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .border(1.dp, BorderGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable { /* Mock lock action */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val w = size.width
                                val h = size.height
                                drawRoundRect(
                                    color = PureWhite,
                                    topLeft = Offset(0f, h * 0.45f),
                                    size = Size(w, h * 0.55f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * density, 2f * density)
                                )
                                drawPath(
                                    path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(w * 0.25f, h * 0.45f)
                                        lineTo(w * 0.25f, h * 0.22f)
                                        quadraticTo(w * 0.5f, -h * 0.05f, w * 0.75f, h * 0.22f)
                                        lineTo(w * 0.75f, h * 0.45f)
                                    },
                                    color = PureWhite,
                                    style = Stroke(width = 1.5f * density)
                                )
                            }
                        }

                        // Flip Camera button box
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .border(1.dp, BorderGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable { /* Mock camera rotate */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↻",
                                color = PureWhite,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Large HUD Spherical Compass Radar overlay centered in the viewport
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SphericalCompassRadarHUD(
                        orientation = orientation,
                        points = capturePoints,
                        modifier = Modifier
                            .size(320.dp)
                            .testTag("spherical_hud_canvas")
                    )

                    // Overlay central reticle scope (clickable for manual camera shutter capture)
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.triggerManualCapture() }
                            .testTag("manual_capture_reticle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val c = Offset(size.width / 2, size.height / 2)
                            
                            // Circle boundary
                            drawCircle(
                                color = CyberCyan.copy(alpha = 0.35f),
                                radius = 55f * density,
                                style = Stroke(
                                    width = 1f * density,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 6f * density))
                                )
                            )

                            // Center reticle ticks
                            drawLine(
                                color = CyberCyan.copy(alpha = 0.5f),
                                start = Offset(c.x - 20f * density, c.y),
                                end = Offset(c.x - 6f * density, c.y),
                                strokeWidth = 1.5f * density
                            )
                            drawLine(
                                color = CyberCyan.copy(alpha = 0.5f),
                                start = Offset(c.x + 6f * density, c.y),
                                end = Offset(c.x + 20f * density, c.y),
                                strokeWidth = 1.5f * density
                            )
                            drawLine(
                                color = CyberCyan.copy(alpha = 0.5f),
                                start = Offset(c.x, c.y - 20f * density),
                                end = Offset(c.x, c.y - 6f * density),
                                strokeWidth = 1.5f * density
                            )
                            drawLine(
                                color = CyberCyan.copy(alpha = 0.5f),
                                start = Offset(c.x, c.y + 6f * density),
                                end = Offset(c.x, c.y + 20f * density),
                                strokeWidth = 1.5f * density
                            )

                            // Dynamic Pitch-and-Roll Slanted horizon level line (Screenshot 1)
                            // It is a solid line that slants with rolls, and translates up/down with pitches.
                            val rollRad = (-orientation.rollDegrees * Math.PI / 180f).toFloat()
                            // Move vertical based on pitch degrees
                            val pitchTranslation = (orientation.pitchDegrees / 45f) * 15f * density
                            
                            val lx = Math.cos(rollRad.toDouble()).toFloat() * 40f * density
                            val ly = Math.sin(rollRad.toDouble()).toFloat() * 40f * density
                            
                            drawLine(
                                color = Color(0xFFA5C5E8), // Beautiful cool slate lavender representing level bar
                                start = Offset(c.x - lx, c.y - ly + pitchTranslation),
                                end = Offset(c.x + lx, c.y + ly + pitchTranslation),
                                strokeWidth = 1.5f * density
                            )

                            // Centered core target dot
                            drawCircle(
                                color = CyberCyan,
                                radius = 2.5f * density,
                                center = Offset(c.x, c.y + pitchTranslation)
                            )
                        }
                    }
                }

                // Left telemetry panel overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryItem(label = "YAW", value = "${String.format("%.1f", orientation.yawDegrees)}°")
                    TelemetryItem(label = "PITCH", value = "${String.format("%.1f", orientation.pitchDegrees)}°")
                    TelemetryItem(label = "ROLL", value = "${String.format("%.1f", orientation.rollDegrees)}°")
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Stabilization status
                    val absRoll = abs(orientation.rollDegrees)
                    val isHorizonLevel = absRoll < 2.0f
                    Box(
                        modifier = Modifier
                            .background(
                                if (isHorizonLevel) CleanGreen.copy(alpha = 0.15f) else DeepCoral.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isHorizonLevel) "LEVEL OK" else "TILT ERR",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isHorizonLevel) CleanGreen else DeepCoral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right exposure configurations panel overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryItem(label = "ISO", value = "AUTO (L)", alignRight = true)
                    TelemetryItem(label = "LENS", value = "W-ANGLE", alignRight = true)
                    TelemetryItem(label = "BRACKET", value = selectedBracket, alignRight = true)

                    Spacer(modifier = Modifier.height(4.dp))

                    // Stable / Tripod activation block
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isTripodMode) CleanGreen else SolarAmber,
                                    RoundedCornerShape(50)
                                )
                        )
                        Text(
                            text = if (isTripodMode) "TRIPOD DET" else "STABILIZING",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTripodMode) CleanGreen else SolarAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = stabilityScore,
                        color = if (stabilityScore > 0.90f) CleanGreen else SolarAmber,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .width(76.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }

                // Shutter trigger count banner at the bottom
                if (isCapturingBurst) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp)
                            .background(SolarAmber, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            val bracketMax = when (selectedBracket) {
                                "Low" -> 3
                                "Medium" -> 5
                                else -> 7
                            }
                            Text(
                                text = "CAPTURING EXPOSURE BRACKET $burstIndex / $bracketMax...",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom camera settings status pill (Screenshot 1)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, BorderGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "ISO", style = MaterialTheme.typography.labelSmall, color = SlateGray, fontSize = 9.sp)
                        Text(text = "100", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite, fontSize = 10.sp)
                        Text(text = "|", color = BorderGray, fontSize = 10.sp)
                        
                        Text(text = "SS", style = MaterialTheme.typography.labelSmall, color = SlateGray, fontSize = 9.sp)
                        Text(text = "1/50", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite, fontSize = 10.sp)
                        Text(text = "|", color = BorderGray, fontSize = 10.sp)

                        Text(text = "EV", style = MaterialTheme.typography.labelSmall, color = SlateGray, fontSize = 9.sp)
                        Text(text = "0.0", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CleanGreen, fontSize = 10.sp)
                        Text(text = "|", color = BorderGray, fontSize = 10.sp)

                        Text(text = "☀️ 5600K", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFA5C5E8), fontSize = 10.sp)
                    }
                }

                // Bottom quick control buttons: Skip / Autofill
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Explanatory info box
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Hold still. Capture starts automatically when target indicator is aligned.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = PureWhite,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Emergency Simulation Button (MANDATORY for virtual emulators!)
                    Button(
                        onClick = { viewModel.autoFillSimulatedRest() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.8f),
                            contentColor = CyberCyan
                        ) ,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .testTag("autofill_sim_button")
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Simulate", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SIMULATE REST",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryItem(
    label: String,
    value: String,
    alignRight: Boolean = false
) {
    Column(
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SlateGray)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = PureWhite, fontWeight = FontWeight.Bold)
    }
}

/**
 * Math implementation of our spherical compass guidance overlay canvas. Correctly maps target yaw and pitches
 * into visual projection arrays.
 */
@Composable
fun SphericalCompassRadarHUD(
    orientation: PhoneOrientation,
    points: List<HdrCapturePoint>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        // Draw compass grid lines
        drawCircle(
            color = BorderGray.copy(alpha = 0.5f),
            radius = r,
            style = Stroke(width = 1f * density)
        )
        drawCircle(
            color = BorderGray.copy(alpha = 0.3f),
            radius = r * 0.66f,
            style = Stroke(width = 1f * density)
        )
        drawCircle(
            color = BorderGray.copy(alpha = 0.2f),
            radius = r * 0.33f,
            style = Stroke(width = 1f * density)
        )

        // Draw crosshair diagonals
        drawLine(
            color = BorderGray.copy(alpha = 0.3f),
            start = Offset(cx - r, cy),
            end = Offset(cx + r, cy),
            strokeWidth = 1f * density
        )
        drawLine(
            color = BorderGray.copy(alpha = 0.3f),
            start = Offset(cx, cy - r),
            end = Offset(cx, cy + r),
            strokeWidth = 1f * density
        )

        // Project nodes onto HUD
        // Max view range inside the HUD ring is +/- 40 degrees
        val maxFovDegrees = 40.0f

        var closestPoint: HdrCapturePoint? = null
        var minDistance = Float.MAX_VALUE

        for (pt in points) {
            // Horizontal yaw offset calculation
            var yawDiff = pt.yawDegrees - orientation.yawDegrees
            yawDiff = (yawDiff % 360f + 360f) % 360f
            if (yawDiff > 180f) yawDiff -= 360f

            // Vertical pitch offset calculation
            val pitchDiff = pt.pitchDegrees - orientation.pitchDegrees

            val dist = sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)
            if (!pt.isCaptured && dist < minDistance) {
                minDistance = dist
                closestPoint = pt
            }

            // Radial projection scale
            val radX = (yawDiff / maxFovDegrees) * r
            val radY = -(pitchDiff / maxFovDegrees) * r // Pitch increases up, canvas Y decreases up

            val pointDist = sqrt(radX * radX + radY * radY)
            
            if (pointDist < r) {
                // Point is within our display HUD radar ring
                val pointColor = when {
                    pt.isCaptured -> CleanGreen
                    pt == closestPoint -> CyberCyan
                    else -> SolarAmber
                }

                val brushAlp = if (pt == closestPoint) blinkProgress else 1.0f

                // Draw capture dot
                drawCircle(
                    color = pointColor.copy(alpha = brushAlp),
                    radius = if (pt == closestPoint) 8f * density else 6f * density,
                    center = Offset(cx + radX, cy + radY)
                )

                // Extra target acquired indicator ring around aligned closest point
                if (pt == closestPoint) {
                    drawCircle(
                        color = CyberCyan.copy(alpha = brushAlp),
                        radius = 16f * density,
                        center = Offset(cx + radX, cy + radY),
                        style = Stroke(width = 1.5f * density)
                    )
                }
            } else {
                // Node is outside range of radar view: draw helper pointer arrow on circle rim
                val angle = atan2(radY, radX)
                val ax = cx + r * cos(angle)
                val ay = cy + r * sin(angle)

                if (!pt.isCaptured) {
                    drawCircle(
                        color = SolarAmber.copy(alpha = 0.35f),
                        radius = 4f * density,
                        center = Offset(ax, ay)
                    )
                }
            }
        }
    }
}

/**
 * CameraX binding Preview inside Compose container.
 */
@Composable
fun AndroidCameraXView(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    onErrorFallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasError by remember { mutableStateOf(false) }

    val isDisposed = remember { booleanArrayOf(false) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            isDisposed[0] = true
            viewModel.frameCaptureProvider = null
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                Log.e("CameraXView", "Failed to unbind camera on dispose", e)
            }
        }
    }

    if (hasError) {
        onErrorFallback()
    } else {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                // Connect frame capture callback provider
                viewModel.frameCaptureProvider = {
                    try {
                        previewView.bitmap
                    } catch (e: Exception) {
                        null
                    }
                }

                cameraProviderFuture.addListener({
                    if (isDisposed[0]) return@addListener
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = CameraPreview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    } catch (e: Exception) {
                        Log.e("CameraXView", "Failed to bind camera lifecycle", e)
                        hasError = true
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = modifier
        )
    }
}

/**
 * Simulated viewfinder grid rendered when physical back camera feeds are locked/unavailable.
 */
@Composable
fun SimulatedMatrixFinder(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        )
    )

    Box(
        modifier = modifier
            .background(DarkBackground)
            .drawBehind {
                // Beautiful matrix grid pattern drawing
                val strokeW = 1f * density
                val separation = 40f * density
                
                // Draw vertical lines
                var x = waveOffset % separation
                while (x < size.width) {
                    drawLine(
                        color = Color(0xFF0C242E).copy(alpha = 0.4f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = strokeW
                    )
                    x += separation
                }

                // Draw horizontal lines
                var y = waveOffset % separation
                while (y < size.height) {
                    drawLine(
                        color = Color(0xFF0C242E).copy(alpha = 0.4f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeW
                    )
                    y += separation
                }

                // Draw center sonar focus grid
                drawCircle(
                    color = CyberCyan.copy(alpha = 0.05f),
                    radius = size.width / 3f
                )
                
                // Dynamic telemetry wave scanline
                val scanLineY = (waveOffset * 8f) % size.height
                drawLine(
                    color = CyberCyan.copy(alpha = 0.15f),
                    start = Offset(0f, scanLineY),
                    end = Offset(size.width, scanLineY),
                    strokeWidth = 3f * density
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lens,
                contentDescription = "Sim Camera",
                tint = CyberCyan.copy(alpha = 0.25f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SIMULATOR CAM ACTIVE",
                style = MaterialTheme.typography.labelMedium,
                color = CyberCyan.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Dual-core bracket exposures simulation online",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = SlateGray,
                textAlign = TextAlign.Center
            )
        }
    }
}
