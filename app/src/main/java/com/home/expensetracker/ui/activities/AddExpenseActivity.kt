package com.home.expensetracker.ui.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.home.expensetracker.data.models.Category
import com.home.expensetracker.data.models.Expense
import com.home.expensetracker.databinding.ActivityAddExpenseBinding
import com.home.expensetracker.ui.adapters.CategoryAdapter
import com.home.expensetracker.utils.Constants
import com.home.expensetracker.utils.DateUtils
import com.home.expensetracker.viewmodel.ExpenseViewModel
import com.home.expensetracker.viewmodel.UiEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class AddExpenseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddExpenseBinding
    private val viewModel: ExpenseViewModel by viewModels()

    private var selectedDate = System.currentTimeMillis()
    private var selectedCategory: Category? = null
    private var categories = listOf<Category>()
    private var editExpenseId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editExpenseId = intent.getLongExtra("expense_id", -1L)
        if (editExpenseId != -1L) supportActionBar?.title = "Edit Expense"

        setupUI()
        observeData()
    }

    private fun setupUI() {
        binding.tvDate.text = DateUtils.formatDate(selectedDate)
        binding.layoutDate.setOnClickListener { showDatePicker() }

        binding.spinnerPayment.adapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line, Constants.PAYMENT_METHODS
        )
        binding.spinnerRecurring.adapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line, Constants.RECURRING_PERIODS
        )
        binding.btnSave.setOnClickListener { saveExpense() }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { cats ->
                        categories = cats
                        setupCategoryGrid(cats)
                    }
                }
                launch {
                    viewModel.uiEvent.collect { event ->
                        when (event) {
                            is UiEvent.ExpenseAdded, is UiEvent.ExpenseUpdated -> {
                                Toast.makeText(this@AddExpenseActivity, "Expense saved!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            is UiEvent.Error -> Toast.makeText(this@AddExpenseActivity, event.message, Toast.LENGTH_SHORT).show()
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun setupCategoryGrid(categories: List<Category>) {
        val adapter = CategoryAdapter { category ->
            selectedCategory = category
            binding.tvCategorySelected.text = "${category.icon} ${category.name}"
        }
        adapter.submitList(categories)
        binding.rvCategories.layoutManager = GridLayoutManager(this, 4)
        binding.rvCategories.adapter = adapter
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            selectedDate = cal.timeInMillis
            binding.tvDate.text = DateUtils.formatDate(selectedDate)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveExpense() {
        val title = binding.etTitle.text.toString().trim()
        val amount = binding.etAmount.text.toString().toDoubleOrNull()
        val note = binding.etNote.text.toString().trim()

        if (title.isEmpty()) { binding.etTitle.error = "Enter a title"; return }
        if (amount == null || amount <= 0) { binding.etAmount.error = "Enter a valid amount"; return }
        if (selectedCategory == null) { Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show(); return }

        val expense = Expense(
            id              = if (editExpenseId != -1L) editExpenseId else 0,
            title           = title,
            amount          = amount,
            category        = selectedCategory!!.name,
            categoryIcon    = selectedCategory!!.icon,
            date            = selectedDate,
            note            = note,
            paymentMethod   = binding.spinnerPayment.selectedItem.toString(),
            isRecurring     = binding.switchRecurring.isChecked,
            recurringPeriod = binding.spinnerRecurring.selectedItem.toString()
        )
        if (editExpenseId != -1L) viewModel.updateExpense(expense)
        else viewModel.insertExpense(expense)
    }

    private fun loadExpenseForEdit(id: Long) {
        lifecycleScope.launch {
            val expense = viewModel.allExpenses.value.firstOrNull { it.id == id } ?: return@launch
            binding.etTitle.setText(expense.title)
            binding.etAmount.setText(expense.amount.toString())
            binding.etNote.setText(expense.note)
            selectedDate = expense.date
            binding.tvDate.text = DateUtils.formatDate(selectedDate)
            val pi = Constants.PAYMENT_METHODS.indexOf(expense.paymentMethod)
            if (pi >= 0) binding.spinnerPayment.setSelection(pi)
            binding.switchRecurring.isChecked = expense.isRecurring
            val ri = Constants.RECURRING_PERIODS.indexOf(expense.recurringPeriod)
            if (ri >= 0) binding.spinnerRecurring.setSelection(ri)
            selectedCategory = categories.firstOrNull { it.name == expense.category }
            selectedCategory?.let { binding.tvCategorySelected.text = "${it.icon} ${it.name}" }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { onBackPressedDispatcher.onBackPressed(); return true }
        return super.onOptionsItemSelected(item)
    }
}
