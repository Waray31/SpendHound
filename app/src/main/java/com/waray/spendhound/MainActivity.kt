package com.waray.spendhound

import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    var navView: BottomNavigationView? = null
    var mAuth: FirebaseAuth? = null
    var totalMonthSpends: kotlin.Double = 0.0
    private var progressBar: ProgressBar? = null
    var currentNickname: kotlin.String? = ""
    var owedNum: Int = 0
    var debtNum: Int = 0
    private var recentTransactionList = java.util.ArrayList<RecentTransaction?>()
    var debtList: java.util.ArrayList<BorrowTransaction?> =
        java.util.ArrayList<BorrowTransaction?>()
    var owedList: java.util.ArrayList<OwedTransaction?> = java.util.ArrayList<OwedTransaction?>()
    private var recentTransactionAdapter: RecentTransactionAdapter? = null

    // FAB Menu fields
    private var fabMain: FloatingActionButton? = null
    private var fabMenuOverlay: android.view.View? = null
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
        fun onCurrentNicknameReceived(CurrentNickname: kotlin.String?)
    }

    fun isUserInvolved(
        transaction: com.waray.spendhound.Transaction?,
        usernameOrUid: kotlin.String?
    ): kotlin.Boolean {
        if (transaction == null || usernameOrUid == null || usernameOrUid.isEmpty()) return false
        val currentUid: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        if (transaction.isUserInvolvedByUid(currentUid)) return true
        if (usernameOrUid == transaction.getUsernamePost()) return true
        if (usernameOrUid == transaction.getPosterDisplayName()) return true
        val payorsList = transaction.getPayorsList()
        if (payorsList != null && payorsList.contains(usernameOrUid)) return true
        val payorsDisplayNames = transaction.getPayorsDisplayNames()
        return payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding =
            com.waray.spendhound.databinding.ActivityMainBinding.inflate(getLayoutInflater())
        setContentView(binding.getRoot())

        progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.setVisibility(android.view.View.VISIBLE)
        mAuth = DeclareDatabase.getAuth()
        UserHelper.preloadAllUsers()

        val currentUserId: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        BalanceHelper.ensureBalancesExist(currentUserId, null)
        BalanceHelper.ensureUserBorrowsExist(currentUserId, null)

        getCurrentNickname(CurrentNicknameCallback { nickname: kotlin.String? -> })

        navView = findViewById<BottomNavigationView?>(R.id.navView)
        val recyclerView: RecyclerView? = findViewById<RecyclerView?>(R.id.transactionListRecycler)
        recentTransactionList = java.util.ArrayList<RecentTransaction?>()
        recentTransactionAdapter = RecentTransactionAdapter(
            recentTransactionList,
            OnTransactionClickListener { transaction: RecentTransaction? ->
                this.onTransactionTap(
                    transaction!!
                )
            })
        if (recyclerView != null) {
            recyclerView.setAdapter(recentTransactionAdapter)
            recyclerView.setLayoutManager(LinearLayoutManager(this))
        }

        fabMain = findViewById<FloatingActionButton?>(R.id.fab_main)
        fabMenuOverlay = findViewById<android.view.View?>(R.id.fab_menu_overlay)
        containerBorrow = findViewById<LinearLayout>(R.id.container_borrow)
        containerAddTransaction = findViewById<LinearLayout>(R.id.container_add_transaction)
        containerAddGroup = findViewById<LinearLayout>(R.id.container_add_group)

        if (fabMain != null) fabMain.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> toggleFabMenu() })
        if (fabMenuOverlay != null) fabMenuOverlay!!.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> collapseFabMenu() })

        findViewById<android.view.View?>(R.id.fab_borrow).setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            collapseFabMenu()
            showBorrowNowDialog()
        })
        findViewById<android.view.View?>(R.id.fab_add_transaction).setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            collapseFabMenu()
            startActivity(Intent(this@MainActivity, AddTransactionActivity::class.java))
        })
        findViewById<android.view.View?>(R.id.fab_add_group).setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            collapseFabMenu()
            showCreateGroupDialog()
        })

        val navHostFragment: NavHostFragment? =
            getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment?
        if (navHostFragment != null) {
            val navController: NavController = navHostFragment.navController
            setupWithNavController(navView, navController)
            navView.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener { item: android.view.MenuItem? ->
                if (isFabMenuOpen) collapseFabMenu()
                onNavDestinationSelected(item, navController)
            })

            navController.addOnDestinationChangedListener(OnDestinationChangedListener { controller: NavController?, destination: NavDestination?, arguments: Bundle? ->
                val id: Int = destination.id
                val hideOnScroll = (id != R.id.navigation_borrow && id != R.id.navigation_profile)
                updateBottomViewBehavior(hideOnScroll)
            })
        }
        progressBar.setVisibility(android.view.View.GONE)
    }

    fun onTransactionTap(transaction: RecentTransaction) {
        if (!transaction.isExpanded()) {
            unhideNavigation()
        }
    }

    fun unhideNavigation() {
        if (navView != null) {
            val layoutParams: ViewGroup.LayoutParams = navView.getLayoutParams()
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val params: CoordinatorLayout.LayoutParams =
                    layoutParams as CoordinatorLayout.LayoutParams
                if (params.getBehavior() is HideBottomViewOnScrollBehavior<*>) {
                    val behavior: HideBottomViewOnScrollBehavior<BottomNavigationView?>? =
                        params.getBehavior() as HideBottomViewOnScrollBehavior<BottomNavigationView?>?
                    behavior.slideUp(navView)
                }
            }
        }

        val fabMainLayout = findViewById<android.view.View?>(R.id.fab_main_layout)
        if (fabMainLayout != null) {
            val layoutParams: ViewGroup.LayoutParams = fabMainLayout.getLayoutParams()
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val params: CoordinatorLayout.LayoutParams =
                    layoutParams as CoordinatorLayout.LayoutParams
                if (params.getBehavior() is HideBottomViewOnScrollBehavior<*>) {
                    val behavior: HideBottomViewOnScrollBehavior<android.view.View?>? =
                        params.getBehavior() as HideBottomViewOnScrollBehavior<android.view.View?>?
                    behavior.slideUp(fabMainLayout)
                }
            }
        }

        if (fabMain != null) {
            val layoutParams: ViewGroup.LayoutParams = fabMain.getLayoutParams()
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val params: CoordinatorLayout.LayoutParams =
                    layoutParams as CoordinatorLayout.LayoutParams
                if (params.getBehavior() is HideBottomViewOnScrollBehavior<*>) {
                    val behavior: HideBottomViewOnScrollBehavior<FloatingActionButton?>? =
                        params.getBehavior() as HideBottomViewOnScrollBehavior<FloatingActionButton?>?
                    behavior.slideUp(fabMain)
                }
            }
        }
    }

    private fun updateBottomViewBehavior(hideOnScroll: kotlin.Boolean) {
        val fabMainLayout = findViewById<android.view.View?>(R.id.fab_main_layout)
        updateViewBehavior(navView, hideOnScroll)
        updateViewBehavior(fabMain, hideOnScroll)
        updateViewBehavior(fabMainLayout, hideOnScroll)
    }

    private fun updateViewBehavior(view: android.view.View?, hideOnScroll: kotlin.Boolean) {
        if (view == null) return
        val layoutParams: ViewGroup.LayoutParams = view.getLayoutParams()
        if (layoutParams is CoordinatorLayout.LayoutParams) {
            val params: CoordinatorLayout.LayoutParams =
                layoutParams as CoordinatorLayout.LayoutParams
            if (hideOnScroll) {
                if (params.getBehavior() !is HideBottomViewOnScrollBehavior<*>) {
                    params.setBehavior(HideBottomViewOnScrollBehavior<android.view.View?>())
                    view.setTranslationY(0f)
                    view.setLayoutParams(params)
                }
            } else {
                if (params.getBehavior() != null) {
                    params.setBehavior(null)
                    view.setTranslationY(0f)
                    view.setLayoutParams(params)
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

        fabMenuOverlay!!.setVisibility(android.view.View.VISIBLE)
        fabMenuOverlay!!.animate().cancel()
        fabMenuOverlay!!.animate().alpha(1f).setDuration(300).setListener(null).start()

        fabMain.animate().cancel()
        fabMain.animate().rotation(45f).setDuration(300).setListener(null).start()

        val radius = 300f // Increased radius to accommodate labels

        setupExpandAnimation(
            containerBorrow,
            (radius * kotlin.math.cos(java.lang.Math.toRadians(210.0))).toFloat(),
            (radius * kotlin.math.sin(java.lang.Math.toRadians(210.0))).toFloat()
        )
        setupExpandAnimation(containerAddTransaction, 0f, -radius)
        setupExpandAnimation(
            containerAddGroup,
            (radius * kotlin.math.cos(java.lang.Math.toRadians(-30.0))).toFloat(),
            (radius * kotlin.math.sin(java.lang.Math.toRadians(-30.0))).toFloat()
        )
    }

    private fun setupExpandAnimation(view: android.view.View, tx: kotlin.Float, ty: kotlin.Float) {
        view.setVisibility(android.view.View.VISIBLE)
        view.setAlpha(0f)
        view.setTranslationX(0f)
        view.setTranslationY(0f)
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

        fabMenuOverlay!!.animate().cancel()
        fabMenuOverlay!!.animate().alpha(0f).setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator?) {
                    fabMenuOverlay!!.setVisibility(android.view.View.GONE)
                }
            }).start()

        fabMain.animate().cancel()
        fabMain.animate().rotation(0f).setDuration(300).setListener(null).start()

        setupCollapseAnimation(containerBorrow)
        setupCollapseAnimation(containerAddTransaction)
        setupCollapseAnimation(containerAddGroup)
    }

    private fun setupCollapseAnimation(view: android.view.View) {
        view.animate().cancel()
        view.animate()
            .translationX(0f)
            .translationY(0f)
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator?) {
                    view.setVisibility(android.view.View.GONE)
                }
            }).start()
    }

    private fun showBorrowNowDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_borrow_now)
        dialog.setCancelable(false)
        if (dialog.getWindow() != null) {
            dialog.getWindow()!!.setBackgroundDrawable(ColorDrawable(android.graphics.Color.WHITE))
            dialog.getWindow()!!
                .setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val dateTV: TextView = dialog.findViewById<TextView>(R.id.dialogBorrowDate)
        val borrowerTV: TextView = dialog.findViewById<TextView>(R.id.dialogBorrower)
        val lenderRecyclerView: RecyclerView =
            dialog.findViewById<RecyclerView>(R.id.lenderRecyclerView)
        val amountEditText: EditText = dialog.findViewById<EditText>(R.id.dialogBorrowEditText)
        val cancelBtn = dialog.findViewById<android.widget.Button>(R.id.dialogCancelBtn)
        val borrowBtn = dialog.findViewById<android.widget.Button>(R.id.dialogBorrowBtn)
        val dialogProgressBar = dialog.findViewById<android.view.View?>(R.id.dialogProgressBar)

        // Show progress bar initially while loading lenders and their images
        if (dialogProgressBar != null) {
            dialogProgressBar.setVisibility(android.view.View.VISIBLE)
        }

        val calendar = java.util.Calendar.getInstance()
        dateTV.setText(
            java.text.SimpleDateFormat("MMMM-dd-yyyy", java.util.Locale.getDefault())
                .format(calendar.getTime())
        )
        borrowerTV.setText(currentNickname)
        setupLenderRecyclerView(lenderRecyclerView, dialogProgressBar)
        cancelBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> dialog.dismiss() })
        borrowBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            val amountStr = amountEditText.getText().toString().trim { it <= ' ' }
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
                borrowBtn.setEnabled(false)
                cancelBtn.setEnabled(false)
                if (dialogProgressBar != null) dialogProgressBar.setVisibility(android.view.View.VISIBLE)
                addBorrowTransaction(
                    selectedLenderName,
                    amount.toString(),
                    dateTV.getText().toString(),
                    dialog,
                    dialogProgressBar,
                    borrowBtn,
                    cancelBtn
                )
            } catch (e: java.lang.NumberFormatException) {
                Toast.makeText(this, "Invalid amount format", Toast.LENGTH_SHORT).show()
            }
        })
        dialog.show()
    }

    private fun setupLenderRecyclerView(
        recyclerView: RecyclerView,
        dialogProgressBar: android.view.View?
    ) {
        val layoutManager: LinearLayoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.setLayoutManager(layoutManager)
        val lenders: kotlin.collections.MutableList<com.waray.spendhound.User?> =
            java.util.ArrayList<com.waray.spendhound.User?>()
        val adapter = LenderAdapter(lenders)
        recyclerView.setAdapter(adapter)
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
                    val centerView: android.view.View? = snapHelper.findSnapView(layoutManager)
                    if (centerView != null) {
                        val pos: Int = layoutManager.getPosition(centerView)
                        val selectedLender = adapter.getLenderAt(pos)
                        if (selectedLender != null) selectedLenderName =
                            selectedLender.getUsername()
                    }
                }
            }
        })
        loadLenders(adapter, lenders, recyclerView, dialogProgressBar)
    }

    private fun updateLayoutEffect(recyclerView: RecyclerView) {
        val midpoint: kotlin.Float = recyclerView.getWidth() / 2f
        val d0 = 0f
        val d1 = 0.9f * midpoint
        val s0 = 1.6f
        val s1 = 1.0f
        val a0 = 1.0f
        val a1 = 0.5f
        for (i in 0..<recyclerView.getChildCount()) {
            val child: android.view.View = recyclerView.getChildAt(i)
            if (recyclerView.getLayoutManager() != null) {
                val childMidpoint: kotlin.Float = (recyclerView.getLayoutManager()
                    .getDecoratedRight(child) + recyclerView.getLayoutManager()
                    .getDecoratedLeft(child)) / 2f
                val d = kotlin.math.min(d1, kotlin.math.abs(midpoint - childMidpoint))
                val scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0)
                val alpha = a0 + (a1 - a0) * (d - d0) / (d1 - d0)
                child.setScaleX(scale)
                child.setScaleY(scale)
                child.setAlpha(alpha)
            }
        }
    }

    private fun loadLenders(
        adapter: LenderAdapter,
        lenders: kotlin.collections.MutableList<com.waray.spendhound.User?>,
        recyclerView: RecyclerView,
        dialogProgressBar: android.view.View?
    ) {
        DeclareDatabase.getDatabaseReference()
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    lenders.clear()
                    lenders.add(com.waray.spendhound.User("", "", "", "", UserBalance()))
                    lenders.add(com.waray.spendhound.User("", "", "", "", UserBalance()))
                    for (userSnapshot in dataSnapshot.getChildren()) {
                        val user: com.waray.spendhound.User? =
                            userSnapshot.getValue(com.waray.spendhound.User::class.java)
                        if (user != null && user.getUsername() != null && (user.getUsername() != currentNickname)) {
                            user.setUid(userSnapshot.getKey())
                            lenders.add(user)
                        }
                    }
                    lenders.add(com.waray.spendhound.User("", "", "", "", UserBalance()))
                    lenders.add(com.waray.spendhound.User("", "", "", "", UserBalance()))
                    adapter.notifyDataSetChanged()

                    // Preload images before hiding progress bar
                    adapter.preloadAllImages(this@MainActivity, java.lang.Runnable {
                        runOnUiThread(java.lang.Runnable {
                            if (dialogProgressBar != null) {
                                dialogProgressBar.setVisibility(android.view.View.GONE)
                            }
                            if (lenders.size > 2) {
                                recyclerView.scrollToPosition(2)
                                recyclerView.post(java.lang.Runnable {
                                    val firstUser = adapter.getLenderAt(2)
                                    if (firstUser != null) selectedLenderName =
                                        firstUser.getUsername()
                                    updateLayoutEffect(recyclerView)
                                })
                            }
                        })
                    })
                }

                public override fun onCancelled(databaseError: DatabaseError) {
                    android.util.Log.e(
                        "MainActivity",
                        "Database error: " + databaseError.getMessage()
                    )
                    if (dialogProgressBar != null) dialogProgressBar.setVisibility(android.view.View.GONE)
                }
            })
    }

    private fun addBorrowTransaction(
        lender: kotlin.String,
        borrowedAmountStr: kotlin.String,
        currentDate: kotlin.String?,
        dialog: android.app.Dialog,
        dialogProgressBar: android.view.View?,
        borrowBtn: android.widget.Button,
        cancelBtn: android.widget.Button
    ) {
        val calendar = java.util.Calendar.getInstance()
        val currentMonthYear =
            java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
                .format(calendar.getTime())
        val currentDay = java.text.SimpleDateFormat("dd", java.util.Locale.getDefault())
            .format(calendar.getTime())
        val timestamp = java.lang.System.currentTimeMillis()
        val dayRef: DatabaseReference =
            DeclareDatabase.getDBRefBorrows().child(currentMonthYear).child(currentDay)
        val borrowId: kotlin.String? = dayRef.push().getKey()
        if (borrowId == null) {
            Toast.makeText(this, "Failed to generate borrow ID", Toast.LENGTH_SHORT).show()
            if (dialogProgressBar != null) dialogProgressBar.setVisibility(android.view.View.GONE)
            borrowBtn.setEnabled(true)
            cancelBtn.setEnabled(true)
            return
        }
        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().getCurrentUser()
        if (currentUser != null) {
            val borrowerID: kotlin.String? = currentUser.getUid()
            getUserIDByName(
                lender,
                com.waray.spendhound.MainActivity.UserIDCallback { lenderID: kotlin.String? ->
                    if (lenderID == null) {
                        runOnUiThread(java.lang.Runnable {
                            Toast.makeText(this, "Failed to find lender", Toast.LENGTH_SHORT).show()
                            if (dialogProgressBar != null) dialogProgressBar.setVisibility(android.view.View.GONE)
                            borrowBtn.setEnabled(true)
                            cancelBtn.setEnabled(true)
                        })
                        return@getUserIDByName
                    }
                    val borrowNowTransaction = BorrowNowTransaction(
                        borrowId,
                        borrowerID,
                        lenderID,
                        currentNickname,
                        currentDate,
                        lender,
                        borrowedAmountStr,
                        "For Lender Approval",
                        timestamp
                    )
                    dayRef.child(borrowId).setValue(borrowNowTransaction)
                        .addOnSuccessListener({ unused ->
                            BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null)
                            BalanceHelper.addLenderEntry(lenderID, borrowId, null)
                            val amount = borrowedAmountStr.toInt()
                            BalanceHelper.updateTotaldebt(borrowerID, amount, null)
                            BalanceHelper.updateTotalreceivable(lenderID, amount, null)
                            runOnUiThread(java.lang.Runnable {
                                Toast.makeText(this, "Borrowed successfully", Toast.LENGTH_SHORT)
                                    .show()
                                dialog.dismiss()
                            })
                        }).addOnFailureListener({ e ->
                            runOnUiThread(java.lang.Runnable {
                                Toast.makeText(this, "Failed to Borrow", Toast.LENGTH_SHORT).show()
                                if (dialogProgressBar != null) dialogProgressBar.setVisibility(
                                    android.view.View.GONE
                                )
                                borrowBtn.setEnabled(true)
                                cancelBtn.setEnabled(true)
                            })
                        })
                })
        }
    }

    private fun getUserIDByName(
        name: kotlin.String,
        callback: com.waray.spendhound.MainActivity.UserIDCallback
    ) {
        FirebaseDatabase.getInstance().getReference("users")
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (userSnapshot in dataSnapshot.getChildren()) {
                        if (name == userSnapshot.child("username")
                                .getValue(kotlin.String::class.java)
                        ) {
                            callback.onUserIDRetrieved(userSnapshot.getKey())
                            return
                        }
                    }
                    callback.onUserIDRetrieved(null)
                }

                public override fun onCancelled(databaseError: DatabaseError) {
                    callback.onUserIDRetrieved(null)
                }
            })
    }

    private interface UserIDCallback {
        fun onUserIDRetrieved(userID: kotlin.String?)
    }

    private fun showCreateGroupDialog() {
        DeclareDatabase.getDatabaseReference()
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val usernamesList: kotlin.collections.MutableList<kotlin.String?> =
                        java.util.ArrayList<kotlin.String?>()
                    val userIdsList: kotlin.collections.MutableList<kotlin.String?> =
                        java.util.ArrayList<kotlin.String?>()
                    val currentUserId: kotlin.String? = java.util.Objects.requireNonNull<T?>(
                        FirebaseAuth.getInstance().getCurrentUser()
                    ).getUid()
                    for (userSnapshot in dataSnapshot.getChildren()) {
                        val username: kotlin.String? =
                            userSnapshot.child("username").getValue(kotlin.String::class.java)
                        val uid: kotlin.String? = userSnapshot.getKey()
                        if (username != null && uid != null && (uid != currentUserId)) {
                            usernamesList.add(username)
                            userIdsList.add(uid)
                        }
                    }
                    val builder = android.app.AlertDialog.Builder(this@MainActivity)
                    val dialogView: android.view.View = LayoutInflater.from(this@MainActivity)
                        .inflate(R.layout.dialog_create_group, null)
                    builder.setView(dialogView)
                    val dialog = builder.create()
                    val groupNameEditText: EditText =
                        dialogView.findViewById<EditText>(R.id.groupNameEditText)
                    val usersCheckboxContainer: LinearLayout =
                        dialogView.findViewById<LinearLayout>(R.id.usersCheckboxContainer)
                    val cancelBtn =
                        dialogView.findViewById<android.widget.Button>(R.id.cancelGroupBtn)
                    val createBtn =
                        dialogView.findViewById<android.widget.Button>(R.id.createGroupBtn)
                    val checkBoxes: kotlin.collections.MutableList<CheckBox?> =
                        java.util.ArrayList<CheckBox?>()
                    for (username in usernamesList) {
                        val checkBox: CheckBox = CheckBox(this@MainActivity)
                        checkBox.setText(username)
                        checkBox.setTextColor(getResources().getColor(R.color.darkBlue))
                        checkBox.setPadding(8, 8, 8, 8)
                        checkBoxes.add(checkBox)
                        usersCheckboxContainer.addView(checkBox)
                    }
                    cancelBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> dialog.dismiss() })
                    createBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
                        val groupName = groupNameEditText.getText().toString().trim { it <= ' ' }
                        if (groupName.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "Please enter a group name",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }
                        val selectedMemberUids: kotlin.collections.MutableList<kotlin.String?> =
                            java.util.ArrayList<kotlin.String?>()
                        val selectedMemberDisplayNames: kotlin.collections.MutableList<kotlin.String?> =
                            java.util.ArrayList<kotlin.String?>()
                        selectedMemberUids.add(currentUserId)
                        selectedMemberDisplayNames.add(if (currentNickname!!.isEmpty()) "Me" else currentNickname)
                        for (i in checkBoxes.indices) {
                            if (checkBoxes.get(i).isChecked()) {
                                selectedMemberUids.add(userIdsList.get(i))
                                selectedMemberDisplayNames.add(usernamesList.get(i))
                            }
                        }
                        if (selectedMemberUids.size <= 1) {
                            Toast.makeText(
                                this@MainActivity,
                                "Please select at least one member",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }
                        saveGroupToDatabase(
                            groupName,
                            selectedMemberUids,
                            selectedMemberDisplayNames,
                            currentUserId
                        )
                        dialog.dismiss()
                    })
                    dialog.show()
                }

                public override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Failed to load users", Toast.LENGTH_SHORT)
                        .show()
                }
            })
    }

    private fun saveGroupToDatabase(
        groupName: kotlin.String?,
        memberUids: kotlin.collections.MutableList<kotlin.String?>?,
        memberDisplayNames: kotlin.collections.MutableList<kotlin.String?>?,
        currentUserId: kotlin.String?
    ) {
        val groupsRef: DatabaseReference = DeclareDatabase.getDBRefGroups().child(currentUserId)
        val groupId: kotlin.String? = groupsRef.push().getKey()
        if (groupId != null) {
            val newGroup =
                PayerGroup(groupId, groupName, memberUids, currentUserId, memberDisplayNames)
            groupsRef.child(groupId).setValue(newGroup).addOnSuccessListener({ aVoid ->
                Toast.makeText(
                    this@MainActivity,
                    "Group created successfully",
                    Toast.LENGTH_SHORT
                ).show()
            })
        }
    }

    fun getTotalMonthSpends(callback: java.lang.Runnable?) {
        if (currentNickname == null || currentNickname!!.isEmpty()) getCurrentNickname(
            CurrentNicknameCallback { nickname: kotlin.String? ->
                fetchTotalMonthSpends(
                    nickname,
                    callback
                )
            })
        else fetchTotalMonthSpends(currentNickname, callback)
    }

    private fun fetchTotalMonthSpends(username: kotlin.String?, callback: java.lang.Runnable?) {
        val monthYearRef: DatabaseReference = DeclareDatabase.getDBRefTransaction().child(
            java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
                .format(java.util.Calendar.getInstance().getTime())
        )
        totalMonthSpends = 0.0
        monthYearRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (daySnapshot in dataSnapshot.getChildren()) {
                    for (timeSnapshot in daySnapshot.getChildren()) {
                        val transaction: com.waray.spendhound.Transaction? =
                            timeSnapshot.getValue(com.waray.spendhound.Transaction::class.java)
                        if (transaction != null && isUserInvolved(
                                transaction,
                                username
                            )
                        ) totalMonthSpends += transaction.getPaymentAmount()
                    }
                }
                val tv: TextView? = findViewById<TextView?>(R.id.totalMonthSpends)
                if (tv != null) tv.setText(CurrencyUtils.formatAmountWithCurrency(totalMonthSpends))
                if (callback != null) callback.run()
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                if (callback != null) callback.run()
            }
        })
    }

    @SuppressLint("DefaultLocale")
    fun getEverydaySpends(callback: java.lang.Runnable?) {
        if (currentNickname == null || currentNickname!!.isEmpty()) getCurrentNickname(
            CurrentNicknameCallback { nickname: kotlin.String? ->
                fetchEverydaySpends(
                    nickname,
                    callback
                )
            })
        else fetchEverydaySpends(currentNickname, callback)
    }

    @SuppressLint("DefaultLocale")
    private fun fetchEverydaySpends(username: kotlin.String?, callback: java.lang.Runnable?) {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val daysFetched = java.util.concurrent.atomic.AtomicInteger(0)
        for (i in 0..6) {
            val currentMonthYear =
                java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
                    .format(calendar.getTime())
            val currentDay = java.text.SimpleDateFormat("dd", java.util.Locale.getDefault())
                .format(calendar.getTime())
            val dayIndex = i
            DeclareDatabase.getDBRefTransaction().child(currentMonthYear).child(currentDay)
                .addListenerForSingleValueEvent(object : ValueEventListener() {
                    public override fun onDataChange(dataSnapshot: DataSnapshot) {
                        var ds = 0.0
                        for (ts in dataSnapshot.getChildren()) {
                            val t: com.waray.spendhound.Transaction? =
                                ts.getValue(com.waray.spendhound.Transaction::class.java)
                            if (t != null && isUserInvolved(t, username)) ds += t.getPaymentAmount()
                        }
                        setViewHeightForDay(dayIndex, ds)
                        if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run()
                    }

                    public override fun onCancelled(databaseError: DatabaseError) {
                        if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run()
                    }
                })
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
    }

    fun getEverydaySpendsForWeek(weekStart: java.util.Calendar, callback: java.lang.Runnable?) {
        if (currentNickname == null || currentNickname!!.isEmpty()) getCurrentNickname(
            CurrentNicknameCallback { nickname: kotlin.String? ->
                fetchEverydaySpendsForWeek(
                    weekStart,
                    nickname,
                    callback
                )
            })
        else fetchEverydaySpendsForWeek(weekStart, currentNickname, callback)
    }

    private fun fetchEverydaySpendsForWeek(
        weekStart: java.util.Calendar,
        username: kotlin.String?,
        callback: java.lang.Runnable?
    ) {
        val calendar = weekStart.clone() as java.util.Calendar
        val daysFetched = java.util.concurrent.atomic.AtomicInteger(0)
        for (i in 0..6) {
            val cmy = java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
                .format(calendar.getTime())
            val cd = java.text.SimpleDateFormat("dd", java.util.Locale.getDefault())
                .format(calendar.getTime())
            val dayIndex = i
            DeclareDatabase.getDBRefTransaction().child(cmy).child(cd)
                .addListenerForSingleValueEvent(object : ValueEventListener() {
                    public override fun onDataChange(dataSnapshot: DataSnapshot) {
                        var ds = 0.0
                        for (ts in dataSnapshot.getChildren()) {
                            val t: com.waray.spendhound.Transaction? =
                                ts.getValue(com.waray.spendhound.Transaction::class.java)
                            if (t != null && isUserInvolved(t, username)) ds += t.getPaymentAmount()
                        }
                        setViewHeightForDay(dayIndex, ds)
                        if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run()
                    }

                    public override fun onCancelled(de: DatabaseError) {
                        if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run()
                    }
                })
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
    }

    fun setViewHeightForDay(day: Int, dailySpends: kotlin.Double) {
        val dsString = CurrencyUtils.formatAmount(dailySpends)
        val ids = kotlin.intArrayOf(
            R.id.totalday7,
            R.id.totalday6,
            R.id.totalday5,
            R.id.totalday4,
            R.id.totalday3,
            R.id.totalday2,
            R.id.totalday1
        )
        val barIds = kotlin.intArrayOf(
            R.id.day7_bar,
            R.id.day6_bar,
            R.id.day5_bar,
            R.id.day4_bar,
            R.id.day3_bar,
            R.id.day2_bar,
            R.id.day1_bar
        )

        val tv: TextView? = findViewById<TextView?>(ids[day])
        if (tv != null) tv.setText(dsString)
        val h =
            if (dailySpends >= 1000) 300 else if (dailySpends <= 50) 17 else (dailySpends / 3).toInt()
        val v = findViewById<android.view.View?>(barIds[day])
        if (v != null) {
            val lp: ViewGroup.LayoutParams = v.getLayoutParams()
            lp.height = h
            v.setLayoutParams(lp)
        }
    }

    fun getRecentTransaction(callback: java.lang.Runnable?) {
        if (currentNickname == null || currentNickname!!.isEmpty()) getCurrentNickname(
            CurrentNicknameCallback { nickname: kotlin.String? ->
                fetchRecentTransactions(
                    nickname,
                    callback
                )
            })
        else fetchRecentTransactions(currentNickname, callback)
    }

    private fun fetchRecentTransactions(username: kotlin.String?, callback: java.lang.Runnable?) {
        recentTransactionList.clear()
        val calendar = java.util.Calendar.getInstance()
        val daysFetched = java.util.concurrent.atomic.AtomicInteger(0)
        for (i in 0..6) {
            val cmy = java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
                .format(calendar.getTime())
            val cd = java.text.SimpleDateFormat("dd", java.util.Locale.getDefault())
                .format(calendar.getTime())
            val fmy = cmy
            val fd = cd
            DeclareDatabase.getDBRefTransaction().child(cmy).child(cd)
                .addListenerForSingleValueEvent(object : ValueEventListener() {
                    public override fun onDataChange(ds: DataSnapshot) {
                        for (ts in ds.getChildren()) {
                            val t: com.waray.spendhound.Transaction? =
                                ts.getValue(com.waray.spendhound.Transaction::class.java)
                            if (t != null && isUserInvolved(t, username)) {
                                val tk: kotlin.String? = ts.getKey()
                                val p: kotlin.Array<kotlin.String?> =
                                    fmy.split("-".toRegex()).dropLastWhile { it.isEmpty() }
                                        .toTypedArray()
                                recentTransactionList.add(
                                    RecentTransaction(
                                        p[0] + " - " + fd,
                                        t.getTransactionType(),
                                        t.getMultilineStr(),
                                        CurrencyUtils.formatAmountWithCurrency(t.getPaymentAmount()),
                                        getTransactionIcon(t.getTransactionType()),
                                        p[1] + "-" + p[0] + "-" + fd + " " + tk,
                                        if (t.getPayorsDisplayNames() != null) t.getPayorsDisplayNames() else t.getPayorsList(),
                                        t.getPayorsList(),
                                        t.getAmountsPaidList(),
                                        t.getTotalIndividualPayment(),
                                        null,
                                        if (t.getPosterDisplayName() != null) t.getPosterDisplayName() else t.getUsernamePost(),
                                        t.getUsernamePost(),
                                        fmy,
                                        fd,
                                        tk
                                    )
                                )
                            }
                        }
                        if (daysFetched.incrementAndGet() == 7) {
                            recentTransactionList.sort(java.util.Comparator { t1: RecentTransaction?, t2: RecentTransaction? ->
                                if (t1!!.getSortDateTime() != null && t2!!.getSortDateTime() != null) t2.getSortDateTime()
                                    .compareTo(t1.getSortDateTime()) else 0
                            })
                            val rv: RecyclerView? =
                                findViewById<RecyclerView?>(R.id.transactionListRecycler)
                            if (rv != null) {
                                recentTransactionAdapter = RecentTransactionAdapter(
                                    recentTransactionList,
                                    OnTransactionClickListener { transaction: RecentTransaction? ->
                                        this@MainActivity.onTransactionTap(
                                            transaction!!
                                        )
                                    })
                                rv.setAdapter(recentTransactionAdapter)
                                rv.setLayoutManager(LinearLayoutManager(this@MainActivity))

                                // Preload images for all transactions in the list
                                recentTransactionAdapter!!.preloadAllImages(this@MainActivity)
                            }
                            if (callback != null) callback.run()
                        }
                    }

                    public override fun onCancelled(de: DatabaseError) {
                        if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run()
                    }
                })
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
    }

    private fun getTransactionIcon(type: kotlin.String?): Int {
        if ("Electricity" == type) return R.drawable.lightning_bolt
        if ("Water" == type) return R.drawable.faucet
        if ("Rent" == type) return R.drawable.house
        if ("Internet" == type) return R.drawable.internet
        if ("Online Shopping" == type) return R.drawable.online_shopping
        if ("Travel" == type) return R.drawable.travel
        if ("Groceries" == type) return R.drawable.groceries
        if ("Foods" == type) return R.drawable.hamburger
        if ("House Necessity" == type) return R.drawable.necessities
        if ("Transportation" == type) return R.drawable.vehicles
        return R.drawable.others
    }

    fun getDebtList(
        selectedStatus: kotlin.String?,
        callback: DebtNumCallback,
        actionListener: OnBorrowerActionListener?
    ) {
        debtList.clear()
        val currentUserId: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        DeclareDatabase.getDBRefBorrows()
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(ds: DataSnapshot) {
                    for (ms in ds.getChildren()) {
                        val my: kotlin.String? = ms.getKey()
                        for (days in ms.getChildren()) {
                            val d: kotlin.String? = days.getKey()
                            for (bs in days.getChildren()) {
                                val bnt: BorrowNowTransaction? =
                                    bs.getValue(BorrowNowTransaction::class.java)
                                if (bnt != null && bnt.getBorrowerID() == currentUserId) {
                                    if ((bnt.getStatus() != "Removed") && (bnt.getStatus() != "Payment Denied") && shouldIncludeForDebtStatus(
                                            bnt.getStatus(),
                                            selectedStatus
                                        )
                                    ) addDebtTransactionFromBorrowNow(bnt, my, d, bs.getKey())
                                }
                            }
                        }
                    }
                    debtList.sort(java.util.Comparator { o1: BorrowTransaction?, o2: BorrowTransaction? ->
                        try {
                            val f =
                                java.text.SimpleDateFormat("MMM-dd-yyyy", java.util.Locale.ENGLISH)
                            return@sort f.parse(o2!!.getDate()).compareTo(f.parse(o1!!.getDate()))
                        } catch (e: java.lang.Exception) {
                            return@sort 0
                        }
                    })
                    val rv: RecyclerView? = findViewById<RecyclerView?>(R.id.debtRecyclerList)
                    if (rv != null) {
                        rv.setAdapter(
                            DebtTransactionAdapter(
                                debtList,
                                if (actionListener != null) actionListener else object :
                                    OnBorrowerActionListener {
                                    override fun onPayClicked(t: BorrowTransaction?, p: Int) {}
                                    override fun onRemoveClicked(t: BorrowTransaction?, p: Int) {}
                                    override fun onTryAgainClicked(t: BorrowTransaction?, p: Int) {}
                                })
                        )
                        rv.setLayoutManager(LinearLayoutManager(this@MainActivity))
                    }
                    debtNum = debtList.size
                    callback.onDebtNumReceived(debtNum)
                }

                public override fun onCancelled(e: DatabaseError) {}
            })
    }

    private fun addDebtTransactionFromBorrowNow(
        borrowNowTransaction: BorrowNowTransaction,
        monthYear: kotlin.String?,
        day: kotlin.String?,
        borrowId: kotlin.String?
    ) {
        val date = changeFormatDate(borrowNowTransaction.getDate())
        val psd = if (borrowNowTransaction.getPaymentSentDate() > 0) java.text.SimpleDateFormat(
            "MMM-dd-yyyy",
            java.util.Locale.ENGLISH
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
        selectedMonth: kotlin.String?,
        selectedStatus: kotlin.String?,
        callback: DebtNumCallback,
        actionListener: OnBorrowerActionListener?
    ) {
        debtList.clear()
        if (selectedMonth == null || selectedMonth == "All") return
        val uid: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        DeclareDatabase.getDBRefBorrows().child(selectedMonth)
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(ds: DataSnapshot) {
                    for (dayS in ds.getChildren()) {
                        val d: kotlin.String? = dayS.getKey()
                        for (bs in dayS.getChildren()) {
                            val bnt: BorrowNowTransaction? =
                                bs.getValue(BorrowNowTransaction::class.java)
                            if (bnt != null && bnt.getBorrowerID() == uid && (bnt.getStatus() != "Removed") && (bnt.getStatus() != "Payment Denied") && shouldIncludeForDebtStatus(
                                    bnt.getStatus(),
                                    selectedStatus
                                )
                            ) addDebtTransactionFromBorrowNow(bnt, selectedMonth, d, bs.getKey())
                        }
                    }
                    debtList.sort(java.util.Comparator { o1: BorrowTransaction?, o2: BorrowTransaction? ->
                        try {
                            val f =
                                java.text.SimpleDateFormat("MMM-dd-yyyy", java.util.Locale.ENGLISH)
                            return@sort f.parse(o2!!.getDate()).compareTo(f.parse(o1!!.getDate()))
                        } catch (e: java.lang.Exception) {
                            return@sort 0
                        }
                    })
                    val rv: RecyclerView? = findViewById<RecyclerView?>(R.id.debtRecyclerList)
                    if (rv != null) {
                        rv.setAdapter(
                            DebtTransactionAdapter(
                                debtList,
                                if (actionListener != null) actionListener else object :
                                    OnBorrowerActionListener {
                                    override fun onPayClicked(t: BorrowTransaction?, p: Int) {}
                                    override fun onRemoveClicked(t: BorrowTransaction?, p: Int) {}
                                    override fun onTryAgainClicked(t: BorrowTransaction?, p: Int) {}
                                })
                        )
                        rv.setLayoutManager(LinearLayoutManager(this@MainActivity))
                    }
                    debtNum = debtList.size
                    callback.onDebtNumReceived(debtNum)
                }

                public override fun onCancelled(e: DatabaseError) {}
            })
    }

    fun getOwedList(
        selectedStatus: kotlin.String?,
        callback: OwedNumCallback,
        actionListener: OnLenderActionListener?
    ) {
        owedList.clear()
        val uid: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        DeclareDatabase.getDBRefBorrows()
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(ds: DataSnapshot) {
                    for (ms in ds.getChildren()) {
                        val my: kotlin.String? = ms.getKey()
                        for (days in ms.getChildren()) {
                            val d: kotlin.String? = days.getKey()
                            for (bs in days.getChildren()) {
                                val bnt: BorrowNowTransaction? =
                                    bs.getValue(BorrowNowTransaction::class.java)
                                if (bnt != null && bnt.getLenderID() == uid && (bnt.getStatus() != "Declined") && (bnt.getStatus() != "Payment Denied") && (bnt.getStatus() != "Removed") && shouldIncludeForStatus(
                                        bnt.getStatus(),
                                        selectedStatus
                                    )
                                ) addOwedTransactionFromBorrowNow(bnt, my, d, bs.getKey())
                            }
                        }
                    }
                    owedList.sort(java.util.Comparator { o1: OwedTransaction?, o2: OwedTransaction? ->
                        try {
                            val f =
                                java.text.SimpleDateFormat("MMM-dd-yyyy", java.util.Locale.ENGLISH)
                            return@sort f.parse(o2!!.getDate()).compareTo(f.parse(o1!!.getDate()))
                        } catch (e: java.lang.Exception) {
                            return@sort 0
                        }
                    })
                    val rv: RecyclerView? = findViewById<RecyclerView?>(R.id.owedRecyclerList)
                    if (rv != null) {
                        rv.setAdapter(
                            OwedTransactionAdapter(
                                owedList,
                                if (actionListener != null) actionListener else object :
                                    OnLenderActionListener {
                                    override fun onNotYetClicked(t: OwedTransaction?, p: Int) {}
                                    override fun onReceivedClicked(t: OwedTransaction?, p: Int) {}
                                    override fun onDeclineClicked(t: OwedTransaction?, p: Int) {}
                                    override fun onApprovedClicked(t: OwedTransaction?, p: Int) {}
                                })
                        )
                        rv.setLayoutManager(LinearLayoutManager(this@MainActivity))
                    }
                    owedNum = owedList.size
                    callback.onOwedNumReceived(owedNum)
                }

                public override fun onCancelled(e: DatabaseError) {}
            })
    }

    fun getCurrentNickname(callback: CurrentNicknameCallback) {
        val uid: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser())
                .getUid()
        DeclareDatabase.getDatabaseReference().child(uid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                fun onDataChange(ds: DataSnapshot) {
                    currentNickname =
                        if (ds.exists()) ds.getValue(kotlin.String::class.java) else ""
                    callback.onCurrentNicknameReceived(currentNickname)
                }

                public override fun onCancelled(e: DatabaseError) {
                    callback.onCurrentNicknameReceived("")
                }
            })
    }

    fun changeFormatDate(date: kotlin.String): kotlin.String? {
        try {
            val d = java.text.SimpleDateFormat("MMMM-dd-yyyy", java.util.Locale.ENGLISH).parse(date)
            return java.text.SimpleDateFormat("MMM-dd-yyyy", java.util.Locale.getDefault())
                .format(d)
        } catch (e: java.lang.Exception) {
            return date
        }
    }

    private fun shouldIncludeForStatus(s: kotlin.String?, ss: kotlin.String?): kotlin.Boolean {
        if ("All" == ss) return true
        if ("Pending" == ss) return "Pending Payment" == s || "For Lender Approval" == s
        return s == ss
    }

    private fun shouldIncludeForDebtStatus(s: kotlin.String?, ss: kotlin.String?): kotlin.Boolean {
        if ("All" == ss) return true
        if ("Pending" == ss) return "Pending Payment" == s || "For Lender Approval" == s || "Declined" == s
        return s == ss
    }

    private fun addOwedTransactionFromBorrowNow(
        bnt: BorrowNowTransaction,
        my: kotlin.String?,
        d: kotlin.String?,
        bid: kotlin.String?
    ) {
        val date = changeFormatDate(bnt.getDate())
        val psd = if (bnt.getPaymentSentDate() > 0) java.text.SimpleDateFormat(
            "MMM-dd-yyyy",
            java.util.Locale.ENGLISH
        ).format(java.util.Date(bnt.getPaymentSentDate())) else null
        owedList.add(
            OwedTransaction(
                date,
                if (bnt.getBorrowerName() != null) bnt.getBorrowerName() else "Unknown",
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
        sm: kotlin.String?,
        ss: kotlin.String?,
        callback: OwedNumCallback,
        actionListener: OnLenderActionListener?
    ) {
        owedList.clear()
        if (sm == null || sm == "All") return
        val uid: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        DeclareDatabase.getDBRefBorrows().child(sm)
            .addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(ds: DataSnapshot) {
                    for (dayS in ds.getChildren()) {
                        val d: kotlin.String? = dayS.getKey()
                        for (bs in dayS.getChildren()) {
                            val bnt: BorrowNowTransaction? =
                                bs.getValue(BorrowNowTransaction::class.java)
                            if (bnt != null && bnt.getLenderID() == uid && (bnt.getStatus() != "Declined") && (bnt.getStatus() != "Payment Denied") && (bnt.getStatus() != "Removed") && shouldIncludeForStatus(
                                    bnt.getStatus(),
                                    ss
                                )
                            ) addOwedTransactionFromBorrowNow(bnt, sm, d, bs.getKey())
                        }
                    }
                    owedList.sort(java.util.Comparator { o1: OwedTransaction?, o2: OwedTransaction? ->
                        try {
                            val f =
                                java.text.SimpleDateFormat("MMM-dd-yyyy", java.util.Locale.ENGLISH)
                            return@sort f.parse(o2!!.getDate()).compareTo(f.parse(o1!!.getDate()))
                        } catch (e: java.lang.Exception) {
                            return@sort 0
                        }
                    })
                    val rv: RecyclerView? = findViewById<RecyclerView?>(R.id.owedRecyclerList)
                    if (rv != null) {
                        rv.setAdapter(
                            OwedTransactionAdapter(
                                owedList,
                                if (actionListener != null) actionListener else object :
                                    OnLenderActionListener {
                                    override fun onNotYetClicked(t: OwedTransaction?, p: Int) {}
                                    override fun onReceivedClicked(t: OwedTransaction?, p: Int) {}
                                    override fun onDeclineClicked(t: OwedTransaction?, p: Int) {}
                                    override fun onApprovedClicked(t: OwedTransaction?, p: Int) {}
                                })
                        )
                        rv.setLayoutManager(LinearLayoutManager(this@MainActivity))
                    }
                    owedNum = owedList.size
                    callback.onOwedNumReceived(owedNum)
                }

                public override fun onCancelled(e: DatabaseError) {}
            })
    }
}
