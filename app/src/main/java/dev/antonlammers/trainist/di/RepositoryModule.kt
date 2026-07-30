package dev.antonlammers.trainist.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antonlammers.trainist.data.backup.BackupExporterImpl
import dev.antonlammers.trainist.data.backup.BackupImporterImpl
import dev.antonlammers.trainist.data.repository.BodyMeasurementRepositoryImpl
import dev.antonlammers.trainist.data.repository.CustomFoodRepositoryImpl
import dev.antonlammers.trainist.data.repository.ExerciseCatalogRepositoryImpl
import dev.antonlammers.trainist.data.repository.FoodEntryRepositoryImpl
import dev.antonlammers.trainist.data.repository.FoodSearchRepositoryImpl
import dev.antonlammers.trainist.data.repository.GoalRepositoryImpl
import dev.antonlammers.trainist.data.repository.RoomTransactionRunner
import dev.antonlammers.trainist.data.repository.SettingsRepositoryImpl
import dev.antonlammers.trainist.data.repository.TransactionRunner
import dev.antonlammers.trainist.data.seed.AssetExerciseSnapshotSource
import dev.antonlammers.trainist.data.seed.ExerciseSnapshotSource
import dev.antonlammers.trainist.data.seed.SeedVersionStore
import dev.antonlammers.trainist.data.seed.SharedPrefsSeedVersionStore
import dev.antonlammers.trainist.domain.backup.BackupExporter
import dev.antonlammers.trainist.domain.backup.BackupImporter
import dev.antonlammers.trainist.data.repository.WeightRepositoryImpl
import dev.antonlammers.trainist.data.repository.WorkoutSessionRepositoryImpl
import dev.antonlammers.trainist.data.repository.WorkoutTemplateRepositoryImpl
import dev.antonlammers.trainist.domain.repository.BodyMeasurementRepository
import dev.antonlammers.trainist.domain.repository.CustomFoodRepository
import dev.antonlammers.trainist.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.FoodSearchRepository
import dev.antonlammers.trainist.domain.repository.GoalRepository
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
import dev.antonlammers.trainist.domain.repository.WorkoutSessionRepository
import dev.antonlammers.trainist.domain.repository.WorkoutTemplateRepository
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
    abstract fun bindBodyMeasurementRepository(impl: BodyMeasurementRepositoryImpl): BodyMeasurementRepository

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
    abstract fun bindBackupExporter(impl: BackupExporterImpl): BackupExporter

    @Binds @Singleton
    abstract fun bindBackupImporter(impl: BackupImporterImpl): BackupImporter

    @Binds @Singleton
    abstract fun bindSeedVersionStore(impl: SharedPrefsSeedVersionStore): SeedVersionStore

    @Binds @Singleton
    abstract fun bindExerciseSnapshotSource(impl: AssetExerciseSnapshotSource): ExerciseSnapshotSource
}
