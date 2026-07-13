package com.waray.spendhound

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.waray.spendhound.ui.multi_transaction.MultiTransactionActivity
import com.waray.spendhound.utils.NetworkMonitor
import com.waray.spendhound.data.local.AppDatabase
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
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
    private var containerBorrowSub: LinearLayout? = null
    private var containerLendSub: LinearLayout? = null
    private var containerAddTransaction: LinearLayout? = null
    private var containerAddGroup: LinearLayout? = null
    private var containerSettlement: LinearLayout? = null
    private var isFabMenuOpen = false

    private var isBorrowSubMenuOpen = false
    private var selectedLenderName = ""

    companion object {
        private const val REQUEST_CODE_BORROW = 1001
    }

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
        Log.d("MainActivity", "onCreate: START")
        val binding = com.waray.spendhound.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("APP_CHECK", "APP STARTED");

        try {
            Log.d("MainActivity", "onCreate: Accessing DeclareDatabase.auth")
            mAuth = DeclareDatabase.auth
            
            val currentSupabaseUser = mAuth?.currentUserOrNull()
            Log.d("MainActivity", "onCreate: Current user from Supabase is ${currentSupabaseUser?.id}")
            if (currentSupabaseUser != null) {
                currentUserId = currentSupabaseUser.id
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate: Failed to access auth: ${e.message}", e)
        }

        // Initialize view components
        Log.d("MainActivity", "onCreate: Initializing views")
        navView = findViewById(R.id.navView)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        setupWithNavController(navView!!, navController)

        setupFabMenu()
        fetchCurrentUserDetails()
        setupNetworkObserver()
        observePendingCount()

        navView?.setOnItemSelectedListener { item ->
            Log.d("MainActivity", "onItemSelected: ${item.itemId}")
            if (isFabMenuOpen) closeFabMenu()
            val handled = NavigationUI.onNavDestinationSelected(item, navController)
            if (item.itemId == R.id.navigation_profile) {
                val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment
                (fragment as? com.waray.spendhound.ui.profile.ProfileFragment)?.loadNicknameAndData()
            }
            handled
        }

        navView?.setOnItemReselectedListener { item ->
            Log.d("MainActivity", "onItemReselected: ${item.itemId}")
            val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment
            when (item.itemId) {
                R.id.navigation_home -> (fragment as? com.waray.spendhound.ui.home.HomeFragment)?.refreshAllData()
                R.id.navigation_transactions -> (fragment as? com.waray.spendhound.ui.transactions.TransactionsFragment)?.refreshTransactions()
                R.id.navigation_borrow -> (fragment as? com.waray.spendhound.ui.borrow.BorrowFragment)?.applyFilters()
                R.id.navigation_profile -> (fragment as? com.waray.spendhound.ui.profile.ProfileFragment)?.loadNicknameAndData()
            }
        }
        Log.d("MainActivity", "onCreate: FINISH")
    }

    private fun setupNetworkObserver() {
        val banner = findViewById<LinearLayout>(R.id.offlineBanner)
        val bannerIcon = banner.findViewById<ImageView>(R.id.bannerIcon)
        val bannerText = banner.findViewById<TextView>(R.id.bannerText)
        var wasOffline = false

        lifecycleScope.launch {
            NetworkMonitor.isOnline.collectLatest { isOnline ->
                try {
                    if (!isOnline) {
                        wasOffline = true
                        banner.visibility = View.VISIBLE
                        banner.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.orange))
                        bannerIcon.setImageResource(R.drawable.ic_wifi_off)
                        bannerText.text = "You're offline — showing cached data"
                        
                        banner.post {
                            if (banner.height > 0) {
                                banner.translationY = -banner.height.toFloat()
                                banner.animate()
                                    .translationY(0f)
                                    .setDuration(300)
                                    .setInterpolator(DecelerateInterpolator())
                                    .start()
                            } else {
                                banner.translationY = 0f
                            }
                        }
                    } else if (wasOffline) {
                        wasOffline = false
                        banner.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.green))
                        bannerIcon.setImageResource(R.drawable.check)
                        bannerText.text = "Back online"
                        
                        delay(2000)
                        banner.animate()
                            .translationY(-banner.height.toFloat())
                            .setDuration(300)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction { banner.visibility = View.GONE }
                            .start()
                    } else {
                        banner.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in network observer: ${e.message}")
                }
            }
        }
    }

    private fun observePendingCount() {
        val database = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            database.pendingTransactionDao().getCount().collectLatest { count ->
                navView?.let { nav ->
                    if (count > 0) {
                        val badge = nav.getOrCreateBadge(R.id.navigation_transactions)
                        badge.isVisible = true
                        badge.number = count
                        badge.backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.orange)
                        badge.badgeTextColor = ContextCompat.getColor(this@MainActivity, R.color.whitest)
                    } else {
                        nav.removeBadge(R.id.navigation_transactions)
                    }
                }
            }
        }
    }

    private fun setupFabMenu() {
        fabMain = findViewById(R.id.fab_main)
        fabMenuOverlay = findViewById(R.id.fab_menu_overlay)
        containerBorrow = findViewById(R.id.container_borrow)
        containerBorrowSub = findViewById(R.id.container_borrow_sub)
        containerLendSub = findViewById(R.id.container_lend_sub)
        containerAddTransaction = findViewById(R.id.container_add_transaction)
        containerAddGroup = findViewById(R.id.container_add_group)
        containerSettlement = findViewById(R.id.container_settlement)

        fabMain?.setOnClickListener { toggleFabMenu() }
        fabMenuOverlay?.setOnClickListener { if (isFabMenuOpen) toggleFabMenu() }

        val fabBorrow = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_borrow)
        val fabBorrowSub = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_borrow_sub)
        val fabLendSub = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_lend_sub)
        val fabAddTransaction = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_transaction)
        val fabAddGroup = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_group)
        val fabSettlement = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_settlement)

        fabBorrow?.setOnClickListener {
            if (isBorrowSubMenuOpen) closeBorrowSubMenu()
            else openBorrowSubMenu()
        }

        fabBorrowSub?.setOnClickListener {
            toggleFabMenu()
            val intent = Intent(this, BorrowNowActivity::class.java)
            intent.putExtra("BORROW_MODE", "BORROW")
            startActivityForResult(intent, REQUEST_CODE_BORROW)
        }

        fabLendSub?.setOnClickListener {
            toggleFabMenu()
            val intent = Intent(this, BorrowNowActivity::class.java)
            intent.putExtra("BORROW_MODE", "LEND")
            startActivityForResult(intent, REQUEST_CODE_BORROW)
        }

        fabAddTransaction?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, MultiTransactionActivity::class.java))
        }

        fabAddGroup?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, GroupsActivity::class.java))
        }

        fabSettlement?.setOnClickListener {
            toggleFabMenu()
            startActivity(Intent(this, SettlementActivity::class.java))
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
        showFabOption(containerBorrow, 157)
        showFabOption(containerAddTransaction, 113)
        showFabOption(containerSettlement, 67)
        showFabOption(containerAddGroup, 23)
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        isBorrowSubMenuOpen = false
        fabMain?.setImageResource(R.drawable.baseline_add_24)
        fabMenuOverlay?.animate()?.alpha(0f)?.setDuration(300)?.withEndAction {
            fabMenuOverlay?.visibility = View.GONE
        }?.start()
        fabMain?.animate()?.rotation(0f)?.setDuration(300)?.start()
        hideFabOption(containerBorrow)
        hideFabOption(containerAddTransaction)
        hideFabOption(containerSettlement)
        hideFabOption(containerAddGroup)
        hideFabOption(containerBorrowSub)
        hideFabOption(containerLendSub)
    }



    private fun openBorrowSubMenu() {
        isBorrowSubMenuOpen = true
        // container_borrow sits at angle 157°
        val radius = 300f
        val angleRadians = Math.toRadians(157.0)
        val baseX = (radius * cos(angleRadians)).toFloat()
        val baseY = -(radius * sin(angleRadians)).toFloat()
        
        // Stack sub-FABs vertically aligned with fab_borrow
        val verticalSpacing = 180f
        showFabOptionAt(containerBorrowSub, baseX, baseY - verticalSpacing)
        showFabOptionAt(containerLendSub, baseX, baseY - (verticalSpacing * 2))
    }

    private fun closeBorrowSubMenu() {
        isBorrowSubMenuOpen = false
        hideFabOption(containerBorrowSub)
        hideFabOption(containerLendSub)
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
        val authId = currentUserId
        Log.d("MainActivity", "fetchCurrentUserDetails: authId=$authId")
        lifecycleScope.launch {
            try {
                // If we have an authId, try to fetch fresh details
                if (authId != null) {
                    Log.d("MainActivity", "fetchCurrentUserDetails: Fetching from network")
                    val user = try {
                        DeclareDatabase.usersTable.select {
                            filter { eq("auth_id", authId) }
                        }.decodeSingleOrNull<User>()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "fetchCurrentUserDetails: Network fetch failed: ${e.message}", e)
                        null
                    }
                    
                    if (user != null) {
                        Log.d("MainActivity", "fetchCurrentUserDetails: Network success, user id=${user.id}")
                        currentUserNumericId = user.id
                        currentNickname = user.username
                        
                        // Cache user details for offline use
                        val id = user.id
                        if (id != null) {
                            val db = AppDatabase.getInstance(this@MainActivity)
                            db.jsonBlobDao().upsert(com.waray.spendhound.data.local.CachedJsonBlob("current_user_id", id.toString(), System.currentTimeMillis()))
                            db.jsonBlobDao().upsert(com.waray.spendhound.data.local.CachedJsonBlob("current_user_obj", kotlinx.serialization.json.Json.encodeToString(User.serializer(), user), System.currentTimeMillis()))
                            
                            // Warm up caches for all tabs
                            warmUpCaches(id, authId)
                        }
                        return@launch
                    }
                }

                // Fallback: Try to load from cache if network failed, offline, or authId is null
                Log.d("MainActivity", "fetchCurrentUserDetails: Loading from cache fallback")
                val db = AppDatabase.getInstance(this@MainActivity)
                val cachedId = db.jsonBlobDao().get("current_user_id")
                Log.d("MainActivity", "fetchCurrentUserDetails: Cached ID found=${cachedId != null}")
                if (cachedId != null) {
                    val id = cachedId.json.toLongOrNull()
                    currentUserNumericId = id
                    
                    // Warm up caches from existing cache even if offline
                    if (id != null) {
                        warmUpCaches(id, authId ?: "")
                    }
                }
                val cachedUser = db.jsonBlobDao().get("current_user_obj")
                Log.d("MainActivity", "fetchCurrentUserDetails: Cached User Object found=${cachedUser != null}")
                if (cachedUser != null) {
                    try {
                        val userObj = kotlinx.serialization.json.Json.decodeFromString(User.serializer(), cachedUser.json)
                        currentNickname = userObj.username
                        Log.d("MainActivity", "fetchCurrentUserDetails: Resolved nickname ${currentNickname}")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "fetchCurrentUserDetails: Cache decode error: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "fetchCurrentUserDetails: Fatal error: ${e.message}", e)
            }
        }
    }

    private fun warmUpCaches(userId: Long, authId: String) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("MainActivity", "warmUpCaches: Starting background warm-up for user $userId")
                val db = AppDatabase.getInstance(this@MainActivity)
                
                // Home and Transactions (Recent)
                val homeRepo = com.waray.spendhound.data.repository.HomeRepository(db)
                val txRepo = com.waray.spendhound.data.repository.TransactionRepository(db)
                
                // Collect at least one item from each flow to trigger the cache logic (fetch fresh if stale)
                try {
                    homeRepo.getHomeData(userId).firstOrNull()
                } catch (e: Exception) { Log.w("MainActivity", "Warmup home data failed: ${e.message}") }
                
                try {
                    txRepo.getRecentTransactions(userId).firstOrNull()
                } catch (e: Exception) { Log.w("MainActivity", "Warmup recent tx failed: ${e.message}") }
                
                try {
                    txRepo.getTransactions(userId).firstOrNull()
                } catch (e: Exception) { Log.w("MainActivity", "Warmup all tx failed: ${e.message}") }
                
                // Borrow data
                val borrowRepo = com.waray.spendhound.data.repository.BorrowRepository(db)
                try {
                    borrowRepo.getBorrowData(userId).firstOrNull()
                } catch (e: Exception) { Log.w("MainActivity", "Warmup borrow data failed: ${e.message}") }
                
                // Profile data
                if (authId.isNotEmpty()) {
                    val profileRepo = com.waray.spendhound.data.repository.ProfileRepository(db)
                    try {
                        profileRepo.getProfile(userId, authId).firstOrNull()
                    } catch (e: Exception) { Log.w("MainActivity", "Warmup profile failed: ${e.message}") }
                    
                    try {
                        profileRepo.getProfileGroups(userId).firstOrNull()
                    } catch (e: Exception) { Log.w("MainActivity", "Warmup profile groups failed: ${e.message}") }
                }
                
                Log.d("MainActivity", "warmUpCaches: Warm-up complete")
            } catch (e: Exception) {
                Log.e("MainActivity", "warmUpCaches: Error during warm-up: ${e.message}")
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_BORROW && resultCode == RESULT_OK) {
            refreshCurrentFragment()
        }
    }

    private fun refreshCurrentFragment() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment
        when (navView?.selectedItemId) {
            R.id.navigation_home -> (fragment as? com.waray.spendhound.ui.home.HomeFragment)?.refreshAllData()
            R.id.navigation_transactions -> (fragment as? com.waray.spendhound.ui.transactions.TransactionsFragment)?.refreshTransactions()
            R.id.navigation_borrow -> (fragment as? com.waray.spendhound.ui.borrow.BorrowFragment)?.applyFilters()
            R.id.navigation_profile -> (fragment as? com.waray.spendhound.ui.profile.ProfileFragment)?.loadNicknameAndData()
        }
    }
}
