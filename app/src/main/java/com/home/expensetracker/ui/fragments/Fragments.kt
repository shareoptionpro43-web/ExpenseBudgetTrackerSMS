package com.home.expensetracker.ui.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.home.expensetracker.data.database.CategoryTotal
import com.home.expensetracker.data.models.Budget
import com.home.expensetracker.data.models.Category
import com.home.expensetracker.databinding.FragmentBudgetBinding
import com.home.expensetracker.databinding.FragmentDashboardBinding
import com.home.expensetracker.databinding.FragmentReportsBinding
import com.home.expensetracker.databinding.FragmentTransactionsBinding
import com.home.expensetracker.ui.activities.AddExpenseActivity
import com.home.expensetracker.ui.adapters.BudgetAdapter
import com.home.expensetracker.ui.adapters.CategoryTotalsAdapter
import com.home.expensetracker.ui.adapters.ExpenseAdapter
import com.home.expensetracker.utils.CSVExporter
import com.home.expensetracker.utils.CurrencyUtils
import com.home.expensetracker.utils.DateUtils
import com.home.expensetracker.viewmodel.BudgetViewModel
import com.home.expensetracker.viewmodel.ExpenseViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// ── Dashboard Fragment ────────────────────────────────────────────────────────
@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val expenseViewModel: ExpenseViewModel by viewModels()
    private val budgetViewModel: BudgetViewModel by viewModels()
    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var categoryAdapter: CategoryTotalsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        expenseAdapter = ExpenseAdapter(
            onEdit = { expense ->
                val intent = Intent(requireContext(), AddExpenseActivity::class.java)
                intent.putExtra("expense_id", expense.id)
                startActivity(intent)
            },
            onDelete = { expense -> expenseViewModel.deleteExpense(expense) }
        )
        binding.rvRecentExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentExpenses.adapter = expenseAdapter

        categoryAdapter = CategoryTotalsAdapter()
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = categoryAdapter

        setupMonthSelector()
        observeData()

        binding.tvViewAll.setOnClickListener { }
    }

    private fun setupMonthSelector() {
        updateMonthLabel()
        binding.btnPrevMonth.setOnClickListener {
            val (m, y) = DateUtils.previousMonth(expenseViewModel.selectedMonth.value, expenseViewModel.selectedYear.value)
            expenseViewModel.setMonth(m, y); budgetViewModel.setMonth(m, y); updateMonthLabel()
        }
        binding.btnNextMonth.setOnClickListener {
            val (m, y) = DateUtils.nextMonth(expenseViewModel.selectedMonth.value, expenseViewModel.selectedYear.value)
            expenseViewModel.setMonth(m, y); budgetViewModel.setMonth(m, y); updateMonthLabel()
        }
    }

    private fun updateMonthLabel() {
        binding.tvMonthYear.text = DateUtils.getMonthYear(
            expenseViewModel.selectedMonth.value, expenseViewModel.selectedYear.value
        )
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    expenseViewModel.currentMonthTotal.collect { total ->
                        binding.tvTotalExpenses.text = CurrencyUtils.format(total ?: 0.0)
                    }
                }
                launch {
                    budgetViewModel.totalBudget.collect { budget ->
                        binding.tvTotalBudget.text = CurrencyUtils.format(budget ?: 0.0)
                        val spent = expenseViewModel.currentMonthTotal.value ?: 0.0
                        val remaining = (budget ?: 0.0) - spent
                        binding.tvRemaining.text = CurrencyUtils.format(remaining)
                    }
                }
                launch {
                    expenseViewModel.currentMonthExpenses.collect { expenses ->
                        binding.tvTransactionCount.text = "${expenses.size} transactions"
                        expenseAdapter.submitList(expenses.take(5))
                        binding.tvEmptyRecent.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    expenseViewModel.currentMonthCategoryTotals.collect { totals ->
                        val total = totals.sumOf { it.totalAmount }
                        categoryAdapter.setTotal(total)
                        categoryAdapter.submitList(totals)
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Transactions Fragment ─────────────────────────────────────────────────────
@AndroidEntryPoint
class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpenseViewModel by viewModels()
    private lateinit var adapter: ExpenseAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExpenseAdapter(
            onEdit = { expense ->
                val intent = Intent(requireContext(), AddExpenseActivity::class.java)
                intent.putExtra("expense_id", expense.id)
                startActivity(intent)
            },
            onDelete = { expense ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Expense")
                    .setMessage("Delete \"${expense.title}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteExpense(expense) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentMonthExpenses.collect { expenses ->
                    adapter.submitList(expenses)
                    binding.tvEmpty.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvTransactions.visibility = if (expenses.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Budget Fragment ───────────────────────────────────────────────────────────
@AndroidEntryPoint
class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BudgetViewModel by viewModels()
    private val expenseViewModel: ExpenseViewModel by viewModels()
    private lateinit var adapter: BudgetAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BudgetAdapter(
            onEdit = { budget -> showEditBudgetDialog(budget) },
            onDelete = { budget -> viewModel.deleteBudget(budget) }
        )
        binding.rvBudgets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBudgets.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentMonthBudgets.collect { budgets ->
                        adapter.submitList(budgets)
                        binding.tvEmpty.visibility = if (budgets.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvTotalBudget.text = CurrencyUtils.format(budgets.sumOf { it.monthlyLimit })
                    }
                }
                launch {
                    expenseViewModel.currentMonthTotal.collect { spent ->
                        binding.tvTotalSpent.text = CurrencyUtils.format(spent ?: 0.0)
                        val total = viewModel.totalBudget.value ?: 0.0
                        if (total > 0) {
                            val pct = ((spent ?: 0.0) / total * 100).toInt().coerceIn(0, 100)
                            binding.pbOverall.progress = pct
                            binding.tvOverallPercent.text = "$pct% of budget used"
                        }
                    }
                }
            }
        }

        binding.fabAddBudget.setOnClickListener { showAddBudgetDialog() }
    }

    private fun showAddBudgetDialog() {
        // Pass categories directly — they are already collected by this fragment
        val cats = expenseViewModel.categories.value
        if (cats.isEmpty()) {
            // Categories not yet loaded — wait and retry once
            viewLifecycleOwner.lifecycleScope.launch {
                expenseViewModel.categories.collect { loaded ->
                    if (loaded.isNotEmpty()) {
                        AddBudgetDialog.newInstance(loaded)
                            .show(childFragmentManager, "add_budget")
                        return@collect
                    }
                }
            }
        } else {
            AddBudgetDialog.newInstance(cats)
                .show(childFragmentManager, "add_budget")
        }
    }

    private fun showEditBudgetDialog(budget: Budget) {
        val cats = expenseViewModel.categories.value
        AddBudgetDialog.newInstance(cats, budget)
            .show(childFragmentManager, "edit_budget")
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Reports Fragment ──────────────────────────────────────────────────────────
@AndroidEntryPoint
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpenseViewModel by viewModels()
    private lateinit var categoryTotalsAdapter: CategoryTotalsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryTotalsAdapter = CategoryTotalsAdapter()
        binding.rvCategoryTotals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategoryTotals.adapter = categoryTotalsAdapter

        setupCharts()
        observeData()
        binding.btnExport.setOnClickListener { exportData() }
    }

    private fun setupCharts() {
        binding.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 55f
            transparentCircleRadius = 60f
            setHoleColor(android.graphics.Color.TRANSPARENT)
            legend.isEnabled = true
        }
        binding.barChart.apply {
            description.isEnabled = false
            setFitBars(true)
            legend.isEnabled = false
            xAxis.granularity = 1f
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentMonthCategoryTotals.collect { totals ->
                        val total = totals.sumOf { it.totalAmount }
                        categoryTotalsAdapter.setTotal(total)
                        categoryTotalsAdapter.submitList(totals)
                        updatePieChart(totals)
                        binding.tvNoData.visibility = if (totals.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun updatePieChart(totals: List<CategoryTotal>) {
        if (totals.isEmpty()) return
        val entries = totals.map { PieEntry(it.totalAmount.toFloat(), it.category) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 11f
            sliceSpace = 2f
        }
        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.invalidate()
    }

    private fun exportData() {
        val expenses = viewModel.currentMonthExpenses.value
        if (expenses.isEmpty()) {
            Toast.makeText(requireContext(), "No expenses to export", Toast.LENGTH_SHORT).show()
            return
        }
        val file = CSVExporter.exportExpenses(requireContext(), expenses)
        if (file != null)
            Toast.makeText(requireContext(), "Exported: ${file.name}", Toast.LENGTH_LONG).show()
        else
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Add Budget Dialog ─────────────────────────────────────────────────────────
class AddBudgetDialog : DialogFragment() {

    private val budgetViewModel: BudgetViewModel by activityViewModels()

    companion object {
        private const val ARG_CAT_NAMES  = "cat_names"
        private const val ARG_CAT_ICONS  = "cat_icons"
        private const val ARG_BUDGET_ID  = "budget_id"
        private const val ARG_BUDGET_CAT = "budget_cat"
        private const val ARG_BUDGET_AMT = "budget_amt"

        fun newInstance(
            categories: List<com.home.expensetracker.data.models.Category>,
            existingBudget: Budget? = null
        ): AddBudgetDialog {
            return AddBudgetDialog().apply {
                arguments = Bundle().apply {
                    putStringArray(ARG_CAT_NAMES, categories.map { it.name }.toTypedArray())
                    putStringArray(ARG_CAT_ICONS, categories.map { it.icon }.toTypedArray())
                    existingBudget?.let {
                        putLong(ARG_BUDGET_ID, it.id)
                        putString(ARG_BUDGET_CAT, it.category)
                        putDouble(ARG_BUDGET_AMT, it.monthlyLimit)
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val view = layoutInflater.inflate(
            com.home.expensetracker.R.layout.dialog_add_budget, null
        )

        val catNames = arguments?.getStringArray(ARG_CAT_NAMES) ?: emptyArray()
        val catIcons = arguments?.getStringArray(ARG_CAT_ICONS) ?: emptyArray()
        val existingCat = arguments?.getString(ARG_BUDGET_CAT)
        val existingAmt = arguments?.getDouble(ARG_BUDGET_AMT, 0.0) ?: 0.0

        // Category spinner
        val spinner = view.findViewById<android.widget.Spinner>(
            com.home.expensetracker.R.id.spinner_category
        )
        val displayNames = catNames.mapIndexed { i, name ->
            "${catIcons.getOrElse(i) { "" }} $name"
        }
        val spinnerAdapter = android.widget.ArrayAdapter(
            ctx,
            android.R.layout.simple_spinner_item,
            displayNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.adapter = spinnerAdapter

        // Pre-select existing category if editing
        if (existingCat != null) {
            val idx = catNames.indexOfFirst { it == existingCat }
            if (idx >= 0) spinner.setSelection(idx)
        }

        // Amount field
        val tilAmount = view.findViewById<com.google.android.material.textfield.TextInputLayout>(
            com.home.expensetracker.R.id.til_amount
        )
        val etAmount = view.findViewById<com.google.android.material.textfield.TextInputEditText>(
            com.home.expensetracker.R.id.et_amount
        )
        if (existingAmt > 0) etAmount.setText(existingAmt.toInt().toString())

        // Build dialog with no default buttons — we use our own layout buttons
        val dialog = android.app.Dialog(ctx, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        dialog.setCancelable(true)

        // Cancel button
        view.findViewById<Button>(com.home.expensetracker.R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        // Save button
        view.findViewById<Button>(com.home.expensetracker.R.id.btn_save).setOnClickListener {
            val text = etAmount.text?.toString()?.trim() ?: ""
            tilAmount.error = null

            if (text.isEmpty()) {
                tilAmount.error = "Please enter an amount"
                etAmount.requestFocus()
                return@setOnClickListener
            }

            val amount = text.replace(",", ".").toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                tilAmount.error = "Enter a number greater than 0"
                etAmount.requestFocus()
                return@setOnClickListener
            }

            if (catNames.isEmpty()) {
                Toast.makeText(ctx, "No categories available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val safeIdx = spinner.selectedItemPosition.coerceIn(0, catNames.lastIndex)
            val budget = Budget(
                id           = arguments?.getLong(ARG_BUDGET_ID, 0) ?: 0,
                category     = catNames[safeIdx],
                categoryIcon = catIcons.getOrElse(safeIdx) { "📦" },
                monthlyLimit = amount,
                month        = budgetViewModel.selectedMonth.value,
                year         = budgetViewModel.selectedYear.value
            )
            budgetViewModel.insertBudget(budget)
            val action = if (existingCat != null) "Updated" else "Budget set"
            Toast.makeText(
                ctx,
                "$action: ${catNames[safeIdx]} ₹${amount.toInt()}",
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        return dialog
    }
}
