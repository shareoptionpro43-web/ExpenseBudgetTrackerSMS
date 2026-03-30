package com.home.expensetracker.sms

import java.util.regex.Pattern

object UpiSmsParser {

    data class ParsedTransaction(
        val amount: Double,
        val merchant: String,       // payee / shop name
        val upiId: String,          // VPA e.g. merchant@okaxis
        val upiRef: String,         // UPI ref / txn ID
        val bankAccount: String,    // masked account e.g. XX1234
        val bankName: String,       // HDFC, SBI etc.
        val availableBalance: Double?, // balance after txn if present
        val timestampMs: Long,
        val rawSms: String,
        val senderAddress: String
    )

    // ── Amount ────────────────────────────────────────────────────────────────
    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("""(?:Rs\.?|INR|₹)\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(\d+(?:[.,]\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""debited\s+(?:by|for|of|with)?\s*(?:Rs\.?|INR|₹)?\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""paid\s+(?:Rs\.?|INR|₹)?\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""sent\s+(?:Rs\.?|INR|₹)?\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    // ── Merchant / payee ──────────────────────────────────────────────────────
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("""(?:to|paid to|sent to|transferred to)\s+([A-Za-z0-9@.\s_-]{3,50}?)(?:\s+on|\s+via|\s+using|\s+UPI|\.|,|\n|$)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:at|merchant:?)\s+([A-Za-z0-9@.\s_-]{3,50}?)(?:\s+on|\s+via|\.|,|$)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""payee\s*:?\s*([A-Za-z0-9@.\s_-]{3,50}?)(?:\.|,|\n|$)""", Pattern.CASE_INSENSITIVE)
    )

    // ── UPI VPA ───────────────────────────────────────────────────────────────
    private val VPA_PATTERNS = listOf(
        Pattern.compile("""(?:VPA|UPI ID|payee VPA)\s*:?\s*([A-Za-z0-9._-]+@[A-Za-z0-9._-]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([A-Za-z0-9._-]+@(?:okaxis|okicici|oksbi|okhdfc|ybl|upi|paytm|apl|ibl|rbl|sib|kvb|barodampay|aubank|mahb|icici|hdfc|axis|sbi|kotak))""", Pattern.CASE_INSENSITIVE)
    )

    // ── UPI Ref / Txn ID ──────────────────────────────────────────────────────
    private val REF_PATTERNS = listOf(
        Pattern.compile("""(?:UPI\s*Ref\.?\s*(?:No\.?|#|:)?|UPI\s*Ref\s*ID\s*:?)\s*([A-Z0-9]{8,22})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Ref\s*No\.?\s*:|Txn\s*ID\s*:?|Transaction\s*ID\s*:?|TxnID\s*:?|UTR\s*:?)\s*([A-Z0-9]{8,22})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Ref|Txn|UTR)[:\s#]+([A-Z0-9]{8,22})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([0-9]{12,18})""")
    )

    // ── Bank account ──────────────────────────────────────────────────────────
    private val ACCOUNT_PATTERNS = listOf(
        Pattern.compile("""(?:A/c|Ac|Account|Acct|a/c)\s*(?:no\.?|number)?\s*[:\*xX]*([0-9Xx*]{4,6})\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:ending|ending in|XX|xx)\s*([0-9]{4})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""[Xx*]{2,}([0-9]{4})""")
    )

    // ── Available balance ─────────────────────────────────────────────────────
    private val BALANCE_PATTERNS = listOf(
        Pattern.compile("""(?:Avl\.?\s*Bal\.?|Available\s*Balance|Bal)\s*(?:Rs\.?|INR|₹)?\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Balance|Bal)\s*:?\s*(?:Rs\.?|INR|₹)\s*(\d+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    // ── Bank name keywords ────────────────────────────────────────────────────
    private val BANK_KEYWORDS = mapOf(
        "hdfc"       to "HDFC Bank",
        "sbi"        to "SBI",
        "icici"      to "ICICI Bank",
        "axis"       to "Axis Bank",
        "kotak"      to "Kotak Bank",
        "pnb"        to "PNB",
        "canara"     to "Canara Bank",
        "bob"        to "Bank of Baroda",
        "union"      to "Union Bank",
        "yes bank"   to "Yes Bank",
        "yesbank"    to "Yes Bank",
        "indusind"   to "IndusInd Bank",
        "rbl"        to "RBL Bank",
        "idfc"       to "IDFC Bank",
        "federal"    to "Federal Bank",
        "paytm"      to "Paytm Bank",
        "phonepe"    to "PhonePe",
        "gpay"       to "Google Pay",
        "google"     to "Google Pay",
        "bhim"       to "BHIM"
    )

    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "paid", "sent", "transferred", "payment of",
        "payment done", "txn successful", "upi payment", "money sent",
        "your a/c", "dr "
    )
    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "money received", "cashback", "refund", "received from"
    )
    private val UPI_SENDERS = setOf(
        "gpay","phonepe","paytm","bhim","upi","hdfcbank","sbiinb","icicibank",
        "axisbank","kotakbk","pnbsms","canarabank","bobsms","yesbank","indusind",
        "rblbank","idfcbank","federalbank","unionbank","tm-","ad-","vm-","jd-"
    )

    fun isUpiDebitSms(sender: String, body: String): Boolean {
        val s = sender.lowercase(); val b = body.lowercase()
        val fromKnown  = UPI_SENDERS.any { s.contains(it) }
        val hasDebit   = DEBIT_KEYWORDS.any { b.contains(it) }
        val hasCredit  = CREDIT_KEYWORDS.any { b.contains(it) }
        val hasCurrency= b.contains("rs") || body.contains("₹") || b.contains("inr")
        return (fromKnown || hasCurrency) && hasDebit && !hasCredit
    }

    fun parse(sender: String, body: String, timestampMs: Long): ParsedTransaction? {
        if (!isUpiDebitSms(sender, body)) return null
        val amount = extractAmount(body) ?: return null
        return ParsedTransaction(
            amount           = amount,
            merchant         = extractMerchant(body) ?: "UPI Payment",
            upiId            = extractVpa(body),
            upiRef           = extractRef(body),
            bankAccount      = extractAccount(body),
            bankName         = extractBankName(sender, body),
            availableBalance = extractBalance(body),
            timestampMs      = timestampMs,
            rawSms           = body,
            senderAddress    = sender
        )
    }

    private fun extractAmount(body: String): Double? {
        for (p in AMOUNT_PATTERNS) {
            val m = p.matcher(body)
            if (m.find()) {
                val v = m.group(1)?.replace(",","")?.toDoubleOrNull() ?: continue
                if (v > 0) return v
            }
        }
        return null
    }

    private fun extractMerchant(body: String): String? {
        for (p in MERCHANT_PATTERNS) {
            val m = p.matcher(body)
            if (m.find()) {
                val raw = m.group(1)?.trim() ?: continue
                if (raw.length >= 2) return clean(raw)
            }
        }
        return null
    }

    private fun extractVpa(body: String): String {
        for (p in VPA_PATTERNS) {
            val m = p.matcher(body)
            if (m.find()) return m.group(1)?.trim() ?: ""
        }
        return ""
    }

    private fun extractRef(body: String): String {
        for (p in REF_PATTERNS) {
            val m = p.matcher(body)
            if (m.find()) return m.group(1)?.trim() ?: ""
        }
        return ""
    }

    private fun extractAccount(body: String): String {
        for (p in ACCOUNT_PATTERNS) {
            val m = p.matcher(body)
            if (m.find()) return "XX${m.group(1)?.trim()}"
        }
        return ""
    }

    private fun extractBankName(sender: String, body: String): String {
        val combined = (sender + " " + body).lowercase()
        return BANK_KEYWORDS.entries.firstOrNull { combined.contains(it.key) }?.value ?: "Bank"
    }

    private fun extractBalance(body: String): Double? {
        for (p in BALANCE_PATTERNS) {
            val m = p.matcher(body)
            if (m.find()) return m.group(1)?.replace(",","")?.toDoubleOrNull()
        }
        return null
    }

    private fun clean(raw: String) = raw
        .replace(Regex("[^A-Za-z0-9@._\\s-]"), "").trim()
        .replace(Regex("\\s+"), " ")

    fun suggestCategory(merchant: String, body: String): Pair<String, String> {
        val t = (merchant + " " + body).lowercase()
        return when {
            t.containsAny("zomato","swiggy","food","restaurant","cafe","hotel","biryani","pizza","burger","dhaba")
                -> "Food & Dining" to "🍽️"
            t.containsAny("bigbasket","grofer","grocery","kirana","blinkit","zepto","dmart","reliance fresh")
                -> "Groceries" to "🛒"
            t.containsAny("uber","ola","rapido","redbus","irctc","petrol","fuel","diesel","metro","bus","auto","cab","train","flight","indigo","spicejet")
                -> "Transportation" to "🚗"
            t.containsAny("electricity","water bill","gas bill","broadband","internet","jio","airtel","bsnl","vi ","vodafone","tata sky","recharge")
                -> "Utilities" to "💡"
            t.containsAny("hospital","clinic","pharmacy","medicine","doctor","health","apollo","medplus","1mg","netmeds")
                -> "Healthcare" to "🏥"
            t.containsAny("amazon","flipkart","myntra","ajio","meesho","nykaa","shop","mall","retail")
                -> "Shopping" to "🛍️"
            t.containsAny("netflix","hotstar","prime","spotify","movie","cinema","pvr","inox","bookmyshow")
                -> "Entertainment" to "🎬"
            t.containsAny("rent","maintenance","society","housing","apartment","flat","home loan","emi")
                -> "Home & Rent" to "🏠"
            t.containsAny("school","college","fee","course","udemy","byju","coaching","tuition")
                -> "Education" to "📚"
            t.containsAny("insurance","lic","policy","premium")
                -> "Insurance" to "🛡️"
            t.containsAny("salon","spa","beauty","haircut","parlour","grooming")
                -> "Personal Care" to "💅"
            t.containsAny("makemytrip","goibibo","oyo","travel","tour","hotel booking")
                -> "Travel" to "✈️"
            else -> "Other" to "📦"
        }
    }

    private fun String.containsAny(vararg kw: String) = kw.any { this.contains(it) }
}
