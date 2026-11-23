package com.oam.timecard.di

import android.content.Context
import androidx.room.Room
import com.oam.timecard.data.db.TimeCardDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide scope for work that must outlive a screen, e.g. broadcast receivers using goAsync(). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): TimeCardDatabase =
        Room.databaseBuilder(context, TimeCardDatabase::class.java, TimeCardDatabase.NAME).build()

    /** Injected so tests can fix "now". */
    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
