package org.fossify.messages.helpers

/** Offline, thread-level SMS classification. No network calls or sender directory are used. */
@Suppress("MaxLineLength", "MagicNumber")
object SmsClassifier {
    enum class Category { PERSONAL, TRANSACTIONS, SERVICES, PROMOTIONS, UNCATEGORISED }
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

    fun classify(body: String): Classification = classify(body, "", false)
    fun classify(body: String, sender: String): Classification = classify(body, sender, false)

    fun classify(body: String, sender: String, isSavedContact: Boolean): Classification =
        classifyThread(listOf(body), sender, isSavedContact)

    /** Recent messages are considered together; the caller supplies newest-first or oldest-first samples. */
    fun classifyThread(messages: List<String>, sender: String, isSavedContact: Boolean = false): Classification {
        val texts = messages.filter { it.isNotBlank() }.takeLast(7)
        if (isSavedContact) return result(Category.PERSONAL, "saved contact")
        if (texts.isEmpty()) return result(Category.UNCATEGORISED, "no message content", 0.0f)

        val header = headerType(sender)
        val joined = texts.joinToString(" ")
        val transactionHits = matchingSignals(transactionSignals, joined)
        val promotionHits = matchingSignals(promotionSignals, joined)
        val serviceHits = matchingSignals(serviceSignals, joined)
        val personalHits = matchingSignals(personalSignals, joined)
        val promotionDominates = promotionHits.size >= 2 && promotionHits.size > transactionHits.size &&
            promotionHits.size >= serviceHits.size

        // Keep clear financial signals protected while refining Services and Promotions.
        if (transactionHits.isNotEmpty() && transactionHits.size >= serviceHits.size) {
            return Classification(Category.TRANSACTIONS, confidence(transactionHits.size, serviceHits.size), transactionHits)
        }
        // A clear sales/enquiry lead is promotional even when it mentions a workshop or service.
        if (Regex("\\b(showing interest|thanks for showing interest|thank you for your interest)\\b", RegexOption.IGNORE_CASE).containsMatchIn(joined)) {
            return Classification(Category.PROMOTIONS, 0.82f, listOf("customer acquisition"))
        }
        if (promotionDominates || (promotionHits.isNotEmpty() && serviceHits.isEmpty())) {
            return Classification(Category.PROMOTIONS, confidence(promotionHits.size, serviceHits.size), promotionHits)
        }
        if (serviceHits.isNotEmpty()) {
            return Classification(Category.SERVICES, confidence(serviceHits.size, transactionHits.size), serviceHits)
        }
        when (header) {
            HeaderType.PROMOTIONAL -> return result(Category.PROMOTIONS, "TRAI header -P")
            HeaderType.TRANSACTIONAL, HeaderType.GOVERNMENT -> return result(Category.TRANSACTIONS, "TRAI header -T/G")
            HeaderType.SERVICE -> return result(Category.SERVICES, "TRAI header -S")
            HeaderType.UNKNOWN -> Unit
        }
        if (personalHits.size >= 2 || personalConversation.containsMatchIn(joined)) {
            return Classification(Category.PERSONAL, 0.72f, personalHits.ifEmpty { listOf("conversational phrase") })
        }
        if (isRegularPhoneNumber(sender)) return result(Category.PERSONAL, "regular phone number")
        return Classification(Category.UNCATEGORISED, 0.35f, emptyList())
    }

    private fun isRegularPhoneNumber(sender: String): Boolean {
        val value = sender.trim()
        return value.matches(Regex("^\\+?[0-9][0-9\\s().-]{6,}$"))
    }

    private fun matchingSignals(signals: List<Pair<String, Regex>>, text: String): List<String> =
        signals.filter { it.second.containsMatchIn(text) }.map { it.first }

    private fun result(category: Category, signal: String, confidence: Float = 0.95f) =
        Classification(category, confidence, listOf(signal))

    private fun confidence(winning: Int, other: Int) =
        (0.65f + (winning - other).coerceAtLeast(0) * 0.08f).coerceAtMost(0.95f)

    private val transactionSignals = listOf(
        "credited" to Regex("\\bcredited\\b", RegexOption.IGNORE_CASE),
        "debited" to Regex("\\b(debited|spent|withdrawn)\\b", RegexOption.IGNORE_CASE),
        "banking" to Regex("\\b(bank|a/c|account|acct|upi|imps|neft|rtgs|ifsc|fastag)\\b", RegexOption.IGNORE_CASE),
        "payment" to Regex("\\b(payment|transaction|txn|transfer|paid|refund|settlement|challan|clearing)\\b", RegexOption.IGNORE_CASE),
        "balance" to Regex("\\b(available balance|current balance|closing balance)\\b", RegexOption.IGNORE_CASE),
        "card" to Regex("\\b(card ending|debit card|credit card|rupay|pos purchase|cheque book|chequebook)\\b", RegexOption.IGNORE_CASE),
        "authentication" to Regex("\\b(otp|one time password|verification code|authenticate)\\b", RegexOption.IGNORE_CASE),
        "due date" to Regex("\\b(due date|bill due|emi due|minimum amount due|due for|statement)\\b", RegexOption.IGNORE_CASE),
        "government compliance" to Regex("\\b(itr|income tax|gst|cpc|e-?voting|aadhaar|challan)\\b", RegexOption.IGNORE_CASE)
    )
    private val serviceSignals = listOf(
        "delivery" to Regex("\\b(delivery|delivered|out for delivery|shipment|tracking|dispatch)\\b", RegexOption.IGNORE_CASE),
        "booking" to Regex("\\b(booking|reservation|ticket|boarding|check.?in|itinerary)\\b", RegexOption.IGNORE_CASE),
        "telecom" to Regex("\\b(data pack|recharge|validity|data balance|network|missed call alert|voicemail|sim)\\b", RegexOption.IGNORE_CASE),
        "service request" to Regex("\\b(service request|request registered|complaint|reference number|case id|appointment|scheduled service|renewal|team will visit|site visit|site survey|application status|application has been|application no)\\b", RegexOption.IGNORE_CASE),
        "utilities" to Regex("\\b(electricity bill|power bill|bill for .* service number|bill has been generated|bill generated|roaming|international call|mobile data|data usage|missed call alert|now available)\\b", RegexOption.IGNORE_CASE),
        "customer follow-up" to Regex("\\b(share your experience|feedback|enjoyed your .* order|track service details|service details|assigned counter|workshop)\\b", RegexOption.IGNORE_CASE),
        "order updates" to Regex("\\b(order confirmed|order placed|return picked|replacement|refund initiated)\\b", RegexOption.IGNORE_CASE)
    )
    private val promotionSignals = listOf(
        "offer" to Regex("\\b(offer|offers|deal|deals|sale|discount|save up to|gift coupon)\\b", RegexOption.IGNORE_CASE),
        "marketing" to Regex("\\b(buy now|shop now|limited time|exclusive|mega|clearance|recommendation)\\b", RegexOption.IGNORE_CASE),
        "coupon" to Regex("\\b(coupon|promo code|voucher|cashback up to)\\b", RegexOption.IGNORE_CASE),
        "unsubscribe" to Regex("\\b(stop to unsubscribe|unsubscribe|opt.?out)\\b", RegexOption.IGNORE_CASE),
        "promotional call-to-action" to Regex("\\b(book now|order now|click here|download the app|install the app|showing interest|thank you for your interest|thanks for showing interest)\\b", RegexOption.IGNORE_CASE),
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
}
