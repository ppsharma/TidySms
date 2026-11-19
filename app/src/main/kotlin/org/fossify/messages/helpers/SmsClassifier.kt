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

    private const val EMPTY_MESSAGE_CONFIDENCE = 0.35f
    private const val PERSONAL_CONFIDENCE = 0.55f
    private const val BASE_CONFIDENCE = 0.65f
    private const val CONFIDENCE_INCREMENT = 0.08f
    private const val MAX_CONFIDENCE = 0.95f

    private val transactionSignals = listOf(
        "credited" to Regex(
            "\\b(credited|credit(ed)?|received)\\b",
            RegexOption.IGNORE_CASE
        ),
        "debited" to Regex(
            "\\b(debited|debit(ed)?|spent|withdrawn)\\b",
            RegexOption.IGNORE_CASE
        ),
        "banking" to Regex(
            "\\b(bank|a/c|account|acct|upi|imps|neft|rtgs|ifsc)\\b",
            RegexOption.IGNORE_CASE
        ),
        "payment" to Regex(
            "\\b(payment|transaction|txn|transfer|paid|refund|settlement)\\b",
            RegexOption.IGNORE_CASE
        ),
        "balance" to Regex(
            "\\b(available balance|current balance|closing balance)\\b",
            RegexOption.IGNORE_CASE
        ),
        "card" to Regex(
            "\\b(card ending|debit card|credit card|pos purchase)\\b",
            RegexOption.IGNORE_CASE
        ),
        "authentication" to Regex(
            "\\b(otp|one time password|verification code|authenticate)\\b",
            RegexOption.IGNORE_CASE
        ),
        "due date" to Regex(
            "\\b(due date|bill due|emi due|minimum amount due)\\b",
            RegexOption.IGNORE_CASE
        )
    )

    private val promotionSignals = listOf(
        "offer" to Regex(
            "\\b(offer|offers|deal|deals|sale|discount|save up to)\\b",
            RegexOption.IGNORE_CASE
        ),
        "marketing" to Regex(
            "\\b(buy now|shop now|limited time|exclusive|mega|clearance)\\b",
            RegexOption.IGNORE_CASE
        ),
        "coupon" to Regex(
            "\\b(coupon|promo code|voucher|cashback up to)\\b",
            RegexOption.IGNORE_CASE
        ),
        "unsubscribe" to Regex(
            "\\b(stop to unsubscribe|unsubscribe|opt.?out)\\b",
            RegexOption.IGNORE_CASE
        ),
        "promotional call-to-action" to Regex(
            "\\b(book now|order now|click here|download the app)\\b",
            RegexOption.IGNORE_CASE
        )
    )

    fun classify(body: String): Classification {
        val text = body.trim()
        if (text.isEmpty()) {
            return Classification(
                Category.PERSONAL,
                EMPTY_MESSAGE_CONFIDENCE,
                emptyList()
            )
        }

        val transactionMatches = transactionSignals
            .filter { (_, pattern) -> pattern.containsMatchIn(text) }
            .map { it.first }
        val promotionMatches = promotionSignals
            .filter { (_, pattern) -> pattern.containsMatchIn(text) }
            .map { it.first }

        // Sender IDs alone are not reliable evidence: generic labels such as
        // UNKNOWN can resemble Indian alphanumeric SMS headers. Only message
        // content contributes to a category decision.
        val transactionScore = transactionMatches.size
        val promotionScore = promotionMatches.size

        return when {
            transactionScore >= 2 && transactionScore > promotionScore ->
                Classification(
                    Category.TRANSACTIONS,
                    confidence(transactionScore, promotionScore),
                    transactionMatches
                )
            promotionScore >= 2 && promotionScore > transactionScore ->
                Classification(
                    Category.PROMOTIONS,
                    confidence(promotionScore, transactionScore),
                    promotionMatches
                )
            else -> Classification(
                Category.PERSONAL,
                PERSONAL_CONFIDENCE,
                emptyList()
            )
        }
    }

    private fun confidence(winningScore: Int, otherScore: Int): Float =
        (BASE_CONFIDENCE +
            (winningScore - otherScore).coerceAtLeast(0) * CONFIDENCE_INCREMENT)
            .coerceAtMost(MAX_CONFIDENCE)
}
