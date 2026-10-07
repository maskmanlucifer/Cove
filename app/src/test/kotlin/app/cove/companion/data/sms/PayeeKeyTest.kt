package app.cove.companion.data.sms

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayeeKeyTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val received = LocalDateTime.of(2026, 10, 5, 10, 22, 0).atZone(zone).toInstant().toEpochMilli()

    private fun parsed(sender: String?, body: String): ParsedSms =
        (SmsTransactionParser.parse(sender, body, received, zone) as? ParseResult.Accepted)?.tx ?: error("rejected: $body")

    @Test fun vpaKeysAreWholeLowercaseHandles() {
        val table = listOf(
            "zomato@okaxis" to "vpa:zomato@okaxis",
            "ZOMATO@OKAXIS" to "vpa:zomato@okaxis",
            "  merchant.name@ybl " to "vpa:merchant.name@ybl",
            "bharatpe.9000123456@fbpe" to "vpa:bharatpe.9000123456@fbpe",
            "gpay-1123@okaxis" to "vpa:gpay-1123@okaxis",
            "9876543210@ybl" to "vpa:9876543210@ybl",
            "rahul.sharma@okicici" to "vpa:rahul.sharma@okicici",
            "swiggy.rzp@icici" to "vpa:swiggy.rzp@icici",
            "q123456789@ybl" to "vpa:q123456789@ybl",
            "paytm.s1abc23@pty" to "vpa:paytm.s1abc23@pty",
            "irctc@sbi" to "vpa:irctc@sbi",
            "shop-42@apl" to "vpa:shop-42@apl",
            "BHARATPE09876543210@yesbankltd" to "vpa:bharatpe09876543210@yesbankltd",
            "dmart.store12@hdfcbank" to "vpa:dmart.store12@hdfcbank",
        )
        for ((raw, key) in table) assertEquals(raw, key, PayeeKey.fromVpa(raw))
    }

    @Test fun paytmQrDropsOnlyThePerQrSuffix() {
        assertEquals("vpa:paytmqr2810050501@paytm", PayeeKey.fromVpa("paytmqr2810050501abcd@paytm"))
        assertEquals("vpa:paytmqr2810050501@paytm", PayeeKey.fromVpa("PAYTMQR2810050501WXYZ@paytm"))
        assertEquals("vpa:paytmqr2810050501@paytm", PayeeKey.fromVpa("paytmqr2810050501@paytm"))
        assertEquals("vpa:paytmqr281005050101@paytm", PayeeKey.fromVpa("paytmqr281005050101a9z@paytm"))
        // A different shop must never merge with it.
        assertNotEquals(PayeeKey.fromVpa("paytmqr2810050501abcd@paytm"), PayeeKey.fromVpa("paytmqr2810050502abcd@paytm"))
        // Short digit runs are not clearly the pattern, so the whole handle stays.
        assertEquals("vpa:paytmqr123abc@paytm", PayeeKey.fromVpa("paytmqr123abc@paytm"))
        // Other families are not guessed at.
        assertEquals("vpa:bharatpe.9000123456@fbpe", PayeeKey.fromVpa("bharatpe.9000123456@fbpe"))
        assertEquals("vpa:paytm.s1abc23@pty", PayeeKey.fromVpa("paytm.s1abc23@pty"))
    }

    @Test fun notAHandleIsNoKey() {
        for (raw in listOf("", "zomato", "a@b", "@okaxis", "zomato@", "hello world@ybl", "x@y.com")) assertNull(raw, PayeeKey.fromVpa(raw))
    }

    @Test fun nameKeysDropNumbersCityAndCompanySuffixes() {
        val table = listOf(
            Triple("Starbucks 1234", "Card", "name:CARD:STARBUCKS"),
            Triple("STARBUCKS COFFEE MUMBAI", "Card", "name:CARD:STARBUCKS COFFEE"),
            Triple("Amazon Pay India", "Card", "name:CARD:AMAZON PAY"),
            Triple("Swiggy Instamart Bangalore", "Card", "name:CARD:SWIGGY INSTAMART"),
            Triple("Reliance Retail Ltd", "Card", "name:CARD:RELIANCE RETAIL"),
            Triple("D Mart Pvt Ltd", "Card", "name:CARD:D MART"),
            Triple("Apple Store", "Card", "name:CARD:APPLE"),
            Triple("Zomato", "Card", "name:CARD:ZOMATO"),
            Triple("ZOMATO", "Card", "name:CARD:ZOMATO"),
            Triple("McDonald's #4521", "Card", "name:CARD:MCDONALD S"),
            Triple("Big Bazaar Store 0045 Pune", "Card", "name:CARD:BIG BAZAAR"),
            Triple("Rahul Sharma", "Bank transfer", "name:BANK:RAHUL SHARMA"),
            Triple("Ramesh Kumar", "UPI", "name:UPI:RAMESH KUMAR"),
            Triple("Netflix", "Card", "name:CARD:NETFLIX"),
            Triple("Uber India Systems Pvt Ltd", "Card", "name:CARD:UBER SYSTEMS"),
            Triple("Delhi Metro", "Card", "name:CARD:DELHI METRO"),
            Triple("Delhi", "Card", "name:CARD:DELHI"),
        )
        for ((name, type, key) in table) assertEquals("$name $type", key, PayeeKey.fromName(name, type))
    }

    @Test fun sameNameDifferentTypeIsADifferentPayee() {
        assertNotEquals(PayeeKey.fromName("Zomato", "Card"), PayeeKey.fromName("Zomato", "UPI"))
    }

    @Test fun genericFallbacksAndCashGiveNoKey() {
        for (n in listOf("Payment", "payment", "Money received", "ATM withdrawal", "UPI", "Transfer", "Merchant", "Paid", "Online", "123456", "A")) {
            assertNull(n, PayeeKey.fromName(n, "UPI"))
        }
        assertNull(PayeeKey.fromName("Zomato", "Cash"))
        assertNull(PayeeKey.derive(null, null, "UPI"))
        assertTrue(PayeeKey.isGenericNote("Payment"))
        assertTrue(PayeeKey.isGenericNote("  money received "))
        assertTrue(PayeeKey.isGenericNote(""))
        assertTrue(!PayeeKey.isGenericNote("Payment to Raju"))
    }

    @Test fun handleBeatsNameAndHandleIsExposedForDisplay() {
        assertEquals("vpa:zomato@okaxis", PayeeKey.derive("zomato@okaxis", "Zomato Ltd", "UPI"))
        assertEquals("zomato@okaxis", PayeeKey.handleOf("vpa:zomato@okaxis"))
        assertNull(PayeeKey.handleOf("name:CARD:ZOMATO"))
        assertNull(PayeeKey.handleOf(null))
    }

    @Test fun parserFillsPayeeKeyFromQrHandleEvenWhenNameIsOpaque() {
        val tx = parsed("AX-HDFCBK", "Rs.120.00 debited from A/c XX1234 on 05-10-26 to VPA paytmqr2810050501abcd@paytm. UPI Ref No 123456789012")
        assertEquals("vpa:paytmqr2810050501@paytm", tx.payeeKey)
        assertNull("opaque QR handles give no merchant name, so the note stays generic", tx.merchant)
    }

    @Test fun parserKeysForRealisticUpiAndCardTexts() {
        val upi = listOf(
            "Rs.60.00 debited from A/c XX1234 on 05-10-26 to VPA bharatpe.9000123456@fbpe. UPI Ref No 123456789012" to "vpa:bharatpe.9000123456@fbpe",
            "Rs.35.00 debited from A/c XX1234 on 05-10-26 to VPA gpay-1123@okaxis. UPI Ref No 123456789013" to "vpa:gpay-1123@okaxis",
            "Rs.220.00 debited from A/c XX1234 on 05-10-26 to VPA merchant.name@ybl. UPI Ref No 123456789014" to "vpa:merchant.name@ybl",
            "Rs.450.00 debited from A/c XX1234 on 05-10-26 to VPA zomato@okaxis. UPI Ref No 123456789015" to "vpa:zomato@okaxis",
        )
        for ((body, key) in upi) assertEquals(body, key, parsed("AX-HDFCBK", body).payeeKey)
        val card = parsed("VM-HDFCBK", "Thank you for using your HDFC Bank Credit Card ending 1234 for Rs 3,499.00 at MYNTRA on 04-10-2026 21:15:09.")
        assertEquals("name:CARD:MYNTRA", card.payeeKey)
        assertEquals("name:UPI:NETFLIX", parsed("VM-ICICIB", "ICICI Bank Acct XX123 debited Rs. 799.00 on 05-Oct-26 Info: UPI/628374650192/NETFLIX. Avl Bal Rs 10,000.00").payeeKey)
    }

    @Test fun walletPaymentsKeyOnTheMerchantOrFallBackToTheWallet() {
        assertEquals("name:WALLET:PLUXEE", parsed("VM-PLUXEE", "Rs 8 spent from Pluxee wallet").payeeKey)
        assertEquals("name:WALLET:CAFE", parsed("VM-PLUXEE", "Rs. 8.00 spent from your Pluxee wallet at CAFE on 05-10-2026. Bal Rs. 1,250").payeeKey)
        assertEquals("name:WALLET:PLUXEE", PayeeKey.fromName("Pluxee", "Wallet"))
        assertNull("a plain wallet has no name to key on", parsed("VM-WALLET", "Rs 8 spent from your wallet").payeeKey)
    }

    @Test fun accountDigitsAloneNeverMakeAKey() {
        val atm = parsed("AX-HDFCBK", "Rs.2000.00 withdrawn from A/c XX1234 at ATM on 05-10-26. Avl Bal Rs 10,000.00")
        assertNull(atm.payeeKey)
        val noName = parsed("AX-HDFCBK", "Rs.500.00 debited from A/c XX1234 on 05-10-26. UPI Ref No 123456789012")
        assertTrue(noName.payeeKey == null || !noName.payeeKey!!.contains("1234"))
    }

    @Test fun ownHandleIsNotThePayee() {
        val tx = parsed("AX-HDFCBK", "Rs.90.00 debited from your VPA me.self@okhdfcbank to VPA chaiwala@ybl. UPI Ref No 123456789012")
        assertEquals("vpa:chaiwala@ybl", tx.payeeKey)
    }
}
