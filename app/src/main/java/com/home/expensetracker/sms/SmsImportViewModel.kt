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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    application: Application
) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication()

    // ── Filter state — provider & month ──────────────────────────────────────
    private val _selectedProvider = MutableStateFlow(UpiProvider.ALL)
    val selectedProvider: StateFlow<UpiProvider> = _selectedProvider.asStateFlow()

    private val _selectedMonthIndex = MutableStateFlow(0)   // 0 = current month
    val selectedMonthIndex: StateFlow<Int> = _selectedMonthIndex.asStateFlow()

    // Month options: current month + last 5 months
    val monthOptions: List<MonthOption> = buildMonthOptions()

    // ── All raw transactions (full scan result, unfiltered) ───────────────────
    private val _allTransactions = MutableStateFlow<List<SmsTransaction>>(emptyList())
    private var existingExpensesCache: List<Expense> = emptyList()

    // ── Derived: filtered transactions (provider + month applied) ─────────────
    val filteredTransactions: StateFlow<List<SmsTransaction>> =
        combine(_allTransactions, _selectedProvider, _selectedMonthIndex) { all, provider, monthIdx ->
            applyFilters(all, provider, monthOptions[monthIdx])
        }.let { flow ->
            val sf = MutableStateFlow<List<SmsTransaction>>(emptyList())
            viewModelScope.launch {
                flow.collect { sf.value = it }
            }
            sf
        }

    // ── UI State ──────────────────────────────────────────────────────────────
    sealed class ImportState {
        object Idle      : ImportState()
        object Scanning  : ImportState()
        object Ready     : ImportState()   // scan done, use filteredTransactions
        object Importing : ImportState()
        data class Done(val imported: Int) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    data class SmsTransaction(
        val parsed: UpiSmsParser.ParsedTransaction,
        val category: String,
        val categoryIcon: String,
        val provider: UpiProvider,
        var isSelected: Boolean = true
    )

    data class MonthOption(
        val label: String,        // e.g. "March 2026"
        val month: Int,           // 1–12
        val year: Int
    )

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    // ── Provider summary counts (shown on chips) ──────────────────────────────
    val providerCounts: StateFlow<Map<UpiProvider, Int>> =
        _allTransactions.let { flow ->
            val sf = MutableStateFlow<Map<UpiProvider, Int>>(emptyMap())
            viewModelScope.launch {
                flow.collect { all ->
                    val counts = mutableMapOf<UpiProvider, Int>()
                    UpiProvider.values().forEach { p ->
                        counts[p] = if (p == UpiProvider.ALL) all.size
                                    else all.count { p.matches(it.parsed) }
                    }
                    sf.value = counts
                }
            }
            sf
        }

    // ── Scan SMS Inbox (reads ALL history, filters applied later) ─────────────
    fun scanSms() {
        viewModelScope.launch {
            _state.value = ImportState.Scanning

            // Read 90 days so month filter has data to work with
            val parsedList: List<UpiSmsParser.ParsedTransaction> =
                withContext(Dispatchers.IO) {
                    SmsInboxReader.readUpiSms(ctx, daysBack = 90)
                }

            existingExpensesCache = repository.getAllExpenses().first()

            val smsTransactions: List<SmsTransaction> = parsedList.map { parsed ->
                val (cat, icon) = UpiSmsParser.suggestCategory(parsed.merchant, parsed.rawSms)
                val provider = detectProvider(parsed)
                SmsTransaction(
                    parsed       = parsed,
                    category     = cat,
                    categoryIcon = icon,
                    provider     = provider,
                    isSelected   = !isAlreadySaved(parsed)
                )
            }

            _allTransactions.value = smsTransactions
            _state.value = ImportState.Ready
        }
    }

    // ── Filter controls ───────────────────────────────────────────────────────
    fun selectProvider(provider: UpiProvider) {
        _selectedProvider.value = provider
        // Re-apply selection based on new filter
        reapplySelections()
    }

    fun selectMonth(index: Int) {
        _selectedMonthIndex.value = index.coerceIn(0, monthOptions.lastIndex)
        reapplySelections()
    }

    fun toggleTransaction(position: Int, selected: Boolean) {
        val current = _allTransactions.value.toMutableList()
        val filtered = filteredTransactions.value
        if (position >= filtered.size) return
        val txn = filtered[position]
        val idx = current.indexOfFirst {
            it.parsed.timestampMs == txn.parsed.timestampMs &&
            it.parsed.upiRef == txn.parsed.upiRef
        }
        if (idx >= 0) {
            current[idx] = current[idx].copy(isSelected = selected)
            _allTransactions.value = current
        }
    }

    fun selectAll(select: Boolean) {
        val monthOpt  = monthOptions[_selectedMonthIndex.value]
        val provider  = _selectedProvider.value
        val updated   = _allTransactions.value.map { txn ->
            val matchesProvider = provider == UpiProvider.ALL || provider.matches(txn.parsed)
            val matchesMonth    = isInMonth(txn.parsed.timestampMs, monthOpt)
            if (matchesProvider && matchesMonth) txn.copy(isSelected = select) else txn
        }
        _allTransactions.value = updated
    }

    // ── Import ────────────────────────────────────────────────────────────────
    fun importSelected() {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            val toImport = filteredTransactions.value.filter { it.isSelected }
            var count = 0
            withContext(Dispatchers.IO) {
                toImport.forEach { txn ->
                    val expense = Expense(
                        title         = txn.parsed.merchant,
                        amount        = txn.parsed.amount,
                        category      = txn.category,
                        categoryIcon  = txn.categoryIcon,
                        date          = txn.parsed.timestampMs,
                        note          = "Via SMS · ${txn.provider.displayName}\nRef: ${txn.parsed.upiRef}",
                        paymentMethod = "UPI"
                    )
                    repository.insertExpense(expense)
                    count++
                }
            }
            _state.value = ImportState.Done(count)
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
        _allTransactions.value = emptyList()
        _selectedProvider.value = UpiProvider.ALL
        _selectedMonthIndex.value = 0
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun applyFilters(
        all: List<SmsTransaction>,
        provider: UpiProvider,
        month: MonthOption
    ): List<SmsTransaction> {
        return all.filter { txn ->
            val matchesProvider = provider == UpiProvider.ALL || provider.matches(txn.parsed)
            val matchesMonth    = isInMonth(txn.parsed.timestampMs, month)
            matchesProvider && matchesMonth
        }
    }

    private fun isInMonth(timestampMs: Long, opt: MonthOption): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        return cal.get(Calendar.MONTH) + 1 == opt.month &&
               cal.get(Calendar.YEAR)       == opt.year
    }

    private fun reapplySelections() {
        // Just trigger recompute — existing isSelected values preserved
        _allTransactions.value = _allTransactions.value.toList()
    }

    private fun isAlreadySaved(txn: UpiSmsParser.ParsedTransaction): Boolean =
        existingExpensesCache.any { exp: Expense ->
            (txn.upiRef.isNotEmpty() && exp.note.contains(txn.upiRef)) ||
            (abs(exp.amount - txn.amount) < 0.01 &&
             abs(exp.date   - txn.timestampMs) < 60_000L)
        }

    private fun detectProvider(txn: UpiSmsParser.ParsedTransaction): UpiProvider {
        return UpiProvider.values()
            .filter { it != UpiProvider.ALL && it != UpiProvider.OTHER_BANKS }
            .firstOrNull { it.matches(txn) }
            ?: if (UpiProvider.OTHER_BANKS.matches(txn)) UpiProvider.OTHER_BANKS
               else UpiProvider.ALL
    }

    private fun buildMonthOptions(): List<MonthOption> {
        val options = mutableListOf<MonthOption>()
        val cal = Calendar.getInstance()
        repeat(6) {
            val month = cal.get(Calendar.MONTH) + 1
            val year  = cal.get(Calendar.YEAR)
            val label = when (it) {
                0    -> "This Month (${monthName(month)} $year)"
                1    -> "Last Month (${monthName(month)} $year)"
                else -> "${monthName(month)} $year"
            }
            options.add(MonthOption(label, month, year))
            cal.add(Calendar.MONTH, -1)
        }
        return options
    }

    private fun monthName(month: Int) = listOf(
        "Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec"
    )[month - 1]
}
