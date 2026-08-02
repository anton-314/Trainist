package dev.antonlammers.trainist.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antonlammers.trainist.BuildConfig
import dev.antonlammers.trainist.data.remote.OpenFoodFactsApi
import dev.antonlammers.trainist.data.remote.OpenFoodFactsSearchApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.create(OpenFoodFactsApi::class.java)

    /**
     * Full-text search runs on its own Open Food Facts host, so it needs a second Retrofit rather
     * than another path on the first — see [OpenFoodFactsSearchApi]. It is qualified, because two
     * unqualified `Retrofit` bindings would be a duplicate-binding error.
     */
    @Provides
    @Singleton
    @SearchRetrofit
    fun provideSearchRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl("https://search.openfoodfacts.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsSearchApi(@SearchRetrofit retrofit: Retrofit): OpenFoodFactsSearchApi =
        retrofit.create(OpenFoodFactsSearchApi::class.java)
}

/** Distinguishes the search host's Retrofit from the main Open Food Facts one. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SearchRetrofit
