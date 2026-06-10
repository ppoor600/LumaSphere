package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.HdrProject
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.allProjects.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // Init mock gallery if completely empty to populate design demo immediately!
    LaunchedEffect(Unit) {
        viewModel.createMockGalleryIfEmpty()
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            // Sleek absolute floating bottom navigation indicator bar matching LumaSphere
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(width = (0.5).dp, color = BorderGray)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Library page button (Selected state on home)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { /* Already on Library dashboard */ }
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val w = size.width
                        val h = size.height
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, h * 0.15f)
                            lineTo(w * 0.35f, h * 0.15f)
                            lineTo(w * 0.48f, h * 0.32f)
                            lineTo(w, h * 0.32f)
                            lineTo(w, h * 0.95f)
                            lineTo(0f, h * 0.95f)
                            close()
                        }
                        drawPath(path, color = CyberCyan)
                    }
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = CyberCyan
                    )
                }

                // Shutter button center piece (Triggers Create Dialog)
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .size(54.dp)
                        .background(CyberCyan, CircleShape)
                        .testTag("nav_capture_trigger")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(2.dp, CardSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(14.dp)) {
                            drawCircle(color = CardSurface, radius = size.minDimension / 2)
                        }
                    }
                }

                // Settings page mock button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { /* Settings indicator */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = SlateGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = SlateGray
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Elegant background ambient glows
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(CyberCyan.copy(alpha = 0.06f), Color.Transparent),
                            radius = 600f,
                            center = Offset(200f, 0f)
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("projects_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Item 1: App Header Row with Aperture icon and Power Badge
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val radius = size.minDimension / 2f
                                val strokeW = 1.5f * density
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                drawCircle(
                                    color = PureWhite,
                                    radius = radius,
                                    style = Stroke(width = strokeW)
                                )
                                for (i in 0 until 6) {
                                    val angle = (i * 60f) * (Math.PI / 180f).toFloat()
                                    val nextAngle = ((i + 1) * 60f) * (Math.PI / 180f).toFloat()
                                    drawLine(
                                        color = PureWhite,
                                        start = Offset(cx + radius * Math.cos(angle.toDouble()).toFloat(), cy + radius * Math.sin(angle.toDouble()).toFloat()),
                                        end = Offset(cx + (radius * 0.45f) * Math.cos(nextAngle.toDouble()).toFloat(), cy + (radius * 0.45f) * Math.sin(nextAngle.toDouble()).toFloat()),
                                        strokeWidth = strokeW
                                    )
                                }
                            }
                            Text(
                                text = "LumaSphere",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = PureWhite
                            )
                        }

                        // Battery status container cap
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(11.dp)
                                .border(1.dp, PureWhite.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                .padding(1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.75f)
                                    .background(PureWhite.copy(alpha = 0.8f), RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }

                // Item 2: User greetings greeting
                item {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Good morning, Alex.",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 24.sp),
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ready for some lighting capture?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SlateGray
                        )
                    }
                }

                // Item 3: Specs Chips indicators list
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Storage chip
                        Row(
                            modifier = Modifier
                                .background(CardSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Canvas(modifier = Modifier.size(11.dp)) {
                                val w = size.width
                                val h = size.height
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(w * 0.2f, 0f)
                                    lineTo(w * 0.8f, 0f)
                                    lineTo(w, h * 0.2f)
                                    lineTo(w, h)
                                    lineTo(w * 0.2f, h)
                                    close()
                                }
                                drawPath(path, color = SlateGray, style = Stroke(width = 1f * density))
                                drawLine(color = SlateGray, start = Offset(w * 0.4f, h * 0.15f), end = Offset(w * 0.4f, h * 0.4f), strokeWidth = 1f * density)
                                drawLine(color = SlateGray, start = Offset(w * 0.6f, h * 0.15f), end = Offset(w * 0.6f, h * 0.4f), strokeWidth = 1f * density)
                                drawLine(color = SlateGray, start = Offset(w * 0.8f, h * 0.15f), end = Offset(w * 0.8f, h * 0.4f), strokeWidth = 1f * density)
                            }
                            Text(
                                text = "124GB FREE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = PureWhite
                            )
                        }

                        // Battery Chip
                        Row(
                            modifier = Modifier
                                .background(CardSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Canvas(modifier = Modifier.size(width = 14.dp, height = 9.dp)) {
                                val w = size.width
                                val h = size.height
                                drawRoundRect(
                                    color = SlateGray,
                                    topLeft = Offset(0f, 0f),
                                    size = Size(w - 2f * density, h),
                                    style = Stroke(width = 1f * density),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * density, 1.5f * density)
                                )
                                drawRect(
                                    color = SlateGray,
                                    topLeft = Offset(1.5f * density, 1.5f * density),
                                    size = Size((w - 5f * density) * 0.75f, h - 3f * density)
                                )
                                drawRect(
                                    color = SlateGray,
                                    topLeft = Offset(w - 1.5f * density, h * 0.3f),
                                    size = Size(1.5f * density, h * 0.4f)
                                )
                            }
                            Text(
                                text = "75% REM",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = PureWhite
                            )
                        }
                    }
                }

                // Item 4: BIG CAPTURE SHUTTER CARD
                item {
                    val density = LocalDensity.current.density
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showCreateDialog = true }
                            .testTag("create_hdri_fab"),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Subtly shaded grid lines background
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawLine(
                                    color = BorderGray.copy(alpha = 0.15f),
                                    start = Offset(0f, size.height / 2f),
                                    end = Offset(size.width, size.height / 2f),
                                    strokeWidth = 1f * density
                                )
                                drawLine(
                                    color = BorderGray.copy(alpha = 0.15f),
                                    start = Offset(size.width / 2f, 0f),
                                    end = Offset(size.width / 2f, size.height),
                                    strokeWidth = 1f * density
                                )
                            }

                            // Info statuses on top right of trigger box
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, CleanGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "GPS LOCKED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = CleanGreen
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, BorderGray, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "EV 0.0",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = SlateGray
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Double ring dome wireframe sphere visual
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val r = size.minDimension / 2f
                                        drawCircle(
                                            color = CyberCyan,
                                            radius = r,
                                            style = Stroke(width = 1.5f * density)
                                        )
                                        drawOval(
                                            color = CyberCyan,
                                            topLeft = Offset(cx - r, cy - r * 0.4f),
                                            size = Size(r * 2f, r * 0.8f),
                                            style = Stroke(width = 1.5f * density)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "CAPTURE NEW HDRI",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        fontSize = 12.sp
                                    ),
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }

                // Item 5: RECENT PROJECTS TITLE BAR
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT PROJECTS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = SlateGray
                        )
                        Text(
                            text = "VIEW ARCHIVE →",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontSize = 11.sp
                            ),
                            color = PureWhite.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { /* expand search */ }
                        )
                    }
                }

                // If projects empty, show beautiful empty state!
                if (projects.isEmpty()) {
                    item {
                        HdrEmptyState(onCreateClick = { showCreateDialog = true })
                    }
                } else {
                    // Display project cards with procedural dynamic 3D gate maps
                    items(projects, key = { it.id }) { project ->
                        HdrProjectCard(
                            project = project,
                            onClick = { viewModel.openExistingHdri(project) },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
                
                // Bottom padding inside scroll layout to avoid navigation bar clipping
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateHdriDialog(
            onDismiss = { showCreateDialog = false },
            onInitialize = { title, bracket ->
                showCreateDialog = false
                viewModel.startNewProject(title, bracket)
            }
        )
    }
}

@Composable
fun HdrProjectCard(
    project: HdrProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column {
            // Stunning procedural 3D wireframe backboard representation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .drawBehind {
                        val w = size.width
                        val h = size.height

                        // Deep slate galactic radial backdrop gradient
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    if (project.id % 2 == 0) Color(0xFF321A4B) else Color(0xFF143B4B),
                                    Color(0xFF0F1012)
                                ),
                                center = Offset(w / 2f, h / 2f),
                                radius = w * 0.75f
                            )
                        )

                        // Perspective horizontal floor
                        val floorY = h * 0.78f
                        drawLine(
                            color = PureWhite.copy(alpha = 0.12f),
                            start = Offset(0f, floorY),
                            end = Offset(w, floorY),
                            strokeWidth = 1f * density
                        )

                        // Grid perspective line beams
                        val numGridBeams = 8
                        for (i in 0..numGridBeams) {
                            val fx = (i.toFloat() / numGridBeams) * w
                            drawLine(
                                color = PureWhite.copy(alpha = 0.08f),
                                start = Offset(fx, floorY),
                                end = Offset(w / 2f + (fx - w / 2f) * 2f, h),
                                strokeWidth = 1f * density
                            )
                        }

                        // Radiant central dome portal logic
                        val cy = h * 0.40f
                        val pw = w * 0.22f
                        val ph = h * 0.42f

                        drawRoundRect(
                            color = if (project.id % 2 == 0) CyberCyan.copy(alpha = 0.12f) else SolarAmber.copy(alpha = 0.12f),
                            topLeft = Offset(w / 2f - pw / 2f, cy - ph / 2f),
                            size = Size(pw, ph),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                        )

                        drawRoundRect(
                            color = if (project.id % 2 == 0) CyberCyan.copy(alpha = 0.35f) else SolarAmber.copy(alpha = 0.35f),
                            topLeft = Offset(w / 2f - pw / 2f + 6f, cy - ph / 2f + 6f),
                            size = Size(pw - 12f, ph - 12f),
                            style = Stroke(width = 1.2f * density),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                        )

                        // Cool ambient lights
                        drawLine(
                            color = if (project.id % 2 == 0) CyberCyan.copy(alpha = 0.08f) else SolarAmber.copy(alpha = 0.08f),
                            start = Offset(w / 2f, cy),
                            end = Offset(w / 2f - w * 0.35f, h),
                            strokeWidth = 1.5f * density
                        )
                        drawLine(
                            color = if (project.id % 2 == 0) CyberCyan.copy(alpha = 0.08f) else SolarAmber.copy(alpha = 0.08f),
                            start = Offset(w / 2f, cy),
                            end = Offset(w / 2f + w * 0.35f, h),
                            strokeWidth = 1.5f * density
                        )
                    }
            ) {
                // Resolution Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, BorderGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (project.id % 2 == 0) "16K EXR" else "12K HDR",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }

                // Delete node button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Project",
                        tint = DeepCoral.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Complete/Indicator Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, BorderGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (project.isCompleted) CleanGreen else SolarAmber,
                                CircleShape
                            )
                    )
                    Text(
                        text = if (project.isCompleted) "EXR Ready" else "Incomplete (${project.capturedCount}/${project.totalPointsCount})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = if (project.isCompleted) CleanGreen else SolarAmber
                    )
                }
            }

            // Info rows lower segment
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )

                    // Dynamic range dB stops badge
                    Box(
                        modifier = Modifier
                            .background(SolarAmber.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, SolarAmber.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", project.dynamicRangeDb)} EV",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = SolarAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateStr = remember(project.createdTimestamp) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.format(Date(project.createdTimestamp))
                    }
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateGray
                    )

                    val bracketCount = when (project.bracketLevel) {
                        "Low" -> "3"
                        "Medium" -> "5"
                        else -> "7"
                    }
                    Text(
                        text = "◱ $bracketCount BRK",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SlateGray
                    )
                }
            }
        }
    }
}

