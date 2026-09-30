package app.tfl.core.database.di

import app.tfl.core.database.DatabaseFactory
import app.tfl.core.database.SqlCipherDatabaseFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    abstract fun databaseFactory(impl: SqlCipherDatabaseFactory): DatabaseFactory
}
