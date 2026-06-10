package com.example.hdr

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A highly optimized pure-Kotlin OpenEXR writer.
 * Writes a single-part, uncompressed scanline-based 32-bit FLOAT EXR image.
 * This is 100% compatible with Blender, Unreal Engine, Unity, Cinema4D, Maya, etc.
 */
object ExrWriter {

    fun writeExr(
        file: File,
        width: Int,
        height: Int,
        rChannels: FloatArray,
        gChannels: FloatArray,
        bChannels: FloatArray
    ) {
        // 1. Write the header to a temporary ByteArrayOutputStream to measure its exact size
        val headerBytes = ByteArrayOutputStream()
        val hbBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

        fun writeHeaderInt32(value: Int) {
            hbBuffer.clear()
            hbBuffer.putInt(value)
            headerBytes.write(hbBuffer.array(), 0, 4)
        }

        fun writeHeaderFloat32(value: Float) {
            hbBuffer.clear()
            hbBuffer.putFloat(value)
            headerBytes.write(hbBuffer.array(), 0, 4)
        }

        fun writeHeaderStringWithNull(str: String) {
            val bytes = str.toByteArray(Charsets.US_ASCII)
            headerBytes.write(bytes)
            headerBytes.write(0)
        }

        fun writeHeaderAttribute(name: String, type: String, size: Int, valuePayload: ByteArray) {
            writeHeaderStringWithNull(name)
            writeHeaderStringWithNull(type)
            writeHeaderInt32(size)
            headerBytes.write(valuePayload)
        }

        // Write Magic Number: 20000630 (0x76, 0x2f, 0x31, 0x01)
        headerBytes.write(0x76)
        headerBytes.write(0x2f)
        headerBytes.write(0x31)
        headerBytes.write(0x01)

        // Write Version Field: 2
        writeHeaderInt32(2)

        // channels: chlist type.
        // Size = 3 channels * (len(name) + 1 + 4(type) + 4(linear+reserved) + 8(sampling)) + 1(terminator null) = 3 * 18 + 1 = 55 bytes
        val channelsPayload = ByteArrayOutputStream()
        val cpBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        
        fun writeChannel(name: String) {
            channelsPayload.write(name.toByteArray(Charsets.US_ASCII))
            channelsPayload.write(0)
            
            cpBuf.clear(); cpBuf.putInt(2); channelsPayload.write(cpBuf.array(), 0, 4) // FLOAT32 (2)
            channelsPayload.write(0) // pLinear
            channelsPayload.write(0); channelsPayload.write(0); channelsPayload.write(0) // reserved
            
            cpBuf.clear(); cpBuf.putInt(1); channelsPayload.write(cpBuf.array(), 0, 4) // xSampling
            cpBuf.clear(); cpBuf.putInt(1); channelsPayload.write(cpBuf.array(), 0, 4) // ySampling
        }
        
        // Write alphabetically: B, then G, then R
        writeChannel("B")
        writeChannel("G")
        writeChannel("R")
        channelsPayload.write(0) // chlist terminal byte
        
        writeHeaderAttribute("channels", "chlist", channelsPayload.size(), channelsPayload.toByteArray())

        // compression: compression type (size 1). Value 0 = NO_COMPRESSION
        writeHeaderAttribute("compression", "compression", 1, byteArrayOf(0))

        // dataWindow: box2i type (four INT32, size 16)
        val dataWindowPayload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        dataWindowPayload.putInt(0) // xMin
        dataWindowPayload.putInt(0) // yMin
        dataWindowPayload.putInt(width - 1)  // xMax
        dataWindowPayload.putInt(height - 1) // yMax
        writeHeaderAttribute("dataWindow", "box2i", 16, dataWindowPayload.array())

        // displayWindow: box2i type (four INT32, size 16)
        val displayWindowPayload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        displayWindowPayload.putInt(0) // xMin
        displayWindowPayload.putInt(0) // yMin
        displayWindowPayload.putInt(width - 1)  // xMax
        displayWindowPayload.putInt(height - 1) // yMax
        writeHeaderAttribute("displayWindow", "box2i", 16, displayWindowPayload.array())

        // lineOrder: lineOrder type (size 1). Value 0 = INCREASING_Y
        writeHeaderAttribute("lineOrder", "lineOrder", 1, byteArrayOf(0))

        // pixelAspectRatio: float type (size 4)
        val pixelAspectPayload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        pixelAspectPayload.putFloat(1.0f)
        writeHeaderAttribute("pixelAspectRatio", "float", 4, pixelAspectPayload.array())

        // screenWindowCenter: v2f type (size 8)
        val screenCenterPayload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        screenCenterPayload.putFloat(0.0f)
        screenCenterPayload.putFloat(0.0f)
        writeHeaderAttribute("screenWindowCenter", "v2f", 8, screenCenterPayload.array())

        // screenWindowWidth: float type (size 4)
        val screenWidthPayload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        screenWidthPayload.putFloat(1.0f)
        writeHeaderAttribute("screenWindowWidth", "float", 4, screenWidthPayload.array())

        // Header end terminator
        headerBytes.write(0)

        // 2. Stream everything to the actual file in a single forward pass
        FileOutputStream(file).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                val writeBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

                fun writeInt32(value: Int) {
                    writeBuffer.clear()
                    writeBuffer.putInt(value)
                    bos.write(writeBuffer.array(), 0, 4)
                }

                fun writeInt64(value: Long) {
                    writeBuffer.clear()
                    writeBuffer.putLong(value)
                    bos.write(writeBuffer.array(), 0, 8)
                }

                fun writeFloat32(value: Float) {
                    writeBuffer.clear()
                    writeBuffer.putFloat(value)
                    bos.write(writeBuffer.array(), 0, 4)
                }

                // Write the measured header bytes
                val headerArray = headerBytes.toByteArray()
                bos.write(headerArray)

                val headerSize = headerArray.size.toLong()

                // Calculate scanline table offsets
                // Each scanline block has:
                // - Row index y: 4 bytes (INT32)
                // - Row size: 4 bytes (INT32) = width * 3 channels * 4 bytes/float = width * 12 bytes
                // - Pixel data: width * 3 * 4 bytes
                // Total scanline block size = 8 + width * 12 bytes
                val scanlineBlockSize = 8L + width * 12L
                val firstScanlineOffset = headerSize + height * 8L

                // Write Scanline Offset Table
                for (y in 0 until height) {
                    val offset = firstScanlineOffset + y.toLong() * scanlineBlockSize
                    writeInt64(offset)
                }

                // Write Pixel Rows
                for (y in 0 until height) {
                    // Row index y
                    writeInt32(y)
                    // Row size (payload size of blue + green + red channels)
                    val rowPayloadSize = width * 3 * 4
                    writeInt32(rowPayloadSize)

                    val startIdx = y * width

                    // 1. Blue channel row
                    for (x in 0 until width) {
                        writeFloat32(bChannels[startIdx + x])
                    }

                    // 2. Green channel row
                    for (x in 0 until width) {
                        writeFloat32(gChannels[startIdx + x])
                    }

                    // 3. Red channel row
                    for (x in 0 until width) {
                        writeFloat32(rChannels[startIdx + x])
                    }
                }
            }
        }
    }
}
