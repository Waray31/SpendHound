package com.waray.spendhound

import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostFragment
import androidx.navigation.ui.NavigationUI.onNavDestinationSelected
import androidx.navigation.ui.NavigationUI.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
        fun onCurrentNicknameReceived(CurrentNickname: String?)
    }

    fun isUserInvolved(
        transaction: Transaction?,
        usernameOrUid: String?
    ): Boolean {
        if (transaction == null || usernameOrUid == null || usernameOrUid.isEmpty()) return false
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
        BalanceHelper.ensureUserBorrowsExist(currentUserId, null)

        getCurrentNickname { nickname -> }

        navView = findViewById(R.id.navView)
        val recyclerView: RecyclerView? = findViewById(R.id.transactionListRecycler)
        recentTransactionList = ArrayList()
        recentTransactionAdapter = RecentTransactionAdapter(
            recentTransactionList,
            OnTransactionClickListener { transaction ->
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
                val amount = amountStr.toInt()
                if (amount <= 0) {
                    Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                borrowBtn.isEnabled = false
                cancelBtn.isEnabled = false
                dialogProgressBar?.visibility = View.VISIBLE
                addBorrowTransaction(
                    selectedLenderName,
                    amount.toString(),
                    dateTV.text.toString(),
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
        FirebaseDatabase.getInstance().getReference("users")
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    lenders.clear()
                    lenders.add(User("", "", "", UserBalance()))
                    lenders.add(User("", "", "", UserBalance()))
                    for (userSnapshot in dataSnapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)
                        if (user != null && user.username != null && user.username != currentNickname) {
                            user.id = userSnapshot.key
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
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    android.util.Log.e("MainActivity", "Database error: " + databaseError.message)
                    dialogProgressBar?.visibility = View.GONE
                }
            })
    }

    private fun addBorrowTransaction(
        lender: String,
        borrowedAmountStr: String,
        currentDate: String?,
        dialog: Dialog,
        dialogProgressBar: View?,
        borrowBtn: Button,
        cancelBtn: Button
    ) {
        val calendar = Calendar.getInstance()
        val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
        val currentDay = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)
        val timestamp = System.currentTimeMillis()
        val dayRef = DeclareDatabase.getDBRefBorrows().child(currentMonthYear).child(currentDay)
        val borrowId = dayRef.push().key
        if (borrowId == null) {
            Toast.makeText(this, "Failed to generate borrow ID", Toast.LENGTH_SHORT).show()
            dialogProgressBar?.visibility = View.GONE
            borrowBtn.isEnabled = true
            cancelBtn.isEnabled = true
            return
        }
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
                    currentDate,
                    lender,
                    borrowedAmountStr,
                    "For Lender Approval",
                    timestamp
                )
                dayRef.child(borrowId).setValue(borrowNowTransaction)
                    .addOnSuccessListener {
                        BalanceHelper.addBorrowerEntry(currentUserId, borrowId, null)
                        BalanceHelper.addLenderEntry(lenderID, borrowId, null)
                        val amount = borrowedAmountStr.toInt()
                        BalanceHelper.updateTotaldebt(currentUserId, amount, null)
                        BalanceHelper.updateTotalreceivable(lenderID, amount, null)
                        runOnUiThread {
                            Toast.makeText(this, "Borrowed successfully", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }.addOnFailureListener {
                        runOnUiThread {
                            Toast.makeText(this, "Failed to Borrow", Toast.LENGTH_SHORT).show()
                            dialogProgressBar?.visibility = View.GONE
                            borrowBtn.isEnabled = true
                            cancelBtn.isEnabled = true
                        }
                    }
            }
        }
    }

    private fun getUserIDByName(
        name: String,
        callback: (String?) -> Unit
    ) {
        FirebaseDatabase.getInstance().getReference("users")
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (userSnapshot in dataSnapshot.children) {
                        if (name == userSnapshot.child("username").getValue(String::class.java)) {
                            callback(userSnapshot.key)
                            return
                        }
                    }
                    callback(null)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    callback(null)
                }
            })
    }

    private fun showCreateGroupDialog() {
        FirebaseDatabase.getInstance().getReference("users")
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val usernamesList: MutableList<String?> = ArrayList()
                    val userIdsList: MutableList<String?> = ArrayList()
                    val currentUserId = mAuth?.currentUserOrNull()?.id
                    for (userSnapshot in dataSnapshot.children) {
                        val username = userSnapshot.child("username").getValue(String::class.java)
                        val uid = userSnapshot.key
                        if (username != null && uid != null && uid != currentUserId) {
                            usernamesList.add(username)
                            userIdsList.add(uid)
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
                        selectedMemberDisplayNames.add(if (currentNickname?.isEmpty() == true) "Me" else currentNickname)
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
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Failed to load users", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveGroupToDatabase(
        groupName: String?,
        memberUids: MutableList<String?>?,
        memberDisplayNames: MutableList<String?>?,
        currentUserId: String?
    ) {
        val groupsRef = DeclareDatabase.getDBRefGroups().child(currentUserId!!)
        val groupId = groupsRef.push().key
        if (groupId != null) {
            val newGroup = PayerGroup(groupId, groupName, memberUids, currentUserId, memberDisplayNames)
            groupsRef.child(groupId).setValue(newGroup).addOnSuccessListener {
                Toast.makeText(this@MainActivity, "Group created successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getTotalMonthSpends(callback: Runnable?) {
        if (currentNickname.isNullOrEmpty()) getCurrentNickname { nickname -> fetchTotalMonthSpends(nickname, callback) }
        else fetchTotalMonthSpends(currentNickname, callback)
    }

    private fun fetchTotalMonthSpends(username: String?, callback: Runnable?) {
        val monthYearRef = DeclareDatabase.getDBRefTransaction().child(SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time))
        totalMonthSpends = 0.0
        monthYearRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (daySnapshot in dataSnapshot.children) {
                    for (timeSnapshot in daySnapshot.children) {
                        val transaction = timeSnapshot.getValue(Transaction::class.java)
                        if (transaction != null && isUserInvolved(transaction, username)) totalMonthSpends += transaction.getPaymentAmount()
                    }
                }
                val tv: TextView? = findViewById(R.id.totalMonthSpends)
                tv?.text = CurrencyUtils.formatAmountWithCurrency(totalMonthSpends)
                callback?.run()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                callback?.run()
            }
        })
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
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val daysFetched = java.util.concurrent.atomic.AtomicInteger(0)
        for (i in 0..6) {
            val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
            val currentDay = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)
            val dayIndex = i
            DeclareDatabase.getDBRefTransaction().child(currentMonthYear).child(currentDay)
                .addListenerForSingleValueEvent(object : ValueEventListener() {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        var ds = 0.0
                        for (ts in dataSnapshot.children) {
                            val t = ts.getValue(Transaction::class.java)
                            if (t != null && isUserInvolved(t, username)) ds += t.getPaymentAmount()
                        }
                        setViewHeightForDay(dayIndex, ds)
                        if (daysFetched.incrementAndGet() == 7) callback?.run()
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        if (daysFetched.incrementAndGet() == 7) callback?.run()
                    }
                })
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
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
        val daysFetched = java.util.concurrent.atomic.AtomicInteger(0)
        for (i in 0..6) {
            val cmy = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
            val cd = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)
            val dayIndex = i
            DeclareDatabase.getDBRefTransaction().child(cmy).child(cd)
                .addListenerForSingleValueEvent(object : ValueEventListener() {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        var ds = 0.0
                        for (ts in dataSnapshot.children) {
                            val t = ts.getValue(Transaction::class.java)
                            if (t != null && isUserInvolved(t, username)) ds += t.getPaymentAmount()
                        }
                        setViewHeightForDay(dayIndex, ds)
                        if (daysFetched.incrementAndGet() == 7) callback?.run()
                    }

                    override fun onCancelled(de: DatabaseError) {
                        if (daysFetched.incrementAndGet() == 7) callback?.run()
                    }
                })
            calendar.add(Calendar.DAY_OF_YEAR, 1)
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
        recentTransactionList.clear()
        val calendar = Calendar.getInstance()
        val daysFetched = java.util.concurrent.atomic.AtomicInteger(0)
        for (i in 0..6) {
            val cmy = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
            val cd = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)
            val fmy = cmy
            val fd = cd
            DeclareDatabase.getDBRefTransaction().child(cmy).child(cd)
                .addListenerForSingleValueEvent(object : ValueEventListener() {
                    override fun onDataChange(ds: DataSnapshot) {
                        for (ts in ds.children) {
                            val t = ts.getValue(Transaction::class.java)
                            if (t != null && isUserInvolved(t, username)) {
                                val tk = ts.key
                                val p = fmy.split("-").toTypedArray()
                                recentTransactionList.add(
                                    RecentTransaction(
                                        p[0] + " - " + fd,
                                        t.getTransactionType(),
                                        t.getMultilineStr(),
                                        CurrencyUtils.formatAmountWithCurrency(t.getPaymentAmount()),
                                        getTransactionIcon(t.getTransactionType()),
                                        p[1] + "-" + p[0] + "-" + fd + " " + tk,
                                        t.getPayorsDisplayNames() ?: t.getPayorsList(),
                                        t.getPayorsList(),
                                        t.getAmountsPaidList(),
                                        t.getTotalIndividualPayment(),
                                        null,
                                        t.getPosterDisplayName() ?: t.getUsernamePost(),
                                        t.getUsernamePost(),
                                        fmy,
                                        fd,
                                        tk
                                    )
                                )
                            }
                        }
                        if (daysFetched.incrementAndGet() == 7) {
                            recentTransactionList.sortWith(Comparator { t1, t2 ->
                                if (t1?.getSortDateTime() != null && t2?.getSortDateTime() != null) t2.getSortDateTime()
                                    .compareTo(t1.getSortDateTime()) else 0
                            })
                            val rv: RecyclerView? = findViewById(R.id.transactionListRecycler)
                            if (rv != null) {
                                recentTransactionAdapter = RecentTransactionAdapter(
                                    recentTransactionList,
                                    OnTransactionClickListener { transaction ->
                                        this@MainActivity.onTransactionTap(transaction!!)
                                    })
                                rv.adapter = recentTransactionAdapter
                                rv.layoutManager = LinearLayoutManager(this@MainActivity)
                                recentTransactionAdapter?.preloadAllImages(this@MainActivity)
                            }
                            callback?.run()
                        }
                    }

                    override fun onCancelled(de: DatabaseError) {
                        if (daysFetched.incrementAndGet() == 7) callback?.run()
                    }
                })
            calendar.add(Calendar.DAY_OF_YEAR, -1)
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
        debtList.clear()
        val currentUserId = mAuth?.currentUserOrNull()?.id
        DeclareDatabase.getDBRefBorrows()
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(ds: DataSnapshot) {
                    for (ms in ds.children) {
                        val my = ms.key
                        for (days in ms.children) {
                            val d = days.key
                            for (bs in days.children) {
                                val bnt = bs.getValue(BorrowNowTransaction::class.java)
                                if (bnt != null && bnt.getBorrowerID() == currentUserId) {
                                    if (bnt.getStatus() != "Removed" && bnt.getStatus() != "Payment Denied" && shouldIncludeForDebtStatus(
                                            bnt.getStatus(),
                                            selectedStatus
                                        )
                                    ) addDebtTransactionFromBorrowNow(bnt, my, d, bs.key)
                                }
                            }
                        }
                    }
                    debtList.sortWith(Comparator { o1, o2 ->
                        try {
                            val f = SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH)
                            return@Comparator f.parse(o2!!.getDate())!!.compareTo(f.parse(o1!!.getDate()))
                        } catch (e: Exception) {
                            return@Comparator 0
                        }
                    })
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
                    debtNum = debtList.size
                    callback.onDebtNumReceived(debtNum)
                }

                override fun onCancelled(e: DatabaseError) {}
            })
    }

    private fun addDebtTransactionFromBorrowNow(
        borrowNowTransaction: BorrowNowTransaction,
        monthYear: String?,
        day: String?,
        borrowId: String?
    ) {
        val date = changeFormatDate(borrowNowTransaction.getDate())
        val psd = if (borrowNowTransaction.getPaymentSentDate() > 0) SimpleDateFormat(
            "MMM-dd-yyyy",
            Locale.ENGLISH
        ).format(java.util.Date(borrowNowTransaction.getPaymentSentDate())) else null
        val bt = BorrowTransaction(
            date,
            borrowNowTransaction.getLender(),
            borrowNowTransaction.getBorrowedAmountStr(),
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
        debtList.clear()
        if (selectedMonth == null || selectedMonth == "All") return
        val uid = mAuth?.currentUserOrNull()?.id
        DeclareDatabase.getDBRefBorrows().child(selectedMonth)
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(ds: DataSnapshot) {
                    for (dayS in ds.children) {
                        val d = dayS.key
                        for (bs in dayS.children) {
                            val bnt = bs.getValue(BorrowNowTransaction::class.java)
                            if (bnt != null && bnt.getBorrowerID() == uid && bnt.getStatus() != "Removed" && bnt.getStatus() != "Payment Denied" && shouldIncludeForDebtStatus(
                                    bnt.getStatus(),
                                    selectedStatus
                                )
                            ) addDebtTransactionFromBorrowNow(bnt, selectedMonth, d, bs.key)
                        }
                    }
                    debtList.sortWith(Comparator { o1, o2 ->
                        try {
                            val f = SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH)
                            return@Comparator f.parse(o2!!.getDate())!!.compareTo(f.parse(o1!!.getDate()))
                        } catch (e: Exception) {
                            return@Comparator 0
                        }
                    })
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
                    debtNum = debtList.size
                    callback.onDebtNumReceived(debtNum)
                }

                override fun onCancelled(e: DatabaseError) {}
            })
    }

    fun getOwedList(
        selectedStatus: String?,
        callback: OwedNumCallback,
        actionListener: OnLenderActionListener?
    ) {
        owedList.clear()
        val uid = mAuth?.currentUserOrNull()?.id
        DeclareDatabase.getDBRefBorrows()
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(ds: DataSnapshot) {
                    for (ms in ds.children) {
                        val my = ms.key
                        for (days in ms.children) {
                            val d = days.key
                            for (bs in days.children) {
                                val bnt = bs.getValue(BorrowNowTransaction::class.java)
                                if (bnt != null && bnt.getLenderID() == uid && bnt.getStatus() != "Declined" && bnt.getStatus() != "Payment Denied" && bnt.getStatus() != "Removed" && shouldIncludeForStatus(
                                        bnt.getStatus(),
                                        selectedStatus
                                    )
                                ) addOwedTransactionFromBorrowNow(bnt, my, d, bs.key)
                            }
                        }
                    }
                    owedList.sortWith(Comparator { o1, o2 ->
                        try {
                            val f = SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH)
                            return@Comparator f.parse(o2!!.getDate())!!.compareTo(f.parse(o1!!.getDate()))
                        } catch (e: Exception) {
                            return@Comparator 0
                        }
                    })
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
                    owedNum = owedList.size
                    callback.onOwedNumReceived(owedNum)
                }

                override fun onCancelled(e: DatabaseError) {}
            })
    }

    fun getCurrentNickname(callback: (String?) -> Unit) {
        val uid = mAuth?.currentUserOrNull()?.id ?: return callback("")
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(ds: DataSnapshot) {
                    currentNickname = if (ds.exists()) ds.getValue(String::class.java) else ""
                    callback(currentNickname)
                }

                override fun onCancelled(e: DatabaseError) {
                    callback("")
                }
            })
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
        val date = changeFormatDate(bnt.getDate())
        val psd = if (bnt.getPaymentSentDate() > 0) SimpleDateFormat(
            "MMM-dd-yyyy",
            Locale.ENGLISH
        ).format(java.util.Date(bnt.getPaymentSentDate())) else null
        owedList.add(
            OwedTransaction(
                date,
                bnt.getBorrowerName() ?: "Unknown",
                bnt.getBorrowedAmountStr(),
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
        owedList.clear()
        if (sm == null || sm == "All") return
        val uid = mAuth?.currentUserOrNull()?.id
        DeclareDatabase.getDBRefBorrows().child(sm)
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(ds: DataSnapshot) {
                    for (dayS in ds.children) {
                        val d = dayS.key
                        for (bs in dayS.children) {
                            val bnt = bs.getValue(BorrowNowTransaction::class.java)
                            if (bnt != null && bnt.getLenderID() == uid && bnt.getStatus() != "Declined" && bnt.getStatus() != "Payment Denied" && bnt.getStatus() != "Removed" && shouldIncludeForStatus(
                                    bnt.getStatus(),
                                    ss
                                )
                            ) addOwedTransactionFromBorrowNow(bnt, sm, d, bs.key)
                        }
                    }
                    owedList.sortWith(Comparator { o1, o2 ->
                        try {
                            val f = SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH)
                            return@Comparator f.parse(o2!!.getDate())!!.compareTo(f.parse(o1!!.getDate()))
                        } catch (e: Exception) {
                            return@Comparator 0
                        }
                    })
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
                    owedNum = owedList.size
                    callback.onOwedNumReceived(owedNum)
                }

                override fun onCancelled(e: DatabaseError) {}
            })
    }
}
