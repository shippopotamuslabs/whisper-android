package com.shippopotamus.whisper

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JniWhisperInferenceTest {

    @Test
    fun transcribe_capsThreadCountAtFour() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val inference = JniWhisperInference(
            nativeBridge = bridge,
            processorCountProvider = ProcessorCountProvider { 16 },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        inference.load("/models/ggml-base.en.bin")
        inference.transcribe(floatArrayOf(0.1f))

        assertEquals(listOf(4), bridge.threadCounts)
    }

    @Test
    fun transcribe_explicitThreadCount_overridesAutoDetect() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val inference = JniWhisperInference(
            nativeBridge = bridge,
            processorCountProvider = ProcessorCountProvider { 16 },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        inference.load("/models/ggml-base.en.bin", WhisperParams(threads = 2))
        inference.transcribe(floatArrayOf(0.1f))

        assertEquals(listOf(2), bridge.threadCounts)
    }

    @Test
    fun transcribe_passesLanguageAndTranslateFromParams() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val inference = JniWhisperInference(
            nativeBridge = bridge,
            processorCountProvider = ProcessorCountProvider { 8 },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        inference.load("/models/ggml-base.bin", WhisperParams(language = "es", translate = true))
        inference.transcribe(floatArrayOf(0.1f))

        assertEquals(listOf("es"), bridge.languages)
        assertEquals(listOf(true), bridge.translateFlags)
    }

    @Test
    fun isLoaded_tracksLoadAndClose() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val inference = JniWhisperInference(
            nativeBridge = bridge,
            processorCountProvider = ProcessorCountProvider { 8 },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertFalse(inference.isLoaded())
        inference.load("/models/ggml-base.en.bin")
        assertTrue(inference.isLoaded())
        inference.close()
        assertFalse(inference.isLoaded())
    }

    @Test
    fun close_isIdempotent() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val inference = JniWhisperInference(
            nativeBridge = bridge,
            processorCountProvider = ProcessorCountProvider { 8 },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        inference.load("/models/ggml-base.en.bin")
        inference.close()
        inference.close()

        assertEquals(listOf(42L), bridge.freedContexts)
    }

    @Test
    fun transcribe_serializesNativeCallsWithMutex() = runTest {
        val bridge = FakeWhisperNativeBridge(blockDuringProcess = true)
        val inference = JniWhisperInference(
            nativeBridge = bridge,
            processorCountProvider = ProcessorCountProvider { 8 },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        inference.load("/models/ggml-base.en.bin")
        val first = async { inference.transcribe(floatArrayOf(0.1f)) }
        val second = async { inference.transcribe(floatArrayOf(0.2f)) }
        first.await()
        second.await()

        assertEquals(2, bridge.processCalls)
        assertFalse(bridge.overlapped)
    }

    private class FakeWhisperNativeBridge(
        private val blockDuringProcess: Boolean = false,
    ) : WhisperNativeBridge {
        val threadCounts = mutableListOf<Int>()
        val languages = mutableListOf<String>()
        val translateFlags = mutableListOf<Boolean>()
        val freedContexts = mutableListOf<Long>()
        var processCalls = 0
        var overlapped = false
        private val activeProcessCalls = AtomicInteger(0)

        override fun initContext(modelPath: String): Long = 42L

        override fun processAudio(
            contextPtr: Long,
            samples: FloatArray,
            numThreads: Int,
            language: String,
            translate: Boolean,
        ): String {
            processCalls += 1
            threadCounts += numThreads
            languages += language
            translateFlags += translate
            if (activeProcessCalls.incrementAndGet() > 1) {
                overlapped = true
            }
            try {
                if (blockDuringProcess) {
                    Thread.sleep(20)
                }
                return "transcript"
            } finally {
                activeProcessCalls.decrementAndGet()
            }
        }

        override fun freeContext(contextPtr: Long) {
            freedContexts += contextPtr
        }
    }
}
