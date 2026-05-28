package dev.antonlammers.macromind.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antonlammers.macromind.data.local.AppDatabase
import dev.antonlammers.macromind.data.local.dao.DailyGoalDao
import dev.antonlammers.macromind.data.local.dao.FoodEntryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "macromind.db").build()

    @Provides
    fun provideFoodEntryDao(db: AppDatabase): FoodEntryDao = db.foodEntryDao()

    @Provides
    fun provideDailyGoalDao(db: AppDatabase): DailyGoalDao = db.dailyGoalDao()
}
