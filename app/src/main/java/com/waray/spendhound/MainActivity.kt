package com.waray.spendhound

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    var navView: BottomNavigationView? = null
    private var mAuth: Auth? = null
    var totalMonthSpends: Double = 0.0
    var dailyTotals: DoubleArray = DoubleArray(7)
    private var progressBar: ProgressBar? = null
    var currentNickname: String? = ""
    var owedNum: Int = 0
    var debtNum: Int = 0
    private var recentTransactionList = ArrayList<RecentTransaction?>()
    var debtList: ArrayList<BorrowTransaction?> = ArrayList()
    var owedList: ArrayList<OwedTransaction?> = ArrayList()
    private var recentTransactionAdapter: RecentTransactionAdapter? = null

    // FAB Menu fields
    private var fabMain: FloatingActionButton? = null
    private var fabMenuOverlay: View? = null
    private var containerBorrow: LinearLayout? = null
    private var containerAddTransaction: LinearLayout? = null
    private var containerAddGroup: LinearLayout? = null
    private var isFabMenuOpen = false
    private var selectedLenderName = ""

    interface OwedNumCallback {
        fun onOwedNumReceived(owedNum: Int)
    }

    interface DebtNumCallback {
        fun onDebtNumReceived(debtNum: Int)
    }

    interface CurrentNicknameCallback {
        fun onCurrentNicknameReceived(currentNickname: String?)
    }

    fun isUserInvolved(
        transaction: Transaction?,
        usernameOrUid: String?
    ): Boolean {
        if (transaction == null || usernameOrUid.isNullOrEmpty()) return false
        val currentUid = mAuth?.currentUserOrNull()?.id
        if (transaction.isUserInvolvedByUid(currentUid)) return true
        if (usernameOrUid == transaction.getUsernamePost()) return true
        if (usernameOrUid == transaction.getPosterDisplayName()) return true
        val payorsList = transaction.getPayorsList()
        if (payorsList != null && payorsList.contains(usernameOrUid)) return true
        val payorsDisplayNames = transaction.getPayorsDisplayNames()
        return payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.waray.spendhound.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressBar = findViewById(R.id.progressBar)
        progressBar?.visibility = View.VISIBLE
        mAuth = DeclareDatabase.auth
        UserHelper.preloadAllUsers()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment?
        val navController = navHostFragment!!.navController
        navView = findViewById(R.id.navView)
        setupWithNavController(navView!!, navController)

        // FAB Menu Setup
        fabMain = findViewById(R.id.fab_main)
        fabMenuOverlay = findViewById(R.id.fab_menu_overlay)
        containerBorrow = findViewById(R.id.container_borrow)
        containerAddTransaction = findViewById(R.id.container_add_transaction)
        containerAddGroup = findViewById(R.id.container_add_group)

        fabMain?.setOnClickListener { toggleFabMenu() }
        fabMenuOverlay?.setOnClickListener { closeFabMenu() }

        containerBorrow?.setOnClickListener {
            closeFabMenu()
            showBorrowDialog()
        }

        containerAddTransaction?.setOnClickListener {
            closeFabMenu()
            val intent = Intent(this, AddTransactionActivity::class.java)
            startActivity(intent)
        }

        containerAddGroup?.setOnClickListener {
            closeFabMenu()
            // showAddGroupDialog()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_home || destination.id == R.id.navigation_borrow || destination.id == R.id.navigation_profile) {
                showBottomBars()
            } else {
                hideBottomBars()
            }
        }
    }

    fun unhideNavigation() {
        showBottomBars()
    }

    fun hideNavigation() {
        hideBottomBars()
    }

    fun showBottomBars() {
        navView?.visibility = View.VISIBLE
        fabMain?.visibility = View.VISIBLE

        val navParams = navView?.layoutParams as? CoordinatorLayout.LayoutParams
        if (navParams != null) {
            val behavior = navParams.behavior as? HideBottomViewOnScrollBehavior<BottomNavigationView>
            behavior?.slideUp(navView!!)
        }

        val fabParams = fabMain?.layoutParams as? CoordinatorLayout.LayoutParams
        if (fabParams != null) {
            val behavior = fabParams.behavior as? HideBottomViewOnScrollBehavior<FloatingActionButton>
            behavior?.slideUp(fabMain!!)
        }
    }

    fun hideBottomBars() {
        navView?.visibility = View.GONE
        fabMain?.visibility = View.GONE
    }

    private fun toggleFabMenu() {
        if (isFabMenuOpen) closeFabMenu() else openFabMenu()
    }

    private fun openFabMenu() {
        isFabMenuOpen = true
        fabMenuOverlay?.visibility = View.VISIBLE
        fabMenuOverlay?.animate()?.alpha(1f)?.setDuration(300)?.start()

        fabMain?.animate()?.rotation(45f)?.setDuration(300)?.start()

        showFabOption(containerBorrow, 1)
        showFabOption(containerAddTransaction, 2)
        showFabOption(containerAddGroup, 3)
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        fabMenuOverlay?.animate()?.alpha(0f)?.setDuration(300)?.withEndAction {
            fabMenuOverlay?.visibility = View.GONE
        }?.start()

        fabMain?.animate()?.rotation(0f)?.setDuration(300)?.start()

        hideFabOption(containerBorrow)
        hideFabOption(containerAddTransaction)
        hideFabOption(containerAddGroup)
    }

    private fun showFabOption(view: LinearLayout?, index: Int) {
        view?.visibility = View.VISIBLE
        view?.alpha = 0f
        view?.translationY = 50f
        view?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(300)
            ?.setStartDelay((index * 50).toLong())
            ?.start()
    }

    private fun hideFabOption(view: LinearLayout?) {
        view?.animate()
            ?.alpha(0f)
            ?.translationY(50f)
            ?.setDuration(300)
            ?.withEndAction { view.visibility = View.GONE }
            ?.start()
    }

    private fun showBorrowDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_borrow_now)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val lendersRecycler = dialog.findViewById<RecyclerView>(R.id.lenderRecyclerView)
        val amountInput = dialog.findViewById<EditText>(R.id.dialogBorrowEditText)
        val borrowBtn = dialog.findViewById<Button>(R.id.dialogBorrowBtn)
        val cancelBtn = dialog.findViewById<Button>(R.id.dialogCancelBtn)
        val dialogBorrower = dialog.findViewById<TextView>(R.id.dialogBorrower)
        val dialogBorrowDate = dialog.findViewById<TextView>(R.id.dialogBorrowDate)

        dialogBorrower.text = currentNickname
        val calendar = Calendar.getInstance()
        dialogBorrowDate.text = SimpleDateFormat("MMM-dd-yyyy", Locale.getDefault()).format(calendar.time)

        lifecycleScope.launch {
            try {
                val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                val allUsers = UserHelper.getAllUsers()
                val lenders = allUsers.filter { it.id != currentUserId }.toMutableList()

                val adapter = LenderAdapter(lenders as MutableList<User?>)
                lendersRecycler.adapter = adapter
                lendersRecycler.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)

                val snapHelper: SnapHelper = LinearSnapHelper()
                snapHelper.attachToRecyclerView(lendersRecycler)

                lendersRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            val centerView = snapHelper.findSnapView(lendersRecycler.layoutManager)
                            if (centerView != null) {
                                val pos = lendersRecycler.getChildAdapterPosition(centerView)
                                val lender = adapter.getLenderAt(pos)
                                if (lender != null) {
                                    selectedLenderName = lender.username ?: ""
                                }
                            }
                        }
                    }

                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val midpoint = recyclerView.width / 2f
                        val d0 = 0f
                        val d1 = 0.5f
                        val s0 = 1f
                        val s1 = 0.8f

                        for (i in 0 until recyclerView.childCount) {
                            val child = recyclerView.getChildAt(i)
                            val childMidpoint = (recyclerView.layoutManager!!.getDecoratedLeft(child) + recyclerView.layoutManager!!.getDecoratedRight(child)) / 2f
                            val d = Math.min(d1.toDouble(), Math.abs(midpoint - childMidpoint).toDouble()).toFloat()
                            val scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0)
                            child.scaleX = scale
                            child.scaleY = scale
                        }
                    }
                })

                cancelBtn.setOnClickListener { dialog.dismiss() }

                borrowBtn.setOnClickListener {
                    val amountStr = amountInput.text.toString()
                    if (amountStr.isEmpty() || selectedLenderName.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Please fill all fields", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val amount = try { amountStr.toDouble() } catch (e: NumberFormatException) { 0.0 }
                    if (amount <= 0) {
                        Toast.makeText(this@MainActivity, "Invalid amount", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val lender = lenders.find { it.username == selectedLenderName }
                    if (lender != null) {
                        submitBorrowRequest(lender, amount)
                        dialog.dismiss()
                    }
                }

                dialog.show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error showing borrow dialog: ${e.message}")
            }
        }
    }

    private fun submitBorrowRequest(lender: User, amount: Double) {
        val currentUser = mAuth?.currentUserOrNull()
        if (currentUser == null) return

        lifecycleScope.launch {
            try {
                val calendar = Calendar.getInstance()
                val monthYear = SimpleDateFormat("MMMM-yyyy", Locale.ENGLISH).format(calendar.time)
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
                val createdAt = sdf.format(calendar.time)

                val borrowTransaction = BorrowNowTransaction(
                    borrowedAmount = amount,
                    borrowerId = currentUser.id?.toLongOrNull(),
                    lenderId = lender.id,
                    statusInt = 1, // 1 = For Lender Approval
                    createdAt = createdAt,
                    monthYear = monthYear,
                    borrowerName = currentNickname,
                    lender = lender.username,
                    timestamp = System.currentTimeMillis()
                )

                val inserted = DeclareDatabase.borrowsTable.insert(borrowTransaction) {
                    select()
                }.decodeSingle<BorrowNowTransaction>()
                
                val borrowIdStr = inserted.id?.toString() ?: ""
                if (borrowIdStr.isNotEmpty()) {
                    BalanceHelper.addBorrowerEntry(currentUser.id, borrowIdStr, null)
                    BalanceHelper.addLenderEntry(lender.id?.toString(), borrowIdStr, null)
                    BalanceHelper.updateTotaldebt(currentUser.id, amount, null)
                    BalanceHelper.updateTotalreceivable(lender.id?.toString(), amount, null)
                }

                Toast.makeText(this@MainActivity, "Borrow request sent!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error submitting borrow request: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to send request", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getRecentTransactions(callback: Runnable?) {
        lifecycleScope.launch {
            try {
                val usernameOrUid = currentNickname ?: ""
                
                val transactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                recentTransactionList.clear()
                
                for (t in transactions) {
                    if (isUserInvolved(t, usernameOrUid)) {
                        val my = t.monthYear ?: ""
                        val d = t.day ?: ""
                        val tk = t.timeKey ?: ""
                        val p = my.split("-").toTypedArray()
                        val month = if (p.isNotEmpty()) p[0] else ""
                        val year = if (p.size > 1) p[1] else ""
                        
                        recentTransactionList.add(
                            RecentTransaction(
                                "$month - $d",
                                t.getTransactionType(),
                                t.getMultilineStr(),
                                CurrencyUtils.formatAmountWithCurrency(t.getPaymentAmount()),
                                getTransactionIcon(t.getTransactionType()),
                                "$year-$month-$d $tk",
                                t.getPayorsDisplayNames() ?: t.getPayorsList(),
                                t.getPayorsList(),
                                t.getAmountsPaidList(),
                                t.getTotalIndividualPayment(),
                                null,
                                t.getPosterDisplayName() ?: t.getUsernamePost(),
                                t.getUsernamePost(),
                                my,
                                d,
                                tk
                            )
                        )
                    }
                }
                
                recentTransactionList.sortWith { t1, t2 ->
                    if (t1?.getSortDateTime() != null && t2?.getSortDateTime() != null) 
                        t2.getSortDateTime()!!.compareTo(t1.getSortDateTime()!!) 
                    else 0
                }
                
                val rv: RecyclerView? = findViewById(R.id.transactionListRecycler)
                if (rv != null) {
                    recentTransactionAdapter = RecentTransactionAdapter(
                        recentTransactionList as ArrayList<RecentTransaction>,
                        RecentTransactionAdapter.OnTransactionClickListener { transaction ->
                            onTransactionTap(transaction!!)
                        })
                    rv.adapter = recentTransactionAdapter
                    rv.layoutManager = LinearLayoutManager(this@MainActivity)
                    recentTransactionAdapter?.preloadAllImages(this@MainActivity)
                }
                callback?.run()
            } catch (e: Exception) {
                callback?.run()
            }
        }
    }

    private fun onTransactionTap(transaction: RecentTransaction) {
        // Implementation for handling transaction tap
    }

    private fun getTransactionIcon(type: String?): Int {
        return when (type) {
            "Electricity" -> R.drawable.lightning_bolt
            "Water" -> R.drawable.faucet
            "Rent" -> R.drawable.house
            "Internet" -> R.drawable.internet
            "Online Shopping" -> R.drawable.online_shopping
            "Travel" -> R.drawable.travel
            "Groceries" -> R.drawable.groceries
            "Foods" -> R.drawable.hamburger
            "House Necessity" -> R.drawable.necessities
            "Transportation" -> R.drawable.vehicles
            else -> R.drawable.others
        }
    }

    fun getTotalMonthSpends(callback: Runnable?) {
        lifecycleScope.launch {
            try {
                val usernameOrUid = currentNickname ?: ""
                val calendar = Calendar.getInstance()
                val monthYear = SimpleDateFormat("MMMM-yyyy", Locale.ENGLISH).format(calendar.time)
                
                val transactions = DeclareDatabase.transactionsTable.select {
                    filter { eq("month_year", monthYear) }
                }.decodeList<Transaction>()
                
                totalMonthSpends = transactions
                    .filter { isUserInvolved(it, usernameOrUid) }
                    .sumOf { it.paymentAmount }
                
                callback?.run()
            } catch (e: Exception) {
                callback?.run()
            }
        }
    }

    fun getEverydaySpends(callback: Runnable?) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        getEverydaySpendsForWeek(calendar, callback)
    }

    fun getEverydaySpendsForWeek(weekStart: Calendar, callback: Runnable?) {
        lifecycleScope.launch {
            try {
                val usernameOrUid = currentNickname ?: ""
                val startDate = weekStart.timeInMillis
                val endDate = weekStart.clone() as Calendar
                endDate.add(Calendar.DAY_OF_YEAR, 7)
                val endTime = endDate.timeInMillis

                val transactions = DeclareDatabase.transactionsTable.select {
                    filter {
                        gte("timestamp", startDate)
                        lt("timestamp", endTime)
                    }
                }.decodeList<Transaction>()

                val newDailyTotals = DoubleArray(7) { 0.0 }
                for (t in transactions) {
                    if (isUserInvolved(t, usernameOrUid)) {
                        val tCal = Calendar.getInstance().apply { timeInMillis = t.timestamp }
                        val dayOfWeek = tCal.get(Calendar.DAY_OF_WEEK) // SUNDAY = 1
                        newDailyTotals[dayOfWeek - 1] += t.paymentAmount
                    }
                }
                dailyTotals = newDailyTotals
                callback?.run()
            } catch (e: Exception) {
                callback?.run()
            }
        }
    }

    fun getDebtList(
        selectedStatus: String?,
        callback: DebtNumCallback,
        actionListener: DebtTransactionAdapter.OnBorrowerActionListener?
    ) {
        lifecycleScope.launch {
            try {
                val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                val borrows = DeclareDatabase.borrowsTable.select().decodeList<BorrowNowTransaction>()
                
                debtList.clear()
                for (bnt in borrows) {
                    if (bnt.borrowerId == currentUserId) {
                        if (bnt.getStatus() != "Removed" && bnt.getStatus() != "Payment Denied" && shouldIncludeForDebtStatus(
                                bnt.getStatus(),
                                selectedStatus
                            )
                        ) {
                            addDebtTransactionFromBorrowNow(bnt, bnt.getMonthYear(), null, bnt.id?.toString())
                        }
                    }
                }
                
                sortAndDisplayDebtList(actionListener)
                debtNum = debtList.size
                callback.onDebtNumReceived(debtNum)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting debt list: ${e.message}")
            }
        }
    }

    private fun sortAndDisplayDebtList(actionListener: DebtTransactionAdapter.OnBorrowerActionListener?) {
        debtList.sortWith { o1, o2 ->
            try {
                val f = SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH)
                return@sortWith f.parse(o2!!.getDate())!!.compareTo(f.parse(o1!!.getDate()))
            } catch (e: Exception) {
                return@sortWith 0
            }
        }
        val rv: RecyclerView? = findViewById(R.id.debtRecyclerList)
        if (rv != null) {
            rv.adapter = DebtTransactionAdapter(
                    debtList,
                    actionListener ?: object : DebtTransactionAdapter.OnBorrowerActionListener {
                        override fun onPayClicked(transaction: BorrowTransaction?, position: Int) {}
                        override fun onRemoveClicked(transaction: BorrowTransaction?, position: Int) {}
                        override fun onTryAgainClicked(transaction: BorrowTransaction?, position: Int) {}
                    })
            rv.layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun addDebtTransactionFromBorrowNow(
        borrowNowTransaction: BorrowNowTransaction,
        monthYear: String?,
        day: String?,
        borrowId: String?
    ) {
        val dateLong = borrowNowTransaction.getDate()
        val date = SimpleDateFormat("MMM-dd-yyyy", Locale.getDefault()).format(java.util.Date(dateLong))
        
        val psdLong = borrowNowTransaction.getPaymentSentDate()
        val psd = if (psdLong > 0) SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH).format(java.util.Date(psdLong)) else null
        
        val bt = BorrowTransaction(
            date,
            borrowNowTransaction.getLender(),
            borrowNowTransaction.getBorrowedAmount().toString(),
            borrowNowTransaction.getStatus()
        )
        bt.setPaymentSentDate(psd)
        bt.setBorrowId(borrowId)
        bt.setMonthYear(monthYear)
        bt.setDay(day)
        debtList.add(bt)
    }

    fun getDebtListMonthly(
        selectedMonth: String?,
        selectedStatus: String?,
        callback: DebtNumCallback,
        actionListener: DebtTransactionAdapter.OnBorrowerActionListener?
    ) {
        if (selectedMonth == null || selectedMonth == "All") return
        lifecycleScope.launch {
            try {
                val uid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                val borrows = DeclareDatabase.borrowsTable.select {
                    filter { eq("month_year", selectedMonth) }
                }.decodeList<BorrowNowTransaction>()
                
                debtList.clear()
                for (bnt in borrows) {
                    if (bnt.borrowerId == uid && bnt.getStatus() != "Removed" && bnt.getStatus() != "Payment Denied" && shouldIncludeForDebtStatus(
                            bnt.getStatus(),
                            selectedStatus
                        )
                    ) {
                        addDebtTransactionFromBorrowNow(bnt, selectedMonth, null, bnt.id?.toString())
                    }
                }
                
                sortAndDisplayDebtList(actionListener)
                debtNum = debtList.size
                callback.onDebtNumReceived(debtNum)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting monthly debt list: ${e.message}")
            }
        }
    }

    fun getOwedList(
        selectedStatus: String?,
        callback: OwedNumCallback,
        actionListener: OwedTransactionAdapter.OnLenderActionListener?
    ) {
        lifecycleScope.launch {
            try {
                val uid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                val borrows = DeclareDatabase.borrowsTable.select().decodeList<BorrowNowTransaction>()
                
                owedList.clear()
                for (bnt in borrows) {
                    if (bnt.lenderId == uid && bnt.getStatus() != "Declined" && bnt.getStatus() != "Payment Denied" && bnt.getStatus() != "Removed" && shouldIncludeForStatus(
                            bnt.getStatus(),
                            selectedStatus
                        )
                    ) {
                        addOwedTransactionFromBorrowNow(bnt, bnt.getMonthYear(), null, bnt.id?.toString())
                    }
                }
                
                sortAndDisplayOwedList(actionListener)
                owedNum = owedList.size
                callback.onOwedNumReceived(owedNum)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting owed list: ${e.message}")
            }
        }
    }

    private fun sortAndDisplayOwedList(actionListener: OwedTransactionAdapter.OnLenderActionListener?) {
        owedList.sortWith { o1, o2 ->
            try {
                val f = SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH)
                return@sortWith f.parse(o2!!.getDate())!!.compareTo(f.parse(o1!!.getDate()))
            } catch (e: Exception) {
                return@sortWith 0
            }
        }
        val rv: RecyclerView? = findViewById(R.id.owedRecyclerList)
        if (rv != null) {
            rv.adapter = OwedTransactionAdapter(
                    owedList as List<OwedTransaction?>,
                    actionListener ?: object : OwedTransactionAdapter.OnLenderActionListener {
                        override fun onNotYetClicked(transaction: OwedTransaction?, position: Int) {}
                        override fun onReceivedClicked(transaction: OwedTransaction?, position: Int) {}
                        override fun onDeclineClicked(transaction: OwedTransaction?, position: Int) {}
                        override fun onApprovedClicked(transaction: OwedTransaction?, position: Int) {}
                    })
            rv.layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    fun getCurrentNickname(callback: (String?) -> Unit) {
        val uid = mAuth?.currentUserOrNull()?.id ?: return callback("")
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("username")) {
                    filter { eq("user_id", uid.toLongOrNull() ?: 0L) }
                }.decodeSingleOrNull<User>()
                currentNickname = user?.username ?: ""
                callback(currentNickname)
            } catch (e: Exception) {
                callback("")
            }
        }
    }

    fun changeFormatDate(date: String): String? {
        return try {
            val d = SimpleDateFormat("MMMM-dd-yyyy", Locale.ENGLISH).parse(date)
            SimpleDateFormat("MMM-dd-yyyy", Locale.getDefault()).format(d!!)
        } catch (e: Exception) {
            date
        }
    }

    private fun shouldIncludeForStatus(s: String?, ss: String?): Boolean {
        if ("All" == ss) return true
        if ("Pending" == ss) return "Pending Payment" == s || "For Lender Approval" == s
        return s == ss
    }

    private fun shouldIncludeForDebtStatus(s: String?, ss: String?): Boolean {
        if ("All" == ss) return true
        if ("Pending" == ss) return "Pending Payment" == s || "For Lender Approval" == s || "Declined" == s
        return s == ss
    }

    private fun addOwedTransactionFromBorrowNow(
        bnt: BorrowNowTransaction,
        my: String?,
        d: String?,
        bid: String?
    ) {
        val dateLong = bnt.getDate()
        val date = SimpleDateFormat("MMM-dd-yyyy", Locale.getDefault()).format(java.util.Date(dateLong))
        
        val psdLong = bnt.getPaymentSentDate()
        val psd = if (psdLong > 0) SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH).format(java.util.Date(psdLong)) else null
        
        owedList.add(
            OwedTransaction(
                date,
                bnt.getBorrowerName() ?: "Unknown",
                bnt.getBorrowedAmount().toString(),
                bnt.getStatus(),
                psd,
                bid,
                my,
                d
            )
        )
    }

    fun getOwedListMonthly(
        sm: String?,
        ss: String?,
        callback: OwedNumCallback,
        actionListener: OwedTransactionAdapter.OnLenderActionListener?
    ) {
        if (sm == null || sm == "All") return
        lifecycleScope.launch {
            try {
                val uid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                val borrows = DeclareDatabase.borrowsTable.select {
                    filter { eq("month_year", sm) }
                }.decodeList<BorrowNowTransaction>()
                
                owedList.clear()
                for (bnt in borrows) {
                    if (bnt.lenderId == uid && bnt.getStatus() != "Declined" && bnt.getStatus() != "Payment Denied" && bnt.getStatus() != "Removed" && shouldIncludeForStatus(
                            bnt.getStatus(),
                            ss
                        )
                    ) {
                        addOwedTransactionFromBorrowNow(bnt, sm, null, bnt.id?.toString())
                    }
                }
                
                sortAndDisplayOwedList(actionListener)
                owedNum = owedList.size
                callback.onOwedNumReceived(owedNum)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting monthly owed list: ${e.message}")
            }
        }
    }
}
