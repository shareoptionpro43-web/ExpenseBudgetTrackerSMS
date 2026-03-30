package com.home.expensetracker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.home.expensetracker.data.database.CategoryTotal
import com.home.expensetracker.data.models.Budget
import com.home.expensetracker.data.models.Category
import com.home.expensetracker.data.models.Expense
import com.home.expensetracker.databinding.*
import com.home.expensetracker.utils.CurrencyUtils
import com.home.expensetracker.utils.DateUtils

// ── Expense Adapter ────────────────────────────────────────────────────────────
class ExpenseAdapter(
    private val onEdit: (Expense) -> Unit,
    private val onDelete: (Expense) -> Unit
) : ListAdapter<Expense, ExpenseAdapter.VH>(object : DiffUtil.ItemCallback<Expense>() {
    override fun areItemsTheSame(a: Expense, b: Expense) = a.id == b.id
    override fun areContentsTheSame(a: Expense, b: Expense) = a == b
}) {
    inner class VH(private val b: ItemExpenseBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: Expense) {
            b.tvIcon.text     = e.categoryIcon
            b.tvTitle.text    = e.title
            b.tvCategory.text = e.category
            b.tvDate.text     = DateUtils.formatDate(e.date)
            b.tvAmount.text   = CurrencyUtils.format(e.amount)
            b.tvPayment.text  = e.paymentMethod
            b.btnEdit.setOnClickListener   { onEdit(e) }
            b.btnDelete.setOnClickListener { onDelete(e) }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemExpenseBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ── Category Adapter ───────────────────────────────────────────────────────────
class CategoryAdapter(
    private val onSelect: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.VH>(object : DiffUtil.ItemCallback<Category>() {
    override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
    override fun areContentsTheSame(a: Category, b: Category) = a == b
}) {
    private var selectedPos = -1

    inner class VH(private val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(cat: Category, selected: Boolean) {
            b.tvIcon.text = cat.icon
            b.tvName.text = cat.name
            b.root.isSelected = selected
            b.root.setOnClickListener {
                val prev = selectedPos
                selectedPos = adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onSelect(cat)
            }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos), pos == selectedPos)
}

// ── Budget Adapter ─────────────────────────────────────────────────────────────
class BudgetAdapter(
    private val onEdit: (Budget) -> Unit,
    private val onDelete: (Budget) -> Unit
) : ListAdapter<Budget, BudgetAdapter.VH>(object : DiffUtil.ItemCallback<Budget>() {
    override fun areItemsTheSame(a: Budget, b: Budget) = a.id == b.id
    override fun areContentsTheSame(a: Budget, b: Budget) = a == b
}) {
    inner class VH(private val b: ItemBudgetBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(budget: Budget) {
            b.tvIcon.text      = budget.categoryIcon
            b.tvCategory.text  = budget.category
            b.tvLimit.text     = "Limit: ${CurrencyUtils.format(budget.monthlyLimit)}"
            b.tvSpent.text     = "Spent: ₹0"
            b.tvPercent.text   = "0%"
            b.pbBudget.progress = 0
            b.btnEdit.setOnClickListener   { onEdit(budget) }
            b.btnDelete.setOnClickListener { onDelete(budget) }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemBudgetBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ── Category Totals Adapter ────────────────────────────────────────────────────
class CategoryTotalsAdapter : ListAdapter<CategoryTotal, CategoryTotalsAdapter.VH>(
    object : DiffUtil.ItemCallback<CategoryTotal>() {
        override fun areItemsTheSame(a: CategoryTotal, b: CategoryTotal) = a.category == b.category
        override fun areContentsTheSame(a: CategoryTotal, b: CategoryTotal) = a == b
    }
) {
    private var totalAmount = 1.0
    fun setTotal(t: Double) { totalAmount = if (t > 0) t else 1.0 }

    inner class VH(private val b: ItemCategoryTotalBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CategoryTotal) {
            b.tvCategory.text = item.category
            b.tvAmount.text   = CurrencyUtils.format(item.totalAmount)
            val pct = ((item.totalAmount / totalAmount) * 100).toInt()
            b.tvPercent.text  = "$pct%"
            b.progressBar.progress = pct
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemCategoryTotalBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}
