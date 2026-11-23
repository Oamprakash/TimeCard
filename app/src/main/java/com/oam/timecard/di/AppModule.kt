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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): TimeCardDatabase =
        Room.databaseBuilder(context, TimeCardDatabase::class.java, TimeCardDatabase.NAME).build()

    /** Injected so tests can fix "now". */
    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
