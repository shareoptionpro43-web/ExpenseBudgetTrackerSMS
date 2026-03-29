package com.home.expensetracker.di

import android.content.Context
import com.home.expensetracker.data.database.ExpenseDatabase
import com.home.expensetracker.data.repository.ExpenseRepository
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
    fun provideDatabase(@ApplicationContext context: Context): ExpenseDatabase =
        ExpenseDatabase.getInstance(context)

    @Provides
    fun provideExpenseDao(db: ExpenseDatabase) = db.expenseDao()

    @Provides
    fun provideBudgetDao(db: ExpenseDatabase) = db.budgetDao()

    @Provides
    fun provideCategoryDao(db: ExpenseDatabase) = db.categoryDao()

    @Provides
    @Singleton
    fun provideRepository(db: ExpenseDatabase): ExpenseRepository =
        ExpenseRepository(
            expenseDao  = db.expenseDao(),
            budgetDao   = db.budgetDao(),
            categoryDao = db.categoryDao()
        )
}
