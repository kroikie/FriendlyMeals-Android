package com.google.firebase.example.friendlymeals.ui.shared

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class AudioPlayerHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var onCompletionCallback: (() -> Unit)? = null

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }

    fun play(audioBytes: ByteArray, onCompletion: () -> Unit) {
        stop()
        onCompletionCallback = onCompletion

        try {
            val wavBytes = ensureWavBytes(audioBytes)
            val tempFile = File(context.cacheDir, "recipe_instructions_tts.wav")
            FileOutputStream(tempFile).use { fos ->
                fos.write(wavBytes)
                fos.flush()
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    onCompletionCallback?.invoke()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            onCompletion()
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio", e)
        }
    }

    fun resume() {
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio", e)
        }
    }

    fun restart() {
        try {
            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting audio", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stop()
        onCompletionCallback = null
    }

    companion object {
        private const val TAG = "AudioPlayerHelper"

        /**
         * Checks whether [audioBytes] is already a RIFF/WAVE file.
         * If it is raw PCM (16-bit 24kHz mono little-endian), prepends a 44-byte WAV header.
         */
        fun ensureWavBytes(
            audioBytes: ByteArray,
            sampleRate: Int = 24000,
            channels: Int = 1,
            bitDepth: Int = 16
        ): ByteArray {
            if (audioBytes.size >= 12 &&
                audioBytes[0] == 'R'.code.toByte() &&
                audioBytes[1] == 'I'.code.toByte() &&
                audioBytes[2] == 'F'.code.toByte() &&
                audioBytes[3] == 'F'.code.toByte() &&
                audioBytes[8] == 'W'.code.toByte() &&
                audioBytes[9] == 'A'.code.toByte() &&
                audioBytes[10] == 'V'.code.toByte() &&
                audioBytes[11] == 'E'.code.toByte()
            ) {
                return audioBytes
            }

            val totalAudioLen = audioBytes.size
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * channels * (bitDepth / 8)
            val header = ByteArray(44)

            // RIFF/WAVE header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // 'fmt ' chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // 16 bytes for PCM format chunk length
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // Audio format = 1 (linear PCM)
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * (bitDepth / 8)).toByte() // Block align
            header[33] = 0
            header[34] = bitDepth.toByte()
            header[35] = 0

            // 'data' chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            return header + audioBytes
        }
    }
}
