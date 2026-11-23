package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpUtilsTest {
    @Test fun extractsCodeBeforeOneTimePasswordAndIgnoresLaterTransactionNumbers() {
        val body = "801108 is One-Time Password for INR 504.00 transaction towards AMAZON using ICICI Bank Credit Card XX7025. OTPs are SECRET."
        assertEquals("801108", OtpUtils.extractOtp(body))
    }

    @Test fun extractsOtpAfterKeyword() {
        assertEquals("123456", OtpUtils.extractOtp("Your OTP is 123456"))
    }

    @Test fun extractsCodeBeforeYourOtp() {
        assertEquals("654321", OtpUtils.extractOtp("654321 is your OTP"))
    }

    @Test fun doesNotTreatCardDigitsAsOtp() {
        assertNull(OtpUtils.extractOtp("Purchase made using credit card XX7025 for INR 504.00"))
    }
}
