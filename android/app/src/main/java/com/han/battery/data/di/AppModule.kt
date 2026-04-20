package com.han.battery.data.di

import android.content.Context
import androidx.room.Room
import com.han.battery.DevConfig
import com.han.battery.data.api.ApiService
import com.han.battery.data.repository.AWSIoTManager
import com.han.battery.data.repository.AuthRepository
import com.han.battery.data.repository.BatteryRepository
import com.han.battery.data.storage.BatteryDao
import com.han.battery.data.storage.BatteryDatabase
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.data.storage.UserManager
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
    fun provideUserManager(@ApplicationContext context: Context): UserManager {
        return UserManager(context)
    }

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return ApiService(baseUrl = DevConfig.API_BASE_URL)
    }

    @Provides
    @Singleton
    fun providePreferenceManager(
        @ApplicationContext context: Context,
        userManager: UserManager
    ): PreferenceManager {
        return PreferenceManager(context, userManager)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        apiService: ApiService,
        userManager: UserManager,
        @ApplicationContext context: Context
    ): AuthRepository {
        return AuthRepository(apiService, userManager, context)
    }

    @Provides
    @Singleton
    fun provideAwsIoTManager(@ApplicationContext context: Context): AWSIoTManager {
        return AWSIoTManager(context)
    }

    @Provides
    @Singleton
    fun provideBatteryDatabase(@ApplicationContext context: Context): BatteryDatabase {
        return Room.databaseBuilder(
            context,
            BatteryDatabase::class.java,
            "battery_db"
        ).build()
    }

    @Provides
    fun provideBatteryDao(database: BatteryDatabase): BatteryDao {
        return database.batteryDao()
    }

    @Provides
    @Singleton
    fun provideBatteryRepository(
        batteryDao: BatteryDao,
        awsIoTManager: AWSIoTManager
    ): BatteryRepository {
        return BatteryRepository(batteryDao, awsIoTManager)
    }
}
