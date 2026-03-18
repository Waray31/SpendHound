package com.waray.spendhound

import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.onNavDestinationSelected
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
import java.util.UUID

class MainActivity : AppCompatActivity() {
    var navView: BottomNavigationView? = null
    private var mAuth: Auth? = null
    var totalMonthSpends: Double = 0.0
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

        val currentUserId = mAuth?.currentUserOrNull()?.id
        BalanceHelper.ensureBalancesExist(currentUserId, null)

        getCurrentNickname { nickname -> }

        navView = findViewById(R.id.navView)
        val recyclerView: RecyclerView? = findViewById(R.id.transactionListRecycler)
        recentTransactionList = ArrayList()
        recentTransactionAdapter = RecentTransactionAdapter(
            recentTransactionList as ArrayList<RecentTransaction>,
            RecentTransactionAdapter.OnTransactionClickListener { transaction ->
                this.onTransactionTap(transaction!!)
            })
        if (recyclerView != null) {
            recyclerView.adapter = recentTransactionAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        fabMain = findViewById(R.id.fab_main)
        fabMenuOverlay = findViewById(R.id.fab_menu_overlay)
        containerBorrow = findViewById(R.id.container_borrow)
        containerAddTransaction = findViewById(R.id.container_add_transaction)
        containerAddGroup = findViewById(R.id.container_add_group)

        fabMain?.setOnClickListener { toggleFabMenu() }
        fabMenuOverlay?.setOnClickListener { collapseFabMenu() }

        findViewById<View>(R.id.fab_borrow).setOnClickListener {
            collapseFabMenu()
            showBorrowNowDialog()
        }
        findViewById<View>(R.id.fab_add_transaction).setOnClickListener {
            collapseFabMenu()
            startActivity(Intent(this@MainActivity, AddTransactionActivity::class.java))
        }
        findViewById<View>(R.id.fab_add_group).setOnClickListener {
            collapseFabMenu()
            showCreateGroupDialog()
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment?
        if (navHostFragment != null) {
            val navController = navHostFragment.navController
            setupWithNavController(navView!!, navController)
            navView?.setOnItemSelectedListener { item ->
                if (isFabMenuOpen) collapseFabMenu()
                onNavDestinationSelected(item, navController)
            }

            navController.addOnDestinationChangedListener { _, destination, _ ->
                val id = destination.id
                val hideOnScroll = (id != R.id.navigation_borrow && id != R.id.navigation_profile)
                updateBottomViewBehavior(hideOnScroll)
            }
        }
        progressBar?.visibility = View.GONE
    }

    fun onTransactionTap(transaction: RecentTransaction) {
        if (!transaction.isExpanded) {
            unhideNavigation()
        }
    }

    fun unhideNavigation() {
        navView?.let { view ->
            val layoutParams = view.layoutParams
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val behavior = layoutParams.behavior as? HideBottomViewOnScrollBehavior<BottomNavigationView>
                behavior?.slideUp(view)
            }
        }

        findViewById<View>(R.id.fab_main_layout)?.let { view ->
            val layoutParams = view.layoutParams
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val behavior = layoutParams.behavior as? HideBottomViewOnScrollBehavior<View>
                behavior?.slideUp(view)
            }
        }

