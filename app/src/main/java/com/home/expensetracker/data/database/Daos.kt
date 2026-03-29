package com.home.expensetracker.data.database

import androidx.room.*
import com.home.expensetracker.data.models.*
import kotlinx.coroutines.flow.Flow

// ── Expense DAO ──────────────────────────────────────────────────────────────
@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("""
        SELECT * FROM expenses 
        WHERE date >= :startDate AND date <= :endDate 
        ORDER BY date DESC
    """)
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses 
        WHERE strftime('%m', datetime(date/1000, 'unixepoch')) = :month
        AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year
        ORDER BY date DESC
    """)
    fun getExpensesByMonth(month: String, year: String): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses 
        WHERE category = :category 
        AND date >= :startDate AND date <= :endDate
        ORDER BY date DESC
    """)
    fun getExpensesByCategoryAndDate(
        category: String, startDate: Long, endDate: Long
    ): Flow<List<Expense>>

    @Query("""
        SELECT category, SUM(amount) as totalAmount, COUNT(*) as transactionCount 
        FROM expenses 
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY category
        ORDER BY totalAmount DESC
    """)
    fun getCategoryTotals(
        startDate: Long, endDate: Long
    ): Flow<List<CategoryTotal>>

    @Query("""
        SELECT SUM(amount) FROM expenses 
        WHERE date >= :startDate AND date <= :endDate
    """)
    fun getTotalExpenseForPeriod(startDate: Long, endDate: Long): Flow<Double?>

    @Query("""
        SELECT SUM(amount) FROM expenses 
        WHERE category = :category 
        AND date >= :startDate AND date <= :endDate
    """)
    suspend fun getTotalForCategoryAndPeriod(
        category: String, startDate: Long, endDate: Long
    ): Double?

    @Query("""
        SELECT strftime('%d', datetime(date/1000, 'unixepoch')) as day,
               SUM(amount) as totalAmount
        FROM expenses 
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY day
        ORDER BY day
    """)
    fun getDailyTotals(startDate: Long, endDate: Long): Flow<List<DailyTotal>>

    @Query("SELECT DISTINCT category FROM expenses ORDER BY category")
    fun getDistinctCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getTotalExpenseCount(): Int
}

// ── Budget DAO ───────────────────────────────────────────────────────────────
@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year")
    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>>

    @Query("""
        SELECT * FROM budgets 
        WHERE category = :category AND month = :month AND year = :year
        LIMIT 1
    """)
    suspend fun getBudgetForCategory(category: String, month: Int, year: Int): Budget?

    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY category")
    fun getAllActiveBudgets(): Flow<List<Budget>>

    @Query("SELECT SUM(monthlyLimit) FROM budgets WHERE month = :month AND year = :year")
    fun getTotalBudgetForMonth(month: Int, year: Int): Flow<Double?>
}

// ── Category DAO ─────────────────────────────────────────────────────────────
@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<Category>)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories ORDER BY name")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
}

// ── Projection classes ───────────────────────────────────────────────────────
data class CategoryTotal(
    val category: String,
    val totalAmount: Double,
    val transactionCount: Int
)

data class DailyTotal(
    val day: String,
    val totalAmount: Double
)
