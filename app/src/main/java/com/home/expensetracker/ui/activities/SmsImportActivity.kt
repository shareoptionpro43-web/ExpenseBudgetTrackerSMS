package com.home.expensetracker.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.home.expensetracker.databinding.ActivitySmsImportBinding
import com.home.expensetracker.sms.SmsImportViewModel
import com.home.expensetracker.sms.SmsImportViewModel.ImportState
import com.home.expensetracker.utils.CurrencyUtils
import com.home.expensetracker.utils.DateUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmsImportBinding
    private val viewModel: SmsImportViewModel by viewModels()
    private lateinit var adapter: SmsTransactionAdapter

    // ── Permission launcher ───────────────────────────────────────────────────
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scanSms()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmsImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Import from SMS"

        setupRecyclerView()
        setupButtons()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = SmsTransactionAdapter { position, isSelected ->
            val state = viewModel.state.value
            if (state is ImportState.Preview) {
                state.transactions[position].isSelected = isSelected
                updateImportButton(state.transactions)
            }
        }
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener { checkPermissionAndScan() }

        binding.btnImport.setOnClickListener {
            val state = viewModel.state.value
            if (state is ImportState.Preview) {
                val selected = state.transactions.filter { it.isSelected }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "Please select at least one transaction", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                AlertDialog.Builder(this)
                    .setTitle("Import ${selected.size} Transactions")
                    .setMessage("Add ${selected.size} UPI transactions as expenses?")
                    .setPositiveButton("Import") { _, _ ->
                        viewModel.importSelected(state.transactions)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        binding.btnSelectAll.setOnClickListener {
            val state = viewModel.state.value
            if (state is ImportState.Preview) {
                val allSelected = state.transactions.all { it.isSelected }
                state.transactions.forEach { it.isSelected = !allSelected }
                adapter.notifyDataSetChanged()
                updateImportButton(state.transactions)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is ImportState.Idle -> showIdleState()
                        is ImportState.Scanning -> showScanningState()
                        is ImportState.Preview -> showPreviewState(state)
                        is ImportState.Importing -> showImportingState()
                        is ImportState.Done -> showDoneState(state.imported)
                        is ImportState.Error -> showErrorState(state.message)
                    }
                }
            }
        }
    }

    // ── State UI ──────────────────────────────────────────────────────────────

    private fun showIdleState() {
        binding.layoutIdle.visibility     = View.VISIBLE
        binding.layoutLoading.visibility  = View.GONE
        binding.layoutPreview.visibility  = View.GONE
        binding.layoutDone.visibility     = View.GONE
    }

    private fun showScanningState() {
        binding.layoutIdle.visibility     = View.GONE
        binding.layoutLoading.visibility  = View.VISIBLE
        binding.layoutPreview.visibility  = View.GONE
        binding.layoutDone.visibility     = View.GONE
        binding.tvLoadingMsg.text         = "Scanning your SMS messages…"
    }

    private fun showPreviewState(state: ImportState.Preview) {
        binding.layoutIdle.visibility    = View.GONE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutPreview.visibility = View.VISIBLE
        binding.layoutDone.visibility    = View.GONE

        if (state.transactions.isEmpty()) {
            binding.tvSmsCount.text   = "No UPI transactions found"
            binding.tvSmsSub.text     = "No debit SMS messages found in the last 30 days"
            binding.btnImport.visibility    = View.GONE
            binding.btnSelectAll.visibility = View.GONE
            binding.rvTransactions.visibility = View.GONE
        } else {
            val selected = state.transactions.count { it.isSelected }
            binding.tvSmsCount.text = "${state.transactions.size} UPI transactions found"
            binding.tvSmsSub.text   = "${state.alreadyImported} already imported · $selected selected"
            binding.btnImport.visibility      = View.VISIBLE
            binding.btnSelectAll.visibility   = View.VISIBLE
            binding.rvTransactions.visibility = View.VISIBLE
            adapter.submitList(state.transactions.toMutableList())
            updateImportButton(state.transactions)
        }
    }

    private fun showImportingState() {
        binding.layoutLoading.visibility  = View.VISIBLE
        binding.layoutPreview.visibility  = View.GONE
        binding.tvLoadingMsg.text         = "Importing transactions…"
    }

    private fun showDoneState(count: Int) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutDone.visibility    = View.VISIBLE
        binding.layoutPreview.visibility = View.GONE
        binding.tvDoneMessage.text       = "✅ $count expenses imported successfully!"
        binding.btnDone.setOnClickListener { finish() }
        binding.btnScanAgain.setOnClickListener {
            viewModel.reset()
            checkPermissionAndScan()
        }
    }

    private fun showErrorState(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        showIdleState()
    }

    private fun updateImportButton(transactions: List<SmsImportViewModel.SmsTransaction>) {
        val count = transactions.count { it.isSelected }
        binding.btnImport.text = if (count > 0) "Import $count Expenses" else "Import"
        val allSelected = transactions.all { it.isSelected }
        binding.btnSelectAll.text = if (allSelected) "Deselect All" else "Select All"
        binding.tvSmsSub.text = "${transactions.size} found · $count selected"
    }

    // ── Permission ────────────────────────────────────────────────────────────

    private fun checkPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.scanSms()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) -> {
                AlertDialog.Builder(this)
                    .setTitle("SMS Permission Required")
                    .setMessage(
                        "BudgetBuddy needs access to your SMS messages to automatically " +
                        "detect and import UPI payment transactions.\n\n" +
                        "Your messages are only read locally — nothing is sent to any server."
                    )
                    .setPositiveButton("Grant Permission") { _, _ ->
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage(
                "SMS permission is required to import UPI transactions. " +
                "You can grant it from Settings → Apps → BudgetBuddy → Permissions."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { onBackPressedDispatcher.onBackPressed(); return true }
        return super.onOptionsItemSelected(item)
    }
}

// ── RecyclerView Adapter ──────────────────────────────────────────────────────
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.home.expensetracker.databinding.ItemSmsTransactionBinding

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
        VH(ItemSmsTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))
}
