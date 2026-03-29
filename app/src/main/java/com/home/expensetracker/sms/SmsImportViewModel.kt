package com.home.expensetracker.sms

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.home.expensetracker.data.models.Expense
import com.home.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    application: Application
) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication()

    // ── UI State ──────────────────────────────────────────────────────────────
    sealed class ImportState {
        object Idle        : ImportState()
        object Scanning    : ImportState()
        data class Preview(
            val transactions: List<SmsTransaction>,
            val alreadyImported: Int = 0
        ) : ImportState()
        object Importing   : ImportState()
        data class Done(val imported: Int) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    data class SmsTransaction(
        val parsed: UpiSmsParser.ParsedTransaction,
        val category: String,
        val categoryIcon: String,
        var isSelected: Boolean = true
    )

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    // ── Scan SMS Inbox ────────────────────────────────────────────────────────
    fun scanSms(daysBack: Int = 30) {
        viewModelScope.launch {
            _state.value = ImportState.Scanning

            val transactions = withContext(Dispatchers.IO) {
                SmsInboxReader.readUpiSms(ctx, daysBack)
            }

            if (transactions.isEmpty()) {
                _state.value = ImportState.Preview(emptyList(), 0)
                return@launch
            }

            // Check which ones are already imported (by UPI ref or amount+date)
            val existingExpenses = repository.getAllExpenses().value
            val alreadyImported = transactions.count { txn ->
                existingExpenses.any { exp ->
                    exp.note.contains(txn.upiRef) ||
                    (Math.abs(exp.amount - txn.amount) < 0.01 &&
                     Math.abs(exp.date - txn.timestampMs) < 60_000)
                }
            }

            val smsTransactions = transactions.map { parsed ->
                val (cat, icon) = UpiSmsParser.suggestCategory(parsed.merchant, parsed.rawSms)
                SmsTransaction(
                    parsed       = parsed,
                    category     = cat,
                    categoryIcon = icon,
                    isSelected   = !existingExpenses.any { exp ->
                        exp.note.contains(parsed.upiRef) ||
                        (Math.abs(exp.amount - parsed.amount) < 0.01 &&
                         Math.abs(exp.date - parsed.timestampMs) < 60_000)
                    }
                )
            }

            _state.value = ImportState.Preview(smsTransactions, alreadyImported)
        }
    }

    // ── Import Selected Transactions ──────────────────────────────────────────
    fun importSelected(transactions: List<SmsTransaction>) {
        viewModelScope.launch {
            _state.value = ImportState.Importing

            val selected = transactions.filter { it.isSelected }
            var count = 0

            withContext(Dispatchers.IO) {
                selected.forEach { txn ->
                    val expense = Expense(
                        title         = txn.parsed.merchant,
                        amount        = txn.parsed.amount,
                        category      = txn.category,
                        categoryIcon  = txn.categoryIcon,
                        date          = txn.parsed.timestampMs,
                        note          = "Auto-imported via SMS\nRef: ${txn.parsed.upiRef}",
                        paymentMethod = "UPI"
                    )
                    repository.insertExpense(expense)
                    count++
                }
            }

            _state.value = ImportState.Done(count)
        }
    }

    fun reset() { _state.value = ImportState.Idle }
}
