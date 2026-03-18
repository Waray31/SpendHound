package com.waray.spendhound

import com.google.firebase.auth.FirebaseAuth

class PendingStatusActivity : AppCompatActivity(),
    com.waray.spendhound.BorrowerListTransactionAdapter.OnTransactionStatusUpdatedListener,
    com.waray.spendhound.PayerListTransactionAdapter.OnTransactionStatusUpdatedListener {
    private var borrowerListTV: TextView? = null
    private var payerListTV: TextView? = null
    private var allTV: TextView? = null
    private var borrowerListScrollView: ScrollView? = null
    private var payerListScrollView: ScrollView? = null
    private var borrowerPayerClicked = false
    private var backBtn: android.widget.ImageView? = null
    private var borrowerImg: android.widget.ImageView? = null
    private var payerImg: android.widget.ImageView? = null
    private var borrowerListLinearLayout: LinearLayout? = null
    private var payerListLinearLayout: LinearLayout? = null
    private var borrowerListBtn: LinearLayout? = null
    private var payerListBtn: LinearLayout? = null
    var borrowerNum: Int = 0
    var payerNum: Int = 0
    var currentNickname: kotlin.String? = null
    var currentNickname2: kotlin.String? = null
    private var borrowerListRecyclerView: RecyclerView? = null
    private var payerListRecyclerView: RecyclerView? = null
    private var adapter: BorrowerListTransactionAdapter? = null
    private var adapterPayer: PayerListTransactionAdapter? = null
    private var borrowerListTransactions: kotlin.collections.MutableList<BorrowerListTransaction?>? =
        null
    private var payerListTransactions: kotlin.collections.MutableList<BorrowerListTransaction?>? =
        null
    private var borrowerListPath: kotlin.collections.MutableList<kotlin.Array<kotlin.String?>?>? =
        null
    private var payerListPath: kotlin.collections.MutableList<kotlin.Array<kotlin.String?>?>? = null
    var transactionList: kotlin.collections.MutableList<BorrowerListTransaction?>? = null
    var pathList: kotlin.collections.MutableList<kotlin.Array<kotlin.String?>?>? = null
    private var context: android.content.Context? = null
    var acceptAllBorrowerBtn: android.widget.Button? = null
    var declineAllBorrowerBtn: android.widget.Button? = null
    var acceptBorrowerBtn: android.widget.Button? = null
    var declineBorrowerBtn: android.widget.Button? = null
    var confirmPayerBtn: android.widget.Button? = null
    var denyPayerBtn: android.widget.Button? = null
    var confirmAllPayerBtn: android.widget.Button? = null
    var denyAllPayerBtn: android.widget.Button? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_status)

        context = this

        borrowerListTV = findViewById<TextView>(R.id.borrowerListTV)
        payerListTV = findViewById<TextView>(R.id.payerListTV)
        borrowerListScrollView = findViewById<ScrollView>(R.id.borrowerListScrollView)
        payerListScrollView = findViewById<ScrollView>(R.id.payerListScrollView)
        backBtn = findViewById<android.widget.ImageView>(R.id.backBtn)
        borrowerListLinearLayout = findViewById<LinearLayout>(R.id.borrowerListLinearLayout)
        payerListLinearLayout = findViewById<LinearLayout>(R.id.payerListLinearLayout)
        borrowerImg = findViewById<android.widget.ImageView>(R.id.borrowerImg)
        acceptBorrowerBtn = findViewById<android.widget.Button>(R.id.acceptBorrowerBtn)
        declineBorrowerBtn = findViewById<android.widget.Button>(R.id.declineBorrowerBtn)
        acceptAllBorrowerBtn = findViewById<android.widget.Button>(R.id.acceptAllBorrowerBtn)
        declineAllBorrowerBtn = findViewById<android.widget.Button>(R.id.declineAllBorrowerBtn)
        allTV = findViewById<TextView>(R.id.allTV)
        payerListBtn = findViewById<LinearLayout>(R.id.payerListBtn)
        borrowerListBtn = findViewById<LinearLayout>(R.id.borrowerListBtn)
        payerImg = findViewById<android.widget.ImageView>(R.id.payerImg)
        confirmPayerBtn = findViewById<android.widget.Button>(R.id.confirmPayerBtn)
        denyPayerBtn = findViewById<android.widget.Button>(R.id.denyPayerBtn)
        confirmAllPayerBtn = findViewById<android.widget.Button>(R.id.confirmAllPayerBtn)
        denyAllPayerBtn = findViewById<android.widget.Button>(R.id.denyAllPayerBtn)

        borrowerPayerClicked = true

        BorrowerListTVClicked()
        PayerListTVClicked()
        BackButtonCLicked()

        val mainActivity = MainActivity()
        mainActivity.getCurrentNickname(object : CurrentNicknameCallback {
            override fun onCurrentNicknameReceived(currentNickname: kotlin.String?) {
                currentNickname2 = currentNickname
            }
        })

        BorrowerList()
        PayerList()
    }

    private fun BorrowerListTVClicked() {
        borrowerListTV.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                payerListTV.setBackgroundResource(R.drawable.button_background_invisible)
                borrowerListTV.setBackgroundResource(R.drawable.top_round_border)
                payerListTV.setTextColor(
                    ContextCompat.getColor(
                        this@PendingStatusActivity,
                        R.color.whitest
                    )
                )
                borrowerListTV.setTextColor(
                    ContextCompat.getColor(
                        this@PendingStatusActivity,
                        R.color.darkBlue
                    )
                )
                borrowerListScrollView.setVisibility(android.view.View.VISIBLE)
                payerListScrollView.setVisibility(android.view.View.GONE)
                borrowerListLinearLayout.setVisibility(android.view.View.VISIBLE)
                payerListLinearLayout.setVisibility(android.view.View.GONE)
                borrowerListBtn.setVisibility(android.view.View.VISIBLE)
                payerListBtn.setVisibility(android.view.View.GONE)

                borrowerListTV.setEnabled(false)
                payerListTV.setEnabled(true)
                borrowerPayerClicked = true
            }
        })
    }

    private fun PayerListTVClicked() {
        payerListTV.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                borrowerListTV.setBackgroundResource(R.drawable.button_background_invisible)
                payerListTV.setBackgroundResource(R.drawable.top_round_border)
                borrowerListTV.setTextColor(
                    ContextCompat.getColor(
                        this@PendingStatusActivity,
                        R.color.whitest
                    )
                )
                payerListTV.setTextColor(
                    ContextCompat.getColor(
                        this@PendingStatusActivity,
                        R.color.darkBlue
                    )
                )
                payerListScrollView.setVisibility(android.view.View.VISIBLE)
                borrowerListScrollView.setVisibility(android.view.View.GONE)
                borrowerListLinearLayout.setVisibility(android.view.View.GONE)
                payerListLinearLayout.setVisibility(android.view.View.VISIBLE)
                borrowerListBtn.setVisibility(android.view.View.GONE)
                payerListBtn.setVisibility(android.view.View.VISIBLE)

                payerListTV.setEnabled(false)
                borrowerListTV.setEnabled(true)
                borrowerPayerClicked = false
            }
        })
    }

    private fun BorrowerList() {
        borrowerListTransactions = java.util.ArrayList<BorrowerListTransaction?>()
        borrowerListPath = java.util.ArrayList<kotlin.Array<kotlin.String?>?>()

        val currentUserId: kotlin.String? = FirebaseAuth.getInstance().getCurrentUser().getUid()

        val databaseReference: DatabaseReference = DeclareDatabase.getDBRefBorrows()
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            var mainActivity: MainActivity = MainActivity()

            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val month: kotlin.String? = monthSnapshot.getKey()
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        val day: kotlin.String? = daySnapshot.getKey()
                        for (borrowSnapshot in daySnapshot.getChildren()) {
                            // Try to read as new structure first
                            val borrowNowTransaction: BorrowNowTransaction? =
                                borrowSnapshot.getValue(BorrowNowTransaction::class.java)

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null && borrowNowTransaction.getBorrowId() != null) {
                                // New UID-based structure: borrows/{month}/{day}/{borrowId}
                                // Check if current user is the lender (receiving borrow requests)
                                if (borrowNowTransaction.getLenderID() == currentUserId) {
                                    val status = borrowNowTransaction.getStatus()
                                    if (status == "For Lender Approval") {
                                        val borrowId = borrowNowTransaction.getBorrowId()
                                        var borrowerName = borrowNowTransaction.getBorrowerName()
                                        if (borrowerName == null || borrowerName.isEmpty()) {
                                            borrowerName = "Unknown"
                                        }
                                        val borrowedAmountStr =
                                            CurrencyUtils.formatAmountWithCurrency(
                                                borrowNowTransaction.getBorrowedAmountStr()
                                            )

                                        // Calculate time difference using timestamp
                                        val timestamp = borrowNowTransaction.getTimestamp()
                                        val timeDifferenceStr = calculateTimeDifference(timestamp)

                                        val borrowerTrans = BorrowerListTransaction(
                                            timeDifferenceStr,
                                            borrowerName,
                                            borrowedAmountStr,
                                            status
                                        )
                                        borrowerListTransactions!!.add(borrowerTrans)

                                        // New path format: month, day, borrowId (no username/time)
                                        borrowerListPath!!.add(
                                            kotlin.arrayOf<kotlin.String?>(
                                                month,
                                                day,
                                                borrowId,
                                                ""
                                            )
                                        )
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                val currentUserStr: kotlin.String? = borrowSnapshot.getKey()
                                if (currentUserStr != currentNickname2) {
                                    for (timeSnapshot in borrowSnapshot.getChildren()) {
                                        val time: kotlin.String? = timeSnapshot.getKey()
                                        val borrowerListTransaction: BorrowerListTransaction? =
                                            timeSnapshot.getValue(BorrowerListTransaction::class.java)
                                        if (borrowerListTransaction != null) {
                                            val status = borrowerListTransaction.getStatus()
                                            var borrowee = borrowerListTransaction.getBorrowee()
                                            if (status == "For Lender Approval" && borrowee == currentNickname2) {
                                                borrowee = currentUserStr
                                                val borrowedAmountStr =
                                                    CurrencyUtils.formatAmountWithCurrency(
                                                        borrowerListTransaction.getBorrowedAmountStr()
                                                    )
                                                val date = borrowerListTransaction.getDate()

                                                val formatPattern = "MMMM-dd-yyyy HH:mm:ss"
                                                var timeDifferenceStr = "0s"

                                                try {
                                                    val dateTime = date + " " + time
                                                    val dateFormat: java.text.DateFormat =
                                                        java.text.SimpleDateFormat(
                                                            formatPattern,
                                                            java.util.Locale.ENGLISH
                                                        )
                                                    val pastDate = dateFormat.parse(dateTime)
                                                    val currentDate = java.util.Date()
                                                    val timeDifferenceMillis =
                                                        currentDate.getTime() - pastDate!!.getTime()
                                                    val secondsSinceDate =
                                                        timeDifferenceMillis / 1000
                                                    timeDifferenceStr =
                                                        formatTimeDifference(secondsSinceDate)
                                                } catch (e: java.text.ParseException) {
                                                    e.printStackTrace()
                                                }

                                                val borrowerTrans = BorrowerListTransaction(
                                                    timeDifferenceStr,
                                                    borrowee,
                                                    borrowedAmountStr,
                                                    status
                                                )
                                                borrowerListTransactions!!.add(borrowerTrans)
                                                borrowerListPath!!.add(
                                                    kotlin.arrayOf<kotlin.String?>(
                                                        month,
                                                        day,
                                                        currentUserStr,
                                                        time
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                adapter = BorrowerListTransactionAdapter(
                    context,
                    borrowerListTransactions,
                    borrowerListPath,
                    this@PendingStatusActivity,
                    acceptAllBorrowerBtn,
                    declineAllBorrowerBtn
                )
                borrowerListRecyclerView = findViewById<RecyclerView>(R.id.borrowerListRecyclerView)
                borrowerListRecyclerView.setAdapter(adapter)
                borrowerListRecyclerView.setLayoutManager(LinearLayoutManager(this@PendingStatusActivity))
                adapter!!.notifyDataSetChanged()

                borrowerNum = borrowerListTransactions!!.size
                if (borrowerNum < 2) {
                    acceptAllBorrowerBtn!!.setEnabled(false)
                    declineAllBorrowerBtn!!.setEnabled(false)
                    acceptAllBorrowerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(this@PendingStatusActivity, R.color.grey)
                        )
                    )
                    declineAllBorrowerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(this@PendingStatusActivity, R.color.grey)
                        )
                    )
                } else {
                    acceptAllBorrowerBtn!!.setEnabled(true)
                    declineAllBorrowerBtn!!.setEnabled(true)
                    acceptAllBorrowerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(this@PendingStatusActivity, R.color.yellow)
                        )
                    )
                    declineAllBorrowerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(this@PendingStatusActivity, R.color.red)
                        )
                    )
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                android.util.Log.e(
                    "FirebaseDatabase",
                    "Database read error: " + databaseError.getMessage()
                )
            }
        })
    }

    private fun calculateTimeDifference(timestamp: kotlin.Long): kotlin.String {
        val currentTime = java.lang.System.currentTimeMillis()
        val differenceMillis = currentTime - timestamp
        val secondsSinceDate = differenceMillis / 1000
        return formatTimeDifference(secondsSinceDate)
    }

    private fun formatTimeDifference(secondsSinceDate: kotlin.Long): kotlin.String {
        if (secondsSinceDate >= 60 * 60 * 24 * 365) {
            val years = secondsSinceDate / (60 * 60 * 24 * 365)
            return years.toString() + "y"
        } else if (secondsSinceDate >= 60 * 60 * 24 * 30) {
            val months = secondsSinceDate / (60 * 60 * 24 * 30)
            return months.toString() + "mo"
        } else if (secondsSinceDate >= 60 * 60 * 24) {
            val days = secondsSinceDate / (60 * 60 * 24)
            return days.toString() + "d"
        } else if (secondsSinceDate >= 60 * 60) {
            val hours = secondsSinceDate / (60 * 60)
            return hours.toString() + "h"
        } else if (secondsSinceDate >= 60) {
            val minutes = secondsSinceDate / 60
            return minutes.toString() + "m"
        } else {
            return secondsSinceDate.toString() + "s"
        }
    }

    private fun PayerList() {
        payerListTransactions = java.util.ArrayList<BorrowerListTransaction?>()
        payerListPath = java.util.ArrayList<kotlin.Array<kotlin.String?>?>()

        val currentUserId: kotlin.String? = FirebaseAuth.getInstance().getCurrentUser().getUid()

        val databaseReference: DatabaseReference = DeclareDatabase.getDBRefBorrows()
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            var mainActivity: MainActivity = MainActivity()

            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val month: kotlin.String? = monthSnapshot.getKey()
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        val day: kotlin.String? = daySnapshot.getKey()
                        for (borrowSnapshot in daySnapshot.getChildren()) {
                            // Try to read as new structure first
                            val borrowNowTransaction: BorrowNowTransaction? =
                                borrowSnapshot.getValue(BorrowNowTransaction::class.java)

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null && borrowNowTransaction.getBorrowId() != null) {
                                // New UID-based structure: borrows/{month}/{day}/{borrowId}
                                // Check if current user is the lender (receiving payment confirmations)
                                if (borrowNowTransaction.getLenderID() == currentUserId) {
                                    val status = borrowNowTransaction.getStatus()
                                    if (status == "Payment Pending") {
                                        val borrowId = borrowNowTransaction.getBorrowId()
                                        var borrowerName = borrowNowTransaction.getBorrowerName()
                                        if (borrowerName == null || borrowerName.isEmpty()) {
                                            borrowerName = "Unknown"
                                        }
                                        val borrowedAmountStr =
                                            CurrencyUtils.formatAmountWithCurrency(
                                                borrowNowTransaction.getBorrowedAmountStr()
                                            )

                                        // Calculate time difference using timestamp
                                        val timestamp = borrowNowTransaction.getTimestamp()
                                        val timeDifferenceStr = calculateTimeDifference(timestamp)

                                        val payerTrans = BorrowerListTransaction(
                                            timeDifferenceStr,
                                            borrowerName,
                                            borrowedAmountStr,
                                            status
                                        )
                                        payerListTransactions!!.add(payerTrans)

                                        // New path format: month, day, borrowId (no username/time)
                                        payerListPath!!.add(
                                            kotlin.arrayOf<kotlin.String?>(
                                                month,
                                                day,
                                                borrowId,
                                                ""
                                            )
                                        )
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                val currentUserStr: kotlin.String? = borrowSnapshot.getKey()
                                if (currentUserStr != currentNickname2) {
                                    for (timeSnapshot in borrowSnapshot.getChildren()) {
                                        val time: kotlin.String? = timeSnapshot.getKey()
                                        val borrowerListTransaction: BorrowerListTransaction? =
                                            timeSnapshot.getValue(BorrowerListTransaction::class.java)
                                        if (borrowerListTransaction != null) {
                                            val status = borrowerListTransaction.getStatus()
                                            var borrowee = borrowerListTransaction.getBorrowee()
                                            if (status == "Payment Pending" && borrowee == currentNickname2) {
                                                borrowee = currentUserStr
                                                val borrowedAmountStr =
                                                    CurrencyUtils.formatAmountWithCurrency(
                                                        borrowerListTransaction.getBorrowedAmountStr()
                                                    )
                                                val date = borrowerListTransaction.getDate()

                                                val formatPattern = "MMMM-dd-yyyy HH:mm:ss"
                                                var timeDifferenceStr = "0s"

                                                try {
                                                    val dateTime = date + " " + time
                                                    val dateFormat: java.text.DateFormat =
                                                        java.text.SimpleDateFormat(
                                                            formatPattern,
                                                            java.util.Locale.ENGLISH
                                                        )
                                                    val pastDate = dateFormat.parse(dateTime)
                                                    val currentDate = java.util.Date()
                                                    val timeDifferenceMillis =
                                                        currentDate.getTime() - pastDate!!.getTime()
                                                    val secondsSinceDate =
                                                        timeDifferenceMillis / 1000
                                                    timeDifferenceStr =
                                                        formatTimeDifference(secondsSinceDate)
                                                } catch (e: java.text.ParseException) {
                                                    e.printStackTrace()
                                                }

                                                val borrowerTrans = BorrowerListTransaction(
                                                    timeDifferenceStr,
                                                    borrowee,
                                                    borrowedAmountStr,
                                                    status
                                                )
                                                payerListTransactions!!.add(borrowerTrans)
                                                payerListPath!!.add(
                                                    kotlin.arrayOf<kotlin.String?>(
                                                        month,
                                                        day,
                                                        currentUserStr,
                                                        time
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                adapterPayer = PayerListTransactionAdapter(
                    context,
                    payerListTransactions,
                    payerListPath,
                    this@PendingStatusActivity,
                    confirmAllPayerBtn,
                    denyAllPayerBtn
                )
                payerListRecyclerView = findViewById<RecyclerView>(R.id.payerListRecyclerView)
                payerListRecyclerView.setAdapter(adapterPayer)
                payerListRecyclerView.setLayoutManager(LinearLayoutManager(this@PendingStatusActivity))
                adapterPayer!!.notifyDataSetChanged()

                payerNum = payerListTransactions!!.size
                if (payerNum < 2) {
                    confirmAllPayerBtn!!.setEnabled(false)
                    denyAllPayerBtn!!.setEnabled(false)
                    confirmAllPayerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(
                                this@PendingStatusActivity,
                                R.color.grey
                            )
                        )
                    )
                    denyAllPayerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(
                                this@PendingStatusActivity,
                                R.color.grey
                            )
                        )
                    )
                } else {
                    confirmAllPayerBtn!!.setEnabled(true)
                    denyAllPayerBtn!!.setEnabled(true)
                    confirmAllPayerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(
                                this@PendingStatusActivity,
                                R.color.yellow
                            )
                        )
                    )
                    denyAllPayerBtn!!.setBackgroundTintList(
                        ColorStateList.valueOf(
                            ContextCompat.getColor(
                                this@PendingStatusActivity,
                                R.color.red
                            )
                        )
                    )
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                android.util.Log.e(
                    "FirebaseDatabase",
                    "Database read error: " + databaseError.getMessage()
                )
            }
        })
    }

    private fun BackButtonCLicked() {
        backBtn!!.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                onBackPressed()
            }
        })
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onTransactionStatusUpdated() {
        BorrowerList()
    }


    private fun AcceptDeclineBtnClicked() {
        acceptBorrowerBtn!!.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                allTV.setVisibility(android.view.View.GONE)
            }
        })
        declineBorrowerBtn!!.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                allTV.setVisibility(android.view.View.GONE)
            }
        })
    }

    fun showToast(message: kotlin.String?) {
        Toast.makeText(this@PendingStatusActivity, message, Toast.LENGTH_SHORT).show()
    }
}
