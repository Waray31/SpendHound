package com.waray.spendhound.ui.transactions

import com.google.firebase.auth.FirebaseAuth

class TransactionsFragment : androidx.fragment.app.Fragment() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: java.util.ArrayList<RecentTransaction?>? = null
    private var monthSpinner: Spinner? = null
    private var groupSpinner: Spinner? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: FirebaseAuth? = null
    private var currentNickname: kotlin.String? = ""
    private var availableMonths: kotlin.collections.MutableList<kotlin.String>? = null
    private var selectedMonth: kotlin.String? = null

    private var groupNames: kotlin.collections.MutableList<kotlin.String>? = null
    private var groupIds: kotlin.collections.MutableList<kotlin.String?>? = null
    private var selectedGroupId: kotlin.String? = "All"
    private var groupAdapter: SpinnerItemMonths? = null

    // Status Tabs
    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var pendingTabTV: TextView? = null
    private var selectedStatusTab = "All"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        val root: android.view.View =
            inflater.inflate(R.layout.fragment_transactions, container, false)

        mAuth = DeclareDatabase.getAuth()
        transactionList = java.util.ArrayList<RecentTransaction?>()
        availableMonths = java.util.ArrayList<kotlin.String>()
        groupNames = java.util.ArrayList<kotlin.String>()
        groupIds = java.util.ArrayList<kotlin.String?>()

        initViews(root)


        // Initialize with default option immediately
        groupNames!!.add("All group")
        groupIds!!.add("All")
        setupGroupSpinner()

        getCurrentNickname()
        loadUserGroups()

        return root
    }

    private fun initViews(root: android.view.View) {
        recyclerView = root.findViewById<RecyclerView>(R.id.allTransactionsRecyclerView)
        monthSpinner = root.findViewById<Spinner?>(R.id.monthSpinner)
        groupSpinner = root.findViewById<Spinner?>(R.id.groupSpinner)
        currentMonthTextView = root.findViewById<TextView>(R.id.currentMonthTextView)
        transactionCountTextView = root.findViewById<TextView>(R.id.transactionCountTextView)
        loadingProgressBar = root.findViewById<ProgressBar?>(R.id.loadingProgressBar)
        emptyStateLayout = root.findViewById<LinearLayout?>(R.id.emptyStateLayout)

        // Status Tabs
        allTabTV = root.findViewById<TextView?>(R.id.allTabTV)
        paidTabTV = root.findViewById<TextView?>(R.id.paidTabTV)
        unpaidTabTV = root.findViewById<TextView?>(R.id.unpaidTabTV)
        pendingTabTV = root.findViewById<TextView?>(R.id.pendingTabTV)

        setupStatusTabs()

        adapter = RecentTransactionAdapter(
            transactionList,
            OnTransactionClickListener { transaction: RecentTransaction? ->
                if (!transaction.isExpanded()) {
                    val mainActivity: MainActivity? = getActivity() as MainActivity?
                    if (mainActivity != null) {
                        mainActivity.unhideNavigation()
                    }
                }
            })
        recyclerView.setLayoutManager(LinearLayoutManager(getContext()))
        recyclerView.setAdapter(adapter)
    }

    private fun setupStatusTabs() {
        setStatusTabSelected(allTabTV)

        allTabTV.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            selectedStatusTab = "All"
            setStatusTabSelected(allTabTV)
            refreshTransactions()
        })

        paidTabTV.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            selectedStatusTab = "Paid"
            setStatusTabSelected(paidTabTV)
            refreshTransactions()
        })

        unpaidTabTV.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            selectedStatusTab = "Unpaid"
            setStatusTabSelected(unpaidTabTV)
            refreshTransactions()
        })

        pendingTabTV.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            selectedStatusTab = "Pending"
            setStatusTabSelected(pendingTabTV)
            refreshTransactions()
        })
    }

    private fun setStatusTabSelected(selectedTab: TextView) {
        if (allTabTV == null) return

        // Reset all tabs
        allTabTV.setBackgroundResource(0)
        paidTabTV.setBackgroundResource(0)
        unpaidTabTV.setBackgroundResource(0)
        pendingTabTV.setBackgroundResource(0)

        // Set selected tab background
        selectedTab.setBackgroundResource(R.drawable.bg_status_tab_selected)
    }

    private fun refreshTransactions() {
        if (selectedMonth != null) {
            fetchTransactionsForMonth(selectedMonth!!)
        }
    }

    private fun getCurrentNickname() {
        val userId: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        val userRef: DatabaseReference = DeclareDatabase.getDatabaseReference().child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    currentNickname = snapshot.child("username").getValue(kotlin.String::class.java)
                    if (currentNickname == null) {
                        currentNickname = ""
                    }
                }
                loadAvailableMonths()
            }

            public override fun onCancelled(error: DatabaseError) {
                loadAvailableMonths()
            }
        })
    }

    private fun loadUserGroups() {
        val currentUid: kotlin.String? =
            java.util.Objects.requireNonNull<T?>(mAuth.getCurrentUser()).getUid()
        val groupsRef: DatabaseReference = DeclareDatabase.getDBRefGroups()

        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            public override fun onDataChange(snapshot: DataSnapshot) {
                groupNames!!.clear()
                groupIds!!.clear()

                groupNames!!.add("All group")
                groupIds!!.add("All")

                for (userSnapshot in snapshot.getChildren()) {
                    for (groupSnapshot in userSnapshot.getChildren()) {
                        val group: PayerGroup? = groupSnapshot.getValue(PayerGroup::class.java)
                        if (group != null && group.getMembers() != null && group.getMembers()
                                .contains(currentUid)
                        ) {
                            groupNames!!.add(group.getGroupName())
                            groupIds!!.add(group.getGroupId())
                        }
                    }
                }

                if (groupAdapter != null) {
                    groupAdapter.notifyDataSetChanged()
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                android.util.Log.e(
                    "TransactionsFragment",
                    "Error loading groups",
                    error.toException()
                )
            }
        })
    }

    private fun setupGroupSpinner() {
        if (getContext() == null || groupSpinner == null) return

        groupAdapter = SpinnerItemMonths(getContext(), groupNames)
        groupSpinner.setAdapter(groupAdapter)

        groupSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: kotlin.Long
            ) {
                selectedGroupId = groupIds!!.get(position)
                refreshTransactions()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    private fun loadAvailableMonths() {
        if (loadingProgressBar != null) loadingProgressBar.setVisibility(android.view.View.VISIBLE)

        val transRef: DatabaseReference = DeclareDatabase.getDBRefTransaction()
        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                val uniqueMonths: kotlin.collections.MutableSet<kotlin.String?> =
                    java.util.HashSet<kotlin.String?>()

                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val monthYear: kotlin.String? = monthSnapshot.getKey()
                    if (monthYear != null && !monthYear.isEmpty()) {
                        for (daySnapshot in monthSnapshot.getChildren()) {
                            for (timeSnapshot in daySnapshot.getChildren()) {
                                val transaction: com.waray.spendhound.Transaction? =
                                    timeSnapshot.getValue(com.waray.spendhound.Transaction::class.java)
                                if (transaction != null && isUserInvolved(
                                        transaction,
                                        currentNickname
                                    )
                                ) {
                                    uniqueMonths.add(monthYear)
                                    break
                                }
                            }
                            if (uniqueMonths.contains(monthYear)) break
                        }
                    }
                }

                availableMonths = java.util.ArrayList<kotlin.String>(uniqueMonths)
                java.util.Collections.sort<kotlin.String?>(
                    availableMonths,
                    java.util.Comparator { m1: kotlin.String?, m2: kotlin.String? ->
                        try {
                            val sdf = java.text.SimpleDateFormat(
                                "MMMM-yyyy",
                                java.util.Locale.getDefault()
                            )
                            return@sort sdf.parse(m2).compareTo(sdf.parse(m1))
                        } catch (e: java.lang.Exception) {
                            return@sort m2!!.compareTo(m1!!)
                        }
                    })

                setupMonthSpinner()
                if (loadingProgressBar != null) loadingProgressBar.setVisibility(android.view.View.GONE)
            }

            public override fun onCancelled(error: DatabaseError) {
                if (loadingProgressBar != null) loadingProgressBar.setVisibility(android.view.View.GONE)
            }
        })
    }

    private fun setupMonthSpinner() {
        if (getContext() == null || monthSpinner == null) return

        if (availableMonths!!.isEmpty()) {
            val calendar = java.util.Calendar.getInstance()
            val dateFormat = java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
            availableMonths!!.add(dateFormat.format(calendar.getTime()))
        }

        val spinnerAdapter: SpinnerItemMonths = SpinnerItemMonths(getContext(), availableMonths)
        monthSpinner.setAdapter(spinnerAdapter)

        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
        val currentMonth = dateFormat.format(calendar.getTime())

        var defaultPosition = 0
        for (i in availableMonths!!.indices) {
            if (availableMonths!!.get(i) == currentMonth) {
                defaultPosition = i
                break
            }
        }

        monthSpinner.setSelection(defaultPosition)
        selectedMonth = availableMonths!!.get(defaultPosition)
        updateMonthDisplay(selectedMonth!!)
        fetchTransactionsForMonth(selectedMonth!!)

        monthSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: kotlin.Long
            ) {
                val month = availableMonths!!.get(position)
                if (month != selectedMonth) {
                    selectedMonth = month
                    updateMonthDisplay(selectedMonth!!)
                    fetchTransactionsForMonth(selectedMonth!!)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    private fun updateMonthDisplay(monthYear: kotlin.String) {
        val displayMonth = monthYear.replace("-", " ")
        currentMonthTextView.setText(displayMonth)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchTransactionsForMonth(monthYear: kotlin.String) {
        if (loadingProgressBar != null) loadingProgressBar.setVisibility(android.view.View.VISIBLE)
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(android.view.View.GONE)
        transactionList!!.clear()
        adapter.notifyDataSetChanged()

        val monthRef: DatabaseReference = DeclareDatabase.getDBRefTransaction().child(monthYear)

        monthRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (daySnapshot in dataSnapshot.getChildren()) {
                    val day: kotlin.String? = daySnapshot.getKey()

                    for (timeSnapshot in daySnapshot.getChildren()) {
                        val transaction: com.waray.spendhound.Transaction? =
                            timeSnapshot.getValue(com.waray.spendhound.Transaction::class.java)
                        val timeKey: kotlin.String? = timeSnapshot.getKey()

                        if (transaction != null && isUserInvolved(transaction, currentNickname)) {
                            // Apply Group Filter

                            if ("All" != selectedGroupId) {
                                if (transaction.getGroupId() == null || transaction.getGroupId() != selectedGroupId) {
                                    continue
                                }
                            }

                            // Apply Status Filter
                            if (!matchesStatusFilter(transaction, selectedStatusTab)) {
                                continue
                            }

                            val parts: kotlin.Array<kotlin.String?> =
                                monthYear.split("-".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()
                            val month = parts[0]
                            val year = if (parts.size > 1) parts[1] else ""
                            val displayDate = month + " - " + day
                            val fullDateWithYear = month + " " + day + ", " + year
                            val sortDateTime = year + "-" + month + "-" + day + " " + timeKey

                            val transactionType = transaction.getTransactionType()
                            val details = transaction.getMultilineStr()
                            val paymentAmount = transaction.getPaymentAmount()
                            val paymentAmountStr: kotlin.String =
                                CurrencyUtils.formatAmountWithCurrency(paymentAmount)
                            val iconResource = getIconForTransactionType(transactionType)

                            var payorsList = transaction.getPayorsDisplayNames()
                            if (payorsList == null || payorsList.isEmpty()) {
                                payorsList = transaction.getPayorsList()
                            }
                            val payorUids = transaction.getPayorsList()
                            val amountsPaidList = transaction.getAmountsPaidList()
                            val totalIndividualPayment = transaction.getTotalIndividualPayment()

                            var createdBy = transaction.getPosterDisplayName()
                            if (createdBy == null || createdBy.isEmpty()) {
                                createdBy = transaction.getUsernamePost()
                            }
                            val createdByUid = transaction.getUsernamePost()

                            val recentTrans: RecentTransaction = RecentTransaction(
                                displayDate, transactionType, details, paymentAmountStr,
                                iconResource, sortDateTime, payorsList, payorUids,
                                amountsPaidList, totalIndividualPayment, fullDateWithYear,
                                createdBy, createdByUid, monthYear, day, timeKey
                            )
                            transactionList!!.add(recentTrans)
                        }
                    }
                }

                java.util.Collections.sort<RecentTransaction?>(
                    transactionList,
                    java.util.Comparator { t1: RecentTransaction?, t2: RecentTransaction? ->
                        val dateTime1: kotlin.String? = t1.getSortDateTime()
                        val dateTime2: kotlin.String? = t2.getSortDateTime()
                        if (dateTime1 != null && dateTime2 != null) {
                            return@sort dateTime2.compareTo(dateTime1)
                        }
                        0
                    })

                adapter.notifyDataSetChanged()
                // Preload images for the loaded transactions
                if (getContext() != null) {
                    adapter.preloadAllImages(getContext())
                }

                if (loadingProgressBar != null) loadingProgressBar.setVisibility(android.view.View.GONE)

                val count = transactionList!!.size
                transactionCountTextView.setText(count.toString() + (if (count == 1) " transaction" else " transactions"))

                if (transactionList!!.isEmpty()) {
                    if (emptyStateLayout != null) emptyStateLayout.setVisibility(android.view.View.VISIBLE)
                    recyclerView.setVisibility(android.view.View.GONE)
                } else {
                    if (emptyStateLayout != null) emptyStateLayout.setVisibility(android.view.View.GONE)
                    recyclerView.setVisibility(android.view.View.VISIBLE)
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                if (loadingProgressBar != null) loadingProgressBar.setVisibility(android.view.View.GONE)
            }
        })
    }

    private fun matchesStatusFilter(
        transaction: com.waray.spendhound.Transaction,
        statusFilter: kotlin.String?
    ): kotlin.Boolean {
        if ("All".equals(statusFilter, ignoreCase = true)) return true

        val paidAmounts = transaction.getAmountsPaidList()
        val totalToPay = transaction.getTotalIndividualPayment()

        if (paidAmounts == null || paidAmounts.isEmpty()) {
            return "Unpaid".equals(statusFilter, ignoreCase = true)
        }

        var allPaid = true
        var allUnpaid = true

        for (paid in paidAmounts) {
            if (paid < totalToPay) {
                allPaid = false
            }
            if (paid > 0) {
                allUnpaid = false
            }
        }

        val status: kotlin.String?
        if (allPaid) {
            status = "Paid"
        } else if (allUnpaid) {
            status = "Unpaid"
        } else {
            status = "Pending"
        }

        return status.equals(statusFilter, ignoreCase = true)
    }

    private fun getIconForTransactionType(transactionType: kotlin.String?): Int {
        if ("Electricity" == transactionType) return R.drawable.lightning_bolt
        else if ("Water" == transactionType) return R.drawable.faucet
        else if ("Rent" == transactionType) return R.drawable.house
        else if ("Internet" == transactionType) return R.drawable.internet
        else if ("Online Shopping" == transactionType) return R.drawable.online_shopping
        else if ("Travel" == transactionType) return R.drawable.travel
        else if ("Groceries" == transactionType) return R.drawable.groceries
        else if ("Foods" == transactionType) return R.drawable.hamburger
        else if ("House Necessity" == transactionType) return R.drawable.necessities
        else if ("Transportation" == transactionType) return R.drawable.vehicles
        else return R.drawable.others
    }

    private fun isUserInvolved(
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
}
