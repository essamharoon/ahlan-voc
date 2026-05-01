package com.fbint.collector.di

import android.content.Context
import androidx.room.Room
import com.fbint.collector.data.local.AppDatabase
import com.fbint.collector.data.local.QueuedFileDao
import com.fbint.collector.data.local.ResponseQueueDao
import com.fbint.collector.data.local.SurveyDao
import com.fbint.collector.data.remote.FormbricksApiFactory
import com.fbint.collector.data.remote.FormbricksClientApi
import com.fbint.collector.data.remote.FormbricksManagementApi
import com.fbint.collector.data.repository.ConfigRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideApiFactory(client: OkHttpClient, moshi: Moshi): FormbricksApiFactory =
        FormbricksApiFactory(client, moshi)

    @Provides @Singleton
    fun provideManagementApi(
        config: ConfigRepository,
        factory: FormbricksApiFactory,
    ): FormbricksManagementApi = factory.management { config.baseUrl() ?: "https://app.formbricks.com" }

    @Provides @Singleton
    fun provideClientApi(
        config: ConfigRepository,
        factory: FormbricksApiFactory,
    ): FormbricksClientApi = factory.client { config.baseUrl() ?: "https://app.formbricks.com" }

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "fbint.db")
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides fun provideSurveyDao(db: AppDatabase): SurveyDao = db.surveyDao()
    @Provides fun provideResponseQueueDao(db: AppDatabase): ResponseQueueDao = db.responseQueueDao()
    @Provides fun provideQueuedFileDao(db: AppDatabase): QueuedFileDao = db.queuedFileDao()
}
