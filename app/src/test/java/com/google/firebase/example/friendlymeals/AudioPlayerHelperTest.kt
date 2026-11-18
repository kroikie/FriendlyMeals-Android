package com.google.firebase.example.friendlymeals

import com.google.firebase.example.friendlymeals.ui.shared.AudioPlayerHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPlayerHelperTest {

    @Test
    fun ensureWavBytes_withExistingWavHeader_returnsOriginalBytes() {
        val existingWav = ByteArray(44)
        existingWav[0] = 'R'.code.toByte()
        existingWav[1] = 'I'.code.toByte()
        existingWav[2] = 'F'.code.toByte()
        existingWav[3] = 'F'.code.toByte()
        existingWav[8] = 'W'.code.toByte()
        existingWav[9] = 'A'.code.toByte()
        existingWav[10] = 'V'.code.toByte()
        existingWav[11] = 'E'.code.toByte()

        val result = AudioPlayerHelper.ensureWavBytes(existingWav)
        assertArrayEquals(existingWav, result)
    }

    @Test
    fun ensureWavBytes_withRawPcm_prependsValidWavHeader() {
        val rawPcm = ByteArray(100) { it.toByte() }
        val sampleRate = 24000
        val channels = 1
        val bitDepth = 16

        val result = AudioPlayerHelper.ensureWavBytes(rawPcm, sampleRate, channels, bitDepth)

        assertEquals(44 + rawPcm.size, result.size)

        // Check RIFF header
        assertEquals('R'.code.toByte(), result[0])
        assertEquals('I'.code.toByte(), result[1])
        assertEquals('F'.code.toByte(), result[2])
        assertEquals('F'.code.toByte(), result[3])

        // Check WAVE
        assertEquals('W'.code.toByte(), result[8])
        assertEquals('A'.code.toByte(), result[9])
        assertEquals('V'.code.toByte(), result[10])
        assertEquals('E'.code.toByte(), result[11])

        // Check fmt
        assertEquals('f'.code.toByte(), result[12])
        assertEquals('m'.code.toByte(), result[13])
        assertEquals('t'.code.toByte(), result[14])
        assertEquals(' '.code.toByte(), result[15])

        // Check sample rate in little endian
        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        val extractedSampleRate = buffer.getInt(24)
        assertEquals(24000, extractedSampleRate)

        // Check byte rate = 24000 * 1 * 2 = 48000
        val extractedByteRate = buffer.getInt(28)
        assertEquals(48000, extractedByteRate)

        // Check channels
        val extractedChannels = buffer.getShort(22).toInt()
        assertEquals(1, extractedChannels)

        // Check bits per sample
        val extractedBitsPerSample = buffer.getShort(34).toInt()
        assertEquals(16, extractedBitsPerSample)

        // Check data subchunk size
        val extractedDataSize = buffer.getInt(40)
        assertEquals(rawPcm.size, extractedDataSize)

        // Verify PCM payload matches
        for (i in rawPcm.indices) {
            assertEquals(rawPcm[i], result[44 + i])
        }
    }
}

