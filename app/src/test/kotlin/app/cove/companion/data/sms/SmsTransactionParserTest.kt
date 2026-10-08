package app.cove.companion.data.sms

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTransactionParserTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val received = LocalDateTime.of(2026, 10, 5, 10, 22, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    private fun parse(sender: String?, body: String) = SmsTransactionParser.parse(sender, body, received, zone)

    private fun ok(sender: String?, body: String): ParsedSms =
        (parse(sender, body) as? ParseResult.Accepted)?.tx ?: error("rejected: ${parse(sender, body)} for $body")

    private data class Case(
        val name: String, val sender: String?, val body: String, val paise: Long, val dir: Direction,
        val merchant: String? = null, val last4: String? = null, val ref: String? = null, val paidWith: String? = null, val bank: String? = null,
        val at: Long? = null,
    )

    private val accepted = listOf(
        Case("payment received is a credit", "AX-HDFCBK", "Payment of Rs.1500.00 received in A/c XX1234 on 05-10-26 from VPA rahul@okicici. UPI Ref No 123456789012", 150000, Direction.Credit, "Rahul", "1234", "123456789012", "UPI", "HDFC Bank", at(2026, 10, 5, 10, 22)),
        Case("hdfc upi debit", "AX-HDFCBK", "Rs.450.00 debited from A/c XX1234 on 05-10-26 to VPA zomato@okaxis. UPI Ref No 123456789012", 45000, Direction.Debit, "Zomato", "1234", "123456789012", "UPI", "HDFC Bank", at(2026, 10, 5, 10, 22)),
        Case("hdfc sent", "AD-HDFCBK", "Sent Rs.250.00 From HDFC Bank A/c *1234 To SWIGGY On 05/10/26 Ref 628374650192 Not You? Call 18002586161", 25000, Direction.Debit, "Swiggy", "1234", "628374650192", null, "HDFC Bank"),
        Case("hdfc card spent", "VM-HDFCBK", "INR 1,200.00 spent on HDFC Bank Card x1234 at AMAZON PAY on 05-Oct-26", 120000, Direction.Debit, "Amazon Pay", "1234", null, "Card", "HDFC Bank", at(2026, 10, 5, 10, 22)),
        Case("hdfc card thank you", "VM-HDFCBK", "Thank you for using your HDFC Bank Credit Card ending 1234 for Rs 3,499.00 at MYNTRA on 04-10-2026 21:15:09.", 349900, Direction.Debit, "Myntra", "1234", null, "Card", "HDFC Bank", at(2026, 10, 4, 21, 15, 9)),
        Case("hdfc credit", "AX-HDFCBK", "Rs. 5000.00 credited to a/c XXXXXX1234 on 05-10-26 by a/c linked to VPA rahul.sharma@okicici (UPI Ref No 987654321098)", 500000, Direction.Credit, "Rahul Sharma", "1234", "987654321098", "UPI", "HDFC Bank"),
        Case("hdfc date iso", "VM-HDFCBK", "Spent Rs.89 On HDFC Bank Card 5678 At BLINKIT On 2026-10-03:12:11:00", 8900, Direction.Debit, "Blinkit", "5678", null, "Card", "HDFC Bank"),
        Case("icici upi debit", "VM-ICICIB", "ICICI Bank Acct XX123 debited for Rs 450.00 on 05-Oct-26; ZOMATO credited. UPI:123456789012. Call 18002662 for dispute.", 45000, Direction.Debit, "Zomato", "123", "123456789012", "UPI", "ICICI Bank"),
        Case("icici credit", "VM-ICICIB", "Dear Customer, Acct XX123 is credited with Rs 5,000.00 on 05-Oct-26 from RAHUL SHARMA. UPI:123456789012-ICICI Bank.", 500000, Direction.Credit, "Rahul Sharma", "123", "123456789012", "UPI", "ICICI Bank"),
        Case("icici card", "VK-ICICIB", "Rs 450.00 spent on ICICI Bank Card XX1234 on 05-Oct-26 at Amazon. Avl Limit: Rs 98,000.00. If not you, call 18002662", 45000, Direction.Debit, "Amazon", "1234", null, "Card", "ICICI Bank"),
        Case("icici info", "VM-ICICIB", "ICICI Bank Acct XX123 debited Rs. 799.00 on 05-Oct-26 Info: UPI/628374650192/NETFLIX. Avl Bal Rs 10,000.00", 79900, Direction.Debit, "Netflix", "123", "628374650192"),
        Case("sbi upi", "JD-SBIUPI", "Dear UPI user A/C X1234 debited by 450.0 on date 05Oct26 trf to ZOMATO Refno 628374650192. If not u? call 1800111109. -SBI", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "SBI"),
        Case("sbi user", "AD-SBIINB", "Dear SBI User, your A/c X1234-debited by Rs450.00 on 05Oct26 transfer to RAMESH KUMAR Ref No 628374650192", 45000, Direction.Debit, "Ramesh Kumar", "1234", "628374650192", null, "SBI"),
        Case("sbi credit", "VM-SBIUPI", "Dear SBI User, your A/c X1234 credited by Rs.5,00,000.00 on 05Oct26 transfer from ACME TECH Ref No 628374650192", 50000000, Direction.Credit, "Acme Tech", "1234", "628374650192"),
        Case("sbi card", "VK-SBICRD", "Rs.2450.00 spent on your SBI Credit Card ending 4321 at BIGBASKET on 05/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispf", 245000, Direction.Debit, "Bigbasket", "4321", null, "Card", "SBI"),
        Case("axis upi", "AX-AXISBK", "Debit INR 450.00 A/c no. XX1234 05-10-26 10:22:33 UPI/P2M/628374650192/ZOMATO SMS BLOCK UPI to 5676", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "Axis Bank", at(2026, 10, 5, 10, 22, 33)),
        Case("axis credit", "AX-AXISBK", "Credit INR 2500.00 A/c no. XX1234 05-10-26 09:00:01 UPI/P2A/628374650192/PRIYA NAIR SMS BLOCK UPI to 5676", 250000, Direction.Credit, "Priya Nair", "1234", "628374650192", "UPI", "Axis Bank"),
        Case("axis card", "AX-AXISBK", "Spent Card no. XX1234 INR 450 05-10-26 10:22:33 AMAZON Avl Lmt INR 100000 SMS BLOCK 1234 to 5676", 45000, Direction.Debit, "Amazon", "1234", null, "Card", "Axis Bank"),
        Case("kotak sent", "BZ-KOTAKB", "Sent Rs.450.00 from Kotak Bank AC X1234 to zomato@okaxis on 05-10-26.UPI Ref 628374650192. Not you? Call 18002099292", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "Kotak Bank"),
        Case("kotak received", "BZ-KOTAKB", "Received Rs.1,500.00 in your Kotak Bank AC X1234 from anita.rao@oksbi on 05-10-26.UPI Ref:628374650192.", 150000, Direction.Credit, "Anita Rao", "1234", "628374650192", "UPI", "Kotak Bank"),
        Case("idfc", "VM-IDFCFB", "INR 450.00 debited from IDFC FIRST Bank A/c XX1234 on 05-10-2026 to VPA zomato@okaxis. UPI Ref 628374650192", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "IDFC First Bank"),
        Case("yes bank", "AD-YESBNK", "Rs 450.00 debited from a/c XX1234 on 05-10-2026 17:26:25 to VPA zomato@ybl. UPI Ref 628374650192 -Yes Bank", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "Yes Bank", at(2026, 10, 5, 17, 26, 25)),
        Case("canara", "AX-CANBNK", "An amount of INR 450.00 has been DEBITED to your account XXX1234 on 05/10/2026 Total Avail.bal INR 12,345.00", 45000, Direction.Debit, null, "1234", null, null, "Canara Bank"),
        Case("canara credit", "AX-CANBNK", "Your a/c XX1234 has been credited with Rs 7,500.00 on 05-10-2026 by NEFT-CNRBN52026100500123-ACME TECH PVT LTD", 750000, Direction.Credit, "Acme Tech", "1234", "CNRBN52026100500123", "Bank transfer"),
        Case("bob", "VM-BOBTXN", "Rs.450.00 Dr. from A/C XXXXXX1234 and Cr. to zomato@okaxis. Ref:628374650192. AvlBal Rs:12345.67(05-10-26 10:22:33)", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "Bank of Baroda"),
        Case("pnb", "AX-PNBSMS", "A/c XX1234 debited INR 450.00 Dt 05-10-26 11:03:45 Thru UPI:628374650192. Bal INR 5,000.00", 45000, Direction.Debit, null, "1234", "628374650192", "UPI", "PNB", at(2026, 10, 5, 11, 3, 45)),
        Case("federal", "VM-FEDBNK", "Rs 450.00 debited from your A/c XX1234 on 05OCT26 to VPA zomato@fbl. UPI Ref 628374650192", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "Federal Bank"),
        Case("indusind", "VM-INDUSB", "INR 450.00 debited from A/c XX1234 on 05-OCT-26. Info: UPI/628374650192/ZOMATO. Avl Bal INR 4,000", 45000, Direction.Debit, "Zomato", "1234", "628374650192", "UPI", "IndusInd Bank"),
        Case("paytm bank paid", "VM-PYTBNK", "Paid Rs.450 to Zomato from Paytm Payments Bank. UPI Ref No 628374650192. Balance Rs 120", 45000, Direction.Debit, "Zomato", null, "628374650192", "UPI", "Paytm Payments Bank"),
        Case("paytm bank received", "VM-PYTBNK", "Rs.500 received in your Paytm Payments Bank a/c from RAHUL (UPI Ref No 628374650192)", 50000, Direction.Credit, "Rahul", null, "628374650192", "UPI", "Paytm Payments Bank"),
        Case("amex", "AX-AMEXIN", "A charge of Rs 450.00 was made on your American Express Card ending 1234 at AMAZON on 5 October, 2026.", 45000, Direction.Debit, "Amazon", "1234", null, "Card", "American Express"),
        Case("atm", "AX-HDFCBK", "Rs 10,000.00 withdrawn at ATM HDFC BANK MUMBAI from A/c XX1234 on 05-10-26. Avl Bal Rs 25,000.00", 1000000, Direction.Debit, "ATM withdrawal", "1234", null, "Cash", "HDFC Bank"),
        Case("imps credit", "AX-SBIINB", "Rs 3,000.00 credited to A/c XX1234 on 05-10-26 by IMPS/P2A/628374650192/ANITA RAO", 300000, Direction.Credit, "Anita Rao", "1234", "628374650192"),
        Case("neft debit", "VM-HDFCBK", "Rs 25,000.00 debited from A/c XX1234 on 05-10-26 NEFT-HDFCN52026100500123-LANDLORD RAJESH. Avl bal Rs 1,000", 2500000, Direction.Debit, "Landlord Rajesh", "1234", "HDFCN52026100500123", "Bank transfer"),
        Case("autopay", "VM-ICICIB", "Rs 199.00 debited from A/c XX1234 on 05-Oct-26 via UPI Autopay for NETFLIX mandate. UPI Ref 628374650192", 19900, Direction.Debit, "Netflix", "1234", "628374650192", "UPI"),
        Case("refund", "AX-HDFCBK", "Refund of Rs 299.00 from SWIGGY credited to your A/c XX1234 on 05-10-26. Ref 628374650192", 29900, Direction.Credit, "Swiggy", "1234", "628374650192"),
        Case("google pay style", "VM-HDFCBK", "Rs.75.50 debited from A/c XX1234 on 05-10-26 to VPA chaiwala.9876543210@ybl. UPI Ref No 628374650192", 7550, Direction.Debit, "Chaiwala", "1234", "628374650192", "UPI"),
        Case("paste no sender", null, "Rs.450.00 debited from A/c XX1234 on 05-10-26 to VPA zomato@okaxis. UPI Ref No 123456789012", 45000, Direction.Debit, "Zomato", "1234", "123456789012", "UPI"),
        Case("upi sent via", "AD-HDFCBK", "Sent Rs.250 via UPI to name@okhdfc", 25000, Direction.Debit, "Name", null, null, "UPI"),
        Case("upi received", "AD-HDFCBK", "received Rs 5,000 from Amit Verma via UPI. UPI Ref 628374650192", 500000, Direction.Credit, "Amit Verma", null, "628374650192", "UPI"),
        Case("indian grouping decimals", "VM-SBIINB", "Rs 12,34,567.89 credited to A/c XX1234 on 05-10-26 by NEFT-SBIN52026100500123-ACME LTD", 123456789, Direction.Credit, "Acme", "1234", "SBIN52026100500123"),
        Case("rupee symbol", "AX-HDFCBK", "₹1,250.50 debited from A/c XX1234 on 05-10-26 to VPA bigbasket@icici. UPI Ref 628374650192", 125050, Direction.Debit, "Bigbasket", "1234", "628374650192"),
        Case("rs no dot space", "AX-HDFCBK", "Rs450 debited from A/c XX1234 on 05-10-26 to VPA zepto@axl. UPI Ref 628374650192", 45000, Direction.Debit, "Zepto", "1234", "628374650192"),
        Case("pluxee short", "VM-PLUXEE", "Rs 8 spent from Pluxee wallet", 800, Direction.Debit, null, null, null, "Wallet", "Pluxee"),
        Case("pluxee at merchant", "VM-PLUXEE", "Rs. 8.00 spent from your Pluxee wallet at CAFE on 07-10-2026. Bal Rs. 1,250", 800, Direction.Debit, "Cafe", null, null, "Wallet", "Pluxee"),
        Case("sodexo meal card", "AX-SODEXO", "Your Sodexo Meal Card ending 1234 is debited by Rs 8.00 at XYZ. Avl bal Rs 1,242.00", 800, Direction.Debit, "Xyz", "1234", null, "Wallet", "Sodexo"),
        Case("pluxee sodexo card", "VM-PLUXEE", "Your Sodexo/Pluxee Meal Card ending 1234 is debited by Rs 8.00 at XYZ", 800, Direction.Debit, "Xyz", "1234", null, "Wallet", "Pluxee"),
        Case("pluxee deducted", "VM-PLUXEE", "Rs 120.50 deducted from your Pluxee wallet at Cafe Coffee Day. Balance: Rs 900", 12050, Direction.Debit, "Cafe Coffee Day", null, null, "Wallet", "Pluxee"),
        Case("pluxee refund", "VM-PLUXEE", "Refund of Rs 45.00 credited to your Pluxee wallet from BIG BAZAAR", 4500, Direction.Credit, "Big Bazaar", null, null, "Wallet", "Pluxee"),
        Case("paste wallet no sender", null, "Rs 8 spent from Pluxee wallet", 800, Direction.Debit, null, null, null, "Wallet", "Pluxee"),
        Case("paste wallet merchant no sender", null, "Rs. 8.00 spent from your Pluxee wallet at CAFE. Bal Rs. 1,250", 800, Direction.Debit, "Cafe", null, null, "Wallet", "Pluxee"),
        Case("paytm wallet", "VM-PAYTMW", "Rs 60 paid from your Paytm Wallet to Chai Point. Bal Rs 340", 6000, Direction.Debit, "Chai Point", null, null, "Wallet", "Paytm Wallet"),
        Case("mobikwik", "VM-MOBIKW", "Rs 99 debited from your MobiKwik wallet at JIOMART. Avl bal Rs 400", 9900, Direction.Debit, "Jiomart", null, null, "Wallet", "Mobikwik"),
        Case("old date year", "AX-HDFCBK", "Rs 100.00 debited from A/c XX1234 on 04-10-26 to VPA dmart@icici. UPI Ref 628374650192", 10000, Direction.Debit, "DMART", "1234", "628374650192", null, null, at(2026, 10, 4, 12, 0)),
    )

    @Test fun acceptsRealisticMessages() {
        assertTrue("need at least 40 samples", accepted.size >= 40)
        val failures = ArrayList<String>()
        for (c in accepted) {
            val r = parse(c.sender, c.body)
            if (r !is ParseResult.Accepted) { failures += "${c.name}: rejected $r"; continue }
            val t = r.tx
            fun check(label: String, want: Any?, got: Any?) { if (want != null && want != got) failures += "${c.name}: $label want $want got $got" }
            check("paise", c.paise, t.amountPaise)
            check("dir", c.dir, t.direction)
            check("merchant", c.merchant, t.merchant)
            check("last4", c.last4, t.last4)
            check("ref", c.ref, t.ref)
            check("paidWith", c.paidWith, t.paidWith)
            check("bank", c.bank, t.bank)
            check("at", c.at, t.at)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private val rejected = listOf(
        Triple("otp", "AX-HDFCBK", "123456 is your OTP for txn of Rs 450.00 at AMAZON. Do not share with anyone. -HDFC Bank") to Rejection.Otp,
        Triple("otp2", "VM-ICICIB", "OTP is 482913 for transaction of INR 1,200.00 on ICICI Bank Card XX1234. Valid 10 mins.") to Rejection.Otp,
        Triple("otp3", "AD-SBIOTP", "Use 556677 as your login OTP. Never share OTP. -SBI") to Rejection.Otp,
        Triple("promo", "VM-HDFCBK", "Pre-approved personal loan of Rs 5,00,000 for you. Apply now at hdfcbank.com/loan") to Rejection.Promo,
        Triple("promo2", "AX-AXISBK", "Get Rs 500 cashback offer on your next UPI payment. T&C apply") to Rejection.Promo,
        Triple("promo3", "VM-ICICIB", "Congratulations! You are eligible for a credit card with limit Rs 2,00,000. Click here") to Rejection.Promo,
        Triple("future", "VM-HDFCBK", "Rs 199.00 will be debited from your A/c XX1234 on 07-10-26 towards NETFLIX mandate") to Rejection.Future,
        Triple("predebit", "VM-ICICIB", "Pre-debit notification: Rs 999 for SPOTIFY will be debited from A/c XX1234 on 06-Oct-26") to Rejection.Future,
        Triple("request", "AX-HDFCBK", "RAHUL has requested money Rs 500.00 on UPI. Approve the request in your UPI app") to Rejection.Request,
        Triple("collect", "VM-SBIUPI", "Collect request of Rs 1,200 from SHOP received on UPI. Ignore if not recognised") to Rejection.Request,
        Triple("failed", "AX-HDFCBK", "Your UPI txn of Rs 450.00 to zomato@okaxis has failed. If amount debited it will be refunded") to Rejection.Failed,
        Triple("declined", "VM-ICICIB", "Txn of INR 1,200.00 on ICICI Bank Card XX1234 at AMAZON declined due to insufficient balance") to Rejection.Failed,
        Triple("reversal", "AX-HDFCBK", "Reversal of Rs 450.00 initiated for failed UPI txn Ref 628374650192. Credit in 3-5 days") to Rejection.Failed,
        Triple("due", "VM-HDFCBK", "Total amount due Rs 12,345.00 on your HDFC Bank Credit Card XX1234. Pay by 15-Oct-26 to avoid late fee") to Rejection.Due,
        Triple("min due", "VM-ICICIB", "Min amount due INR 620.00 for your ICICI Bank Card XX1234 is due on 18-Oct-26") to Rejection.Due,
        Triple("statement", "VM-SBICRD", "Your SBI Card statement for Sep is generated. Total amt due Rs 4,500.00") to Rejection.Statement,
        Triple("balance", "AX-HDFCBK", "Avl Bal in A/c XX1234 is Rs 25,000.00 as on 05-10-26") to Rejection.BalanceOnly,
        Triple("card bill ack", "VM-HDFCBK", "Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card XX1234. Thank you") to Rejection.CardBillAck,
        Triple("mandate setup", "VM-ICICIB", "Your UPI Autopay mandate for NETFLIX of Rs 199 has been created successfully") to Rejection.Mandate,
        Triple("personal number", "+919876543210", "Rs 500 sent to you. UPI Ref 628374650192 debited from A/c XX1234") to Rejection.PersonalSender,
        Triple("personal 10 digit", "9876543210", "Paid Rs 450 via UPI to zomato@okaxis") to Rejection.PersonalSender,
        Triple("chat", "VM-ZOMATO", "Your order is on its way! Total Rs 450 will be collected on delivery") to Rejection.NoAmount,
        Triple("empty", "VM-HDFCBK", "   ") to Rejection.Empty,
        Triple("wallet otp", "VM-PLUXEE", "123456 is your OTP to pay Rs 8.00 from Pluxee wallet. Do not share.") to Rejection.Otp,
        Triple("wallet promo", "VM-PLUXEE", "Congratulations! Get Rs 100 cashback offer on your Pluxee wallet. Click here") to Rejection.Promo,
        Triple("wallet future", "VM-PLUXEE", "Rs 8 will be debited from your Pluxee wallet on 08-10-26") to Rejection.Future,
        Triple("wallet balance only", "VM-PLUXEE", "Your Pluxee wallet balance is Rs 1,242.00 as on 07-10-26") to Rejection.BalanceOnly,
        Triple("wallet low balance", "VM-PLUXEE", "Low balance alert: Avl bal Rs 12.00 in your Pluxee Meal Card ending 1234") to Rejection.BalanceOnly,
        Triple("wallet failed", "VM-PLUXEE", "Rs 8 payment from your Pluxee wallet failed. Please try again") to Rejection.Failed,
        Triple("wallet top up", "VM-PLUXEE", "Rs 150 added to Pluxee wallet. Bal Rs 1,400") to Rejection.Transfer,
        Triple("wallet top up 2", "VM-PAYTMW", "Rs 500 added to your Paytm Wallet via UPI. Bal Rs 560") to Rejection.Transfer,
        Triple("wallet credited", "VM-PLUXEE", "Rs 2,200.00 credited to your Sodexo Meal Card ending 1234") to Rejection.Transfer,
        Triple("paste wallet no amount", null, "Your Pluxee wallet was used at CAFE") to Rejection.NoAmount,
        Triple("paste top up no sender", null, "Rs 150 added to Pluxee wallet") to Rejection.Transfer,
    )

    @Test fun rejectsNonTransactions() {
        val failures = ArrayList<String>()
        for ((c, why) in rejected) {
            val r = parse(c.second, c.third)
            if (r !is ParseResult.Rejected) failures += "${c.first}: accepted $r"
            else if (r.reason != why) failures += "${c.first}: reason want $why got ${r.reason}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun walletPaymentIsLabelledAndNotedAsAWallet() {
        val t = ok("VM-PLUXEE", "Rs 8 spent from Pluxee wallet")
        assertEquals("Wallet", t.paidWith)
        assertEquals("Pluxee", t.bank)
        assertEquals("Pluxee wallet", t.noteFor("spent"))
        assertEquals("name:WALLET:PLUXEE", t.payeeKey)
        assertEquals(800L, t.amountPaise)
        assertEquals(received, t.at)
    }

    @Test fun walletMerchantWinsOverTheWalletNote() {
        val t = ok("VM-PLUXEE", "Rs. 8.00 spent from your Pluxee wallet at CAFE on 07-10-2026. Bal Rs. 1,250")
        assertEquals("Cafe", t.noteFor("spent"))
        assertEquals("name:WALLET:CAFE", t.payeeKey)
    }

    @Test fun walletNoteWordsAreNotDoubled() {
        assertEquals("Paytm Wallet", ok("VM-PAYTMW", "Rs 60 paid from your Paytm Wallet to Chai Point").bank)
        val t = ok("VM-PAYTMW", "Rs 60 paid from your Paytm Wallet. Bal Rs 340")
        assertEquals("Paytm Wallet", t.noteFor("spent"))
    }

    @Test fun plainTextWithoutAWalletNameAndSenderStaysWeak() {
        assertTrue(parse(null, "Rs 8 spent from your wallet") is ParseResult.Rejected)
        assertTrue(parse(null, "Rs 8 spent at the cafe") is ParseResult.Rejected)
    }

    @Test fun walletHandleInAVpaIsNotAWallet() {
        val t = ok("AX-HDFCBK", "Rs.450.00 debited from A/c XX1234 on 05-10-26 to VPA wallet@okaxis. UPI Ref No 123456789012")
        assertEquals("UPI", t.paidWith)
    }

    @Test fun refundsToAWalletAreReceived() {
        val t = ok("VM-PLUXEE", "Refund of Rs 45.00 credited to your Pluxee wallet from BIG BAZAAR")
        assertEquals(Direction.Credit, t.direction)
        assertEquals("Big Bazaar", t.noteFor("received"))
    }

    @Test fun parserVersionWasBumpedForWallets() {
        assertTrue(SmsTransactionParser.VERSION >= 2)
    }

    @Test fun weakTextFromUnknownSenderIsRejected() {
        val r = parse(null, "I paid Rs 450 for lunch yesterday, you owe me")
        assertTrue(r is ParseResult.Rejected)
    }

    @Test fun balanceAmountIsNotTheTransactionAmount() {
        val t = ok("AX-HDFCBK", "Avl Bal Rs 25,000.00. Rs 450.00 debited from A/c XX1234 on 05-10-26 to VPA x@ybl")
        assertEquals(45000L, t.amountPaise)
    }

    @Test fun missingDateFallsBackToSmsTime() {
        val t = ok("AX-HDFCBK", "Rs 50.00 debited from A/c XX1234 to VPA tea@ybl. UPI Ref 628374650192")
        assertEquals(received, t.at)
        assertTrue(!t.dateFromText)
    }

    @Test fun futureDatedTextIsIgnored() {
        val t = ok("AX-HDFCBK", "Rs 50.00 debited from A/c XX1234 on 05-12-26 to VPA tea@ybl. UPI Ref 628374650192")
        assertEquals(received, t.at)
    }

    @Test fun confidenceGrowsWithDetail() {
        val rich = ok("AX-HDFCBK", "Rs.450.00 debited from A/c XX1234 on 05-10-26 to VPA zomato@okaxis. UPI Ref No 123456789012")
        val thin = ok("AX-HDFCBK", "Rs 450 debited")
        assertTrue(rich.confidence > thin.confidence)
        assertTrue(rich.confidence <= 1f)
    }

    @Test fun nameCleaning() {
        val cases = mapOf(
            "VPA zomato@okaxis" to null,
            "AMAZON PAY INDIA PRIVATE LIMITED" to "Amazon Pay India",
            "SWIGGY.   " to "Swiggy",
            "RAHUL SHARMA UPI Ref 123456789012" to "Rahul Sharma",
            "IRCTC" to "IRCTC",
            "1234567890" to null,
        )
        for ((raw, want) in cases) assertEquals(raw, want, SmsTransactionParser.cleanName(raw))
    }

    @Test fun splitsPastedMessages() {
        val one = SmsTransactionParser.splitPasted("Rs.450 debited from A/c XX1234 to VPA a@ybl\nUPI Ref 123456789012")
        assertEquals(1, one.size)
        val blank = SmsTransactionParser.splitPasted("Rs.450 debited from A/c XX1234 to VPA a@ybl\n\nRs.20 debited from A/c XX1234 to VPA b@ybl")
        assertEquals(2, blank.size)
        val lines = SmsTransactionParser.splitPasted("Rs.450 debited from A/c XX1234 to VPA a@ybl\nRs.20 debited from A/c XX1234 to VPA b@ybl\nRs.30 debited from A/c XX1234 to VPA c@ybl")
        assertEquals(3, lines.size)
    }

    @Test fun parsedValueHoldsNoMessageText() {
        val t = ok("AX-HDFCBK", "Rs.450.00 debited from A/c XX1234 on 05-10-26 to VPA zomato@okaxis. UPI Ref No 123456789012 Call 18002586161")
        assertNull(t.merchant?.takeIf { it.contains("Call") })
    }
}
