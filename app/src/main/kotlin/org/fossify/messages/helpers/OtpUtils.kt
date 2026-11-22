package org.fossify.messages.helpers

object OtpUtils {
    // Keep extraction anchored to OTP-like language. Do not treat arbitrary 4-8 digit
    // amounts, dates, invoice numbers or reference IDs as one-time passwords.
    private val afterKeyword = Regex(
        "(?:OTP|one[ -]?time password|verification code|security code|login code|passcode)" +
            "\\b[^0-9]{0,80}([0-9]{4,8})",
        RegexOption.IGNORE_CASE
    )
    private val beforeKeyword = Regex(
        "([0-9]{4,8})[^A-Za-z0-9]{0,32}(?:is your|is the|for your|for this|as your)?" +
            "\\s*(?:OTP|one[ -]?time password|verification code|security code|login code|passcode)\\b",
        RegexOption.IGNORE_CASE
    )
    private val useCode = Regex(
        "(?:use|enter|verify with|share)\\s+([0-9]{4,8})\\s+(?:as|for)\\s+(?:your\\s+)?(?:OTP|verification code|security code)\\b",
        RegexOption.IGNORE_CASE
    )

    fun extractOtp(body: String): String? =
        (afterKeyword.find(body)?.groupValues?.getOrNull(1)
            ?: beforeKeyword.find(body)?.groupValues?.getOrNull(1)
            ?: useCode.find(body)?.groupValues?.getOrNull(1))
}
