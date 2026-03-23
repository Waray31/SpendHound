package com.waray.spendhound

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
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    var navView: BottomNavigationView? = null
    private var mAuth: Auth? = null
    var totalMonthSpends: Double = 0.0
    var dailyTotals: DoubleArray = DoubleArray(7)
    private var progressBar: ProgressBar? = null
    var currentNickname: String? = ""
    var owedNum: Int = 0
    var debtNum: Int = 0
    private var recentTransactionList = ArrayList<RecentTransaction>()
    var debtList: ArrayList<BorrowTransaction> = ArrayList()
    var owedList: ArrayList<OwedTransaction> = ArrayList()
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

    fun isUserInvolved(
        transaction: Transaction?,
        usernameOrUid: String?
    ): Boolean {
        if (transaction == null || usernameOrUid.isNullOrEmpty()) return false
        val currentUid = mAuth?.currentUserOrNull()?.id
        return transaction.isUserInvolvedByUid(currentUid) || transaction.isUserInvolvedByUsername(usernameOrUid)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.waray.spendhound.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressBar = findViewById(R.id.progressBar)
        progressBar?.visibility = View.VISIBLE
        mAuth = DeclareDatabase.auth
        
        lifecycleScope.launch {
            UserHelper.preloadAllUsers()
            progressBar?.visibility = View.GONE
        }

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
            if (destination.id == R.id.navigation_home || 
                destination.id == R.id.navigation_transactions || 
                destination.id == R.id.navigation_borrow || 
                destination.id == R.id.navigation_profile) {
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
                            val d = min(d1, abs(midpoint - childMidpoint))
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

                    val amount = amountStr.toDoubleOrNull() ?: 0.0
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
        val currentUser = mAuth?.currentUserOrNull() ?: return

        lifecycleScope.launch {
            try {
                val calendar = Calendar.getInstance()
                val monthYear = SimpleDateFormat("MMMM-yyyy", Locale.ENGLISH).format(calendar.time)
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
                val createdAt = sdf.format(calendar.time)

                val borrowTransaction = BorrowNowTransaction(
                    borrowedAmount = amount,
                    borrowerId = currentUser.id.toLongOrNull(),
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

    fun getRecentTransactions(onComplete: (ArrayList<RecentTransaction>) -> Unit) {
        lifecycleScope.launch {
            try {
                val currentUid = mAuth?.currentUserOrNull()?.id
                if (currentUid == null) {
                    onComplete(ArrayList())
                    return@launch
                }
                val result = DeclareDatabase.postgrest.from("transactions")
                    .select() {
                        order("timestamp", Order.DESCENDING)
                    }
                    .decodeList<Transaction>()

                val recentList = ArrayList<RecentTransaction>()
                for (transaction in result) {
                    if (transaction.isUserInvolvedByUid(currentUid)) {
                        val rt = RecentTransaction(
                            mostRecentDate = transaction.monthYear,
                            mostRecentTransactionType = transaction.transactionType,
                            mostRecentDetails = transaction.multilineStr,
                            mostRecentPaymentAmountStr = transaction.paymentAmount.toString(),
                            iconResource = R.drawable.plus,
                            sortDateTime = transaction.timestamp.toString(),
                            payorsList = transaction.payorsList,
                            amountsPaidList = transaction.amountsPaidList,
                            fullDateWithYear = "${transaction.day}-${transaction.monthYear}",
                            createdBy = transaction.posterDisplayName,
                            createdByUid = transaction.usernamePost
                        )
                        recentList.add(rt)
                    }
                }
                recentTransactionList = recentList
                onComplete(recentList)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching recent transactions: ${e.message}")
                onComplete(ArrayList())
            }
        }
    }

    fun getTotalMonthSpends(onComplete: (Double) -> Unit) {
        lifecycleScope.launch {
            try {
                val currentUid = mAuth?.currentUserOrNull()?.id
                if (currentUid == null) {
                    onComplete(0.0)
                    return@launch
                }
                val calendar = Calendar.getInstance()
                val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.ENGLISH).format(calendar.time)
                
                val result = DeclareDatabase.postgrest.from("transactions")
                    .select() {
                        filter {
                            eq("monthYear", currentMonthYear)
                        }
                    }
                    .decodeList<Transaction>()

                var total = 0.0
                for (transaction in result) {
                    if (transaction.usernamePost == currentUid) {
                        total += transaction.paymentAmount
                    }
                }
                totalMonthSpends = total
                onComplete(total)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error calculating total month spends: ${e.message}")
                onComplete(0.0)
            }
        }
    }

    fun getDebtList(status: String, callback: DebtNumCallback) {
        lifecycleScope.launch {
            try {
                val currentUid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                if (currentUid == null) {
                    callback.onDebtNumReceived(0)
                    return@launch
                }
                val statusInt = when (status) {
                    "Pending" -> 2
                    "Paid" -> 3
                    else -> 0
                }
                
                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() {
                        filter {
                            eq("borrower_id", currentUid)
                            if (statusInt > 0) eq("status", statusInt)
                        }
                    }
                    .decodeList<BorrowNowTransaction>()

                val list = ArrayList<BorrowTransaction>()
                for (b in result) {
                    list.add(BorrowTransaction(
                        date = b.createdAt,
                        borrowee = b.lenderId?.toString(),
                        borrowedAmountStr = b.borrowedAmount?.toString(),
                        status = b.getStatus(),
                        borroweeDisplayName = b.lender,
                        paymentSentDate = b.paymentSentDate,
                        borrowId = b.id?.toString(),
                        monthYear = b.monthYear,
                        day = null
                    ))
                }
                debtList = list
                debtNum = list.size
                callback.onDebtNumReceived(debtNum)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching debt list: ${e.message}")
                callback.onDebtNumReceived(0)
            }
        }
    }

    fun getOwedList(status: String, callback: OwedNumCallback) {
        lifecycleScope.launch {
            try {
                val currentUid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                if (currentUid == null) {
                    callback.onOwedNumReceived(0)
                    return@launch
                }
                val statusInt = when (status) {
                    "Pending" -> 2
                    "Paid" -> 3
                    else -> 0
                }

                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() {
                        filter {
                            eq("lender_id", currentUid)
                            if (statusInt > 0) eq("status", statusInt)
                        }
                    }
                    .decodeList<BorrowNowTransaction>()

                val list = ArrayList<OwedTransaction>()
                for (b in result) {
                    list.add(OwedTransaction(
                        date = b.createdAt,
                        borrower = b.borrowerName,
                        borrowedAmountStr = b.borrowedAmount?.toString(),
                        status = b.getStatus(),
                        paymentSentDate = b.paymentSentDate,
                        borrowId = b.id?.toString(),
                        monthYear = b.monthYear,
                        day = null
                    ))
                }
                owedList = list
                owedNum = list.size
                callback.onOwedNumReceived(owedNum)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching owed list: ${e.message}")
                callback.onOwedNumReceived(0)
            }
        }
    }

    fun getDebtListMonthly(monthYear: String, status: String, callback: DebtNumCallback) {
        lifecycleScope.launch {
            try {
                val currentUid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                if (currentUid == null) {
                    callback.onDebtNumReceived(0)
                    return@launch
                }
                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() {
                        filter {
                            eq("borrower_id", currentUid)
                            eq("monthYear", monthYear)
                        }
                    }
                    .decodeList<BorrowNowTransaction>()

                val list = ArrayList<BorrowTransaction>()
                for (b in result) {
                    if (status == "All" || b.getStatus() == status) {
                        list.add(BorrowTransaction(
                            date = b.createdAt,
                            borrowee = b.lenderId?.toString(),
                            borrowedAmountStr = b.borrowedAmount?.toString(),
                            status = b.getStatus(),
                            borroweeDisplayName = b.lender,
                            paymentSentDate = b.paymentSentDate,
                            borrowId = b.id?.toString(),
                            monthYear = b.monthYear,
                            day = null
                        ))
                    }
                }
                debtList = list
                callback.onDebtNumReceived(list.size)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching monthly debt list: ${e.message}")
                callback.onDebtNumReceived(0)
            }
        }
    }

    fun getOwedListMonthly(monthYear: String, status: String, callback: OwedNumCallback) {
        lifecycleScope.launch {
            try {
                val currentUid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
                if (currentUid == null) {
                    callback.onOwedNumReceived(0)
                    return@launch
                }
                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() {
                        filter {
                            eq("lender_id", currentUid)
                            eq("monthYear", monthYear)
                        }
                    }
                    .decodeList<BorrowNowTransaction>()

                val list = ArrayList<OwedTransaction>()
                for (b in result) {
                    if (status == "All" || b.getStatus() == status) {
                        list.add(OwedTransaction(
                            date = b.createdAt,
                            borrower = b.borrowerName,
                            borrowedAmountStr = b.borrowedAmount?.toString(),
                            status = b.getStatus(),
                            paymentSentDate = b.paymentSentDate,
                            borrowId = b.id?.toString(),
                            monthYear = b.monthYear,
                            day = null
                        ))
                    }
                }
                owedList = list
                callback.onOwedNumReceived(list.size)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching monthly owed list: ${e.message}")
                callback.onOwedNumReceived(0)
            }
        }
    }

    fun getCurrentNickname(callback: (String?) -> Unit) {
        if (!currentNickname.isNullOrEmpty()) {
            callback(currentNickname)
            return
        }
        val uid = mAuth?.currentUserOrNull()?.id?.toLongOrNull()
        if (uid != null) {
            UserHelper.getUsernameById(uid, object : UserHelper.UsernameCallback {
                override fun onUsernameRetrieved(username: String?) {
                    currentNickname = username
                    callback(username)
                }
                override fun onError(error: String?) {
                    callback(null)
                }
            })
        } else {
            callback(null)
        }
    }

    fun getEverydaySpends(onComplete: () -> Unit) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        getEverydaySpendsForWeek(calendar, onComplete)
    }

    fun getEverydaySpendsForWeek(startDate: Calendar, onComplete: () -> Unit) {
        lifecycleScope.launch {
            try {
                val currentUid = mAuth?.currentUserOrNull()?.id
                if (currentUid == null) {
                    onComplete()
                    return@launch
                }
                val startMillis = startDate.timeInMillis
                val endCalendar = startDate.clone() as Calendar
                endCalendar.add(Calendar.DAY_OF_YEAR, 7)
                val endMillis = endCalendar.timeInMillis

                val result = DeclareDatabase.postgrest.from("transactions")
                    .select() {
                        filter {
                            gte("timestamp", startMillis)
                            lt("timestamp", endMillis)
                        }
                    }
                    .decodeList<Transaction>()

                val totals = DoubleArray(7) { 0.0 }
                for (transaction in result) {
                    if (isUserInvolved(transaction, currentUid)) {
                        val transCal = Calendar.getInstance()
                        transCal.timeInMillis = transaction.timestamp
                        val dayOfWeek = transCal.get(Calendar.DAY_OF_WEEK)
                        val index = dayOfWeek - 1
                        if (index in 0..6) {
                            totals[index] += transaction.paymentAmount
                        }
                    }
                }
                dailyTotals = totals
                onComplete()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching everyday spends: ${e.message}")
                onComplete()
            }
        }
    }
}
