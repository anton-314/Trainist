package dev.antonlammers.macrotrac.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antonlammers.macrotrac.data.local.AppDatabase
import dev.antonlammers.macrotrac.data.local.dao.CustomFoodDao
import dev.antonlammers.macrotrac.data.local.dao.DailyGoalDao
import dev.antonlammers.macrotrac.data.local.dao.ExerciseDao
import dev.antonlammers.macrotrac.data.local.dao.FoodEntryDao
import dev.antonlammers.macrotrac.data.local.dao.WeightEntryDao
import dev.antonlammers.macrotrac.data.local.dao.WorkoutSessionDao
import dev.antonlammers.macrotrac.data.local.dao.WorkoutTemplateDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "macrotrac.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
            )
            .build()

    @Provides
    fun provideFoodEntryDao(db: AppDatabase): FoodEntryDao = db.foodEntryDao()

    @Provides
    fun provideDailyGoalDao(db: AppDatabase): DailyGoalDao = db.dailyGoalDao()

    @Provides
    fun provideWeightEntryDao(db: AppDatabase): WeightEntryDao = db.weightEntryDao()

    @Provides
    fun provideCustomFoodDao(db: AppDatabase): CustomFoodDao = db.customFoodDao()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideWorkoutTemplateDao(db: AppDatabase): WorkoutTemplateDao = db.workoutTemplateDao()

    @Provides
    fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()
}
