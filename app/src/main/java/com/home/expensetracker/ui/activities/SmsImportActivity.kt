package com.home.expensetracker.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
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
import com.google.android.material.chip.Chip
import com.home.expensetracker.R
import com.home.expensetracker.databinding.ActivitySmsImportBinding
import com.home.expensetracker.sms.SmsImportViewModel
import com.home.expensetracker.sms.SmsImportViewModel.ImportState
import com.home.expensetracker.sms.UpiProvider
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
        setupProviderChips()
        setupMonthSpinner()
        setupButtons()
        observeState()
        observeFilters()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = SmsTransactionAdapter { position, isSelected ->
            viewModel.toggleTransaction(position, isSelected)
        }
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }

    private fun setupProviderChips() {
        val providers = listOf(
            UpiProvider.ALL,
            UpiProvider.GPAY,
            UpiProvider.PHONEPE,
            UpiProvider.PAYTM,
            UpiProvider.BHIM,
            UpiProvider.AMAZON_PAY,
            UpiProvider.HDFC,
            UpiProvider.SBI,
            UpiProvider.ICICI,
            UpiProvider.AXIS,
            UpiProvider.KOTAK,
            UpiProvider.OTHER_BANKS
        )

        providers.forEach { provider ->
            val chip = Chip(this).apply {
                id = provider.ordinal
                text = "${provider.emoji} ${provider.displayName}"
                isCheckable = true
                isChecked = (provider == UpiProvider.ALL)
                setChipBackgroundColorResource(R.color.surface_variant)
                setCheckedIconResource(android.R.drawable.checkbox_on_background)
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) viewModel.selectProvider(provider)
            }
            binding.chipGroupProviders.addView(chip)
        }
    }

    private fun setupMonthSpinner() {
        val labels = viewModel.monthOptions.map { it.label }
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerMonth.adapter = adapter
        binding.spinnerMonth.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    viewModel.selectMonth(pos)
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener { checkPermissionAndScan() }

        binding.btnImport.setOnClickListener {
            val filtered = viewModel.filteredTransactions.value
            val selected = filtered.count { it.isSelected }
            if (selected == 0) {
                Toast.makeText(this, "Select at least one transaction", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Import $selected Transactions")
                .setMessage("Add $selected UPI transactions as expenses?")
                .setPositiveButton("Import") { _, _ -> viewModel.importSelected() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSelectAll.setOnClickListener {
            val filtered = viewModel.filteredTransactions.value
            val allSelected = filtered.all { it.isSelected }
            viewModel.selectAll(!allSelected)
            binding.btnSelectAll.text = if (!allSelected) "Deselect All" else "Select All"
        }
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is ImportState.Idle      -> showIdle()
                        is ImportState.Scanning  -> showLoading("Scanning SMS messages…")
                        is ImportState.Ready     -> showReady()
                        is ImportState.Importing -> showLoading("Importing expenses…")
                        is ImportState.Done      -> showDone(state.imported)
                        is ImportState.Error     -> {
                            Toast.makeText(this@SmsImportActivity, state.message, Toast.LENGTH_LONG).show()
                            showIdle()
                        }
                    }
                }
            }
        }
    }

    private fun observeFilters() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Update list when filtered transactions change
                launch {
                    viewModel.filteredTransactions.collect { transactions ->
                        adapter.submitList(transactions.toMutableList())
                        val selected = transactions.count { it.isSelected }
                        val total    = transactions.size

                        binding.tvSummary.text = "$total transactions · $selected selected"
                        binding.btnImport.text = if (selected > 0) "Import $selected" else "Import"

                        binding.rvTransactions.visibility  =
                            if (total > 0) View.VISIBLE else View.GONE
                        binding.tvEmptyFilter.visibility   =
                            if (total == 0 && viewModel.state.value == ImportState.Ready)
                                View.VISIBLE else View.GONE

                        val allSel = total > 0 && transactions.all { it.isSelected }
                        binding.btnSelectAll.text = if (allSel) "Deselect All" else "Select All"
                    }
                }

                // Update chip counts when provider counts change
                launch {
                    viewModel.providerCounts.collect { counts ->
                        UpiProvider.values().forEach { provider ->
                            val chip = binding.chipGroupProviders.findViewById<Chip>(provider.ordinal)
                            val count = counts[provider] ?: 0
                            chip?.text = "${provider.emoji} ${provider.displayName} ($count)"
                        }
                    }
                }
            }
        }
    }

    // ── State UI ──────────────────────────────────────────────────────────────

    private fun showIdle() {
        binding.layoutIdle.visibility      = View.VISIBLE
        binding.layoutLoading.visibility   = View.GONE
        binding.layoutReady.visibility     = View.GONE
        binding.layoutImporting.visibility = View.GONE
        binding.layoutDone.visibility      = View.GONE
    }

    private fun showLoading(msg: String) {
        binding.layoutIdle.visibility      = View.GONE
        binding.layoutLoading.visibility   = View.VISIBLE
        binding.layoutReady.visibility     = View.GONE
        binding.layoutImporting.visibility = View.GONE
        binding.layoutDone.visibility      = View.GONE
        binding.tvLoadingMsg.text          = msg
    }

    private fun showReady() {
        binding.layoutIdle.visibility      = View.GONE
        binding.layoutLoading.visibility   = View.GONE
        binding.layoutReady.visibility     = View.VISIBLE
        binding.layoutImporting.visibility = View.GONE
        binding.layoutDone.visibility      = View.GONE
    }

    private fun showDone(count: Int) {
        binding.layoutIdle.visibility      = View.GONE
        binding.layoutLoading.visibility   = View.GONE
        binding.layoutReady.visibility     = View.GONE
        binding.layoutImporting.visibility = View.GONE
        binding.layoutDone.visibility      = View.VISIBLE
        binding.tvDoneMessage.text         = "✅ $count expenses imported!"
        binding.btnDone.setOnClickListener { finish() }
        binding.btnScanAgain.setOnClickListener {
            viewModel.reset()
            checkPermissionAndScan()
        }
    }

    // ── Permission ────────────────────────────────────────────────────────────

    private fun checkPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> viewModel.scanSms()

            shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) ->
                AlertDialog.Builder(this)
                    .setTitle("SMS Permission Required")
                    .setMessage(
                        "BudgetBuddy needs SMS access to detect UPI payment transactions.\n\n" +
                        "Your messages are read only on your device."
                    )
                    .setPositiveButton("Grant") { _, _ ->
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

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
