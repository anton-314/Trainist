package dev.antonlammers.macrotrac.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antonlammers.macrotrac.data.repository.CustomFoodRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.ExerciseCatalogRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.FoodEntryRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.FoodSearchRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.GoalRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.RoomTransactionRunner
import dev.antonlammers.macrotrac.data.repository.SettingsRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.TransactionRunner
import dev.antonlammers.macrotrac.data.seed.AssetExerciseSnapshotSource
import dev.antonlammers.macrotrac.data.seed.ExerciseSnapshotSource
import dev.antonlammers.macrotrac.data.seed.SeedVersionStore
import dev.antonlammers.macrotrac.data.seed.SharedPrefsSeedVersionStore
import dev.antonlammers.macrotrac.data.repository.WeightRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.WorkoutSessionRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.WorkoutTemplateRepositoryImpl
import dev.antonlammers.macrotrac.domain.repository.CustomFoodRepository
import dev.antonlammers.macrotrac.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.FoodSearchRepository
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import dev.antonlammers.macrotrac.domain.repository.SettingsRepository
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutSessionRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutTemplateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindFoodSearchRepository(impl: FoodSearchRepositoryImpl): FoodSearchRepository

    @Binds @Singleton
    abstract fun bindFoodEntryRepository(impl: FoodEntryRepositoryImpl): FoodEntryRepository

    @Binds @Singleton
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository

    @Binds @Singleton
    abstract fun bindWeightRepository(impl: WeightRepositoryImpl): WeightRepository

    @Binds @Singleton
    abstract fun bindCustomFoodRepository(impl: CustomFoodRepositoryImpl): CustomFoodRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindExerciseCatalogRepository(impl: ExerciseCatalogRepositoryImpl): ExerciseCatalogRepository

    @Binds @Singleton
    abstract fun bindWorkoutTemplateRepository(impl: WorkoutTemplateRepositoryImpl): WorkoutTemplateRepository

    @Binds @Singleton
    abstract fun bindWorkoutSessionRepository(impl: WorkoutSessionRepositoryImpl): WorkoutSessionRepository

    @Binds @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner

    @Binds @Singleton
    abstract fun bindSeedVersionStore(impl: SharedPrefsSeedVersionStore): SeedVersionStore

    @Binds @Singleton
    abstract fun bindExerciseSnapshotSource(impl: AssetExerciseSnapshotSource): ExerciseSnapshotSource
}
