package dev.antonlammers.macrotrac.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antonlammers.macrotrac.data.repository.CustomFoodRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.FoodEntryRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.FoodSearchRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.GoalRepositoryImpl
import dev.antonlammers.macrotrac.data.repository.WeightRepositoryImpl
import dev.antonlammers.macrotrac.domain.repository.CustomFoodRepository
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.FoodSearchRepository
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
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
}
