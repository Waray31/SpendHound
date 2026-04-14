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
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.waray.spendhound.ui.multi_transaction.MultiTransactionActivity
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    var navView: BottomNavigationView? = null
    private var mAuth: Auth? = null
    var totalMonthSpends: Double = 0.0
    var dailyTotals: DoubleArray = DoubleArray(7)
    internal var currentUserNumericId: Long? = null
    private var currentUserId: String? = null
    var currentNickname: String? = null
    
    var debtList: List<BorrowTransaction> = emptyList()
    var owedList: List<OwedTransaction> = emptyList()
    var debtNum: Int = 0
    var owedNum: Int = 0

    private var fabMain: FloatingActionButton? = null
    private var fabMenuOverlay: View? = null
    private var containerBorrow: LinearLayout? = null
    private var containerAddTransaction: LinearLayout? = null
    private var containerAddGroup: LinearLayout? = null
    private var containerSingleTransaction: LinearLayout? = null
    private var containerMultiTransaction: LinearLayout? = null
    private var isFabMenuOpen = false
    private var isTransactionSubMenuOpen = false
    private var selectedLenderName = ""

    interface OwedNumCallback {
        fun onOwedNumReceived(owedNum: Int)
    }

    interface DebtNumCallback {
        fun onDebtNumReceived(debtNum: Int)
    }

    fun isUserInvolved(
        transaction: Transaction?,
        usernameOrUserId: String?
    ): Boolean {
        if (transaction == null) return false
        val userId = mAuth?.currentUserOrNull()?.id
        return transaction.isUserInvolvedByUserId(userId) || (usernameOrUserId != null && transaction.isUserInvolvedByUsername(usernameOrUserId))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.waray.spendhound.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = DeclareDatabase.auth
        
        val currentSupabaseUser = mAuth?.currentUserOrNull()
        if (currentSupabaseUser != null) {
            currentUserId = currentSupabaseUser.id
        }

        // Initialize view components
        navView = findViewById(R.id.navView)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        setupWithNavController(navView!!, navController)

        setupFabMenu()
        fetchCurrentUserDetails()

        navView?.setOnItemSelectedListener { item ->
            if (isFabMenuOpen) closeFabMenu()
            NavigationUI.onNavDestinationSelected(item, navController)
        }

        navView?.setOnItemReselectedListener { item ->
            val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment
            when (item.itemId) {
                R.id.navigation_home -> (fragment as? com.waray.spendhound.ui.home.HomeFragment)?.refreshAllData()
                R.id.navigation_transactions -> (fragment as? com.waray.spendhound.ui.transactions.TransactionsFragment)?.refreshTransactions()
                R.id.navigation_borrow -> (fragment as? com.waray.spendhound.ui.borrow.BorrowFragment)?.applyFilters()
                R.id.navigation_profile -> (fragment as? com.waray.spendhound.ui.profile.ProfileFragment)?.loadNicknameAndData()
            }
        }
    }

    private fun setupFabMenu() {
        fabMain = findViewById(R.id.fab_main)
        fabMenuOverlay = findViewById(R.id.fab_menu_overlay)
        containerBorrow = findViewById(R.id.container_borrow)
        containerAddTransaction = findViewById(R.id.container_add_transaction)
        containerAddGroup = findViewById(R.id.container_add_group)
        containerSingleTransaction = findViewById(R.id.container_single_transaction)
        containerMultiTransaction = findViewById(R.id.container_multi_transaction)

        fabMain?.setOnClickListener { toggleFabMenu() }
        fabMenuOverlay?.setOnClickListener { if (isFabMenuOpen) toggleFabMenu() }

        val fabBorrow = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_borrow)
        val fabAddTransaction = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_transaction)
        val fabAddGroup = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_group)
        val fabSingle = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_single_transaction)
        val fabMulti = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_multi_transaction)

        fabBorrow?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, BorrowNowActivity::class.java))
        }

        fabAddTransaction?.setOnClickListener {
            if (isTransactionSubMenuOpen) closeTransactionSubMenu()
            else openTransactionSubMenu()
        }

        fabAddGroup?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, GroupsActivity::class.java))
        }

        fabSingle?.setOnClickListener {
            toggleFabMenu()
            val intent = Intent(this, MultiTransactionActivity::class.java)
            intent.putExtra("TRANSACTION_MODE", "SINGLE")
            startActivity(intent)
        }

        fabMulti?.setOnClickListener {
            toggleFabMenu()
            val intent = Intent(this, MultiTransactionActivity::class.java)
            intent.putExtra("TRANSACTION_MODE", "MULTIPLE")
            startActivity(intent)
        }
    }

    private fun toggleFabMenu() {
        if (isFabMenuOpen) closeFabMenu() else openFabMenu()
    }

    private fun openFabMenu() {
        isFabMenuOpen = true
        fabMain?.setImageResource(R.drawable.ic_close_24dp)
        fabMenuOverlay?.visibility = View.VISIBLE
        fabMenuOverlay?.animate()?.alpha(1f)?.setDuration(300)?.start()
        fabMain?.animate()?.rotation(45f)?.setDuration(300)?.start()
        showFabOption(containerBorrow, 150)
        showFabOption(containerAddTransaction, 90)
        showFabOption(containerAddGroup, 30)
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        isTransactionSubMenuOpen = false
        fabMain?.setImageResource(R.drawable.baseline_add_24)
        fabMenuOverlay?.animate()?.alpha(0f)?.setDuration(300)?.withEndAction {
            fabMenuOverlay?.visibility = View.GONE
        }?.start()
        fabMain?.animate()?.rotation(0f)?.setDuration(300)?.start()
        hideFabOption(containerBorrow)
        hideFabOption(containerAddTransaction)
        hideFabOption(containerAddGroup)
        hideFabOption(containerSingleTransaction)
        hideFabOption(containerMultiTransaction)
    }

    private fun openTransactionSubMenu() {
        isTransactionSubMenuOpen = true
        // container_add_transaction sits at angle 90° → translationX=0, translationY=-300
        // Sub-FABs branch left and right above it, adding extra upward offset
        val baseX = 0f
        val baseY = -300f
        val spread = 140f
        val extraUp = 260f
        showFabOptionAt(containerSingleTransaction, baseX - spread, baseY - extraUp)
        showFabOptionAt(containerMultiTransaction, baseX + spread, baseY - extraUp)
    }

    private fun closeTransactionSubMenu() {
        isTransactionSubMenuOpen = false
        hideFabOption(containerSingleTransaction)
        hideFabOption(containerMultiTransaction)
    }

    private fun showFabOption(view: LinearLayout?, angleDegrees: Int) {
        view?.visibility = View.VISIBLE
        view?.alpha = 0f
        
        val radius = 300f // distance from main FAB
        val angleRadians = Math.toRadians(angleDegrees.toDouble())
        val targetX = (radius * cos(angleRadians)).toFloat()
        val targetY = -(radius * sin(angleRadians)).toFloat()

        view?.translationX = 0f
        view?.translationY = 0f
        
        view?.animate()
            ?.alpha(1f)
            ?.translationX(targetX)
            ?.translationY(targetY)
            ?.setDuration(300)
            ?.start()
    }

    private fun showFabOptionAt(view: LinearLayout?, targetX: Float, targetY: Float) {
        view?.visibility = View.VISIBLE
        view?.alpha = 0f
        view?.translationX = 0f
        view?.translationY = 0f
        view?.animate()
            ?.alpha(1f)
            ?.translationX(targetX)
            ?.translationY(targetY)
            ?.setDuration(300)
            ?.start()
    }

    private fun hideFabOption(view: LinearLayout?) {
        view?.animate()
            ?.alpha(0f)
            ?.translationX(0f)
            ?.translationY(0f)
            ?.setDuration(300)
            ?.withEndAction { view?.visibility = View.GONE }
            ?.start()
    }

    private fun fetchCurrentUserDetails() {
        val authId = currentUserId ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                
                currentUserNumericId = user?.id
                currentNickname = user?.username
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching user details: ${e.message}")
            }
        }
    }

    fun getRecentTransactions(onComplete: () -> Unit) {
        // Implementation placeholder
        onComplete()
    }

    fun getTotalMonthSpends(monthYear: String? = null, onComplete: (Double) -> Unit) {
        val sdf = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
        val targetMonthYear = monthYear ?: sdf.format(Calendar.getInstance().time)
        lifecycleScope.launch {
            try {
                val allTransactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                val allSplits = DeclareDatabase.transactionSplitsTable.select()
                    .decodeList<com.waray.spendhound.ui.multi_transaction.TransactionSplitTable>()

                val currentUserIdLong = currentUserNumericId
                val involvedTxIds = allSplits
                    .filter { it.userId == currentUserIdLong }
                    .map { it.transactionId }.toSet()

                var total = 0.0
                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in involvedTxIds) continue
                    // parse createdAt to check month-year
                    val parsedDate = try {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(tx.createdAt ?: "")
                    } catch (e: Exception) { null } ?: continue
                    if (sdf.format(parsedDate) != targetMonthYear) continue
                    // sum only this user's split for the transaction
                    val userSplit = allSplits
                        .filter { it.transactionId == txId && it.userId == currentUserIdLong }
                        .sumOf { it.amount }
                    total += userSplit
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
                val currentUserIdLong = currentUserNumericId
                if (currentUserIdLong == null) {
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
                            eq("borrower_id", currentUserIdLong)
                            if (statusInt > 0) eq("status", statusInt)
                        }
                    }.decodeList<BorrowNowTransaction>()

                val lenderIds = result.mapNotNull { it.lenderId }.distinct()
                val usersById = fetchUsernamesById(lenderIds)

                val list = ArrayList<BorrowTransaction>()
                for (b in result) {
                    list.add(BorrowTransaction(
                        date = b.createdAt,
                        borrowee = b.lenderId?.toString(),
                        borrowedAmountStr = b.borrowedAmount?.toString(),
                        status = b.getStatus(),
                        borroweeDisplayName = usersById[b.lenderId] ?: b.lenderId?.toString(),
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
                val currentUserIdLong = currentUserNumericId
                if (currentUserIdLong == null) {
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
                            eq("lender_id", currentUserIdLong)
                            if (statusInt > 0) eq("status", statusInt)
                        }
                    }.decodeList<BorrowNowTransaction>()

                val borrowerIds = result.mapNotNull { it.borrowerId }.distinct()
                val usersById = fetchUsernamesById(borrowerIds)

                val list = ArrayList<OwedTransaction>()
                for (b in result) {
                    list.add(OwedTransaction(
                        date = b.createdAt,
                        borrower = usersById[b.borrowerId] ?: b.borrowerId?.toString(),
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
                val currentUserIdLong = currentUserNumericId
                if (currentUserIdLong == null) {
                    callback.onDebtNumReceived(0)
                    return@launch
                }
                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() { filter { eq("borrower_id", currentUserIdLong) } }
                    .decodeList<BorrowNowTransaction>()

                val lenderIds = result.mapNotNull { it.lenderId }.distinct()
                val usersById = fetchUsernamesById(lenderIds)

                val list = ArrayList<BorrowTransaction>()
                for (b in result) {
                    if (b.monthYear == monthYear && (status == "All" || b.getStatus() == status)) {
                        list.add(BorrowTransaction(
                            date = b.createdAt,
                            borrowee = b.lenderId?.toString(),
                            borrowedAmountStr = b.borrowedAmount?.toString(),
                            status = b.getStatus(),
                            borroweeDisplayName = usersById[b.lenderId] ?: b.lenderId?.toString(),
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
                val currentUserIdLong = currentUserNumericId
                if (currentUserIdLong == null) {
                    callback.onOwedNumReceived(0)
                    return@launch
                }
                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() { filter { eq("lender_id", currentUserIdLong) } }
                    .decodeList<BorrowNowTransaction>()

                val borrowerIds = result.mapNotNull { it.borrowerId }.distinct()
                val usersById = fetchUsernamesById(borrowerIds)

                val list = ArrayList<OwedTransaction>()
                for (b in result) {
                    if (b.monthYear == monthYear && (status == "All" || b.getStatus() == status)) {
                        list.add(OwedTransaction(
                            date = b.createdAt,
                            borrower = usersById[b.borrowerId] ?: b.borrowerId?.toString(),
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

    private suspend fun fetchUsernamesById(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        return try {
            DeclareDatabase.usersTable.select {
                filter { isIn("user_id", ids) }
            }.decodeList<User>().associate { it.id!! to (it.username ?: it.id.toString()) }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error fetching usernames: ${e.message}")
            emptyMap()
        }
    }

    fun getCurrentNickname(callback: (String?) -> Unit) {
        if (!currentNickname.isNullOrEmpty()) {
            callback(currentNickname)
            return
        }
        val authId = currentUserId ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                currentNickname = user?.username
                callback(currentNickname)
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    fun getEverydaySpends(startMillis: Long? = null, endMillis: Long? = null, onComplete: () -> Unit) {
        val now = Calendar.getInstance()
        val weekStart = now.clone() as Calendar
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        weekStart.set(Calendar.HOUR_OF_DAY, 0); weekStart.set(Calendar.MINUTE, 0)
        weekStart.set(Calendar.SECOND, 0); weekStart.set(Calendar.MILLISECOND, 0)
        val weekEnd = weekStart.clone() as Calendar
        weekEnd.add(Calendar.DAY_OF_YEAR, 6)
        weekEnd.set(Calendar.HOUR_OF_DAY, 23); weekEnd.set(Calendar.MINUTE, 59); weekEnd.set(Calendar.SECOND, 59)

        val sMillis = startMillis ?: weekStart.timeInMillis
        val eMillis = endMillis ?: weekEnd.timeInMillis

        lifecycleScope.launch {
            try {
                val allTransactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                val allSplits = DeclareDatabase.transactionSplitsTable.select()
                    .decodeList<com.waray.spendhound.ui.multi_transaction.TransactionSplitTable>()

                val currentUserIdLong = currentUserNumericId
                val userSplitsByTx = allSplits
                    .filter { it.userId == currentUserIdLong }
                    .groupBy { it.transactionId }

                val totals = DoubleArray(7) { 0.0 }
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in userSplitsByTx) continue
                    val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                    if (timestamp !in sMillis..eMillis) continue

                    val transCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val index = transCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun..6=Sat
                    val userSplit = userSplitsByTx[txId]?.sumOf { it.amount } ?: 0.0
                    totals[index] += userSplit
                }
                dailyTotals = totals
                onComplete()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching everyday spends: ${e.message}")
                onComplete()
            }
        }
    }

    fun getEverydaySpendsForWeek(calendar: Calendar, onComplete: () -> Unit) {
        val start = calendar.clone() as Calendar
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        
        val end = calendar.clone() as Calendar
        end.add(Calendar.DAY_OF_YEAR, 6)
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        
        getEverydaySpends(start.timeInMillis, end.timeInMillis, onComplete)
    }

    fun hideNavigation() {
        navView?.visibility = View.GONE
    }

    fun unhideNavigation() {
        navView?.visibility = View.VISIBLE
    }
}
