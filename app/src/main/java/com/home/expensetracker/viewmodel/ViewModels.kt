package com.home.expensetracker.viewmodel

import androidx.lifecycle.*
import com.home.expensetracker.data.models.*
import com.home.expensetracker.data.repository.ExpenseRepository
import com.home.expensetracker.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val calendar = Calendar.getInstance()
    private val _selectedMonth = MutableStateFlow(calendar.get(Calendar.MONTH) + 1)
    private val _selectedYear  = MutableStateFlow(calendar.get(Calendar.YEAR))

    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    val selectedYear:  StateFlow<Int> = _selectedYear.asStateFlow()

    val allExpenses: StateFlow<List<Expense>> = repository.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentMonthExpenses: StateFlow<List<Expense>> =
        combine(_selectedMonth, _selectedYear) { m, y -> m to y }
            .flatMapLatest { (m, y) ->
                repository.getExpensesByMonth(m.toString().padStart(2, '0'), y.toString())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentMonthTotal: StateFlow<Double?> =
        combine(_selectedMonth, _selectedYear) { m, y -> m to y }
            .flatMapLatest { (m, y) ->
                val (start, end) = DateUtils.getMonthRange(m, y)
                repository.getTotalExpenseForPeriod(start, end)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentMonthCategoryTotals =
        combine(_selectedMonth, _selectedYear) { m, y -> m to y }
            .flatMapLatest { (m, y) ->
                val (start, end) = DateUtils.getMonthRange(m, y)
                repository.getCategoryTotals(start, end)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    fun setMonth(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value  = year
    }

    fun insertExpense(expense: Expense) = viewModelScope.launch {
        repository.insertExpense(expense)
        _uiEvent.emit(UiEvent.ExpenseAdded)
    }

    fun updateExpense(expense: Expense) = viewModelScope.launch {
        repository.updateExpense(expense)
        _uiEvent.emit(UiEvent.ExpenseUpdated)
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        repository.deleteExpense(expense)
        _uiEvent.emit(UiEvent.ExpenseDeleted)
    }
}

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val calendar = Calendar.getInstance()
    private val _selectedMonth = MutableStateFlow(calendar.get(Calendar.MONTH) + 1)
    private val _selectedYear  = MutableStateFlow(calendar.get(Calendar.YEAR))

    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    val selectedYear:  StateFlow<Int> = _selectedYear.asStateFlow()

    val currentMonthBudgets: StateFlow<List<Budget>> =
        combine(_selectedMonth, _selectedYear) { m, y -> m to y }
            .flatMapLatest { (m, y) -> repository.getBudgetsForMonth(m, y) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalBudget: StateFlow<Double?> =
        combine(_selectedMonth, _selectedYear) { m, y -> m to y }
            .flatMapLatest { (m, y) -> repository.getTotalBudgetForMonth(m, y) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    fun setMonth(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value  = year
    }

    fun insertBudget(budget: Budget) = viewModelScope.launch {
        repository.insertBudget(budget)
        _uiEvent.emit(UiEvent.BudgetSaved)
    }

    fun updateBudget(budget: Budget) = viewModelScope.launch {
        repository.updateBudget(budget)
        _uiEvent.emit(UiEvent.BudgetSaved)
    }

    fun deleteBudget(budget: Budget) = viewModelScope.launch {
        repository.deleteBudget(budget)
    }
}

sealed class UiEvent {
    object ExpenseAdded   : UiEvent()
    object ExpenseUpdated : UiEvent()
    object ExpenseDeleted : UiEvent()
    object BudgetSaved    : UiEvent()
    data class Error(val message: String) : UiEvent()
}
