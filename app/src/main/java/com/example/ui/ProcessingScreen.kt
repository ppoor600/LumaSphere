package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProcessingScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val progress by viewModel.processingProgress.collectAsState()
    val logs by viewModel.processingLogs.collectAsState()

    val logListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Automatically scroll core console logs to the bottom as they stream
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch {
                logListState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    // Modern spinning animation for the loader rings
    val infiniteTransition = rememberInfiniteTransition()
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "COMPUTATIONAL VFX COPROCESSOR",
            style = MaterialTheme.typography.labelMedium,
            color = SolarAmber,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Assembling equirectangular 32-bit map...",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateGray,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        // Spectacular circular glowing composite loader
        Box(
            modifier = Modifier
                .size(160.dp)
                .testTag("processing_loader_box"),
            contentAlignment = Alignment.Center
        ) {
            // Glowing outer circle brush
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .rotate(spinAngle)
                    .border(
                        width = 4.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(CyberCyan, Color.Transparent, CyberCyan, Color.Transparent)
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )

            // Inverse spinning inner circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .rotate(-spinAngle * 1.5f)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(SolarAmber, Color.Transparent, SolarAmber, Color.Transparent)
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )

            // Centered progress percentage readout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    ),
                    color = PureWhite
                )
                Text(
                    text = "STITCHING",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateGray
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Progress Linear Bar
        LinearProgressIndicator(
            progress = progress,
            color = CyberCyan,
            trackColor = BorderGray,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .testTag("processing_progress_bar")
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Cyberpunk terminal console log output box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = SlateGray, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "COPROCESSOR PIPELINE MONITOR",
                style = MaterialTheme.typography.labelSmall,
                color = SlateGray,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            state = logListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .testTag("terminal_console_logs"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.labelSmall.copy(
                        lineHeight = 16.sp,
                        letterSpacing = 0.sp
                    ),
                    color = if (log.contains("ERROR")) DeepCoral else if (log.contains("Complete") || log.contains("successfully") || log.contains("EXR Ready")) CleanGreen else if (log.startsWith("Step")) CyberCyan else PureWhite
                )
            }
        }
    }
}
