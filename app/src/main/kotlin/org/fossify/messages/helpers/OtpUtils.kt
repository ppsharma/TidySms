package org.fossify.messages.helpers

object OtpUtils {
    // Accept both common formats:
    // "OTP is 123456" and "123456 is One-Time Password".
    // Keep the match adjacent to the OTP phrase so later amounts, dates,
    // card digits and transaction references cannot be mistaken for the code.
    private val afterKeyword = Regex(
        """(?:OTP|one[ -]?time password|verification code|security code|login code|passcode)\b\s*(?:is\s+|[:=-]\s*)?(?:your\s+|the\s+)?([0-9]{4,8})\b""",
        RegexOption.IGNORE_CASE
    )
    private val beforeKeyword = Regex(
        """\b([0-9]{4,8})\b\s*(?:is\s+)?(?:your\s+|the\s+)?(?:OTP|one[ -]?time password|verification code|security code|login code|passcode)\b""",
        RegexOption.IGNORE_CASE
    )
    private val useCode = Regex(
        """(?:use|enter|verify with|share)\s+([0-9]{4,8})\s+(?:as|for)\s+(?:your\s+)?(?:OTP|verification code|security code)\b""",
        RegexOption.IGNORE_CASE
    )

    fun extractOtp(body: String): String? =
        (beforeKeyword.find(body)?.groupValues?.getOrNull(1)
            ?: afterKeyword.find(body)?.groupValues?.getOrNull(1)
            ?: useCode.find(body)?.groupValues?.getOrNull(1))
}
