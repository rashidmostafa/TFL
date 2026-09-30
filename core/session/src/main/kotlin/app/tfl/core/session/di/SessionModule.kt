package app.tfl.core.session.di

import app.tfl.core.session.restart.AndroidProcessRestarter
import app.tfl.core.session.restart.ProcessRestarter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Lives as long as the process: auto-lock timers and other work that outlives any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Where blocking crypto and file work runs (Argon2id, Keystore, lock-state writes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WorkDispatcher

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @WorkDispatcher
    fun workDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionBindings {
    @Binds
    abstract fun processRestarter(impl: AndroidProcessRestarter): ProcessRestarter
}
