package org.fossify.messages.helpers

object OtpUtils {
    private val afterKeyword = Regex("(?:OTP|one[ -]?time password|verification code|security code)[^0-9]{0,32}([0-9]{4,8})", RegexOption.IGNORE_CASE)
    private val beforeKeyword = Regex("([0-9]{4,8})[^A-Za-z0-9]{0,24}(?:is your|is the|for your|for this)?\\s*(?:OTP|one[ -]?time password|verification code|security code)", RegexOption.IGNORE_CASE)
    fun extractOtp(body: String): String? = (afterKeyword.find(body) ?: beforeKeyword.find(body))?.groupValues?.getOrNull(1)
}
