package dev.antonlammers.macromind.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antonlammers.macromind.data.repository.FoodEntryRepositoryImpl
import dev.antonlammers.macromind.data.repository.FoodSearchRepositoryImpl
import dev.antonlammers.macromind.data.repository.GoalRepositoryImpl
import dev.antonlammers.macromind.domain.repository.FoodEntryRepository
import dev.antonlammers.macromind.domain.repository.FoodSearchRepository
import dev.antonlammers.macromind.domain.repository.GoalRepository
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
}
