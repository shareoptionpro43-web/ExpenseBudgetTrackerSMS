package com.home.expensetracker.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

// ── Expense Entity ───────────────────────────────────────────────────────────
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val categoryIcon: String,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val paymentMethod: String = "Cash",
    val isRecurring: Boolean = false,
    val recurringPeriod: String = "None",   // None, Daily, Weekly, Monthly
    val createdAt: Long = System.currentTimeMillis()
)

// ── Budget Entity ────────────────────────────────────────────────────────────
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val categoryIcon: String,
    val monthlyLimit: Double,
    val month: Int,       // 1-12
    val year: Int,
    val alertThreshold: Int = 80,   // alert when 80% spent
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

// ── Category Entity ──────────────────────────────────────────────────────────
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,
    val colorHex: String,
    val isDefault: Boolean = false
)

// ── Summary Model (not a Room entity) ───────────────────────────────────────
data class MonthlyBudgetSummary(
    val category: String,
    val categoryIcon: String,
    val budgetLimit: Double,
    val amountSpent: Double,
    val percentage: Float = if (budgetLimit > 0) ((amountSpent / budgetLimit) * 100).toFloat() else 0f,
    val isOverBudget: Boolean = amountSpent > budgetLimit
)

data class CategoryExpenseSummary(
    val category: String,
    val categoryIcon: String,
    val colorHex: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Float = 0f
)

data class DailyExpense(
    val date: Long,
    val totalAmount: Double
)

data class ExpenseFilter(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val category: String? = null,
    val paymentMethod: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null
)
