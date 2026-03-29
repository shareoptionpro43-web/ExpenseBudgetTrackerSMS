package com.home.expensetracker.sms

import java.util.regex.Pattern

/**
 * Parses UPI / bank debit SMS messages from all major Indian banks and UPI apps.
 * Covers: GPay, PhonePe, Paytm, BHIM, HDFC, SBI, ICICI, Axis, Kotak, BOB, PNB, Canara, etc.
 */
object UpiSmsParser {

    data class ParsedTransaction(
        val amount: Double,
        val merchant: String,
        val upiRef: String,
        val timestampMs: Long,
        val rawSms: String,
        val senderAddress: String
    )

    // ── Amount patterns ───────────────────────────────────────────────────────
    // Matches: Rs.500, Rs 500, INR 500, ₹500, ₹ 500.00, Rs500.50
    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("""(?:Rs\.?|INR|₹)\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(\d+(?:[.,]\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""debited\s+(?:by|for|of|with)?\s*(?:Rs\.?|INR|₹)?\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""paid\s+(?:Rs\.?|INR|₹)?\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""sent\s+(?:Rs\.?|INR|₹)?\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    // ── Merchant / payee patterns ─────────────────────────────────────────────
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("""(?:to|paid to|sent to|transferred to)\s+([A-Za-z0-9@.\s_-]{3,40}?)(?:\s+on|\s+via|\s+using|\s+UPI|\.|\n|$)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:at|merchant)\s+([A-Za-z0-9@.\s_-]{3,40}?)(?:\s+on|\s+via|\.|$)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""VPA\s+([A-Za-z0-9@._-]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""UPI[:\s]+([A-Za-z0-9@._-]+)""", Pattern.CASE_INSENSITIVE)
    )

    // ── UPI reference number patterns ─────────────────────────────────────────
    private val REF_PATTERNS = listOf(
        Pattern.compile("""(?:UPI\s*Ref\.?\s*(?:No\.?|#)?|Ref\s*No\.?\s*:|Transaction\s*ID\s*:?|TxnID\s*:?)\s*([A-Z0-9]{8,20})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Ref|Txn|UTR)\s*[:#]?\s*([A-Z0-9]{8,20})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([0-9]{12,18})""") // fallback: long number sequence
    )

    // ── Debit / outgoing SMS indicators ──────────────────────────────────────
    // Only parse DEBIT (money going out), not credit
    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "paid", "sent", "transferred", "payment of",
        "payment done", "txn successful", "upi payment", "money sent",
        "your a/c", "your account.*debited"
    )

    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "money received", "credit", "cashback",
        "refund", "received from"
    )

    // ── UPI SMS senders ───────────────────────────────────────────────────────
    private val UPI_SENDERS = setOf(
        "gpay", "phonepe", "paytm", "bhim", "upi", "hdfcbank", "sbiinb",
        "icicibank", "axisbank", "kotakbk", "pnbsms", "canarabank", "bobsms",
        "yesbank", "indusind", "rblbank", "idfcbank", "federalbank", "unionbank",
        "bankofindia", "cbi", "syndicatebank", "vijayabank", "andhrbank",
        "tm-", "ad-", "vm-", "jd-"
    )

    /**
     * Returns true if this SMS looks like a UPI/bank debit transaction.
     */
    fun isUpiDebitSms(sender: String, body: String): Boolean {
        val senderLower = sender.lowercase()
        val bodyLower   = body.lowercase()

        // Must be from a known UPI/bank sender
        val fromKnownSender = UPI_SENDERS.any { senderLower.contains(it) }

        // Must contain a debit keyword
        val hasDebit = DEBIT_KEYWORDS.any { bodyLower.contains(it) }

        // Must NOT be a credit SMS
        val hasCredit = CREDIT_KEYWORDS.any { bodyLower.contains(it) }

        // Must contain rupee symbol or Rs/INR
        val hasCurrency = bodyLower.contains("rs") ||
                body.contains("₹") ||
                bodyLower.contains("inr")

        return (fromKnownSender || hasCurrency) && hasDebit && !hasCredit
    }

