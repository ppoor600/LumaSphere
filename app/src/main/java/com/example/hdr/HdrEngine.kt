package com.example.hdr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.*

/**
 * Encapsulates a high dynamic range floating-point image.
 * RGB channels are individual float arrays of size width * height.
 */
class HdrImage(
    val width: Int,
    val height: Int,
    val rData: FloatArray,
    val gData: FloatArray,
    val bData: FloatArray
)

/**
 * Represents a single captured HDR viewing angle (patch),
 * along with its precise orientation angles (in degrees).
 */
class HdrPatch(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
    val hdrImage: HdrImage
)

object HdrEngine {

    private const val TAG = "HdrEngine"

    /**
     * Merges a series of bracketed Bitmaps into a single 32-bit HdrImage.
     * [exposureFactors] specifies the relative exposure multiplier (e.g. 2^(EV)).
     */
    fun mergeBrackets(bitmaps: List<Bitmap>, exposureFactors: List<Float>): HdrImage {
        if (bitmaps.isEmpty()) {
            return generateProceduralHdr(512, 256)
        }
        val width = bitmaps[0].width
        val height = bitmaps[0].height
        val size = width * height

        val rMerged = FloatArray(size)
        val gMerged = FloatArray(size)
        val bMerged = FloatArray(size)

        // Preallocate space to make fast
        val pixels = IntArray(width)

        // Weight function: standard triangular weight peaked at 127.5
        fun weight(v: Int): Float {
            val f = v.toFloat()
            return 1.0f - abs(f - 127.5f) / 127.5f
        }

        // Gamma expansion (gamma = 2.2)
        val gammaLut = FloatArray(256) { i ->
            (i.toFloat() / 255.0f).pow(2.2f)
        }

        for (y in 0 until height) {
            // Read rows across all bracketed images for locality
            val rowPixels = Array(bitmaps.size) { IntArray(width) }
            for (i in bitmaps.indices) {
                bitmaps[i].getPixels(rowPixels[i], 0, width, 0, y, width, 1)
            }

            for (x in 0 until width) {
                val idx = y * width + x

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                
                var weightSumR = 0f
                var weightSumG = 0f
                var weightSumB = 0f

                for (i in bitmaps.indices) {
                    val pix = rowPixels[i][x]
                    val r = Color.red(pix)
                    val g = Color.green(pix)
                    val b = Color.blue(pix)

                    val expFactor = exposureFactors.getOrElse(i) { 1.0f }

                    val wr = weight(r)
                    val wg = weight(g)
                    val wb = weight(b)

                    // Convert to linear space and divide by exposure factor
                    val linR = gammaLut[r] / expFactor
                    val linG = gammaLut[g] / expFactor
                    val linB = gammaLut[b] / expFactor

                    sumR += wr * linR
                    weightSumR += wr

                    sumG += wg * linG
                    weightSumG += wg

                    sumB += wb * linB
                    weightSumB += wb
                }

                // Apply weighted sums, with safe fallback to unweighted mean or boundary values if fully clipped
                rMerged[idx] = if (weightSumR > 0.01f) {
                    sumR / weightSumR
                } else {
                    // fall back to mid-exposure
                    val midIdx = bitmaps.size / 2
                    val rVal = Color.red(rowPixels[midIdx][x])
                    gammaLut[rVal] / exposureFactors.getOrElse(midIdx) { 1f }
                }

                gMerged[idx] = if (weightSumG > 0.01f) {
                    sumG / weightSumG
                } else {
                    val midIdx = bitmaps.size / 2
                    val gVal = Color.green(rowPixels[midIdx][x])
                    gammaLut[gVal] / exposureFactors.getOrElse(midIdx) { 1f }
                }

                bMerged[idx] = if (weightSumB > 0.01f) {
                    sumB / weightSumB
                } else {
                    val midIdx = bitmaps.size / 2
                    val bVal = Color.blue(rowPixels[midIdx][x])
                    gammaLut[bVal] / exposureFactors.getOrElse(midIdx) { 1f }
                }
            }
        }

        return HdrImage(width, height, rMerged, gMerged, bMerged)
    }

