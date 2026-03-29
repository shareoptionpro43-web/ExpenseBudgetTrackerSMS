package com.home.expensetracker.utils

import android.content.Context
import android.os.Environment
import com.home.expensetracker.data.models.Expense
import java.io.File
import java.io.FileWriter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Date Utilities ────────────────────────────────────────────────────────────
object DateUtils {

    fun getMonthRange(month: Int, year: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    fun formatDate(timestamp: Long, pattern: String = "dd MMM yyyy"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String =
        formatDate(timestamp, "dd MMM yyyy, hh:mm a")

    fun getMonthName(month: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, month - 1)
        return SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
    }

    fun getMonthYear(month: Int, year: Int): String =
        "${getMonthName(month)} $year"

    fun getCurrentMonth() = Calendar.getInstance().get(Calendar.MONTH) + 1
    fun getCurrentYear()  = Calendar.getInstance().get(Calendar.YEAR)

    fun previousMonth(month: Int, year: Int): Pair<Int, Int> {
        return if (month == 1) Pair(12, year - 1) else Pair(month - 1, year)
    }

    fun nextMonth(month: Int, year: Int): Pair<Int, Int> {
        return if (month == 12) Pair(1, year + 1) else Pair(month + 1, year)
    }
}

// ── Currency Utilities ────────────────────────────────────────────────────────
object CurrencyUtils {
    private val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun format(amount: Double): String = formatter.format(amount)

    fun formatCompact(amount: Double): String = when {
        amount >= 100_000 -> "₹${String.format("%.1f", amount / 100_000)}L"
        amount >= 1_000   -> "₹${String.format("%.1f", amount / 1_000)}K"
        else              -> format(amount)
    }
}

// ── CSV Exporter ──────────────────────────────────────────────────────────────
object CSVExporter {

    fun exportExpenses(context: Context, expenses: List<Expense>): File? {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val fileName = "expenses_${DateUtils.formatDate(System.currentTimeMillis(), "yyyyMMdd_HHmmss")}.csv"
            val file = File(dir, fileName)

            FileWriter(file).use { writer ->
                // Header
                writer.append("Date,Title,Category,Amount,Payment Method,Note,Recurring\n")
                // Rows
                expenses.forEach { expense ->
                    writer.append("\"${DateUtils.formatDate(expense.date)}\",")
                    writer.append("\"${expense.title}\",")
                    writer.append("\"${expense.category}\",")
                    writer.append("${expense.amount},")
                    writer.append("\"${expense.paymentMethod}\",")
                    writer.append("\"${expense.note}\",")
                    writer.append("${expense.isRecurring}\n")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────
object Constants {
    const val PAYMENT_CASH   = "Cash"
    const val PAYMENT_CARD   = "Card"
    const val PAYMENT_UPI    = "UPI"
    const val PAYMENT_BANK   = "Bank Transfer"
    const val PAYMENT_WALLET = "Wallet"

    val PAYMENT_METHODS = listOf(
        PAYMENT_CASH, PAYMENT_CARD, PAYMENT_UPI, PAYMENT_BANK, PAYMENT_WALLET
    )

    val RECURRING_PERIODS = listOf("None", "Daily", "Weekly", "Monthly", "Yearly")
}
