package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class PhoneOrientation(
    val yawDegrees: Float = 0f,  // 0 to 360 (compass heading)
    val pitchDegrees: Float = 0f, // -90 (down) to +90 (up)
    val rollDegrees: Float = 0f,  // -180 to 180 (slant angle)
    val timestamp: Long = 0
)

class OrientationManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    // Fallbacks
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _orientation = MutableStateFlow(PhoneOrientation())
    val orientation: StateFlow<PhoneOrientation> = _orientation.asStateFlow()

    private var isRegistered = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val accelValues = FloatArray(3)
    private val magValues = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    // Exponential Moving Average (EMA) smoothing variables
    private val SMOOTHING_FACTOR = 0.12f // 12% current frame / 88% historic EMA
    private var isFirstSample = true
    private var smoothedCosYaw = 0.0
    private var smoothedSinYaw = 0.0
    private var smoothedPitch = 0f
    private var smoothedRoll = 0f

    private fun processAndPublishOrientation(rawYaw: Float, rawPitch: Float, rawRoll: Float, timestamp: Long) {
        if (isFirstSample) {
            smoothedCosYaw = cos(Math.toRadians(rawYaw.toDouble()))
            smoothedSinYaw = sin(Math.toRadians(rawYaw.toDouble()))
            smoothedPitch = rawPitch
            smoothedRoll = rawRoll
            isFirstSample = false
        } else {
            // Periodic angular smoothing via trigonometric coordinate projection
            val cosY = cos(Math.toRadians(rawYaw.toDouble()))
            val sinY = sin(Math.toRadians(rawYaw.toDouble()))
            
            smoothedCosYaw = smoothedCosYaw * (1.0 - SMOOTHING_FACTOR) + cosY * SMOOTHING_FACTOR
            smoothedSinYaw = smoothedSinYaw * (1.0 - SMOOTHING_FACTOR) + sinY * SMOOTHING_FACTOR
            
            // Standard linear exponential interpolation for Pitch & Roll
            smoothedPitch = smoothedPitch * (1f - SMOOTHING_FACTOR) + rawPitch * SMOOTHING_FACTOR
            smoothedRoll = smoothedRoll * (1f - SMOOTHING_FACTOR) + rawRoll * SMOOTHING_FACTOR
        }

        var smoothedYaw = Math.toDegrees(atan2(smoothedSinYaw, smoothedCosYaw)).toFloat()
        if (smoothedYaw < 0) {
            smoothedYaw += 360f
        }

        _orientation.value = PhoneOrientation(smoothedYaw, smoothedPitch, smoothedRoll, timestamp)
    }

    fun startListening() {
        if (isRegistered) return
        isFirstSample = true
        
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Register fallbacks
            if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (magneticSensor != null) {
                sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        isRegistered = true
    }

    fun stopListening() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        hasAccel = false
        hasMag = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val remapMatrix = FloatArray(9)
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapMatrix)
            SensorManager.getOrientation(remapMatrix, orientationAngles)
            
            // Convert radians to degrees
            // yaw (z): compass orientation. Range is [-PI, PI]. Convert to [0, 360]
            var yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (yaw < 0) {
                yaw += 360f
            }
            
            // pitch (x): inclination. [-PI/2, PI/2]
            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            
            // roll (y): bank slant. [-PI, PI]
            val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

            processAndPublishOrientation(yaw, pitch, roll, event.timestamp)
        } else {
            // Fallback strategy: Accelerometer + Magnetic Field sensor
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelValues, 0, 3)
                hasAccel = true
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magValues, 0, 3)
                hasMag = true
            }

            if (hasAccel && hasMag) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) {
                    val remapMatrix = FloatArray(9)
                    SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapMatrix)
                    SensorManager.getOrientation(remapMatrix, orientationAngles)
                    var yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    if (yaw < 0) {
                        yaw += 360f
                    }
                    val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                    
                    processAndPublishOrientation(yaw, pitch, roll, event.timestamp)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not used
    }
}