    /**
     * Parses a UPI SMS body and returns a ParsedTransaction or null if parsing fails.
     */
    fun parse(
        sender: String,
        body: String,
        timestampMs: Long
    ): ParsedTransaction? {
        if (!isUpiDebitSms(sender, body)) return null

        val amount   = extractAmount(body)   ?: return null
        val merchant = extractMerchant(body) ?: "UPI Payment"
        val ref      = extractRef(body)      ?: ""

        return ParsedTransaction(
            amount         = amount,
            merchant       = merchant.trim().take(60),
            upiRef         = ref,
            timestampMs    = timestampMs,
            rawSms         = body,
            senderAddress  = sender
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun extractAmount(body: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", "") ?: continue
                val value = raw.toDoubleOrNull() ?: continue
                if (value > 0) return value
            }
        }
        return null
    }

    private fun extractMerchant(body: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val found = matcher.group(1)?.trim() ?: continue
                if (found.length >= 2) return cleanMerchant(found)
            }
        }
        return null
    }

    private fun extractRef(body: String): String? {
        for (pattern in REF_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        return null
    }

    private fun cleanMerchant(raw: String): String {
        return raw
            .replace(Regex("[^A-Za-z0-9@._\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Suggest a category based on merchant name / SMS content.
     */
    fun suggestCategory(merchant: String, body: String): Pair<String, String> {
        val text = (merchant + " " + body).lowercase()
        return when {
            text.containsAny("zomato", "swiggy", "food", "restaurant", "cafe",
                "hotel", "biryani", "pizza", "burger", "dhaba", "eatery")
                -> "Food & Dining" to "🍽️"

            text.containsAny("bigbasket", "grofer", "grocery", "kirana", "supermart",
                "blinkit", "zepto", "dmart", "reliance fresh")
                -> "Groceries" to "🛒"

            text.containsAny("uber", "ola", "rapido", "redbus", "irctc", "petrol",
                "fuel", "diesel", "metro", "bus", "auto", "cab", "train", "flight",
                "indigo", "spicejet", "airindia", "goair", "vistara")
                -> "Transportation" to "🚗"

            text.containsAny("electricity", "water bill", "gas bill", "broadband",
                "internet", "jio", "airtel", "bsnl", "vi ", "vodafone", "tata sky",
                "dish tv", "dtsc", "msedcl", "bescom", "tneb", "recharge")
                -> "Utilities" to "💡"

            text.containsAny("hospital", "clinic", "pharmacy", "medicine", "doctor",
                "health", "apollo", "medplus", "1mg", "netmeds", "practo")
                -> "Healthcare" to "🏥"

            text.containsAny("amazon", "flipkart", "myntra", "ajio", "meesho",
                "nykaa", "shop", "mall", "store", "retail", "fashion")
                -> "Shopping" to "🛍️"

            text.containsAny("netflix", "hotstar", "prime", "spotify", "youtube",
                "movie", "cinema", "pvr", "inox", "bookmyshow", "game", "entertainment")
                -> "Entertainment" to "🎬"

            text.containsAny("rent", "maintenance", "society", "housing",
                "apartment", "flat", "home loan", "emi")
                -> "Home & Rent" to "🏠"

            text.containsAny("school", "college", "university", "fee", "course",
                "udemy", "byju", "coaching", "tuition", "education")
                -> "Education" to "📚"

            text.containsAny("insurance", "lic", "policy", "premium", "icici pru",
                "hdfc life", "max life")
                -> "Insurance" to "🛡️"

            text.containsAny("salon", "spa", "beauty", "haircut", "parlour",
                "grooming", "personal care")
                -> "Personal Care" to "💅"

            text.containsAny("swiggy instamart", "pet", "veterinary", "vet")
                -> "Pets" to "🐾"

            text.containsAny("makemytrip", "goibibo", "yatra", "hotel booking",
                "oyo", "travel", "tour")
                -> "Travel" to "✈️"

            else -> "Other" to "📦"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }
}
