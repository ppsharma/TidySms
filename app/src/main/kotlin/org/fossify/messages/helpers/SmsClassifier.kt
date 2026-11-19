package org.fossify.messages.helpers

/** Offline, rule-based SMS classifier. Uncertain messages remain Uncategorised. */
@Suppress("MaxLineLength", "MagicNumber")
object SmsClassifier {
    enum class Category { PERSONAL, TRANSACTIONS, PROMOTIONS, UNCATEGORISED }
    data class Classification(val category: Category, val confidence: Float, val matchedSignals: List<String>)

    private val transactionSignals = listOf(
        "credited" to Regex("\\b(credited|credit(ed)?|received)\\b", RegexOption.IGNORE_CASE),
        "debited" to Regex("\\b(debited|debit(ed)?|spent|withdrawn)\\b", RegexOption.IGNORE_CASE),
        "banking" to Regex("\\b(bank|a/c|account|acct|upi|imps|neft|rtgs|ifsc)\\b", RegexOption.IGNORE_CASE),
        "payment" to Regex("\\b(payment|transaction|txn|transfer|paid|refund|settlement)\\b", RegexOption.IGNORE_CASE),
        "balance" to Regex("\\b(available balance|current balance|closing balance)\\b", RegexOption.IGNORE_CASE),
        "card" to Regex("\\b(card ending|debit card|credit card|pos purchase)\\b", RegexOption.IGNORE_CASE),
        "authentication" to Regex("\\b(otp|one time password|verification code|authenticate)\\b", RegexOption.IGNORE_CASE),
        "due date" to Regex("\\b(due date|bill due|emi due|minimum amount due)\\b", RegexOption.IGNORE_CASE)
    )
    private val promotionSignals = listOf(
        "offer" to Regex("\\b(offer|offers|deal|deals|sale|discount|save up to)\\b", RegexOption.IGNORE_CASE),
        "marketing" to Regex("\\b(buy now|shop now|limited time|exclusive|mega|clearance)\\b", RegexOption.IGNORE_CASE),
        "coupon" to Regex("\\b(coupon|promo code|voucher|cashback up to)\\b", RegexOption.IGNORE_CASE),
        "unsubscribe" to Regex("\\b(stop to unsubscribe|unsubscribe|opt.?out)\\b", RegexOption.IGNORE_CASE),
        "promotional call-to-action" to Regex("\\b(book now|order now|click here|download the app)\\b", RegexOption.IGNORE_CASE)
    )
    private val personalSignals = listOf(
        "greeting" to Regex("\\b(hi|hello|hey|good morning|good evening)\\b", RegexOption.IGNORE_CASE),
        "personal question" to Regex("\\b(how are you|are you free|what time|where are you|shall we|are we)\\b", RegexOption.IGNORE_CASE),
        "personal plans" to Regex("\\b(see you|meet you|coming over|reached home|on my way|dinner|lunch)\\b", RegexOption.IGNORE_CASE),
        "personal thanks" to Regex("\\b(thanks|thank you|sorry|miss you|call me)\\b", RegexOption.IGNORE_CASE)
    )

    fun classify(body: String): Classification {
        val text = body.trim()
        if (text.isEmpty()) return Classification(Category.UNCATEGORISED, 0.0f, emptyList())
        val transactions = transactionSignals.filter { it.second.containsMatchIn(text) }.map { it.first }
        val promotions = promotionSignals.filter { it.second.containsMatchIn(text) }.map { it.first }
        val personal = personalSignals.filter { it.second.containsMatchIn(text) }.map { it.first }
        return when {
            transactions.size >= 2 && transactions.size > promotions.size ->
                Classification(Category.TRANSACTIONS, confidence(transactions.size, promotions.size), transactions)
            promotions.size >= 2 && promotions.size > transactions.size ->
                Classification(Category.PROMOTIONS, confidence(promotions.size, transactions.size), promotions)
            personal.isNotEmpty() && transactions.isEmpty() && promotions.isEmpty() ->
                Classification(Category.PERSONAL, (0.68f + personal.size * 0.06f).coerceAtMost(0.9f), personal)
            else -> Classification(Category.UNCATEGORISED, 0.35f, emptyList())
        }
    }

    private fun confidence(winning: Int, other: Int) =
        (0.65f + (winning - other).coerceAtLeast(0) * 0.08f).coerceAtMost(0.95f)
}
