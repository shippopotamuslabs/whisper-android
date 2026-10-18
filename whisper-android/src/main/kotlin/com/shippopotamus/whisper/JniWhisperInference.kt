package com.shippopotamus.whisper

import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [WhisperEngine] implementation over the whisper.cpp JNI bridge. All native calls are
 * serialized through a mutex because a whisper context is not thread-safe.
 */
class JniWhisperInference internal constructor(
    private val nativeBridge: WhisperNativeBridge,
    private val processorCountProvider: ProcessorCountProvider,
    private val dispatcher: CoroutineDispatcher,
) : WhisperEngine {
    constructor() : this(
        nativeBridge = JniWhisperNativeBridge,
        processorCountProvider = RuntimeProcessorCountProvider,
        dispatcher = Dispatchers.Default,
    )

    private val mutex = Mutex()

    @Volatile
    private var contextPtr: Long = 0L
    private var params: WhisperParams = WhisperParams()

    override suspend fun load(modelPath: String, params: WhisperParams) {
        mutex.withLock {
            if (contextPtr != 0L) {
                closeLocked()
            }

            this.params = params
            contextPtr = withContext(dispatcher) {
                nativeBridge.initContext(modelPath)
            }
            check(contextPtr != 0L) { "Failed to initialize Whisper model: $modelPath" }
        }
    }

    override suspend fun transcribe(samples: FloatArray): String {
        return mutex.withLock {
            val ptr = contextPtr
            check(ptr != 0L) { "Whisper context is not initialized." }

            withContext(dispatcher) {
                nativeBridge.processAudio(
                    contextPtr = ptr,
                    samples = samples,
                    numThreads = resolveThreadCount(),
                    language = params.language,
                    translate = params.translate,
                )
            }
        }
    }

    override fun isLoaded(): Boolean = contextPtr != 0L

    override suspend fun close() {
        mutex.withLock {
            closeLocked()
        }
    }

    private suspend fun closeLocked() {
        val ptr = contextPtr
        if (ptr == 0L) return

        contextPtr = 0L
        withContext(dispatcher) {
            nativeBridge.freeContext(ptr)
        }
    }

    private fun resolveThreadCount(): Int {
        params.threads?.let { return it.coerceAtLeast(1) }

        val halfProcessors = processorCountProvider.availableProcessors() / 2
        return halfProcessors.coerceAtLeast(1).coerceAtMost(MAX_THREADS)
    }

    internal companion object {
        const val MAX_THREADS = 4

        @Keep
        @JvmStatic
        external fun initContext(modelPath: String): Long

        @Keep
        @JvmStatic
        external fun processAudio(
            contextPtr: Long,
            samples: FloatArray,
            numThreads: Int,
            language: String,
            translate: Boolean,
        ): String

        @Keep
        @JvmStatic
        external fun freeContext(contextPtr: Long)
    }
}

internal interface WhisperNativeBridge {
    fun initContext(modelPath: String): Long

    fun processAudio(
        contextPtr: Long,
        samples: FloatArray,
        numThreads: Int,
        language: String,
        translate: Boolean,
    ): String

    fun freeContext(contextPtr: Long)
}

internal object JniWhisperNativeBridge : WhisperNativeBridge {
    init {
        System.loadLibrary("whisper-jni")
    }

    override fun initContext(modelPath: String): Long = JniWhisperInference.initContext(modelPath)

    override fun processAudio(
        contextPtr: Long,
        samples: FloatArray,
        numThreads: Int,
        language: String,
        translate: Boolean,
    ): String {
        return JniWhisperInference.processAudio(
            contextPtr = contextPtr,
            samples = samples,
            numThreads = numThreads,
            language = language,
            translate = translate,
        )
    }

    override fun freeContext(contextPtr: Long) {
        JniWhisperInference.freeContext(contextPtr)
    }
}

internal fun interface ProcessorCountProvider {
    fun availableProcessors(): Int
}

internal object RuntimeProcessorCountProvider : ProcessorCountProvider {
    override fun availableProcessors(): Int = Runtime.getRuntime().availableProcessors()
}
