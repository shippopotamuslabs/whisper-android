package com.shippopotamus.whisper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Microphone capture producing 16 kHz mono PCM samples normalized to floats in
 * [-1, 1] — exactly the input format whisper.cpp expects. Copied from Enclave's
 * `AndroidAudioCapture` (ENC-46), minus the Hilt annotations and Enclave port types.
 *
 * Collecting [audioChunks] requires the `RECORD_AUDIO` permission.
 */
class AudioRecorder internal constructor(
    private val audioRecordFactory: AudioRecordFactory,
) {
    constructor(context: Context) : this(AndroidAudioRecordFactory(context.applicationContext))

    fun audioChunks(): Flow<FloatArray> =
        flow {
            val minBufferSize = audioRecordFactory.getMinBufferSize()
            require(minBufferSize > 0) { "Invalid AudioRecord buffer size: $minBufferSize" }

            val bufferSize = minBufferSize * BUFFER_MULTIPLIER
            val recorder = audioRecordFactory.create(bufferSize)
            val shortBuffer = ShortArray(bufferSize / BYTES_PER_SAMPLE)

            try {
                recorder.startRecording()
                while (currentCoroutineContext().isActive) {
                    when (val readCount = recorder.read(shortBuffer, shortBuffer.size)) {
                        0 -> Unit
                        in 1..shortBuffer.size -> emit(shortBuffer.toFloatArray(readCount))
                        else -> throw IllegalStateException("AudioRecord read failed: $readCount")
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }.flowOn(Dispatchers.IO)

    private fun ShortArray.toFloatArray(readCount: Int): FloatArray =
        FloatArray(readCount) { index ->
            this[index] / PCM_16_NORMALIZATION_FACTOR
        }

    private companion object {
        const val BUFFER_MULTIPLIER = 2
        const val BYTES_PER_SAMPLE = 2
        const val PCM_16_NORMALIZATION_FACTOR = 32_768.0f
    }
}

interface AudioRecordFactory {
    fun getMinBufferSize(): Int

    fun create(bufferSizeInBytes: Int): AudioRecordDevice
}

interface AudioRecordDevice {
    fun startRecording()

    fun read(
        buffer: ShortArray,
        size: Int,
    ): Int

    fun stop()

    fun release()
}

class AndroidAudioRecordFactory(
    private val context: Context,
) : AudioRecordFactory {
    override fun getMinBufferSize(): Int =
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

    override fun create(bufferSizeInBytes: Int): AudioRecordDevice {
        val audioFormat =
            AudioFormat
                .Builder()
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

        require(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        ) {
            "RECORD_AUDIO permission is required to capture microphone input"
        }

        val audioRecord =
            try {
                AudioRecord
                    .Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build()
            } catch (exception: SecurityException) {
                throw IllegalStateException(
                    "RECORD_AUDIO permission is required to capture microphone input",
                    exception,
                )
            }

        return AndroidAudioRecordDevice(audioRecord)
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}

private class AndroidAudioRecordDevice(
    private val audioRecord: AudioRecord,
) : AudioRecordDevice {
    override fun startRecording() {
        audioRecord.startRecording()
    }

    override fun read(
        buffer: ShortArray,
        size: Int,
    ): Int = audioRecord.read(buffer, 0, size, AudioRecord.READ_BLOCKING)

    override fun stop() {
        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop()
        }
    }

    override fun release() {
        audioRecord.release()
    }
}
