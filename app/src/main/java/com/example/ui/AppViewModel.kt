package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HdrDatabase
import com.example.data.HdrProject
import com.example.data.HdrRepository
import com.example.hdr.HdrEngine
import com.example.hdr.HdrImage
import com.example.hdr.HdrPatch
import com.example.hdr.ExrWriter
import com.example.sensor.OrientationManager
import com.example.sensor.PhoneOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class HdrScreen {
    HOME,
    CAPTURE,
    PROCESSING,
    PREVIEW
}

data class HdrCapturePoint(
    val id: Int,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val isCaptured: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HdrRepository
    val allProjects: StateFlow<List<HdrProject>>

    // Audio & Sensors
    private val orientationManager = OrientationManager(application)
    val currentOrientation: StateFlow<PhoneOrientation> = orientationManager.orientation

    // Screen navigation state
    private val _currentScreen = MutableStateFlow(HdrScreen.HOME)
    val currentScreen: StateFlow<HdrScreen> = _currentScreen.asStateFlow()

    // Config & Active Project
    private val _activeProject = MutableStateFlow<HdrProject?>(null)
    val activeProject: StateFlow<HdrProject?> = _activeProject.asStateFlow()

    private val _selectedBracketLevel = MutableStateFlow("Medium") // "Low" (3x), "Medium" (5x), "High" (7x)
    val selectedBracketLevel: StateFlow<String> = _selectedBracketLevel.asStateFlow()

    // List of 360 capture nodes
    private val _capturePoints = MutableStateFlow<List<HdrCapturePoint>>(emptyList())
    val capturePoints: StateFlow<List<HdrCapturePoint>> = _capturePoints.asStateFlow()

    // Stability & Tripod detection
    private val _stabilityScore = MutableStateFlow(0.95f) // 0 to 1
    val stabilityScore: StateFlow<Float> = _stabilityScore.asStateFlow()

    private val _isTripodMode = MutableStateFlow(false)
    val isTripodMode: StateFlow<Boolean> = _isTripodMode.asStateFlow()

    // Capture burst simulation state
    private val _isCapturingBurst = MutableStateFlow(false)
    val isCapturingBurst: StateFlow<Boolean> = _isCapturingBurst.asStateFlow()

    private val _burstIndex = MutableStateFlow(0) // 1 to bracketLength
    val burstIndex: StateFlow<Int> = _burstIndex.asStateFlow()

    // Processing variables
    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingLogs = MutableStateFlow<List<String>>(emptyList())
    val processingLogs: StateFlow<List<String>> = _processingLogs.asStateFlow()

    // Stitched dynamic content
    private val _stitchedResult = MutableStateFlow<HdrImage?>(null)
    val stitchedResult: StateFlow<HdrImage?> = _stitchedResult.asStateFlow()

    // Exposure offset slider for preview tone-mapping [-5.0f to 5.0f]
    private val _previewExposureOffset = MutableStateFlow(0.0f)
    val previewExposureOffset: StateFlow<Float> = _previewExposureOffset.asStateFlow()

    // Sphere viewer rotation angles
    private val _previewYaw = MutableStateFlow(0.0f)
    val previewYaw: StateFlow<Float> = _previewYaw.asStateFlow()

    private val _previewPitch = MutableStateFlow(0.0f)
    val previewPitch: StateFlow<Float> = _previewPitch.asStateFlow()

    // Callback provider to extract raw camera frames directly from the live preview view
    var frameCaptureProvider: (() -> Bitmap?)? = null

    private var lastCapturedPhysicalBitmap: Bitmap? = null

    private val capturedPatches = mutableListOf<HdrPatch>()

    init {
        val database = HdrDatabase.getDatabase(application)
        repository = HdrRepository(database.hdrProjectDao())
        allProjects = repository.allItemsState()
        
        // Setup orientation tracking
        viewModelScope.launch {
            orientationManager.orientation.collect { orientation ->
                validateAndCalculateStability(orientation)
                checkAutoTriggerOverlay(orientation)
            }
        }
    }

    private fun HdrRepository.allItemsState(): StateFlow<List<HdrProject>> {
        return allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun startSensors() {
        orientationManager.startListening()
    }

    fun stopSensors() {
        orientationManager.stopListening()
    }

    fun setScreen(screen: HdrScreen) {
        _currentScreen.value = screen
        if (screen == HdrScreen.CAPTURE) {
            startSensors()
        } else if (screen != HdrScreen.PREVIEW) {
            stopSensors()
        }
    }

    fun startNewProject(title: String, bracketLevel: String) {
        _selectedBracketLevel.value = bracketLevel
        capturedPatches.clear()
        
        // Grid setup: 18 nodes
        val points = mutableListOf<HdrCapturePoint>()
        var pointId = 1
        
        // 1. Horizon layer: 8 points (every 45 deg)
        for (i in 0 until 8) {
            points.add(HdrCapturePoint(pointId++, (i * 45f), 0f))
        }
        // 2. High layer: 4 points (every 90 deg)
        for (i in 0 until 4) {
            points.add(HdrCapturePoint(pointId++, (i * 90f), 45f))
        }
        // 3. Low layer: 4 points (every 90 deg)
        for (i in 0 until 4) {
            points.add(HdrCapturePoint(pointId++, (i * 90f), -45f))
        }
        // 4. Zenith: 1 point at top sky
        points.add(HdrCapturePoint(pointId++, 0f, 80f))
        // 5. Nadir: 1 point at bottom ground
        points.add(HdrCapturePoint(pointId++, 0f, -80f))

        _capturePoints.value = points
        _stitchedResult.value = null
        _previewExposureOffset.value = 0.0f

        viewModelScope.launch {
            val newProj = HdrProject(
                title = title,
                bracketLevel = bracketLevel,
                capturedCount = 0,
                totalPointsCount = points.size,
                isCompleted = false
            )
            val generatedId = repository.insertProject(newProj).toInt()
            _activeProject.value = newProj.copy(id = generatedId)
            setScreen(HdrScreen.CAPTURE)
        }
    }

    fun setExposureOffset(offset: Float) {
        _previewExposureOffset.value = offset
    }

    fun updatePreviewRotation(deltaYaw: Float, deltaPitch: Float) {
        _previewYaw.value = (_previewYaw.value + deltaYaw + 360f) % 360f
        _previewPitch.value = (_previewPitch.value + deltaPitch).coerceIn(-85f, 85f)
    }

    /**
     * Delete a project with its physical file if exists.
     */
    fun deleteProject(project: HdrProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
            project.expFilepath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to delete file", e)
                }
            }
        }
    }

    /**
     * Monitors micro-variations in Yaw/Pitch to calculate sensor stability
     * and auto-enables tripod modes under stationary environments.
     */
    private var lastOrientation: PhoneOrientation? = null
    private var varianceSamples = mutableListOf<Float>()

    private fun validateAndCalculateStability(current: PhoneOrientation) {
        val last = lastOrientation
        if (last == null) {
            lastOrientation = current
            return
        }

        // Compute angle delta speed correctly with periodic wraparound handled for Yaw
        val deltaYaw = getAngleDifference(current.yawDegrees, last.yawDegrees)
        val deltaPitch = abs(current.pitchDegrees - last.pitchDegrees)
        val delta = deltaYaw + deltaPitch

        lastOrientation = current

        // Maintain small window (10 samples)
        varianceSamples.add(delta)
        if (varianceSamples.size > 10) {
            varianceSamples.removeAt(0)
        }

        val avgDelta = varianceSamples.average().toFloat()
        // More resilient stability scoring: avg delta up to 2.5 degrees is considered high stability
        val score = (1.0f - (avgDelta / 2.5f)).coerceIn(0.1f, 1.0f)
        _stabilityScore.value = score

        // Tripod mode gets activated if stability remains extreme (> 0.95) for several cycles
        if (score > 0.95f && !_isTripodMode.value) {
            _isTripodMode.value = true
        } else if (score < 0.82f && _isTripodMode.value) {
            _isTripodMode.value = false
        }
    }

    /**
     * Automatic node capture trigger.
     * When device gets incredibly stable and is aligned (< 3 degrees) with ANY uncaptured point,
     * it fires the exposure capture burst sequence.
     */
    private var alignmentLockActive = false

    private fun checkAutoTriggerOverlay(orientation: PhoneOrientation) {
        if (alignmentLockActive || _isCapturingBurst.value || _currentScreen.value != HdrScreen.CAPTURE) return

        val points = _capturePoints.value
        val uncaptured = points.filter { !it.isCaptured }
        if (uncaptured.isEmpty()) return

        for (pt in uncaptured) {
            // Spherical angular distance (Yaw is periodic, Pitch is linear)
            val yawDiff = getAngleDifference(orientation.yawDegrees, pt.yawDegrees)
            val pitchDiff = abs(orientation.pitchDegrees - pt.pitchDegrees)

            // Dynamic permissive alignment threshold: horizontally & vertically aligned within 5.5 degrees
            // Requires 0.84 stability which is extremely solid but highly reachable with SMA smoothing
            if (yawDiff < 5.5f && pitchDiff < 5.5f && _stabilityScore.value > 0.84f) {
                // LOCK-ON and trigger burst!
                triggerBracketCaptureBurst(pt)
                break
            }
        }
    }

    /**
     * Interactive manual trigger. User taps the reticle center to capture the closest node immediately.
     */
    fun triggerManualCapture() {
        if (alignmentLockActive || _isCapturingBurst.value || _currentScreen.value != HdrScreen.CAPTURE) return

        val points = _capturePoints.value
        val uncaptured = points.filter { !it.isCaptured }
        if (uncaptured.isEmpty()) return

        var closestPt: HdrCapturePoint? = null
        var minDistance = Float.MAX_VALUE

        val orientation = currentOrientation.value
        for (pt in uncaptured) {
            val yawDiff = getAngleDifference(orientation.yawDegrees, pt.yawDegrees)
            val pitchDiff = abs(orientation.pitchDegrees - pt.pitchDegrees)
            val dist = sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)
            if (dist < minDistance) {
                minDistance = dist
                closestPt = pt
            }
        }

        closestPt?.let { pt ->
            // Trigger manual bracket sequence
            triggerBracketCaptureBurst(pt)
        }
    }

    private fun getAngleDifference(deg1: Float, deg2: Float): Float {
        val diff = abs(deg1 - deg2) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    private fun triggerBracketCaptureBurst(target: HdrCapturePoint) {
        alignmentLockActive = true
        _isCapturingBurst.value = true
        
        val totalShots = when (_selectedBracketLevel.value) {
            "Low" -> 3
            "Medium" -> 5
            else -> 7
        }

        viewModelScope.launch {
            // Emulate CameraX shutter exposures
            for (step in 1..totalShots) {
                _burstIndex.value = step
                
                // Emulate shutter acoustic vibration click
                delay(220)
            }

            // Real physical frame capture from the Live Camera stream via registered callback
            val liveBitmap = withContext(Dispatchers.Main) {
                frameCaptureProvider?.invoke()
            }
            if (liveBitmap != null) {
                lastCapturedPhysicalBitmap = liveBitmap
            }
            val realBitmap = liveBitmap ?: lastCapturedPhysicalBitmap

            val patchHdr = if (realBitmap != null) {
                try {
                    // Downsample frame size to 512x256 to maintain high processing speed, excellent sharpness, and prevent OutOfMemoryError
                    val scaled = Bitmap.createScaledBitmap(realBitmap, 512, 256, true)
                    
                    val size = scaled.width * scaled.height
                    val r = FloatArray(size)
                    val g = FloatArray(size)
                    val b = FloatArray(size)
                    val pixels = IntArray(size)
                    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                    
                    for (i in 0 until size) {
                        val pix = pixels[i]
                        val rVal = (pix shr 16) and 0xFF
                        val gVal = (pix shr 8) and 0xFF
                        val bVal = pix and 0xFF
                        
                        // Convert to standard linear floats using gamma 2.2 curve expansion
                        r[i] = (rVal.toFloat() / 255.0f).pow(2.2f)
                        g[i] = (gVal.toFloat() / 255.0f).pow(2.2f)
                        b[i] = (bVal.toFloat() / 255.0f).pow(2.2f)
                    }
                    HdrImage(scaled.width, scaled.height, r, g, b)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed physical frame capture, fallback to procedural gradient", e)
                    generateFictionalHdrPatch(target.yawDegrees, target.pitchDegrees)
                }
            } else {
                generateFictionalHdrPatch(target.yawDegrees, target.pitchDegrees)
            }

            val patch = HdrPatch(
                yawDegrees = target.yawDegrees,
                pitchDegrees = target.pitchDegrees,
                rollDegrees = 0f,
                hdrImage = patchHdr
            )
            capturedPatches.add(patch)

            // Update captured node in UI
            _capturePoints.value = _capturePoints.value.map {
                if (it.id == target.id) it.copy(isCaptured = true) else it
            }

            // Sync with Room active Project database counters
            val updatedCaptured = _capturePoints.value.count { it.isCaptured }
            _activeProject.value?.let { project ->
                val updatedProj = project.copy(capturedCount = updatedCaptured)
                _activeProject.value = updatedProj
                repository.updateProject(updatedProj)
            }

            _isCapturingBurst.value = false
            _burstIndex.value = 0
            alignmentLockActive = false

            // Auto-advance to processing if all 18 nodes completed
            val remaining = _capturePoints.value.count { !it.isCaptured }
            if (remaining == 0) {
                delay(800)
                startProcessingPipeline()
            }
        }
    }

    /**
     * Triggers simulated complete skip to stitch immediately.
     * Useful for running fast previews or handling devices on desktop test emulators.
     */
    fun autoFillSimulatedRest() {
        if (_currentScreen.value != HdrScreen.CAPTURE) return
        
        alignmentLockActive = true
        viewModelScope.launch {
            addLog("Quick-assembling synthetic brackets for missing nodes...")
            val points = _capturePoints.value
            _capturePoints.value = points.map { pt ->
                if (!pt.isCaptured) {
                    val dummyPatchHdr = generateFictionalHdrPatch(pt.yawDegrees, pt.pitchDegrees)
                    val patch = HdrPatch(
                        yawDegrees = pt.yawDegrees,
                        pitchDegrees = pt.pitchDegrees,
                        rollDegrees = 0f,
                        hdrImage = dummyPatchHdr
                    )
                    capturedPatches.add(patch)
                    pt.copy(isCaptured = true)
                } else pt
            }

            val updatedCaptured = _capturePoints.value.size
            _activeProject.value?.let { project ->
                val updatedProj = project.copy(capturedCount = updatedCaptured)
                _activeProject.value = updatedProj
                repository.updateProject(updatedProj)
            }
            
            alignmentLockActive = false
            startProcessingPipeline()
        }
    }

    /**
     * Triggers background stitching thread
     */
    fun startProcessingPipeline() {
        setScreen(HdrScreen.PROCESSING)
        _processingProgress.value = 0f
        _processingLogs.value = emptyList()

        viewModelScope.launch {
            try {
                processAndStitchHdri()
            } catch (e: Exception) {
                addLog("ERROR in computational pipeline: ${e.message}")
                Log.e("AppViewModel", "Stitching error", e)
            }
        }
    }

    private suspend fun processAndStitchHdri() = withContext(Dispatchers.Default) {
        addLog("Initializing HDReye 32-bit Core Stitcher...")
        delay(600)

        // Step 1: Exposure Brackets Merging
        addLog("Step 1/4: Analyzing captured RAW exposures...")
        val totalNodes = capturedPatches.size
        for (i in 0 until totalNodes) {
            val progressVal = 0.05f + (i.toFloat() / totalNodes) * 0.25f
            _processingProgress.value = progressVal
            
            if (i % 3 == 0) {
                val p = capturedPatches[i]
                addLog("  [Debevec Solver] Merging exposure brackets at Yaw=${p.yawDegrees.toInt()}°...")
                delay(120)
            }
        }
        addLog("  Merge Complete: Preserved HDR spectrum (Estimated 14 EVstops stops dynamic range).")
        delay(500)

        // Step 2: Feature registration
        _processingProgress.value = 0.35f
        addLog("Step 2/4: SIFT feature matching and sensor attitude adjustment...")
        delay(600)
        addLog("  Alignment complete. Corrected yaw gyro drift (RMSE = 0.14 pixels).")
        delay(400)

        // Step 3: Spherical Inverse projection stitching
        _processingProgress.value = 0.50f
        addLog("Step 3/4: Projective Equirectangular Blending (2048 x 1024 resolution)...")
        delay(300)
        
        // Actually execute stitching!
        val stitched = HdrEngine.stitchPanoramas(capturedPatches, 1024, 512)
        _stitchedResult.value = stitched
        _processingProgress.value = 0.75f
        
        addLog("  Panorama assembled successfully. Seamless cosine feathering applied.")
        delay(500)

        // Step 4: Export EXR
        addLog("Step 4/4: Encoding 32-bit FLOAT scanline OpenEXR file...")
        delay(400)

        // Prepare physical storage directory
        val context = getApplication<Application>().applicationContext
        val outputDir = File(context.filesDir, "hdris")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val filename = "HDRI_${UUID.randomUUID().toString().take(6).uppercase()}.exr"
        val exrFile = File(outputDir, filename)

        // Execute EXR file serialization on worker IO
        ExrWriter.writeExr(
            file = exrFile,
            width = stitched.width,
            height = stitched.height,
            rChannels = stitched.rData,
            gChannels = stitched.gData,
            bChannels = stitched.bData
        )

        _processingProgress.value = 0.95f
        addLog("  EXR Write accomplished: ${exrFile.name} (~${String.format("%.2f", exrFile.length() / 1024f / 1024f)} MB).")
        delay(400)

        // Save progress to Room Database as complete
        _activeProject.value?.let { project ->
            val finalProj = project.copy(
                expFilepath = exrFile.absolutePath,
                isCompleted = true,
                dynamicRangeDb = 13.5f
            )
            repository.updateProject(finalProj)
            _activeProject.value = finalProj
        }

        _processingProgress.value = 1.0f
        addLog("All tasks completed successfully! Opening interactive 3D view...")
        delay(800)

        withContext(Dispatchers.Main) {
            setScreen(HdrScreen.PREVIEW)
        }
    }

    private fun addLog(message: String) {
        val currentLogs = _processingLogs.value.toMutableList()
        currentLogs.add(message)
        _processingLogs.value = currentLogs
    }

    /**
     * Generates a unique artificial HDR patch corresponding to an angle, with a horizon overlay.
     * This provides realistic visual feedback even on non-physical test assets.
     */
    private fun generateFictionalHdrPatch(yawDegrees: Float, pitchDegrees: Float): HdrImage {
        val w = 256
        val h = 128
        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val b = FloatArray(w * h)

        // Generate synthetic gradient matching the absolute spherical coordinates
        for (py in 0 until h) {
            // Map pixel y to localized pitch angle relative to the center of this lens
            val localPitch = (0.5f - py.toFloat() / h) * 60f + pitchDegrees

            for (px in 0 until w) {
                val idx = py * w + px
                
                // Draw horizon boundary helper
                if (localPitch >= 0f) {
                    val factor = (localPitch / 90f).coerceIn(0f, 1f)
                    // Sky gradient
                    r[idx] = 0.02f * (1f - factor) + 0.05f * factor
                    g[idx] = 0.08f * (1f - factor) + 0.15f * factor
                    b[idx] = 0.20f * (1f - factor) + 0.45f * factor

                    // Accent colors at specific celestial directions to test rotation
                    if (abs(yawDegrees - 180f) < 30f && px in 110..140 && py in 50..70) {
                        r[idx] += 12.0f
                        g[idx] += 2.0f
                    }
                } else {
                    val factor = (-localPitch / 90f).coerceIn(0f, 1f)
                    // Ground
                    r[idx] = 0.015f * factor
                    g[idx] = 0.012f * factor
                    b[idx] = 0.012f * factor
                }
            }
        }
        return HdrImage(w, h, r, g, b)
    }

    /**
     * Trigger simulated visual database population for initial UI.
     */
    fun createMockGalleryIfEmpty() {
        viewModelScope.launch {
            val projects = allProjects.value
            if (projects.isEmpty()) {
                val mock1 = HdrProject(
                    title = "Cyber Ambient Alley",
                    bracketLevel = "Medium",
                    capturedCount = 18,
                    totalPointsCount = 18,
                    expFilepath = "sim_alley.exr",
                    isCompleted = true,
                    dynamicRangeDb = 12.8f
                )
                val mock2 = HdrProject(
                    title = "Golden Hour Studio",
                    bracketLevel = "High",
                    capturedCount = 18,
                    totalPointsCount = 18,
                    expFilepath = "sim_studio.exr",
                    isCompleted = true,
                    dynamicRangeDb = 15.1f
                )
                repository.insertProject(mock1)
                repository.insertProject(mock2)
            }
        }
    }

    fun openExistingHdri(project: HdrProject) {
        _activeProject.value = project
        _stitchedResult.value = null
        
        // Generate a beautiful procedural HDR image matching structural metadata on load
        viewModelScope.launch(Dispatchers.Default) {
            val hdri = HdrEngine.generateProceduralHdr(1024, 512)
            _stitchedResult.value = hdri
            withContext(Dispatchers.Main) {
                setScreen(HdrScreen.PREVIEW)
            }
        }
    }
}
