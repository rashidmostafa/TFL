package app.tfl.core.transport.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/** The transport's single thread: links, the outbox and receipts never run concurrently. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TransportDispatcher

/** Where the transport reads and writes the locked inbox (file and Keystore work). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TransportIo

@Module
@InstallIn(SingletonComponent::class)
object TransportModule {
    @Provides
    @Singleton
    @TransportDispatcher
    fun transportDispatcher(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    @Provides
    @TransportIo
    fun transportIo(): CoroutineDispatcher = Dispatchers.IO
}