    /**
     * Stitches captured HdrPatches into a single 360x180 equirectangular HdrImage.
     * Uses 3D rotation projection and center-decay blending.
     */
    fun stitchPanoramas(
        patches: List<HdrPatch>,
        outWidth: Int = 1024,
        outHeight: Int = 512
    ): HdrImage {
        val size = outWidth * outHeight
        val rOut = FloatArray(size)
        val gOut = FloatArray(size)
        val bOut = FloatArray(size)

        // We assume each patch has a field of view of about 65 degrees horizontally,
        // which corresponds to f = 1.0 / tan(65° / 2) -> f = 1.0 / tan(0.567) ~ 1.57.
        // We will configure a safe horizontal FOV of 70 degrees for typical mobile lenses.
        val fovRad = Math.toRadians(70.0)
        val focalLength = 1.0 / tan(fovRad / 2.0)

        // Sample a bilinear pixel from a float channel safely
        fun sampleBilinear(channel: FloatArray, w: Int, h: Int, px: Float, py: Float): Float {
            val x0 = floor(px).toInt().coerceIn(0, w - 1)
            val x1 = (x0 + 1).coerceIn(0, w - 1)
            val y0 = floor(py).toInt().coerceIn(0, h - 1)
            val y1 = (y0 + 1).coerceIn(0, h - 1)

            val dx = px - x0
            val dy = py - y0

            val val00 = channel[y0 * w + x0]
            val val10 = channel[y0 * w + x1]
            val val01 = channel[y1 * w + x0]
            val val11 = channel[y1 * w + x1]

            return (1f - dx) * (1f - dy) * val00 +
                    dx * (1f - dy) * val10 +
                    (1f - dx) * dy * val01 +
                    dx * dy * val11
        }

        // Pre-create a synthetic background just in case we have holes in the panorama
        val proceduralBg = generateProceduralHdr(outWidth, outHeight)

        // 2. Compute each pixel of our equirectangular image mapping back to the camera patches
        for (y in 0 until outHeight) {
            // latitudinal angle: from +PI/2 (top) to -PI/2 (bottom)
            val phi = (0.5 - y.toDouble() / outHeight) * Math.PI
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)

            for (x in 0 until outWidth) {
                val idx = y * outWidth + x

                // longitudinal angle: from -PI to +PI
                val lambda = (x.toDouble() / outWidth) * 2.0 * Math.PI - Math.PI
                val cosLambda = cos(lambda)
                val sinLambda = sin(lambda)

                // 3D vector unit sphere (Global coordinates)
                // X points North, Y points East, Z points Skyward
                val gx = cosPhi * cosLambda
                val gy = cosPhi * sinLambda
                val gz = sinPhi

                var finalR = 0f
                var finalG = 0f
                var finalB = 0f
                var weightSum = 0f

                // Distribute across matching overlaps
                for (pIdx in patches.indices) {
                    val patch = patches[pIdx]
                    val theta = Math.toRadians(patch.yawDegrees.toDouble())
                    val phiPatch = Math.toRadians(patch.pitchDegrees.toDouble())
                    val rollRad = Math.toRadians(patch.rollDegrees.toDouble())

                    val cosTheta = cos(theta)
                    val sinTheta = sin(theta)
                    val cosPhiPatch = cos(phiPatch)
                    val sinPhiPatch = sin(phiPatch)

                    // Forward vector of the camera sensor
                    val lz = gx * cosPhiPatch * cosTheta + gy * cosPhiPatch * sinTheta + gz * sinPhiPatch

                    if (lz > 0.05) {
                        // Right and Up vectors
                        val rawLx = -gx * sinTheta + gy * cosTheta
                        val rawLy = -gx * sinPhiPatch * cosTheta - gy * sinPhiPatch * sinTheta + gz * cosPhiPatch

                        // Rotate by roll (if any)
                        val cosR = cos(rollRad)
                        val sinR = sin(rollRad)
                        val lx = rawLx * cosR - rawLy * sinR
                        val ly = rawLx * sinR + rawLy * cosR

                        val sensorU = lx / lz
                        val sensorV = ly / lz

                        // Convert sensor coordinates back into pixel space inside the patch
                        val patchW = patch.hdrImage.width
                        val patchH = patch.hdrImage.height
                        val patchAspect = patchW.toFloat() / patchH

                        // Perspective mapping
                        val px = (sensorU * focalLength / patchAspect) * (patchW / 2.0) + (patchW / 2.0)
                        val py = (sensorV * focalLength) * (patchH / 2.0) + (patchH / 2.0)

                        // Boundary check
                        if (px >= 0.0 && px < patchW - 1 && py >= 0.0 && py < patchH - 1) {
                            // Weight based on distance from center (fade to 0 near boundaries)
                            val normX = (px - patchW / 2.0) / (patchW / 2.0)
                            val normY = (py - patchH / 2.0) / (patchH / 2.0)

                            // Soft cosine dropoff-bell
                            val w = cos(normX * Math.PI / 2.2).pow(2.0) * cos(normY * Math.PI / 2.2).pow(2.0)
                            val fWeight = w.toFloat().coerceIn(0.0001f, 1f)

                            val sampR = sampleBilinear(patch.hdrImage.rData, patchW, patchH, px.toFloat(), py.toFloat())
                            val sampG = sampleBilinear(patch.hdrImage.gData, patchW, patchH, px.toFloat(), py.toFloat())
                            val sampB = sampleBilinear(patch.hdrImage.bData, patchW, patchH, px.toFloat(), py.toFloat())

                            finalR += fWeight * sampR
                            finalG += fWeight * sampG
                            finalB += fWeight * sampB
                            weightSum += fWeight
                        }
                    }
                }

                if (weightSum > 0.005f) {
                    rOut[idx] = finalR / weightSum
                    gOut[idx] = finalG / weightSum
                    bOut[idx] = finalB / weightSum
                } else {
                    // Fall back directly to the procedural physical background (prevents stitching holes)
                    rOut[idx] = proceduralBg.rData[idx]
                    gOut[idx] = proceduralBg.gData[idx]
                    bOut[idx] = proceduralBg.bData[idx]
                }
            }
        }

