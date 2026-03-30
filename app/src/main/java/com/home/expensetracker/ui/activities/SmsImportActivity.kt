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
import com.home.expensetracker.ui.adapters.SmsTransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmsImportBinding
    private val viewModel: SmsImportViewModel by viewModels()
    private lateinit var adapter: SmsTransactionAdapter

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.scanSms()
        else showPermissionDeniedDialog()
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
                        is ImportState.Idle     -> showIdleState()
                        is ImportState.Scanning -> showScanningState()
                        is ImportState.Preview  -> showPreviewState(state)
                        is ImportState.Importing-> showImportingState()
                        is ImportState.Done     -> showDoneState(state.imported)
                        is ImportState.Error    -> showErrorState(state.message)
                    }
                }
            }
        }
    }

    private fun showIdleState() {
        binding.layoutIdle.visibility    = View.VISIBLE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutPreview.visibility = View.GONE
        binding.layoutDone.visibility    = View.GONE
    }

    private fun showScanningState() {
        binding.layoutIdle.visibility    = View.GONE
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutPreview.visibility = View.GONE
        binding.layoutDone.visibility    = View.GONE
        binding.tvLoadingMsg.text        = "Scanning your SMS messages…"
    }

    private fun showPreviewState(state: ImportState.Preview) {
        binding.layoutIdle.visibility    = View.GONE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutPreview.visibility = View.VISIBLE
        binding.layoutDone.visibility    = View.GONE

        if (state.transactions.isEmpty()) {
            binding.tvSmsCount.text           = "No UPI transactions found"
            binding.tvSmsSub.text             = "No debit SMS found in the last 30 days"
            binding.btnImport.visibility      = View.GONE
            binding.btnSelectAll.visibility   = View.GONE
            binding.rvTransactions.visibility = View.GONE
        } else {
            val selected = state.transactions.count { it.isSelected }
            binding.tvSmsCount.text           = "${state.transactions.size} UPI transactions found"
            binding.tvSmsSub.text             = "${state.alreadyImported} already imported · $selected selected"
            binding.btnImport.visibility      = View.VISIBLE
            binding.btnSelectAll.visibility   = View.VISIBLE
            binding.rvTransactions.visibility = View.VISIBLE
            adapter.submitList(state.transactions.toMutableList())
            updateImportButton(state.transactions)
        }
    }

    private fun showImportingState() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutPreview.visibility = View.GONE
        binding.tvLoadingMsg.text        = "Importing transactions…"
    }

    private fun showDoneState(count: Int) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutDone.visibility    = View.VISIBLE
        binding.layoutPreview.visibility = View.GONE
        binding.tvDoneMessage.text       = "✅ $count expenses imported successfully!"
        binding.btnDone.setOnClickListener      { finish() }
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
        binding.btnImport.text    = if (count > 0) "Import $count Expenses" else "Import"
        binding.btnSelectAll.text = if (transactions.all { it.isSelected }) "Deselect All" else "Select All"
        binding.tvSmsSub.text     = "${transactions.size} found · $count selected"
    }

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
                        "BudgetBuddy needs access to your SMS to detect UPI payment transactions.\n\n" +
                        "Your messages are read only on your device — nothing is sent to any server."
                    )
                    .setPositiveButton("Grant Permission") { _, _ ->
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage(
                "SMS permission is required to import UPI transactions.\n" +
                "Go to Settings → Apps → BudgetBuddy → Permissions to enable it."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
