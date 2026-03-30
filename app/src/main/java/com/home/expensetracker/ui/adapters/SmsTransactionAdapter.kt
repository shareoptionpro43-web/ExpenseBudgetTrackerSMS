package com.home.expensetracker.ui.adapters

import android.view.LayoutInflater
import android.view.View
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
            override fun areItemsTheSame(a: SmsImportViewModel.SmsTransaction, b: SmsImportViewModel.SmsTransaction) =
                a.parsed.upiRef == b.parsed.upiRef && a.parsed.timestampMs == b.parsed.timestampMs
            override fun areContentsTheSame(a: SmsImportViewModel.SmsTransaction, b: SmsImportViewModel.SmsTransaction) =
                a == b
        }
    }

    inner class VH(private val b: ItemSmsTransactionBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: SmsImportViewModel.SmsTransaction) {
            val p = item.parsed

            // ── Top row ───────────────────────────────────────────────────────
            b.tvIcon.text           = item.categoryIcon
            b.tvMerchant.text       = p.merchant.ifEmpty { "UPI Payment" }
            b.tvAmount.text         = "- ${CurrencyUtils.format(p.amount)}"
            b.tvDate.text           = DateUtils.formatDateTime(p.timestampMs)
            b.tvProviderBadge.text  = "${item.provider.emoji} ${item.provider.displayName}"

            // ── Detail rows ───────────────────────────────────────────────────

            // Paid To
            b.tvPaidTo.text = p.merchant.ifEmpty { "UPI Payment" }

            // UPI ID — show only if found
            if (p.upiId.isNotEmpty()) {
                b.rowUpiId.visibility = View.VISIBLE
                b.tvUpiId.text = p.upiId
            } else {
                b.rowUpiId.visibility = View.GONE
            }

            // Category
            b.tvCategory.text = "${item.categoryIcon} ${item.category}"

            // Bank + Account
            val bankText = buildString {
                if (p.bankName.isNotEmpty()) append(p.bankName)
                if (p.bankAccount.isNotEmpty()) {
                    if (isNotEmpty()) append("  ·  ")
                    append(p.bankAccount)
                }
            }
            if (bankText.isNotEmpty()) {
                b.rowBank.visibility = View.VISIBLE
                b.tvBank.text = bankText
            } else {
                b.rowBank.visibility = View.GONE
            }

            // UPI Ref
            if (p.upiRef.isNotEmpty()) {
                b.rowRef.visibility = View.VISIBLE
                b.tvRef.text = p.upiRef
            } else {
                b.rowRef.visibility = View.GONE
            }

            // Available Balance
            if (p.availableBalance != null && p.availableBalance > 0) {
                b.rowBalance.visibility = View.VISIBLE
                b.tvBalance.text = CurrencyUtils.format(p.availableBalance)
            } else {
                b.rowBalance.visibility = View.GONE
            }

            // ── Checkbox ──────────────────────────────────────────────────────
            b.cbSelect.setOnCheckedChangeListener(null)
            b.cbSelect.isChecked = item.isSelected
            b.cbSelect.setOnCheckedChangeListener { _, checked ->
                item.isSelected = checked
                onCheckedChange(adapterPosition, checked)
            }
            b.root.setOnClickListener { b.cbSelect.isChecked = !b.cbSelect.isChecked }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSmsTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
