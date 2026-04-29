package com.vektor.di

import android.content.Context
import androidx.room.Room
import com.vektor.data.local.db.EmergencyQueueDao
import com.vektor.data.local.db.VektorDatabase
import com.vektor.data.local.prefs.ProfileDataStore
import com.vektor.emergency.EmergencyDispatcher
import com.vektor.qr.QrManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProfileDataStore(@ApplicationContext context: Context): ProfileDataStore {
        return ProfileDataStore(context)
    }

    @Provides
    @Singleton
    fun provideVektorDatabase(@ApplicationContext context: Context): VektorDatabase {
        return Room.databaseBuilder(
            context,
            VektorDatabase::class.java,
            "vektor_db"
        ).build()
    }

    @Provides
    fun provideEmergencyQueueDao(database: VektorDatabase): EmergencyQueueDao =
        database.emergencyQueueDao()

    @Provides
    @Singleton
    fun provideQrManager(@ApplicationContext context: Context): QrManager =
        QrManager(context)

    @Provides
    @Singleton
    fun provideEmergencyDispatcher(
        profileStore: ProfileDataStore,
        emergencyQueueDao: EmergencyQueueDao,
        @ApplicationContext context: Context
    ): EmergencyDispatcher = EmergencyDispatcher(profileStore, emergencyQueueDao, context)
}
