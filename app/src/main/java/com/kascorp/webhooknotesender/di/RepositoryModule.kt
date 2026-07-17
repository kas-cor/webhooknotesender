package com.kascorp.webhooknotesender.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository bindings are handled automatically via @Inject constructors
 * on ProfileRepository and QueueRepository with @Singleton scope.
 *
 * This module exists as a placeholder for future repository bindings
 * that may require custom configuration.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