        fabMain?.let { view ->
            val layoutParams = view.layoutParams
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val behavior = layoutParams.behavior as? HideBottomViewOnScrollBehavior<FloatingActionButton>
                behavior?.slideUp(view)
            }
        }
    }

    private fun updateBottomViewBehavior(hideOnScroll: Boolean) {
        val fabMainLayout = findViewById<View>(R.id.fab_main_layout)
        updateViewBehavior(navView, hideOnScroll)
        updateViewBehavior(fabMain, hideOnScroll)
        updateViewBehavior(fabMainLayout, hideOnScroll)
    }

    private fun updateViewBehavior(view: View?, hideOnScroll: Boolean) {
        if (view == null) return
        val layoutParams = view.layoutParams
        if (layoutParams is CoordinatorLayout.LayoutParams) {
            if (hideOnScroll) {
                if (layoutParams.behavior !is HideBottomViewOnScrollBehavior<*>) {
                    layoutParams.behavior = HideBottomViewOnScrollBehavior<View>()
                    view.translationY = 0f
                    view.layoutParams = layoutParams
                }
            } else {
                if (layoutParams.behavior != null) {
                    layoutParams.behavior = null
                    view.translationY = 0f
                    view.layoutParams = layoutParams
                }
            }
        }
    }

    private fun toggleFabMenu() {
        if (isFabMenuOpen) collapseFabMenu()
        else expandFabMenu()
    }

    private fun expandFabMenu() {
        if (isFabMenuOpen) return
        isFabMenuOpen = true

        fabMenuOverlay?.visibility = View.VISIBLE
        fabMenuOverlay?.animate()?.cancel()
        fabMenuOverlay?.animate()?.alpha(1f)?.setDuration(300)?.setListener(null)?.start()

        fabMain?.animate()?.cancel()
        fabMain?.animate()?.rotation(45f)?.setDuration(300)?.setListener(null)?.start()

        val radius = 300f

        setupExpandAnimation(
            containerBorrow!!,
            (radius * kotlin.math.cos(Math.toRadians(210.0))).toFloat(),
            (radius * kotlin.math.sin(Math.toRadians(210.0))).toFloat()
        )
        setupExpandAnimation(containerAddTransaction!!, 0f, -radius)
        setupExpandAnimation(
            containerAddGroup!!,
            (radius * kotlin.math.cos(Math.toRadians(-30.0))).toFloat(),
            (radius * kotlin.math.sin(Math.toRadians(-30.0))).toFloat()
        )
    }

    private fun setupExpandAnimation(view: View, tx: Float, ty: Float) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationX = 0f
        view.translationY = 0f
        view.animate().cancel()
        view.animate()
            .translationX(tx)
            .translationY(ty)
            .alpha(1f)
            .setDuration(300)
            .setListener(null)
            .start()
    }

    private fun collapseFabMenu() {
        if (!isFabMenuOpen) return
        isFabMenuOpen = false

        fabMenuOverlay?.animate()?.cancel()
        fabMenuOverlay?.animate()?.alpha(0f)?.setDuration(300)
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    fabMenuOverlay?.visibility = View.GONE
                }
            })?.start()

        fabMain?.animate()?.cancel()
        fabMain?.animate()?.rotation(0f)?.setDuration(300)?.setListener(null)?.start()

        setupCollapseAnimation(containerBorrow!!)
        setupCollapseAnimation(containerAddTransaction!!)
        setupCollapseAnimation(containerAddGroup!!)
    }

    private fun setupCollapseAnimation(view: View) {
        view.animate().cancel()
        view.animate()
            .translationX(0f)
            .translationY(0f)
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.visibility = View.GONE
                }
            })?.start()
    }

    private fun showBorrowNowDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_borrow_now)
        dialog.setCancelable(false)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.WHITE))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val dateTV: TextView = dialog.findViewById(R.id.dialogBorrowDate)
        val borrowerTV: TextView = dialog.findViewById(R.id.dialogBorrower)
        val lenderRecyclerView: RecyclerView = dialog.findViewById(R.id.lenderRecyclerView)
        val amountEditText: EditText = dialog.findViewById(R.id.dialogBorrowEditText)
        val cancelBtn = dialog.findViewById<Button>(R.id.dialogCancelBtn)
        val borrowBtn = dialog.findViewById<Button>(R.id.dialogBorrowBtn)
        val dialogProgressBar = dialog.findViewById<View>(R.id.dialogProgressBar)

        dialogProgressBar?.visibility = View.VISIBLE

        val calendar = Calendar.getInstance()
        dateTV.text = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault()).format(calendar.time)
        borrowerTV.text = currentNickname
        setupLenderRecyclerView(lenderRecyclerView, dialogProgressBar)
        cancelBtn.setOnClickListener { dialog.dismiss() }
        borrowBtn.setOnClickListener {
            val amountStr = amountEditText.text.toString().trim()
            if (amountStr.isEmpty() || selectedLenderName.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val amount = amountStr.toDouble()
                if (amount <= 0) {
                    Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                borrowBtn.isEnabled = false
                cancelBtn.isEnabled = false
                dialogProgressBar?.visibility = View.VISIBLE
                addBorrowTransaction(
                    selectedLenderName,
                    amount,
                    calendar.timeInMillis,
                    dialog,
                    dialogProgressBar,
                    borrowBtn,
                    cancelBtn
                )
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Invalid amount format", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun setupLenderRecyclerView(
        recyclerView: RecyclerView,
        dialogProgressBar: View?
    ) {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.layoutManager = layoutManager
        val lenders: MutableList<User?> = ArrayList()
        val adapter = LenderAdapter(lenders)
        recyclerView.adapter = adapter
        val snapHelper: SnapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateLayoutEffect(recyclerView)
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(layoutManager)
                    centerView?.let {
                        val pos = layoutManager.getPosition(it)
                        val selectedLender = adapter.getLenderAt(pos)
                        selectedLender?.let { lender ->
                            selectedLenderName = lender.getUsername() ?: ""
                        }
                    }
                }
            }
        })
        loadLenders(adapter, lenders, recyclerView, dialogProgressBar)
    }

    private fun updateLayoutEffect(recyclerView: RecyclerView) {
        val midpoint = recyclerView.width / 2f
        val d0 = 0f
        val d1 = 0.9f * midpoint
        val s0 = 1.6f
        val s1 = 1.0f
        val a0 = 1.0f
        val a1 = 0.5f
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            recyclerView.layoutManager?.let { lm ->
                val childMidpoint = (lm.getDecoratedRight(child) + lm.getDecoratedLeft(child)) / 2f
                val d = Math.min(d1, Math.abs(midpoint - childMidpoint))
                val scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0)
                val alpha = a0 + (a1 - a0) * (d - d0) / (d1 - d0)
                child.scaleX = scale
                child.scaleY = scale
                child.alpha = alpha
            }
        }
    }

    private fun loadLenders(
        adapter: LenderAdapter,
        lenders: MutableList<User?>,
        recyclerView: RecyclerView,
        dialogProgressBar: View?
    ) {
        lifecycleScope.launch {
            try {
                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                lenders.clear()
                // Padding for snapping
                lenders.add(User("", "", "", UserBalance()))
                lenders.add(User("", "", "", UserBalance()))
                
                for (user in users) {
                    if (user.username != null && user.username != currentNickname) {
                        lenders.add(user)
                    }
                }
                
                lenders.add(User("", "", "", UserBalance()))
                lenders.add(User("", "", "", UserBalance()))
                
                adapter.notifyDataSetChanged()
                adapter.preloadAllImages(this@MainActivity) {
                    runOnUiThread {
                        dialogProgressBar?.visibility = View.GONE
                        if (lenders.size > 2) {
                            recyclerView.scrollToPosition(2)
                            recyclerView.post {
                                val firstUser = adapter.getLenderAt(2)
                                firstUser?.let { selectedLenderName = it.username ?: "" }
                                updateLayoutEffect(recyclerView)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading lenders: " + e.message)
                dialogProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun addBorrowTransaction(
        lender: String,
        borrowedAmount: Double,
        date: Long,
        dialog: Dialog,
        dialogProgressBar: View?,
        borrowBtn: Button,
        cancelBtn: Button
    ) {
        val calendar = Calendar.getInstance()
        val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
        val timestamp = System.currentTimeMillis()
        val borrowId = UUID.randomUUID().toString()
        
        val currentUserId = mAuth?.currentUserOrNull()?.id
        if (currentUserId != null) {
            getUserIDByName(lender) { lenderID ->
                if (lenderID == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to find lender", Toast.LENGTH_SHORT).show()
                        dialogProgressBar?.visibility = View.GONE
                        borrowBtn.isEnabled = true
                        cancelBtn.isEnabled = true
                    }
                    return@getUserIDByName
                }
                
                val borrowNowTransaction = BorrowNowTransaction(
                    borrowId,
                    currentUserId,
                    lenderID,
                    currentNickname,
                    date,
                    lender,
                    borrowedAmount,
                    "For Lender Approval",
                    timestamp
                )
                borrowNowTransaction.setMonthYear(currentMonthYear)

                lifecycleScope.launch {
                    try {
                        DeclareDatabase.borrowsTable.insert(borrowNowTransaction)
                        
                        BalanceHelper.updateTotaldebt(currentUserId, borrowedAmount, null)
                        BalanceHelper.updateTotalreceivable(lenderID, borrowedAmount, null)
                        
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Borrowed successfully", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to borrow: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Failed to Borrow", Toast.LENGTH_SHORT).show()
                            dialogProgressBar?.visibility = View.GONE
                            borrowBtn.isEnabled = true
                            cancelBtn.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun getUserIDByName(
        name: String,
        callback: (String?) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("id")) {
                    filter { eq("username", name) }
                }.decodeSingleOrNull<User>()
                callback(user?.id)
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    private fun showCreateGroupDialog() {
        lifecycleScope.launch {
            try {
                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                val usernamesList: MutableList<String?> = ArrayList()
                val userIdsList: MutableList<String?> = ArrayList()
                val currentUserId = mAuth?.currentUserOrNull()?.id
                
                for (user in users) {
                    if (user.username != null && user.id != null && user.id != currentUserId) {
                        usernamesList.add(user.username)
                        userIdsList.add(user.id)
                    }
                }

                val builder = android.app.AlertDialog.Builder(this@MainActivity)
                val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_create_group, null)
                builder.setView(dialogView)
                val dialog = builder.create()
                val groupNameEditText: EditText = dialogView.findViewById(R.id.groupNameEditText)
                val usersCheckboxContainer: LinearLayout = dialogView.findViewById(R.id.usersCheckboxContainer)
                val cancelBtn = dialogView.findViewById<Button>(R.id.cancelGroupBtn)
                val createBtn = dialogView.findViewById<Button>(R.id.createGroupBtn)
                val checkBoxes: MutableList<CheckBox> = ArrayList()
                
                for (username in usernamesList) {
                    val checkBox = CheckBox(this@MainActivity)
                    checkBox.text = username
                    checkBox.setTextColor(resources.getColor(R.color.darkBlue))
                    checkBox.setPadding(8, 8, 8, 8)
                    checkBoxes.add(checkBox)
                    usersCheckboxContainer.addView(checkBox)
                }
                
                cancelBtn.setOnClickListener { dialog.dismiss() }
                createBtn.setOnClickListener {
                    val groupName = groupNameEditText.text.toString().trim()
                    if (groupName.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Please enter a group name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val selectedMemberUids: MutableList<String?> = ArrayList()
                    val selectedMemberDisplayNames: MutableList<String?> = ArrayList()
                    selectedMemberUids.add(currentUserId)
                    selectedMemberDisplayNames.add(if (currentNickname.isNullOrEmpty()) "Me" else currentNickname)
                    for (i in checkBoxes.indices) {
                        if (checkBoxes[i].isChecked) {
                            selectedMemberUids.add(userIdsList[i])
                            selectedMemberDisplayNames.add(usernamesList[i])
                        }
                    }
                    if (selectedMemberUids.size <= 1) {
                        Toast.makeText(this@MainActivity, "Please select at least one member", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    saveGroupToDatabase(groupName, selectedMemberUids, selectedMemberDisplayNames, currentUserId)
                    dialog.dismiss()
                }
                dialog.show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveGroupToDatabase(
        groupName: String?,
        memberUids: MutableList<String?>?,
        memberDisplayNames: MutableList<String?>?,
        currentUserId: String?
    ) {
        if (currentUserId == null) return
        val groupId = UUID.randomUUID().toString()
        val newGroup = PayerGroup(groupId, groupName, memberUids, currentUserId, memberDisplayNames)
        
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupsTable.insert(newGroup)
                Toast.makeText(this@MainActivity, "Group created successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to create group", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getTotalMonthSpends(callback: Runnable?) {
        if (currentNickname.isNullOrEmpty()) getCurrentNickname { nickname -> fetchTotalMonthSpends(nickname, callback) }
        else fetchTotalMonthSpends(currentNickname, callback)
    }

    private fun fetchTotalMonthSpends(username: String?, callback: Runnable?) {
        val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.transactionsTable.select {
                    filter { eq("monthYear", currentMonthYear) }
                }.decodeList<Transaction>()
                
                totalMonthSpends = 0.0
                for (transaction in transactions) {
                    if (isUserInvolved(transaction, username)) {
                        totalMonthSpends += transaction.getPaymentAmount()
                    }
                }
                val tv: TextView? = findViewById(R.id.totalMonthSpends)
                tv?.text = CurrencyUtils.formatAmountWithCurrency(totalMonthSpends)
                callback?.run()
            } catch (e: Exception) {
                callback?.run()
            }
        }
    }

    @SuppressLint("DefaultLocale")
    fun getEverydaySpends(callback: Runnable?) {
        if (currentNickname.isNullOrEmpty()) getCurrentNickname { nickname -> fetchEverydaySpends(nickname, callback) }
        else fetchEverydaySpends(currentNickname, callback)
    }

    @SuppressLint("DefaultLocale")
    private fun fetchEverydaySpends(username: String?, callback: Runnable?) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        fetchEverydaySpendsForWeek(calendar, username, callback)
    }

    fun getEverydaySpendsForWeek(weekStart: Calendar, callback: Runnable?) {
        if (currentNickname.isNullOrEmpty()) getCurrentNickname { nickname -> fetchEverydaySpendsForWeek(weekStart, nickname, callback) }
        else fetchEverydaySpendsForWeek(weekStart, currentNickname, callback)
    }

    private fun fetchEverydaySpendsForWeek(
        weekStart: Calendar,
        username: String?,
        callback: Runnable?
    ) {
        val calendar = weekStart.clone() as Calendar
        val startOfWeek = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val endOfWeek = calendar.timeInMillis
        
        // This is tricky with the current schema where transactions are split by monthYear/day in old DB.
        // In Supabase, we should ideally have a single 'transactions' table with a timestamp.
        // Assuming 'transactions' table has all transactions.
        
        lifecycleScope.launch {
            try {
                val allTransactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                
                val dailySpends = DoubleArray(7) { 0.0 }
                val weekCalendar = weekStart.clone() as Calendar
                
                for (i in 0..6) {
                    val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(weekCalendar.time)
                    val currentDay = SimpleDateFormat("dd", Locale.getDefault()).format(weekCalendar.time)
                    
                    for (t in allTransactions) {
                        if (t.monthYear == currentMonthYear && t.day == currentDay && isUserInvolved(t, username)) {
                            dailySpends[i] += t.getPaymentAmount()
                        }
                    }
                    setViewHeightForDay(i, dailySpends[i])
                    weekCalendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                callback?.run()
            } catch (e: Exception) {
                callback?.run()
            }
        }
    }

    fun setViewHeightForDay(day: Int, dailySpends: Double) {
        val dsString = CurrencyUtils.formatAmount(dailySpends)
        val ids = intArrayOf(
            R.id.totalday7,
            R.id.totalday6,
            R.id.totalday5,
            R.id.totalday4,
            R.id.totalday3,
            R.id.totalday2,
            R.id.totalday1
        )
        val barIds = intArrayOf(
            R.id.day7_bar,
            R.id.day6_bar,
            R.id.day5_bar,
            R.id.day4_bar,
            R.id.day3_bar,
            R.id.day2_bar,
            R.id.day1_bar
        )

        val tv: TextView? = findViewById(ids[day])
        tv?.text = dsString
        val h = if (dailySpends >= 1000) 300 else if (dailySpends <= 50) 17 else (dailySpends / 3).toInt()
        val v = findViewById<View>(barIds[day])
        v?.let {
            val lp = it.layoutParams
            lp.height = h
            it.layoutParams = lp
        }
    }

    fun getRecentTransaction(callback: Runnable?) {
        if (currentNickname.isNullOrEmpty()) getCurrentNickname { nickname -> fetchRecentTransactions(nickname, callback) }
        else fetchRecentTransactions(currentNickname, callback)
    }

    private fun fetchRecentTransactions(username: String?, callback: Runnable?) {
        lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                recentTransactionList.clear()
                
                for (t in transactions) {
                    if (isUserInvolved(t, username)) {
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
                            this@MainActivity.onTransactionTap(transaction!!)
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

    fun getDebtList(
        selectedStatus: String?,
        callback: DebtNumCallback,
        actionListener: OnBorrowerActionListener?
    ) {
        lifecycleScope.launch {
            try {
                val currentUserId = mAuth?.currentUserOrNull()?.id
                val borrows = DeclareDatabase.borrowsTable.select().decodeList<BorrowNowTransaction>()
                
                debtList.clear()
                for (bnt in borrows) {
                    if (bnt.getBorrowerID() == currentUserId) {
                        if (bnt.getStatus() != "Removed" && bnt.getStatus() != "Payment Denied" && shouldIncludeForDebtStatus(
                                bnt.getStatus(),
                                selectedStatus
                            )
                        ) {
                            addDebtTransactionFromBorrowNow(bnt, bnt.getMonthYear(), null, bnt.getBorrowId())
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

    private fun sortAndDisplayDebtList(actionListener: OnBorrowerActionListener?) {
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
                    actionListener ?: object : OnBorrowerActionListener {
                        override fun onPayClicked(t: BorrowTransaction?, p: Int) {}
                        override fun onRemoveClicked(t: BorrowTransaction?, p: Int) {}
                        override fun onTryAgainClicked(t: BorrowTransaction?, p: Int) {}
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
        val date = if (dateLong != null) SimpleDateFormat("MMM-dd-yyyy", Locale.getDefault()).format(java.util.Date(dateLong)) else ""
        
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
        actionListener: OnBorrowerActionListener?
    ) {
        if (selectedMonth == null || selectedMonth == "All") return
        lifecycleScope.launch {
            try {
                val uid = mAuth?.currentUserOrNull()?.id
                val borrows = DeclareDatabase.borrowsTable.select {
                    filter { eq("month_year", selectedMonth) }
                }.decodeList<BorrowNowTransaction>()
                
                debtList.clear()
                for (bnt in borrows) {
                    if (bnt.getBorrowerID() == uid && bnt.getStatus() != "Removed" && bnt.getStatus() != "Payment Denied" && shouldIncludeForDebtStatus(
                            bnt.getStatus(),
                            selectedStatus
                        )
                    ) {
                        addDebtTransactionFromBorrowNow(bnt, selectedMonth, null, bnt.getBorrowId())
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
        actionListener: OnLenderActionListener?
    ) {
        lifecycleScope.launch {
            try {
                val uid = mAuth?.currentUserOrNull()?.id
                val borrows = DeclareDatabase.borrowsTable.select().decodeList<BorrowNowTransaction>()
                
                owedList.clear()
                for (bnt in borrows) {
                    if (bnt.getLenderID() == uid && bnt.getStatus() != "Declined" && bnt.getStatus() != "Payment Denied" && bnt.getStatus() != "Removed" && shouldIncludeForStatus(
                            bnt.getStatus(),
                            selectedStatus
                        )
                    ) {
                        addOwedTransactionFromBorrowNow(bnt, bnt.getMonthYear(), null, bnt.getBorrowId())
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

    private fun sortAndDisplayOwedList(actionListener: OnLenderActionListener?) {
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
                    owedList,
                    actionListener ?: object : OnLenderActionListener {
                        override fun onNotYetClicked(t: OwedTransaction?, p: Int) {}
                        override fun onReceivedClicked(t: OwedTransaction?, p: Int) {}
                        override fun onDeclineClicked(t: OwedTransaction?, p: Int) {}
                        override fun onApprovedClicked(t: OwedTransaction?, p: Int) {}
                    })
            rv.layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    fun getCurrentNickname(callback: (String?) -> Unit) {
        val uid = mAuth?.currentUserOrNull()?.id ?: return callback("")
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("username")) {
                    filter { eq("id", uid) }
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
        val date = if (dateLong != null) SimpleDateFormat("MMM-dd-yyyy", Locale.getDefault()).format(java.util.Date(dateLong)) else ""
        
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
        actionListener: OnLenderActionListener?
    ) {
        if (sm == null || sm == "All") return
        lifecycleScope.launch {
            try {
                val uid = mAuth?.currentUserOrNull()?.id
                val borrows = DeclareDatabase.borrowsTable.select {
                    filter { eq("month_year", sm) }
                }.decodeList<BorrowNowTransaction>()
                
                owedList.clear()
                for (bnt in borrows) {
                    if (bnt.getLenderID() == uid && bnt.getStatus() != "Declined" && bnt.getStatus() != "Payment Denied" && bnt.getStatus() != "Removed" && shouldIncludeForStatus(
                            bnt.getStatus(),
                            ss
                        )
                    ) {
                        addOwedTransactionFromBorrowNow(bnt, sm, null, bnt.getBorrowId())
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