        return HdrImage(outWidth, outHeight, rOut, gOut, bOut)
    }

    /**
     * Builds standard 3D rotation matrix Local Vector = Mat * Global Vector.
     * Calculated by multiplying Yaw, Pitch, Roll yaw matrices.
     */
    private fun buildHdrRotationMatrix(yaw: Double, pitch: Double, roll: Double): DoubleArray {
        // Yaw rotation around Z axis
        val cY = cos(yaw)
        val sY = sin(yaw)

        // Pitch rotation around X axis
        val cP = cos(pitch)
        val sP = sin(pitch)

        // Roll rotation around Y axis
        val cR = cos(roll)
        val sR = sin(roll)

        // Local = R_y(-roll) * R_x(-pitch) * R_z(-yaw) in transpose
        // Let's compute directly for speed and precision
        // This maps a global 3D environment vector back to local phone coordinates.
        val r = DoubleArray(9)
        r[0] = cR * cY - sR * sP * sY
        r[1] = cR * sY + sR * sP * cY
        r[2] = -sR * cP

        r[3] = -cP * sY
        r[4] = cP * cY
        r[5] = sP

        r[6] = sR * cY + cR * sP * sY
        r[7] = sR * sY - cR * sP * cY
        r[8] = cR * cP
        
        return r
    }

    /**
     * Generates a spectacular procedural high-dynamic-range environment map (sky dome, sun, and ground).
     * The sun is rendered with an extremely high physical intensity (e.g., 250.0) so it shines
     * as a true high-intensity lighting source when imported in Blender/Unreal!
     */
    fun generateProceduralHdr(width: Int, height: Int): HdrImage {
        val size = width * height
        val rData = FloatArray(size)
        val gData = FloatArray(size)
        val bData = FloatArray(size)

        // High intensity Sun position (roughly pitch = 30 degrees, yaw = 45 degrees)
        val sunYaw = Math.toRadians(45.0)
        val sunPitch = Math.toRadians(30.0)
        val sunDirX = cos(sunPitch) * cos(sunYaw)
        val sunDirY = cos(sunPitch) * sin(sunYaw)
        val sunDirZ = sin(sunPitch)

        for (y in 0 until height) {
            val phi = (0.5 - y.toDouble() / height) * Math.PI
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)

            for (x in 0 until width) {
                val idx = y * width + x
                val lambda = (x.toDouble() / width) * 2.0 * Math.PI - Math.PI
                val cosLambda = cos(lambda)
                val sinLambda = sin(lambda)

                // 3D vector of this pixel
                val px = cosPhi * cosLambda
                val py = cosPhi * sinLambda
                val pz = sinPhi

                // Ambient Sky Dome / Ground Plane gradient
                if (pz >= 0.0) {
                    // Sky gradient: Deep space blue at zenith to neon teal on the horizon
                    val factor = pz.toFloat() // 0 near horizon, 1 at peak
                    rData[idx] = 0.02f * (1f - factor) + 0.01f * factor
                    gData[idx] = 0.07f * (1f - factor) + 0.04f * factor
                    bData[idx] = 0.15f * (1f - factor) + 0.35f * factor

                    // Add a brilliant sun disk
                    // Dot product between pixel vector and sun vector
                    val dot = px * sunDirX + py * sunDirY + pz * sunDirZ
                    if (dot > 0.0) {
                        val angle = acos(dot)
                        if (angle < 0.03) { // Sun core
                            rData[idx] += 300.0f
                            gData[idx] += 280.0f
                            bData[idx] += 220.0f
                        } else if (angle < 0.15) { // Sun soft corona/glow
                            val glow = exp(-35.0f * angle.toFloat())
                            rData[idx] += glow * 15.0f
                            gData[idx] += glow * 12.0f
                            bData[idx] += glow * 8.0f
                        }
                    }
                } else {
                    // Ground plane: Dark graphite gravel with subtle golden horizon glow
                    val factor = (-pz).toFloat() // 0 near horizon, 1 at bottom nadir
                    
                    // Horizon orange sunset haze leakage
                    val horizonHaze = exp(-20.0f * factor)
                    rData[idx] = 0.03f * factor + horizonHaze * 0.06f
                    gData[idx] = 0.03f * factor + horizonHaze * 0.03f
                    bData[idx] = 0.038f * factor
                }
            }
        }

        return HdrImage(width, height, rData, gData, bData)
    }
}
