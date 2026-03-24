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
    private var progressBar: ProgressBar? = null
    private var currentUserNumericId: Long? = null
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

        progressBar = findViewById(R.id.progressBar)
        progressBar?.visibility = View.VISIBLE
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
    }

    private fun setupFabMenu() {
        fabMain = findViewById(R.id.fab_main)
        fabMenuOverlay = findViewById(R.id.fab_menu_overlay)
        containerBorrow = findViewById(R.id.container_borrow)
        containerAddTransaction = findViewById(R.id.container_add_transaction)
        containerAddGroup = findViewById(R.id.container_add_group)

        fabMain?.setOnClickListener {
            toggleFabMenu()
        }

        fabMenuOverlay?.setOnClickListener {
            if (isFabMenuOpen) toggleFabMenu()
        }

        containerBorrow?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, BorrowNowActivity::class.java))
        }

        containerAddTransaction?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, MultiTransactionActivity::class.java))
        }

        containerAddGroup?.setOnClickListener {
            toggleFabMenu()
            showAddGroupDialog()
        }
    }

    private fun showAddGroupDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_create_group)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
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

        // Curved horizontal layout: 150, 90, 30 degrees (from left to right)
        showFabOption(containerBorrow, 150)
        showFabOption(containerAddTransaction, 90)
        showFabOption(containerAddGroup, 30)
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        fabMain?.setImageResource(R.drawable.baseline_add_24)
        fabMenuOverlay?.animate()?.alpha(0f)?.setDuration(300)?.withEndAction {
            fabMenuOverlay?.visibility = View.GONE
        }?.start()

        fabMain?.animate()?.rotation(0f)?.setDuration(300)?.start()

        hideFabOption(containerBorrow)
        hideFabOption(containerAddTransaction)
        hideFabOption(containerAddGroup)
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
                progressBar?.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching user details: ${e.message}")
                progressBar?.visibility = View.GONE
            }
        }
    }

    fun getRecentTransactions(onComplete: () -> Unit) {
        // Implementation placeholder
        onComplete()
    }

    fun getTotalMonthSpends(monthYear: String? = null, onComplete: (Double) -> Unit) {
        val targetMonthYear = monthYear ?: SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        lifecycleScope.launch {
            try {
                // Fetch all and filter locally because monthYear is @Transient
                val result = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()

                var total = 0.0
                for (transaction in result) {
                    if (isUserInvolved(transaction, currentNickname) && transaction.monthYear == targetMonthYear) {
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
                val currentUserIdLong = currentUserNumericId
                if (currentUserIdLong == null) {
                    callback.onDebtNumReceived(0)
                    return@launch
                }
                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() {
                        filter {
                            eq("borrower_id", currentUserIdLong)
                        }
                    }
                    .decodeList<BorrowNowTransaction>()

                val list = ArrayList<BorrowTransaction>()
                for (b in result) {
                    if (b.monthYear == monthYear && (status == "All" || b.getStatus() == status)) {
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
                val currentUserIdLong = currentUserNumericId
                if (currentUserIdLong == null) {
                    callback.onOwedNumReceived(0)
                    return@launch
                }
                val result = DeclareDatabase.postgrest.from("borrows")
                    .select() {
                        filter {
                            eq("lender_id", currentUserIdLong)
                        }
                    }
                    .decodeList<BorrowNowTransaction>()

                val list = ArrayList<OwedTransaction>()
                for (b in result) {
                    if (b.monthYear == monthYear && (status == "All" || b.getStatus() == status)) {
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
        val sMillis = startMillis ?: now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val eMillis = endMillis ?: now.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis

        lifecycleScope.launch {
            try {
                val result = DeclareDatabase.postgrest.from("transactions")
                    .select()
                    .decodeList<Transaction>()

                val totals = DoubleArray(7) { 0.0 }
                for (transaction in result) {
                    val transTime = transaction.timestamp
                    if (transTime in sMillis..eMillis && isUserInvolved(transaction, currentNickname)) {
                        val transCal = Calendar.getInstance()
                        transCal.timeInMillis = transTime
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
