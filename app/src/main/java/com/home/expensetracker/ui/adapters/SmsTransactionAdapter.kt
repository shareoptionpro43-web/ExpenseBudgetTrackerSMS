package com.home.expensetracker.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.home.expensetracker.databinding.ItemSmsTransactionBinding
import com.home.expensetracker.sms.SmsImportViewModel
import com.home.expensetracker.utils.CurrencyUtils
import com.home.expensetracker.utils.DateUtils

class SmsTransactionAdapter(
    private val onCheckedChange: (position: Int, isSelected: Boolean) -> Unit
) : ListAdapter<SmsImportViewModel.SmsTransaction, SmsTransactionAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SmsImportViewModel.SmsTransaction>() {
            override fun areItemsTheSame(
                a: SmsImportViewModel.SmsTransaction,
                b: SmsImportViewModel.SmsTransaction
            ) = a.parsed.upiRef == b.parsed.upiRef &&
                a.parsed.timestampMs == b.parsed.timestampMs

            override fun areContentsTheSame(
                a: SmsImportViewModel.SmsTransaction,
                b: SmsImportViewModel.SmsTransaction
            ) = a == b
        }
    }

    inner class VH(private val b: ItemSmsTransactionBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: SmsImportViewModel.SmsTransaction) {
            b.tvIcon.text     = item.categoryIcon
            b.tvMerchant.text = item.parsed.merchant
            b.tvAmount.text   = CurrencyUtils.format(item.parsed.amount)
            b.tvCategory.text = item.category
            b.tvDate.text     = DateUtils.formatDate(item.parsed.timestampMs)
            b.tvRef.text      = if (item.parsed.upiRef.isNotEmpty())
                "Ref: ${item.parsed.upiRef}" else "UPI Payment"

            b.cbSelect.setOnCheckedChangeListener(null)
            b.cbSelect.isChecked = item.isSelected

            b.cbSelect.setOnCheckedChangeListener { _, checked ->
                item.isSelected = checked
                onCheckedChange(adapterPosition, checked)
            }

            b.root.setOnClickListener {
                b.cbSelect.isChecked = !b.cbSelect.isChecked
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            ItemSmsTransactionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))
}
