package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("MaxLineLength")
class SmsClassifierTest {
    @Test fun bankDebitAlertIsTransaction() = assertCategory(SmsClassifier.Category.TRANSACTIONS, "INR 5,000 debited from your A/c XX1234 via UPI. Available balance INR 12,000.")
    @Test fun upiCreditAlertIsTransaction() = assertCategory(SmsClassifier.Category.TRANSACTIONS, "Rs. 750 credited to your account through UPI. Transaction reference 123456.")
    @Test fun shoppingOfferIsPromotion() = assertCategory(SmsClassifier.Category.PROMOTIONS, "Mega sale! Get 50% discount on selected products. Shop now.")
    @Test fun couponMessageIsPromotion() = assertCategory(SmsClassifier.Category.PROMOTIONS, "Use coupon SAVE20 for an exclusive deal. Limited time offer.")
    @Test fun ordinaryPersonalMessageIsPersonal() = assertCategory(SmsClassifier.Category.PERSONAL, "Hey, are we still meeting for dinner tonight?")
    @Test fun genericThankYouIsUncategorised() = assertCategory(SmsClassifier.Category.UNCATEGORISED, "Thank you. Your request has been received.")
    @Test fun greetingAloneIsUncategorised() = assertCategory(SmsClassifier.Category.UNCATEGORISED, "Hello.")
    @Test fun emptyMessageIsUncategorised() = assertCategory(SmsClassifier.Category.UNCATEGORISED, "   ")
    @Test fun callLaterFromUnknownSenderIsPersonal() = assertCategory(SmsClassifier.Category.PERSONAL, "Can't talk right now, call later")
    @Test fun callBackMessageIsPersonal() = assertCategory(SmsClassifier.Category.PERSONAL, "I'll call you back")
    @Test fun incomeTaxReminderIsTransaction() = assertCategory(SmsClassifier.Category.TRANSACTIONS, "Reminder: ITR filing due date is 31 July. Complete your income tax filing.")
    @Test fun challanPaymentIsTransaction() = assertCategory(SmsClassifier.Category.TRANSACTIONS, "Your income tax challan payment successful.")
    @Test fun gstFilingReminderIsTransaction() = assertCategory(SmsClassifier.Category.TRANSACTIONS, "GST return filing due date reminder.")
    @Test fun serviceReminderIsTransaction() = assertCategory(SmsClassifier.Category.TRANSACTIONS, "Your Royal Enfield scheduled service reminder.")
    @Test fun downloadAppMessageIsPromotion() = assertCategory(SmsClassifier.Category.PROMOTIONS, "Download the app now and get started.")
    @Test fun traiPromotionalHeaderWins() = assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify("Hello", "CP-SUJMR T-P").category)
    @Test fun traiTransactionalHeaderWins() = assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify("Call me later", "AX-BANK-T").category)
    @Test fun serviceHeaderMapsToTransactionsWhenNoPromotion() = assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify("Your service request is registered", "AX-ITDCPC-S").category)
    @Test fun governmentHeaderMapsToTransactions() = assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify("Your ITR has been processed", "AD-ITDEPT-G").category)

    private fun assertCategory(expected: SmsClassifier.Category, body: String) {
        assertEquals(expected, SmsClassifier.classify(body).category)
    }
}