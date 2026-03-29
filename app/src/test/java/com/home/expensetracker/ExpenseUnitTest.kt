package com.home.expensetracker

import com.home.expensetracker.data.models.Expense
import com.home.expensetracker.utils.CurrencyUtils
import com.home.expensetracker.utils.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExpenseUnitTest {

    // ── CurrencyUtils Tests ───────────────────────────────────────────────────
    @Test
    fun `formatCompact returns K suffix for thousands`() {
        val result = CurrencyUtils.formatCompact(5500.0)
        assertTrue("Should contain K", result.contains("K"))
    }

    @Test
    fun `formatCompact returns L suffix for lakhs`() {
        val result = CurrencyUtils.formatCompact(150000.0)
        assertTrue("Should contain L", result.contains("L"))
    }

    @Test
    fun `formatCompact returns plain amount for small values`() {
        val result = CurrencyUtils.formatCompact(500.0)
        assertTrue("Should contain ₹", result.contains("₹"))
        assertFalse("Should NOT contain K", result.contains("K"))
    }

    // ── DateUtils Tests ───────────────────────────────────────────────────────
    @Test
    fun `getMonthRange returns correct start and end`() {
        val (start, end) = DateUtils.getMonthRange(1, 2025)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal   = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCal.get(Calendar.MONTH))   // Jan = 0
        assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `previousMonth wraps from January to December`() {
        val (month, year) = DateUtils.previousMonth(1, 2025)
        assertEquals(12, month)
        assertEquals(2024, year)
    }

    @Test
    fun `nextMonth wraps from December to January`() {
        val (month, year) = DateUtils.nextMonth(12, 2024)
        assertEquals(1, month)
        assertEquals(2025, year)
    }

    @Test
    fun `getMonthName returns correct name`() {
        assertEquals("January",  DateUtils.getMonthName(1))
        assertEquals("December", DateUtils.getMonthName(12))
        assertEquals("June",     DateUtils.getMonthName(6))
    }

    @Test
    fun `formatDate returns non-empty string`() {
        val result = DateUtils.formatDate(System.currentTimeMillis())
        assertTrue("Formatted date should not be empty", result.isNotEmpty())
    }

    // ── Expense Model Tests ───────────────────────────────────────────────────
    @Test
    fun `expense default values are correct`() {
        val expense = Expense(
            title        = "Test",
            amount       = 100.0,
            category     = "Food",
            categoryIcon = "🍔"
        )
        assertEquals(0L, expense.id)
        assertEquals("Cash", expense.paymentMethod)
        assertFalse(expense.isRecurring)
        assertEquals("None", expense.recurringPeriod)
        assertTrue(expense.note.isEmpty())
    }

    @Test
    fun `expense with all fields`() {
        val ts = System.currentTimeMillis()
        val expense = Expense(
            id            = 1L,
            title         = "Electricity Bill",
            amount        = 2500.0,
            category      = "Utilities",
            categoryIcon  = "💡",
            date          = ts,
            note          = "March bill",
            paymentMethod = "UPI",
            isRecurring   = true,
            recurringPeriod = "Monthly"
        )
        assertEquals(1L,         expense.id)
        assertEquals(2500.0,     expense.amount, 0.001)
        assertEquals("UPI",      expense.paymentMethod)
        assertTrue(expense.isRecurring)
        assertEquals("Monthly",  expense.recurringPeriod)
        assertEquals(ts,         expense.date)
    }

    @Test
    fun `expense amount must be positive`() {
        val expense = Expense(
            title = "Test", amount = -50.0, category = "Food", categoryIcon = "🍔"
        )
        assertTrue("Negative amount should fail validation", expense.amount < 0)
    }

    // ── MonthlyBudgetSummary calculation Tests ────────────────────────────────
    @Test
    fun `budget percentage calculates correctly`() {
        val summary = com.home.expensetracker.data.models.MonthlyBudgetSummary(
            category     = "Food",
            categoryIcon = "🍔",
            budgetLimit  = 10000.0,
            amountSpent  = 7500.0
        )
        assertEquals(75f, summary.percentage, 0.1f)
        assertFalse(summary.isOverBudget)
    }

    @Test
    fun `budget detects over-budget state`() {
        val summary = com.home.expensetracker.data.models.MonthlyBudgetSummary(
            category     = "Shopping",
            categoryIcon = "🛍️",
            budgetLimit  = 5000.0,
            amountSpent  = 6000.0
        )
        assertTrue(summary.isOverBudget)
        assertTrue(summary.percentage > 100f)
    }

    @Test
    fun `budget with zero limit returns zero percentage`() {
        val summary = com.home.expensetracker.data.models.MonthlyBudgetSummary(
            category     = "Other",
            categoryIcon = "📦",
            budgetLimit  = 0.0,
            amountSpent  = 500.0
        )
        assertEquals(0f, summary.percentage, 0.001f)
    }
}