@Composable
fun HdrEmptyState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No light maps captured",
            style = MaterialTheme.typography.titleMedium,
            color = PureWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Initiate a spherical high dynamic capture sequence to light up your VFX environment models.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onCreateClick,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Create First HDRI")
        }
    }
}

@Composable
fun CreateHdriDialog(
    onDismiss: () -> Unit,
    onInitialize: (title: String, bracket: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedBracket by remember { mutableStateOf("Medium") } // Low, Medium, High

    val bracketOptions = listOf(
        Triple("Low", "3 shots (EV-2, 0, +2)", "Lightweight"),
        Triple("Medium", "5 shots (EV-4, -2, 0, +2, +4)", "Standard VFX"),
        Triple("High", "7 shots (EV-6 to +6)", "Infinite Range")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGray, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "INITIALIZE LIGHT CAPTURE",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name (e.g. Sunset Studio)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderGray,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = SlateGray,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_project_title_input")
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "EXPOSURE BRACKETING DEPTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateGray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                bracketOptions.forEach { (name, label, desc) ->
                    val isSelected = selectedBracket == name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (isSelected) CyberCyan.copy(alpha = 0.05f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) CyberCyan.copy(alpha = 0.4f) else BorderGray,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedBracket = name }
                            .padding(12.dp)
                            .testTag("bracket_choice_$name"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedBracket = name },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CyberCyan,
                                unselectedColor = SlateGray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "$name Dynamic Depth",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                            Text(
                                text = "$label · $desc",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = SlateGray),
                        modifier = Modifier.testTag("dialog_cancel_btn")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalTitle = title.ifBlank { "HDRI Map ${UUID.randomUUID().toString().take(4).uppercase()}" }
                            onInitialize(finalTitle, selectedBracket)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dialog_confirm_btn")
                    ) {
                        Text("Start Capture")
                    }
                }
            }
        }
    }
}
