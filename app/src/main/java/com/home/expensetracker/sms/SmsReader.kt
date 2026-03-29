package com.home.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.home.expensetracker.data.models.Expense
import com.home.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Live SMS Receiver (for new incoming SMS) ──────────────────────────────────
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ExpenseRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages?.forEach { smsMessage ->
            val sender = smsMessage.displayOriginatingAddress ?: ""
            val body   = smsMessage.messageBody ?: ""
            val time   = smsMessage.timestampMillis

            val parsed = UpiSmsParser.parse(sender, body, time) ?: return@forEach

            val (category, icon) = UpiSmsParser.suggestCategory(parsed.merchant, parsed.rawSms)

            val expense = Expense(
                title         = parsed.merchant,
                amount        = parsed.amount,
                category      = category,
                categoryIcon  = icon,
                date          = parsed.timestampMs,
                note          = "Auto-imported from SMS\nRef: ${parsed.upiRef}",
                paymentMethod = "UPI"
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.insertExpense(expense)
                    Log.d("SmsReceiver", "Auto-imported: ₹${expense.amount} → ${expense.category}")
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Failed to insert SMS expense", e)
                }
            }
        }
    }
}

// ── Historical SMS Reader (reads existing inbox) ──────────────────────────────
object SmsInboxReader {

    /**
     * Reads the SMS inbox for the past [daysBack] days and returns
     * all parsed UPI transactions sorted by date descending.
     */
    fun readUpiSms(
        context: Context,
        daysBack: Int = 30
    ): List<UpiSmsParser.ParsedTransaction> {
        val results = mutableListOf<UpiSmsParser.ParsedTransaction>()

        val cutoffMs = System.currentTimeMillis() - (daysBack.toLong() * 24 * 60 * 60 * 1000)

        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "date")
        val selection  = "date > ?"
        val selArgs    = arrayOf(cutoffMs.toString())
        val sortOrder  = "date DESC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selArgs, sortOrder)
            cursor?.use {
                val addrIdx = it.getColumnIndexOrThrow("address")
                val bodyIdx = it.getColumnIndexOrThrow("body")
                val dateIdx = it.getColumnIndexOrThrow("date")

                while (it.moveToNext()) {
                    val sender = it.getString(addrIdx) ?: continue
                    val body   = it.getString(bodyIdx) ?: continue
                    val date   = it.getLong(dateIdx)

                    val parsed = UpiSmsParser.parse(sender, body, date) ?: continue
                    results.add(parsed)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsInboxReader", "Error reading SMS inbox", e)
        } finally {
            cursor?.close()
        }

        return results.sortedByDescending { it.timestampMs }
    }
}
