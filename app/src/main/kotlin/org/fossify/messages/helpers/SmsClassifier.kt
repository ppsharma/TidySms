package org.fossify.messages.helpers

/**
 * Offline, rule-based first-pass SMS classifier.
 *
 * This class deliberately has no Android dependencies so its behaviour can be
 * tested independently. It does not move, delete, hide, or notify about SMS.
 * Unknown/ambiguous messages stay in PERSONAL rather than being misfiled.
 */
object SmsClassifier {

    enum class Category {
        PERSONAL,
        TRANSACTIONS,
        PROMOTIONS
    }

    data class Classification(
        val category: Category,
        val confidence: Float,
        val matchedSignals: List<String>
    )

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

    private val serviceSender = Regex(
        "^(?:[A-Z]{2,3}-)?[A-Z0-9]{4,10}(?:-[A-Z])?$",
        RegexOption.IGNORE_CASE
    )

    fun classify(sender: String?, body: String): Classification {
        val text = body.trim()
        if (text.isEmpty()) return Classification(Category.PERSONAL, 0.35f, emptyList())

        val transactionMatches = transactionSignals
            .filter { (_, pattern) -> pattern.containsMatchIn(text) }
            .map { it.first }
        val promotionMatches = promotionSignals
            .filter { (_, pattern) -> pattern.containsMatchIn(text) }
            .map { it.first }

        var transactionScore = transactionMatches.size
        var promotionScore = promotionMatches.size

        if (!sender.isNullOrBlank() && serviceSender.matches(sender.trim())) {
            if (transactionMatches.isNotEmpty()) transactionScore += 1
            if (promotionMatches.isNotEmpty()) promotionScore += 1
        }

        return when {
            transactionScore >= 2 && transactionScore > promotionScore ->
                Classification(Category.TRANSACTIONS, confidence(transactionScore, promotionScore), transactionMatches)
            promotionScore >= 2 && promotionScore > transactionScore ->
                Classification(Category.PROMOTIONS, confidence(promotionScore, transactionScore), promotionMatches)
            else -> Classification(Category.PERSONAL, 0.55f, emptyList())
        }
    }

    private fun confidence(winningScore: Int, otherScore: Int): Float =
        (0.65f + (winningScore - otherScore).coerceAtLeast(0) * 0.08f).coerceAtMost(0.95f)
}
