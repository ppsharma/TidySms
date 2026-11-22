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
    @Test fun serviceReminderIsService() = assertCategory(SmsClassifier.Category.SERVICES, "Your Royal Enfield scheduled service reminder.")
    @Test fun downloadAppMessageIsPromotion() = assertCategory(SmsClassifier.Category.PROMOTIONS, "Download the app now and get started.")
    @Test fun traiPromotionalHeaderWins() = assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify("Hello", "CP-SUJMR T-P").category)
    @Test fun traiTransactionalHeaderWins() = assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify("Call me later", "AX-BANK-T").category)
    @Test fun serviceHeaderMapsToServicesWhenNoOtherSignal() = assertEquals(SmsClassifier.Category.SERVICES, SmsClassifier.classify("Your service request is registered", "AX-ITDCPC-S").category)
    @Test fun governmentHeaderMapsToTransactions() = assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify("Your ITR has been processed", "AD-ITDEPT-G").category)

    @Test fun savedContactAlwaysClassifiesAsPersonal() {
        assertEquals(SmsClassifier.Category.PERSONAL, SmsClassifier.classify("Your account OTP is 123456", "9876543210", true).category)
    }

    @Test fun unknownSenderCallLaterIsPersonal() {
        assertEquals(SmsClassifier.Category.PERSONAL, SmsClassifier.classify("Can't talk right now, call later", "9876543210").category)
    }

    @Test fun promotionalHeaderOverridesPersonalWording() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify("Call me later", "AD-TEST-P").category)
    }


    @Test fun serviceDeliveryMessageIsService() {
        assertEquals(SmsClassifier.Category.SERVICES, SmsClassifier.classify("Your parcel is out for delivery today", "AD-COURIER").category)
    }

    @Test fun mobileNumberPromotionalMessageIsStillPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify("Special offer! Get 40% discount, shop now", "9876543210").category)
    }

    @Test fun regularMobileNumberIsPersonal() {
        assertEquals(SmsClassifier.Category.PERSONAL, SmsClassifier.classify("Please call me", "9876543210").category)
    }

    @Test fun threadCompositionKeepsMostlyTransactionalThreadInTransactions() {
        val messages = listOf(
            "Your account was debited by Rs 500",
            "Your UPI payment was successful",
            "Available balance is Rs 1200",
            "Special offer! Shop now and get a discount"
        )
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classifyThread(messages, "AD-BANK").category)
    }

    @Test fun sameSenderCanHavePromotionalThread() {
        val messages = listOf(
            "Exclusive offer: get 30% discount today",
            "Shop now and use coupon SAVE30",
            "Limited time deal, click here"
        )
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classifyThread(messages, "AD-BANK-T").category)
    }

    @Test fun transactionOnlyClassifierKeepsBankDebitInRules() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS,
            SmsClassifier.classifyTransaction(listOf("INR 500 debited from your bank account"), "AD-BANK")?.category)
    }

    @Test fun transactionOnlyClassifierLeavesPromotionForMl() {
        assertEquals(null,
            SmsClassifier.classifyTransaction(listOf("Exclusive offer: get 30% discount, shop now"), "AD-BANK-T"))
    }

    @Test fun transactionOnlyClassifierLeavesServiceForMl() {
        assertEquals(null,
            SmsClassifier.classifyTransaction(listOf("Your parcel is out for delivery"), "AD-COURIER"))
    }

    @Test fun transactionOnlyClassifierRespectsSavedContact() {
        assertEquals(null,
            SmsClassifier.classifyTransaction(listOf("Your account was debited"), "9876543210", true))
    }

    @Test fun miraeSipProcessedMessageIsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify(
            "Greetings. Your SIP of 20/03/2023 for Rs.4999.75 under Folio 70819410461 in Mirae Asset Hybrid - Equity Fund has been processed for NAV of 21.500 subject to realisation.",
            "VM-MIRAE"
        ).category)
    }

    @Test fun axisStpUnitsAllottedMessageIsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify(
            "Dear Investor, STP request from Axis Money Market Fund to Axis Small Cap Fund - Regular Growth has been processed and 163.018 units at NAV 61.34 are allotted under folio XXXXXXXX3902.",
            "VK-AXISMF"
        ).category)
    }

    @Test fun libertyInsurancePremiumNoticeIsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify(
            "Dear Customer, Your PNR 2454833813 with PA Ins No 211623000010866222. Premium Rs 0.35. To update nominee visit libertyinsurance.in Liberty General Ins",
            "AD-LIBGEN"
        ).category)
    }

    @Test fun eyeglassesDiscountIsPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "Dear Customer! Get Eyeglasses & Sunglasses @ Rs 374. Use Code FLAT1000 T&C Apply",
            "TX-EMEEYE"
        ).category)
    }

    @Test fun workOnlineEarningsMessageIsPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "INFO work online, can earn Rs 3000-6000 easily, click for detail at Pine Labs",
            "AD-PLUTUS"
        ).category)
    }

    @Test fun homeVisitSofaCleaningAdvertisementIsPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "Dear Shivam Sofa wash Home Visit Time 9904857044 Chemical Washing of Sofa Seat CARPET CURTAIN BLIND CAR INTERIOR ROMAN ROLER Target Advtng",
            "VM-SFADRY"
        ).category)
    }

    @Test fun samsungProductLaunchAndCashbackMessagesArePromotions() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "Bring the sparkle in your life with Samsung Washing Machine. Upto 20% cashback during Big Laundry Days.",
            "VM-SAMSNG"
        ).category)
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "Get ready to unpack the latest Galaxy with us on February 1. Pre-reserve now and get exclusive benefits.",
            "AX-SAMSNG"
        ).category)
    }

    @Test fun samsungProductFeatureAdvertisementIsPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "Say goodbye to OOPS and WHAT! Cancel the noise with amazing Voice Focus feature in Galaxy A, starting at Rs 25999.",
            "AX-SAMSNG"
        ).category)
    }

    @Test fun hdfcErgoEvEducationLinkIsPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "HDFC ERGO's All Things EV makes everything about Electric Vehicles easy. Planning to buy an EV or want to know more about EVs, click here.",
            "VM-HDFCGI"
        ).category)
    }

    @Test fun rociaShoesSaleIsPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "ROCIA SHOES LAST DAY of its Dazzling Wedding Week Enjoy 500 off 2500 & 1000 off 5000 at all stores.",
            "VM-ROCIAS"
        ).category)
    }


    @Test fun ayurvedicWeightLossMessageIsPromotion() {
        assertEquals(SmsClassifier.Category.PROMOTIONS, SmsClassifier.classify(
            "Wajan Ghataye Umar Badaaye Keval 90 Dino Me 35 Kilo Tak Wajan Ghataye Warna Paise Wapis Paaye Govt. Approved 100% Ayurved Treatment",
            "BZ-ITNDIA"
        ).category)
    }

    @Test fun kotakLifePolicyIssuanceIsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify(
            "Welcome to the Kotak Life Insurance family. Your policy 75563235 has been issued. An electronic copy of this policy has been emailed to you.",
            "JD-KOTAKL"
        ).category)
    }

    @Test fun kotakPolicyServicingInstructionsAreService() {
        assertEquals(SmsClassifier.Category.SERVICES, SmsClassifier.classify(
            "You can now use our latest servicing modes to manage your policy: WhatsApp, chat with Kaya, or visit the online policy manager.",
            "JD-KOTAKL"
        ).category)
    }

    @Test fun resolvedHaierServiceRequestIsService() {
        assertEquals(SmsClassifier.Category.SERVICES, SmsClassifier.classify(
            "Your request ID AH20221024000026 is resolved. For further assistance please visit service support or call.",
            "VM-eHaier"
        ).category)
    }

    @Test fun invoiceCreatedByVendorIsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify(
            "Your Invoice No. 2022-23-1782 created by Charmeuse Technologies Pvt Ltd of Rs. 23600 - VastraApp",
            "AD-VTRAPP"
        ).category)
    }

    @Test fun cryptoRiskDisclaimerIsService() {
        assertEquals(SmsClassifier.Category.SERVICES, SmsClassifier.classify(
            "Disclaimer: Crypto & NFTs are unregulated and can be highly risky. There may be no regulatory recourse for any loss from such transactions. CoinSwitch PRO",
            "TXCKUBER"
        ).category)
    }

    @Test fun insurancePolicyCopyConfirmationIsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify(
            "Thank you for choosing Reliance DOMESTIC policy 920292028342273376. For policy copy, please click.",
            "JXRELGEN"
        ).category)
    }

    @Test fun incomeTaxTdsAnd26AsNoticeIsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify(
            "Total TDS by Employer of PAN GUKXXXXXX9F for Qtr ending Jun 30 is Rs 0 and cumulative TDS for FY 20-21 is Rs 0. View 26AS for details.",
            "VM-ITDEFL"
        ).category)
    }

    @Test fun vehicleWarrantyExtensionIsService() {
        assertEquals(SmsClassifier.Category.SERVICES, SmsClassifier.classify(
            "If your two wheeler expiry date of free service, warranty and extended warranty lies between 15/03/2020 to 31/05/2020 has been extended to 31/07/2020.",
            "BH-EHONDA"
        ).category)
    }

    @Test fun pucPassedAndExpiryRemindersAreServices() {
        assertEquals(SmsClassifier.Category.SERVICES, SmsClassifier.classifyThread(listOf(
            "Vehicle GJ18AH8613 has passed emission test. Certificate no D31RJ38101705 valid till 14-08-2020. Rs.100 charged for PUC test. Transport Deptt, Rajasthan.",
            "Reminder: PUC certificate of vehicle GJ18AH8613 will expire on 14 Aug 2020, kindly get it renewed before expiry."
        ), "QP-RJPUCC").category)
    }

    
    @Test fun otpExtractorFindsCommonOtpPhrasings() {
        assertEquals("482913", org.fossify.messages.helpers.OtpUtils.extractOtp("Your OTP is 482913"))
        assertEquals("123456", org.fossify.messages.helpers.OtpUtils.extractOtp("Use 123456 as your OTP"))
        assertEquals("765432", org.fossify.messages.helpers.OtpUtils.extractOtp("Your verification code: 765432"))
    }

    @Test fun otpExtractorDoesNotTreatInvoiceOrMoneyAsOtp() {
        assertEquals(null, org.fossify.messages.helpers.OtpUtils.extractOtp("Invoice No. 2022-23-1782 created for Rs. 23600"))
        assertEquals(null, org.fossify.messages.helpers.OtpUtils.extractOtp("Your balance is Rs 123456"))
    }

    @Test fun bankOtpRemainsTransaction() {
        assertEquals(SmsClassifier.Category.TRANSACTIONS, SmsClassifier.classify("Your bank OTP is 123456", "AD-BANK").category)
    }

    private fun assertCategory(expected: SmsClassifier.Category, body: String) {
        assertEquals(expected, SmsClassifier.classify(body).category)
    }
}
