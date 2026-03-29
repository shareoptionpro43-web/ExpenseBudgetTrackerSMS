package com.home.expensetracker.data.repository

import com.home.expensetracker.data.database.*
import com.home.expensetracker.data.models.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao
) {

    // ── Expenses ─────────────────────────────────────────────────────────────
    suspend fun insertExpense(expense: Expense) = expenseDao.insertExpense(expense)
    suspend fun updateExpense(expense: Expense) = expenseDao.updateExpense(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.deleteExpense(expense)
    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()
    suspend fun getExpenseById(id: Long) = expenseDao.getExpenseById(id)

    fun getExpensesByMonth(month: String, year: String) =
        expenseDao.getExpensesByMonth(month, year)

    fun getExpensesByDateRange(start: Long, end: Long) =
        expenseDao.getExpensesByDateRange(start, end)

    fun getCategoryTotals(start: Long, end: Long) =
        expenseDao.getCategoryTotals(start, end)

    fun getTotalExpenseForPeriod(start: Long, end: Long) =
        expenseDao.getTotalExpenseForPeriod(start, end)

    suspend fun getTotalForCategoryAndPeriod(
        category: String, start: Long, end: Long
    ) = expenseDao.getTotalForCategoryAndPeriod(category, start, end)

    fun getDailyTotals(start: Long, end: Long) =
        expenseDao.getDailyTotals(start, end)

    // ── Budgets ───────────────────────────────────────────────────────────────
    suspend fun insertBudget(budget: Budget) = budgetDao.insertBudget(budget)
    suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)
    suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    fun getBudgetsForMonth(month: Int, year: Int) =
        budgetDao.getBudgetsForMonth(month, year)

    suspend fun getBudgetForCategory(category: String, month: Int, year: Int) =
        budgetDao.getBudgetForCategory(category, month, year)

    fun getTotalBudgetForMonth(month: Int, year: Int) =
        budgetDao.getTotalBudgetForMonth(month, year)

    // ── Categories ────────────────────────────────────────────────────────────
    suspend fun insertCategory(category: Category) = categoryDao.insertCategory(category)
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()
}
