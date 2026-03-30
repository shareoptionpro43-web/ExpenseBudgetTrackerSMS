package com.home.expensetracker.sms

/**
 * Enum of supported UPI providers with their SMS sender keywords,
 * display name, and emoji icon.
 */
enum class UpiProvider(
    val displayName: String,
    val emoji: String,
    val senderKeywords: List<String>,
    val bodyKeywords: List<String> = emptyList()
) {
    ALL(
        displayName    = "All",
        emoji          = "📱",
        senderKeywords = emptyList()
    ),
    GPAY(
        displayName    = "GPay",
        emoji          = "🔵",
        senderKeywords = listOf("gpay", "google", "okaxis", "okicici", "oksbi", "okhdfc"),
        bodyKeywords   = listOf("google pay", "gpay")
    ),
    PHONEPE(
        displayName    = "PhonePe",
        emoji          = "🟣",
        senderKeywords = listOf("phonepe", "yesbank", "idfcbank"),
        bodyKeywords   = listOf("phonepe", "phone pe")
    ),
    PAYTM(
        displayName    = "Paytm",
        emoji          = "🔷",
        senderKeywords = listOf("paytm", "paytmbank"),
        bodyKeywords   = listOf("paytm")
    ),
    BHIM(
        displayName    = "BHIM",
        emoji          = "🇮🇳",
        senderKeywords = listOf("bhim", "npci"),
        bodyKeywords   = listOf("bhim")
    ),
    AMAZON_PAY(
        displayName    = "Amazon Pay",
        emoji          = "🟠",
        senderKeywords = listOf("amazon", "axisbank"),
        bodyKeywords   = listOf("amazon pay")
    ),
    HDFC(
        displayName    = "HDFC",
        emoji          = "🏦",
        senderKeywords = listOf("hdfcbank", "hdfc"),
        bodyKeywords   = listOf("hdfc")
    ),
    SBI(
        displayName    = "SBI",
        emoji          = "🏛️",
        senderKeywords = listOf("sbiinb", "sbi", "sbipsg"),
        bodyKeywords   = listOf("sbi", "state bank")
    ),
    ICICI(
        displayName    = "ICICI",
        emoji          = "🏦",
        senderKeywords = listOf("icicibank", "icici"),
        bodyKeywords   = listOf("icici")
    ),
    AXIS(
        displayName    = "Axis",
        emoji          = "🏦",
        senderKeywords = listOf("axisbank", "axis"),
        bodyKeywords   = listOf("axis bank")
    ),
    KOTAK(
        displayName    = "Kotak",
        emoji          = "🏦",
        senderKeywords = listOf("kotakbk", "kotak"),
        bodyKeywords   = listOf("kotak")
    ),
    OTHER_BANKS(
        displayName    = "Other Banks",
        emoji          = "🏦",
        senderKeywords = listOf(
            "pnbsms", "canarabank", "bobsms", "rblbank", "federalbank",
            "unionbank", "indusind", "yesbank", "idfcbank"
        ),
        bodyKeywords   = emptyList()
    );

    /**
     * Returns true if this transaction matches this provider.
     */
    fun matches(txn: UpiSmsParser.ParsedTransaction): Boolean {
        if (this == ALL) return true
        val senderLower = txn.senderAddress.lowercase()
        val bodyLower   = txn.rawSms.lowercase()
        return senderKeywords.any { senderLower.contains(it) } ||
               bodyKeywords.any   { bodyLower.contains(it) }
    }
}
