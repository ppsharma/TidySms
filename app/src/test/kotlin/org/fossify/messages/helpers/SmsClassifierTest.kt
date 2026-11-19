package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsClassifierTest {

    @Test
    fun bankDebitAlertIsTransaction() {
        val result = SmsClassifier.classify(
            "VM-HDFCBK",
            "INR 5,000 debited from your A/c XX1234 via UPI. Available balance INR 12,000."
        )

        assertEquals(SmsClassifier.Category.TRANSACTIONS, result.category)
        assertTrue(result.confidence >= 0.65f)
    }

    @Test
    fun upiCreditAlertIsTransaction() {
        val result = SmsClassifier.classify(
            "AD-ICICIB",
            "Rs. 750 credited to your account through UPI. Transaction reference 123456."
        )

        assertEquals(SmsClassifier.Category.TRANSACTIONS, result.category)
    }

    @Test
    fun shoppingOfferIsPromotion() {
        val result = SmsClassifier.classify(
            "VM-AMAZON",
            "Mega sale! Get 50% discount on selected products. Shop now."
        )

        assertEquals(SmsClassifier.Category.PROMOTIONS, result.category)
    }

    @Test
    fun couponMessageIsPromotion() {
        val result = SmsClassifier.classify(
            "AD-STORE",
            "Use coupon SAVE20 for an exclusive deal. Limited time offer."
        )

        assertEquals(SmsClassifier.Category.PROMOTIONS, result.category)
    }

    @Test
    fun ordinaryPersonalMessageStaysPersonal() {
        val result = SmsClassifier.classify(
            "+919876543210",
            "Reached home. See you tomorrow."
        )

        assertEquals(SmsClassifier.Category.PERSONAL, result.category)
    }

    @Test
    fun ambiguousMessageDefaultsToPersonal() {
        val result = SmsClassifier.classify(
            "UNKNOWN",
            "Thank you. Your request has been received."
        )

        assertEquals(SmsClassifier.Category.PERSONAL, result.category)
    }

    @Test
    fun emptyMessageDefaultsToPersonal() {
        val result = SmsClassifier.classify("UNKNOWN", "   ")

        assertEquals(SmsClassifier.Category.PERSONAL, result.category)
        assertTrue(result.confidence < 0.5f)
    }
}
