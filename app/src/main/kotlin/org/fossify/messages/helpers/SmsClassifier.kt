package org.fossify.messages.helpers

/** Offline, rule-based SMS classifier. Sender metadata is evaluated before message wording. */
@Suppress("MaxLineLength", "MagicNumber")
object SmsClassifier {
    enum class Category { PERSONAL, TRANSACTIONS, PROMOTIONS, UNCATEGORISED }
    enum class HeaderType { PROMOTIONAL, TRANSACTIONAL, SERVICE, GOVERNMENT, UNKNOWN }
    data class Classification(val category: Category, val confidence: Float, val matchedSignals: List<String>)

    fun headerType(sender: String): HeaderType {
        val suffix = sender.trim().uppercase().substringAfterLast('-', "")
        return when (suffix) {
            "P" -> HeaderType.PROMOTIONAL
            "T" -> HeaderType.TRANSACTIONAL
            "S" -> HeaderType.SERVICE
            "G" -> HeaderType.GOVERNMENT
            else -> HeaderType.UNKNOWN
        }
    }

    fun classify(body: String): Classification = classify(body, "")

    fun classify(body: String, sender: String): Classification {
        val text = body.trim()
        val header = headerType(sender)
        if (header == HeaderType.PROMOTIONAL) return result(Category.PROMOTIONS, "TRAI header -P")
        if (header == HeaderType.TRANSACTIONAL) return result(Category.TRANSACTIONS, "TRAI header -T")
        if (text.isEmpty()) return Classification(Category.UNCATEGORISED, 0.0f, emptyList())

        val transactions = transactionSignals.filter { it.second.containsMatchIn(text) }.map { it.first }
        val promotions = promotionSignals.filter { it.second.containsMatchIn(text) }.map { it.first }
        val personal = personalSignals.filter { it.second.containsMatchIn(text) }.map { it.first }
        val personalPhrase = personalConversation.containsMatchIn(text)
        val hasPersonalContext = personal.contains("personal question") ||
            personal.contains("personal plans") || personal.size >= 2 || personalPhrase

        if (header == HeaderType.SERVICE || header == HeaderType.GOVERNMENT) {
            return when {
                promotions.size >= 2 && promotions.size > transactions.size ->
                    Classification(Category.PROMOTIONS, confidence(promotions.size, transactions.size), promotions)
                transactions.isNotEmpty() -> Classification(Category.TRANSACTIONS, 0.72f, transactions + header.name)
                else -> Classification(Category.TRANSACTIONS, 0.62f, listOf("TRAI header -${if (header == HeaderType.SERVICE) "S" else "G"}"))
            }
        }

        return when {
            transactions.size >= 2 && transactions.size > promotions.size ->
                Classification(Category.TRANSACTIONS, confidence(transactions.size, promotions.size), transactions)
            promotions.size >= 2 && promotions.size > transactions.size ->
                Classification(Category.PROMOTIONS, confidence(promotions.size, transactions.size), promotions)
            hasPersonalContext && transactions.isEmpty() && promotions.isEmpty() ->
                Classification(Category.PERSONAL, 0.72f, personal + if (personalPhrase) listOf("conversational phrase") else emptyList())
            else -> Classification(Category.UNCATEGORISED, 0.35f, emptyList())
        }
    }

    private fun result(category: Category, signal: String) = Classification(category, 0.95f, listOf(signal))

    private val transactionSignals = listOf(
        "credited" to Regex("\\b(credited|credit(ed)?|received)\\b", RegexOption.IGNORE_CASE),
        "debited" to Regex("\\b(debited|debit(ed)?|spent|withdrawn)\\b", RegexOption.IGNORE_CASE),
        "banking" to Regex("\\b(bank|a/c|account|acct|upi|imps|neft|rtgs|ifsc)\\b", RegexOption.IGNORE_CASE),
        "payment" to Regex("\\b(payment|transaction|txn|transfer|paid|refund|settlement|challan)\\b", RegexOption.IGNORE_CASE),
        "balance" to Regex("\\b(available balance|current balance|closing balance)\\b", RegexOption.IGNORE_CASE),
        "card" to Regex("\\b(card ending|debit card|credit card|pos purchase)\\b", RegexOption.IGNORE_CASE),
        "authentication" to Regex("\\b(otp|one time password|verification code|authenticate)\\b", RegexOption.IGNORE_CASE),
        "due date" to Regex("\\b(due date|bill due|emi due|minimum amount due|due for|filing)\\b", RegexOption.IGNORE_CASE),
        "government compliance" to Regex("\\b(itr|income tax|gst|cpc|e-?voting|aadhaar|challan)\\b", RegexOption.IGNORE_CASE),
        "service reminder" to Regex("\\b(service reminder|scheduled service|renewal|appointment)\\b", RegexOption.IGNORE_CASE)
    )
    private val promotionSignals = listOf(
        "offer" to Regex("\\b(offer|offers|deal|deals|sale|discount|save up to)\\b", RegexOption.IGNORE_CASE),
        "marketing" to Regex("\\b(buy now|shop now|limited time|exclusive|mega|clearance|recommendation)\\b", RegexOption.IGNORE_CASE),
        "coupon" to Regex("\\b(coupon|promo code|voucher|cashback up to)\\b", RegexOption.IGNORE_CASE),
        "unsubscribe" to Regex("\\b(stop to unsubscribe|unsubscribe|opt.?out)\\b", RegexOption.IGNORE_CASE),
        "promotional call-to-action" to Regex("\\b(book now|order now|click here|download the app|install the app)\\b", RegexOption.IGNORE_CASE),
        "investment solicitation" to Regex("\\b(stock tip|buy shares|target price|multibagger)\\b", RegexOption.IGNORE_CASE)
    )
    private val personalSignals = listOf(
        "greeting" to Regex("\\b(hi|hello|hey|good morning|good evening)\\b", RegexOption.IGNORE_CASE),
        "personal question" to Regex("\\b(how are you|are you free|what time|where are you|shall we|are we)\\b", RegexOption.IGNORE_CASE),
        "personal plans" to Regex("\\b(see you|meet you|coming over|reached home|on my way|dinner|lunch)\\b", RegexOption.IGNORE_CASE),
        "personal contact" to Regex("\\b(miss you|call me|call later|call you back|can't talk|cannot talk)\\b", RegexOption.IGNORE_CASE)
    )
    private val personalConversation = Regex(
        "\\b(call you back|call me later|can't talk right now|cannot talk right now|talk later|where are you|reached home|on my way)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun confidence(winning: Int, other: Int) =
        (0.65f + (winning - other).coerceAtLeast(0) * 0.08f).coerceAtMost(0.95f)
}