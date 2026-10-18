package com.shippopotamus.whisper.hilt

import com.shippopotamus.whisper.JniWhisperInference
import com.shippopotamus.whisper.WhisperEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the whisper-android core library. Kept in this optional module so
 * the core library itself has no Hilt dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
object WhisperModule {
    @Provides
    @Singleton
    fun provideWhisperEngine(): WhisperEngine = JniWhisperInference()
}
