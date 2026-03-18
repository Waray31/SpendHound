package com.waray.spendhound

import com.google.firebase.auth.FirebaseAuth

class AddTransactionActivity : AppCompatActivity() {
    private var container: LinearLayout? = null
    private var groupsContainer: LinearLayout? = null
    private var btnAdd: android.widget.Button? = null
    private var btnAddGroup: android.widget.Button? = null
    private var addTransactionbtn: android.widget.Button? = null
    private var payorSpinner: Spinner? = null
    private var transactionTypeSpinner: Spinner? = null
    private var transactionType: kotlin.String? = null
    var paymentAmountStr: kotlin.String? = null
    var multilineStr: kotlin.String? = null
    private var progressBar: android.view.View? = null
    var usernames: kotlin.collections.MutableList<kotlin.String?>? = null
    var mAuth: FirebaseAuth? = null
    private var rows: kotlin.collections.MutableList<android.view.View>? = null
    private var groupViews: kotlin.collections.MutableList<android.view.View?>? = null
    private var payerGroups: kotlin.collections.MutableList<PayerGroup?>? = null
    var payorsList: kotlin.collections.MutableList<kotlin.String?>? = null // Now stores UIDs
    var payorsDisplayNames: kotlin.collections.MutableList<kotlin.String?>? =
        null // Display names for UI
    var amountsPaidList: kotlin.collections.MutableList<kotlin.Double?>? = null
    var totalAmountPaid: kotlin.Double = 0.0
    var paymentAmount: kotlin.Double? = null
    private var paymentAmountEditText: EditText? = null
    private var editTextTextMultiLine: EditText? = null
    private var individualPayment: TextView? = null
    private var totalIndividualPayment = 0.0
    private val totalBalanced = 0
    private val totalUnpaid = 0
    private val totalOwed = 0
    private val totalDept = 0
    var usernamePost: kotlin.String? = null // Now stores UID
    var posterDisplayName: kotlin.String? = null // Display name for UI
    private var currentUserId: kotlin.String? = null
    private val recentTransactionList = java.util.ArrayList<RecentTransaction?>()
    private var selectedGroup: PayerGroup? = null
    private var selectedGroupView: android.view.View? = null
    private var payorTooltipPopup: PopupWindow? = null
    private var firstRow: android.view.View? = null
    private var groupMemberUsernames: kotlin.collections.MutableList<kotlin.String?>? = null

    // Maps for UID-username lookups
    private val usernameToUidMap: kotlin.collections.MutableMap<kotlin.String?, kotlin.String?> =
        java.util.HashMap<kotlin.String?, kotlin.String?>()
    private val uidToUsernameMap: kotlin.collections.MutableMap<kotlin.String?, kotlin.String?> =
        java.util.HashMap<kotlin.String?, kotlin.String?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        // Get the Firebase Authentication instance
        mAuth = DeclareDatabase.getAuth()

        // Check the user's authentication state
        val currentUser: FirebaseUser? = mAuth.getCurrentUser()
        if (currentUser == null) {
            // User is not authenticated, you can redirect them to the login activity
            val intent: Intent = Intent(this@AddTransactionActivity, LoginActivity::class.java)
            startActivity(intent)
            finish() // Finish this activity to prevent returning to it when pressing back
            return
        }

        transactionTypeSpinner = findViewById<Spinner>(R.id.transactionType)
        val transactionTypes = getResources().getStringArray(R.array.transactionTypes_String)
        val adapter = SpinnerItem(this, java.util.Arrays.asList<kotlin.String?>(*transactionTypes))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        transactionTypeSpinner.setAdapter(adapter)

        container = findViewById<LinearLayout>(R.id.container)
        groupsContainer = findViewById<LinearLayout>(R.id.groupsContainer)
        btnAdd = findViewById<android.widget.Button?>(R.id.btnAdd)
        btnAddGroup = findViewById<android.widget.Button>(R.id.btnAddGroup)
        rows = java.util.ArrayList<android.view.View>()
        groupViews = java.util.ArrayList<android.view.View?>()
        payerGroups = java.util.ArrayList<PayerGroup?>()
        progressBar = findViewById<android.view.View>(R.id.progressBar)
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid()

        fetchUsernamesAndSetupInitialRow()
        loadExistingGroups()

        // Initially disable btnAdd and show tooltip since no group is selected
        btnAdd!!.setEnabled(false)
        btnAdd!!.setAlpha(0.5f)
        showPayorTooltip()

        btnAdd!!.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            if (selectedGroup == null) {
                // Show tooltip if no group is selected
                showPayorTooltip()
                return@setOnClickListener
            }
            if (groupMemberUsernames != null && rows!!.size < groupMemberUsernames!!.size - 1) {
                addRow()
            } else {
                Toast.makeText(
                    this@AddTransactionActivity,
                    "You can't add more payors.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        addTransactionbtn = findViewById<android.widget.Button>(R.id.addTransactionbtn)
        addTransactionbtn!!.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> addTransaction() })

        btnAddGroup!!.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> showCreateGroupDialog() })

        paymentAmountEditText = findViewById<EditText>(R.id.paymentAmount)
        individualPayment = findViewById<TextView>(R.id.individualPayment)
        setupIndividualPaymentCalculator()

        detailsCharacterCount()
        exitEditText()
    }

    private fun fetchUsernamesAndSetupInitialRow() {
        val databaseReference: DatabaseReference = DeclareDatabase.getDatabaseReference()
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                usernames = java.util.ArrayList<kotlin.String?>()
                usernames!!.add("Select a payor:")
                usernameToUidMap.clear()
                uidToUsernameMap.clear()

                for (userSnapshot in dataSnapshot.getChildren()) {
                    val username: kotlin.String? =
                        userSnapshot.child("username").getValue(kotlin.String::class.java)
                    val uid: kotlin.String? = userSnapshot.getKey()
                    if (username != null && uid != null) {
                        usernames!!.add(username)
                        // Store both mappings for UID lookup
                        usernameToUidMap.put(username, uid)
                        uidToUsernameMap.put(uid, username)
                        // Update the global UserHelper cache as well
                        UserHelper.updateCache(uid, username)
                    }
                }
                // Add the initial row but hide it (no group selected initially)
                addRow()
                if (rows!!.size > 0) {
                    firstRow = rows!!.get(0)
                    firstRow!!.setVisibility(android.view.View.GONE)
                }
                // Keep btnAdd disabled until a group is selected
                btnAdd!!.setEnabled(false)
                btnAdd!!.setAlpha(0.5f)
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                android.util.Log.e(
                    "FirebaseDatabase",
                    "Database read error occurred: " + databaseError.getMessage()
                )
                Toast.makeText(getApplicationContext(), "Failed to load users.", Toast.LENGTH_LONG)
                    .show()
            }
        })
    }

    private fun addRow() {
        val inflater: LayoutInflater = LayoutInflater.from(this)
        val row: android.view.View = inflater.inflate(R.layout.row_layout, container, false)

        payorSpinner = row.findViewById<Spinner>(R.id.payor)

        // Use group members if a group is selected, otherwise use all usernames
        val spinnerUsernames =
            if (groupMemberUsernames != null && !groupMemberUsernames!!.isEmpty())
                groupMemberUsernames
            else
                usernames

        if (spinnerUsernames != null && !spinnerUsernames.isEmpty()) {
            val adapter = SpinnerItem(this@AddTransactionActivity, spinnerUsernames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            payorSpinner.setAdapter(adapter)
        }

        val btnMinus = row.findViewById<android.widget.Button>(R.id.closeBtn)
        btnMinus.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            removeRow(
                row
            )
        })

        val roundedDrawable = getResources().getDrawable(R.drawable.rounded_alternating_row)
        ViewCompat.setBackground(row, roundedDrawable)

        rows!!.add(row)
        container.addView(row)
    }

    private fun removeRow(row: android.view.View?) {
        container.removeView(row)
        rows!!.remove(row!!)
    }

    private fun addTransaction() {
        progressBar!!.setVisibility(android.view.View.VISIBLE)
        transactionType = transactionTypeSpinner.getSelectedItem().toString()
        paymentAmountEditText = findViewById<EditText>(R.id.paymentAmount)
        editTextTextMultiLine = findViewById<EditText>(R.id.editTextTextMultiLine)

        paymentAmountStr = paymentAmountEditText.getText().toString()
        multilineStr = editTextTextMultiLine.getText().toString()

        if (TextUtils.isEmpty(paymentAmountStr)) {
            paymentAmount = 0.0
        } else {
            paymentAmount = paymentAmountStr!!.toDouble()
        }

        payorsList = java.util.ArrayList<kotlin.String?>() // Will store UIDs
        payorsDisplayNames = java.util.ArrayList<kotlin.String?>() // Will store display names
        amountsPaidList = java.util.ArrayList<kotlin.Double?>()

        // Validation: Check transaction type - must not be default option
        if ("Select what kind of bill:" == transactionType || "Select a transaction:" == transactionType) {
            Toast.makeText(this, "Please select what kind of bill", Toast.LENGTH_SHORT).show()
            progressBar!!.setVisibility(android.view.View.GONE)
            return
        }

        // Validation: Check payment amount
        if (paymentAmount == 0.0) {
            Toast.makeText(this, "Please enter a payment amount", Toast.LENGTH_SHORT).show()
            progressBar!!.setVisibility(android.view.View.GONE)
            return
        }

        // Validation: Check if a group is selected
        if (selectedGroup == null) {
            Toast.makeText(this, "Please select a group first", Toast.LENGTH_SHORT).show()
            progressBar!!.setVisibility(android.view.View.GONE)
            return
        }

        // Validation: Check if group has members
        if (selectedGroup!!.getMembers() == null || selectedGroup!!.getMembers().isEmpty()) {
            Toast.makeText(this, "Selected group has no members", Toast.LENGTH_SHORT).show()
            progressBar!!.setVisibility(android.view.View.GONE)
            return
        }

        // Check if manual payor rows are visible and filled (when user wants custom amounts)
        var hasManualPayorRows = false
        for (row in rows!!) {
            if (row.getVisibility() == android.view.View.VISIBLE) {
                hasManualPayorRows = true
                break
            }
        }

        if (hasManualPayorRows && rows!!.size > 0) {
            // Use manual payor rows with custom amounts
            // But we must also include all other members of the group as unpaid (0.0)
            val groupMembers = selectedGroup!!.getMembers()
            val groupMemberNames = selectedGroup!!.getMemberDisplayNames()

            val manualPayments: kotlin.collections.MutableMap<kotlin.String?, kotlin.Double?> =
                java.util.HashMap<kotlin.String?, kotlin.Double?>()
            val uniquePayors = java.util.HashSet<kotlin.String?>()

            for (row in rows!!) {
                if (row.getVisibility() != android.view.View.VISIBLE) continue

                val payorSpinner: Spinner = row.findViewById<Spinner>(R.id.payor)
                val amountPaidEditText: EditText = row.findViewById<EditText>(R.id.amountPaid)

                val payorDisplayName = payorSpinner.getSelectedItem().toString()
                val amountPaidStr = amountPaidEditText.getText().toString().trim { it <= ' ' }

                // Validation: Check if payor is selected (not default option)
                if ("Select a payor:" == payorDisplayName) {
                    Toast.makeText(
                        this@AddTransactionActivity,
                        "Please select a payor for all rows",
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar!!.setVisibility(android.view.View.GONE)
                    return
                }

                // Validation: Check if amount is entered
                if (TextUtils.isEmpty(amountPaidStr)) {
                    Toast.makeText(
                        this@AddTransactionActivity,
                        "Please enter amount paid for: " + payorDisplayName,
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar!!.setVisibility(android.view.View.GONE)
                    return
                }

                // Convert username to UID
                val payorUid = usernameToUidMap.get(payorDisplayName)
                if (payorUid == null) {
                    Toast.makeText(
                        this@AddTransactionActivity,
                        "User not found: " + payorDisplayName,
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar!!.setVisibility(android.view.View.GONE)
                    return
                }

                // Validation: Check for duplicate payors
                if (!uniquePayors.add(payorUid)) {
                    Toast.makeText(
                        this@AddTransactionActivity,
                        "Duplicate payor detected: " + payorDisplayName,
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar!!.setVisibility(android.view.View.GONE)
                    return
                }

                try {
                    val amountPaid = amountPaidStr.toDouble()
                    // Validation: Amount must be greater than 0
                    if (amountPaid <= 0) {
                        Toast.makeText(
                            this@AddTransactionActivity,
                            "Amount must be greater than 0 for: " + payorDisplayName,
                            Toast.LENGTH_SHORT
                        ).show()
                        progressBar!!.setVisibility(android.view.View.GONE)
                        return
                    }
                    manualPayments.put(payorUid, amountPaid)
                } catch (e: java.lang.NumberFormatException) {
                    Toast.makeText(
                        this@AddTransactionActivity,
                        "Invalid amount format for: " + payorDisplayName,
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar!!.setVisibility(android.view.View.GONE)
                    return
                }
            }

            // Validation: Ensure at least one payor was added
            if (manualPayments.isEmpty()) {
                Toast.makeText(this, "Please add at least one payor", Toast.LENGTH_SHORT).show()
                progressBar!!.setVisibility(android.view.View.GONE)
                return
            }

            // Verify total matches paymentAmount
            var sumOfAmounts = 0.0
            for (amount in manualPayments.values) {
                sumOfAmounts += amount!!
            }

            if (kotlin.math.abs(sumOfAmounts - paymentAmount!!) > 0.01) {
                Toast.makeText(
                    this@AddTransactionActivity,
                    "Total of individual amounts (" + CurrencyUtils.formatAmountWithCurrency(
                        sumOfAmounts
                    ) + ") does not match payment amount (" + CurrencyUtils.formatAmountWithCurrency(
                        paymentAmount!!
                    ) + ")",
                    Toast.LENGTH_LONG
                ).show()
                progressBar!!.setVisibility(android.view.View.GONE)
                return
            }

            // Now populate the full list including all group members
            payorsList!!.clear()
            payorsDisplayNames!!.clear()
            amountsPaidList!!.clear()

            for (i in groupMembers.indices) {
                val memberUid = groupMembers.get(i)
                val memberDisplayName =
                    if (groupMemberNames != null && i < groupMemberNames.size) groupMemberNames.get(
                        i
                    ) else uidToUsernameMap.get(memberUid)

                payorsList!!.add(memberUid)
                payorsDisplayNames!!.add(if (memberDisplayName != null) memberDisplayName else "Unknown User")

                val paid: kotlin.Double =
                    (if (manualPayments.containsKey(memberUid)) manualPayments.get(memberUid) else 0.0)!!
                amountsPaidList!!.add(paid)
            }

            totalIndividualPayment = paymentAmount!! / groupMembers.size
        } else {
            // Use group members with equal split
            val groupMembers = selectedGroup!!.getMembers()
            val groupMemberNames = selectedGroup!!.getMemberDisplayNames()
            val numberOfMembers = groupMembers.size

            // Validation: Ensure at least one member
            if (numberOfMembers == 0) {
                Toast.makeText(this, "Selected group has no members", Toast.LENGTH_SHORT).show()
                progressBar!!.setVisibility(android.view.View.GONE)
                return
            }

            val individualAmount = paymentAmount!! / numberOfMembers

            totalAmountPaid = 0.0
            for (i in groupMembers.indices) {
                val memberValue = groupMembers.get(i)
                val memberUid: kotlin.String?
                val memberDisplayName: kotlin.String?

                // Check if memberValue is a UID or a username (for backward compatibility)
                if (uidToUsernameMap.containsKey(memberValue)) {
                    // It's a UID - get the display name from the map
                    memberUid = memberValue
                    memberDisplayName = uidToUsernameMap.get(memberValue)
                } else if (usernameToUidMap.containsKey(memberValue)) {
                    // It's a username (old format) - convert to UID
                    memberUid = usernameToUidMap.get(memberValue)
                    memberDisplayName = memberValue
                } else {
                    // Try to get from display names list if available
                    memberUid = memberValue
                    if (groupMemberNames != null && i < groupMemberNames.size) {
                        memberDisplayName = groupMemberNames.get(i)
                    } else {
                        memberDisplayName = "Unknown User"
                    }
                }

                // Validation: Check for invalid/empty payor UID
                if (memberUid == null || memberUid.isEmpty()) {
                    Toast.makeText(this, "Invalid payor found in group", Toast.LENGTH_SHORT).show()
                    progressBar!!.setVisibility(android.view.View.GONE)
                    return
                }

                payorsList!!.add(memberUid) // Store UID
                payorsDisplayNames!!.add(if (memberDisplayName != null) memberDisplayName else "Unknown User")

                amountsPaidList!!.add(individualAmount)
                totalAmountPaid += individualAmount
            }

            totalIndividualPayment = individualAmount
        }

        // Final validation: Total of amountsPaidList must equal payment amount
        var finalSum = 0.0
        for (amount in amountsPaidList!!) {
            finalSum += amount!!
        }

        if (kotlin.math.abs(finalSum - paymentAmount!!) > 0.01) {
            Toast.makeText(
                this,
                "Total amount paid (" + CurrencyUtils.formatAmountWithCurrency(finalSum) + ") does not match payment amount (" + CurrencyUtils.formatAmountWithCurrency(
                    paymentAmount!!
                ) + ")",
                Toast.LENGTH_LONG
            ).show()
            progressBar!!.setVisibility(android.view.View.GONE)
            return
        }

        // Final validation: Ensure payorsList is not empty
        if (payorsList!!.isEmpty()) {
            Toast.makeText(
                this,
                "No payors found. Please select a group with members.",
                Toast.LENGTH_SHORT
            ).show()
            progressBar!!.setVisibility(android.view.View.GONE)
            return
        }

        // Log all payors and amounts for verification
        android.util.Log.d("AddTransaction", "=== Transaction Summary ===")
        android.util.Log.d("AddTransaction", "Transaction Type: " + transactionType)
        android.util.Log.d("AddTransaction", "Payment Amount: ₱" + paymentAmount)
        android.util.Log.d("AddTransaction", "Number of Payors: " + payorsList!!.size)
        for (i in payorsList!!.indices) {
            android.util.Log.d(
                "AddTransaction", "Payor " + (i + 1) + ": " + payorsDisplayNames!!.get(i) +
                        " (UID: " + payorsList!!.get(i) + ") - Amount: ₱" + amountsPaidList!!.get(i)
            )
        }
        android.util.Log.d("AddTransaction", "Total of Individual Amounts: ₱" + finalSum)
        android.util.Log.d("AddTransaction", "===========================")

        val currentUserID: kotlin.String? = FirebaseAuth.getInstance().getCurrentUser().getUid()
        val usersRef: DatabaseReference =
            DeclareDatabase.getDatabaseReference().child(currentUserID)

        usersRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    posterDisplayName = dataSnapshot.getValue(kotlin.String::class.java)
                    usernamePost = currentUserID // Store UID instead of username
                    saveTransaction()
                } else {
                    progressBar!!.setVisibility(android.view.View.GONE)
                    Toast.makeText(
                        this@AddTransactionActivity,
                        "Username not found.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                progressBar!!.setVisibility(android.view.View.GONE)
                android.util.Log.e(
                    "FirebaseDatabase",
                    "Database read error occurred: " + databaseError.getMessage()
                )
            }
        })
    }

    private fun saveTransaction() {
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
        val dayFormat = java.text.SimpleDateFormat("dd", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

        val currentMonthYear = dateFormat.format(calendar.getTime())
        val currentDay = dayFormat.format(calendar.getTime())
        val currentTime = timeFormat.format(calendar.getTime())

        val timestampRef: DatabaseReference = DeclareDatabase.getDBRefTransaction()
            .child(currentMonthYear)
            .child(currentDay)
            .child(currentTime)

        val transaction: com.waray.spendhound.Transaction?
        if (selectedGroup != null) {
            // Use new constructor with display names - payorsList contains UIDs, usernamePost contains UID
            transaction = com.waray.spendhound.Transaction(
                transactionType, paymentAmount!!, multilineStr,
                payorsList, amountsPaidList, usernamePost, totalIndividualPayment,
                selectedGroup!!.getGroupId(), selectedGroup!!.getGroupName(),
                payorsDisplayNames, posterDisplayName
            )
        } else {
            // Use new constructor with display names - payorsList contains UIDs, usernamePost contains UID
            transaction = com.waray.spendhound.Transaction(
                transactionType, paymentAmount!!, multilineStr,
                payorsList, amountsPaidList, usernamePost, totalIndividualPayment,
                null, null, payorsDisplayNames, posterDisplayName
            )
        }

        timestampRef.setValue(transaction)
            .addOnSuccessListener({ aVoid ->
                progressBar!!.setVisibility(android.view.View.GONE)
                Toast.makeText(
                    this@AddTransactionActivity,
                    "Transaction added successfully",
                    Toast.LENGTH_SHORT
                ).show()
                val intent: Intent = Intent(this@AddTransactionActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            })
            .addOnFailureListener({ e ->
                progressBar!!.setVisibility(android.view.View.GONE)
                Toast.makeText(
                    this@AddTransactionActivity,
                    "Failed to add transaction",
                    Toast.LENGTH_SHORT
                ).show()
            })
    }


    private fun setupIndividualPaymentCalculator() {
        paymentAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: kotlin.CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                s: kotlin.CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                calculateAndDisplayIndividualPayment()
            }
        })
    }

    private fun calculateAndDisplayIndividualPayment() {
        val amountStr = paymentAmountEditText.getText().toString()
        if (!TextUtils.isEmpty(amountStr)) {
            try {
                val amount = amountStr.toDouble()
                val numberOfUsers: Int

                // If a group is selected, use the group's member count
                if (selectedGroup != null && selectedGroup!!.getMembers() != null && !selectedGroup!!.getMembers()
                        .isEmpty()
                ) {
                    numberOfUsers = selectedGroup!!.getMembers().size
                } else if (usernames != null && usernames!!.size > 1) {
                    // Fall back to all users if no group is selected
                    numberOfUsers = usernames!!.size - 1 // Exclude "Select a payor:"
                } else {
                    individualPayment.setText("₱ 0")
                    return
                }

                totalIndividualPayment = amount / numberOfUsers
                individualPayment.setText(
                    CurrencyUtils.formatAmountWithCurrency(
                        totalIndividualPayment
                    )
                )
            } catch (e: java.lang.NumberFormatException) {
                individualPayment.setText("₱ 0")
            }
        } else {
            individualPayment.setText("₱ 0")
        }
    }

    private fun detailsCharacterCount() {
        // Implement character count logic here if needed
    }

    private fun exitEditText() {
        // Implement exit edit text logic here if needed
    }

    private fun loadExistingGroups() {
        val groupsRef: DatabaseReference = DeclareDatabase.getDBRefGroups().child(currentUserId)
        groupsRef.addValueEventListener(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                groupsContainer.removeAllViews()
                groupViews!!.clear()
                payerGroups!!.clear()

                for (groupSnapshot in dataSnapshot.getChildren()) {
                    val group: PayerGroup? = groupSnapshot.getValue(PayerGroup::class.java)
                    if (group != null) {
                        group.setGroupId(groupSnapshot.getKey())
                        payerGroups!!.add(group)
                        addGroupView(group)
                    }
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                android.util.Log.e(
                    "FirebaseDatabase",
                    "Failed to load groups: " + databaseError.getMessage()
                )
            }
        })
    }

    private fun addGroupView(group: PayerGroup) {
        val inflater: LayoutInflater = LayoutInflater.from(this)
        val groupView: android.view.View =
            inflater.inflate(R.layout.item_group, groupsContainer, false)

        val groupNameTV: TextView = groupView.findViewById<TextView>(R.id.groupName)
        val groupMembersTV: TextView = groupView.findViewById<TextView>(R.id.groupMembers)
        val editBtn = groupView.findViewById<android.widget.Button>(R.id.editGroupBtn)
        val removeBtn = groupView.findViewById<android.widget.Button>(R.id.removeGroupBtn)

        groupNameTV.setText(group.getGroupName())

        // Use display names if available, otherwise resolve UIDs to usernames
        val displayNames = group.getMemberDisplayNames()
        if (displayNames != null && !displayNames.isEmpty()) {
            val membersText = "Members: " + java.lang.String.join(", ", displayNames)
            groupMembersTV.setText(membersText)
        } else if (group.getMembers() != null) {
            // Legacy data or need to resolve UIDs - try to get display names from cache
            val resolvedNames: kotlin.collections.MutableList<kotlin.String?> =
                java.util.ArrayList<kotlin.String?>()
            for (memberIdOrName in group.getMembers()) {
                val displayName = uidToUsernameMap.get(memberIdOrName)
                if (displayName != null) {
                    resolvedNames.add(displayName)
                } else {
                    // Could be old format with username directly
                    resolvedNames.add(memberIdOrName)
                }
            }
            val membersText = "Members: " + java.lang.String.join(", ", resolvedNames)
            groupMembersTV.setText(membersText)
        }

        editBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            showEditGroupDialog(
                group,
                groupView
            )
        })
        removeBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            showRemoveGroupConfirmation(
                group,
                groupView
            )
        })

        // Add click listener for group selection
        groupView.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            selectGroup(
                group,
                groupView
            )
        })

        groupViews!!.add(groupView)
        groupsContainer.addView(groupView)
    }

    private fun selectGroup(group: PayerGroup, groupView: android.view.View) {
        // If the same group is tapped again, deselect it
        if (selectedGroup != null && selectedGroup!!.getGroupId() == group.getGroupId()) {
            deselectGroup()
            return
        }

        // Deselect previous group if any
        if (selectedGroupView != null) {
            selectedGroupView!!.setBackgroundResource(R.drawable.rounded_border_transparent_bg)
        }

        // Select the new group
        selectedGroup = group
        selectedGroupView = groupView
        groupView.setBackgroundResource(R.drawable.rounded_border_selected_bg)

        // Set group member display names for the spinner (not UIDs)
        groupMemberUsernames = java.util.ArrayList<kotlin.String?>()
        groupMemberUsernames!!.add("Select a payor:")

        // Use display names for spinner if available, otherwise resolve from cache
        if (group.getMemberDisplayNames() != null && !group.getMemberDisplayNames().isEmpty()) {
            groupMemberUsernames!!.addAll(group.getMemberDisplayNames())
        } else if (group.getMembers() != null) {
            // Resolve UIDs to display names from cache
            for (memberIdOrName in group.getMembers()) {
                val displayName = uidToUsernameMap.get(memberIdOrName)
                if (displayName != null) {
                    groupMemberUsernames!!.add(displayName)
                } else {
                    // Could be old format with username directly
                    groupMemberUsernames!!.add(memberIdOrName)
                }
            }
        }

        // Enable btnAdd and hide tooltip when a group is selected
        btnAdd!!.setEnabled(true)
        btnAdd!!.setAlpha(1.0f)
        dismissPayorTooltip()

        // Show and update the first row with group members
        if (firstRow != null) {
            firstRow!!.setVisibility(android.view.View.VISIBLE)
            updateRowSpinnerWithGroupMembers(firstRow!!)
        }

        // Update existing rows' spinners with group members
        for (row in rows!!) {
            updateRowSpinnerWithGroupMembers(row)
        }

        // Recalculate individual payment based on selected group members
        calculateAndDisplayIndividualPayment()

        Toast.makeText(this, "Selected group: " + group.getGroupName(), Toast.LENGTH_SHORT).show()
    }

    private fun updateRowSpinnerWithGroupMembers(row: android.view.View) {
        val spinner: Spinner? = row.findViewById<Spinner?>(R.id.payor)
        if (spinner != null && groupMemberUsernames != null && !groupMemberUsernames!!.isEmpty()) {
            val adapter = SpinnerItem(this@AddTransactionActivity, groupMemberUsernames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.setAdapter(adapter)
        }
    }

    private fun deselectGroup() {
        if (selectedGroupView != null) {
            selectedGroupView!!.setBackgroundResource(R.drawable.rounded_border_transparent_bg)
        }
        selectedGroup = null
        selectedGroupView = null
        groupMemberUsernames = null

        // Disable btnAdd and show tooltip when no group is selected
        btnAdd!!.setEnabled(false)
        btnAdd!!.setAlpha(0.5f)
        showPayorTooltip()

        // Hide the first row when no group is selected
        if (firstRow != null) {
            firstRow!!.setVisibility(android.view.View.GONE)
        }

        // Remove all additional rows (keep only the first hidden row)
        while (rows!!.size > 1) {
            val row = rows!!.get(rows!!.size - 1)
            container.removeView(row)
            rows!!.remove(row)
        }

        calculateAndDisplayIndividualPayment()
        Toast.makeText(this, "Group deselected", Toast.LENGTH_SHORT).show()
    }

    private fun showCreateGroupDialog() {
        if (usernames == null || usernames!!.size <= 1) {
            Toast.makeText(this, "No users available to add to group", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = android.app.AlertDialog.Builder(this)
        val dialogView: android.view.View =
            LayoutInflater.from(this).inflate(R.layout.dialog_create_group, null)
        builder.setView(dialogView)

        val dialog = builder.create()

        val groupNameEditText: EditText = dialogView.findViewById<EditText>(R.id.groupNameEditText)
        val usersCheckboxContainer: LinearLayout =
            dialogView.findViewById<LinearLayout>(R.id.usersCheckboxContainer)
        val cancelBtn = dialogView.findViewById<android.widget.Button>(R.id.cancelGroupBtn)
        val createBtn = dialogView.findViewById<android.widget.Button>(R.id.createGroupBtn)

        val checkBoxes: kotlin.collections.MutableList<CheckBox> = java.util.ArrayList<CheckBox>()

        // Add checkboxes for each user (skip "Select a payor:" and current user)
        for (i in 1..<usernames!!.size) {
            val username = usernames!!.get(i)
            val uid = usernameToUidMap.get(username)

            // Skip the current user in the UI
            if (uid != null && uid == currentUserId) {
                continue
            }

            val checkBox: CheckBox = CheckBox(this)
            checkBox.setText(username)
            checkBox.setTextColor(getResources().getColor(R.color.darkBlue))
            checkBox.setPadding(8, 8, 8, 8)
            checkBoxes.add(checkBox)
            usersCheckboxContainer.addView(checkBox)
        }

        cancelBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> dialog.dismiss() })

        createBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            val groupName = groupNameEditText.getText().toString().trim { it <= ' ' }
            if (TextUtils.isEmpty(groupName)) {
                Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMemberUids: kotlin.collections.MutableList<kotlin.String?> =
                java.util.ArrayList<kotlin.String?>()
            val selectedMemberDisplayNames: kotlin.collections.MutableList<kotlin.String?> =
                java.util.ArrayList<kotlin.String?>()

            // Automatically include current user
            selectedMemberUids.add(currentUserId)
            val currentUserDisplayName = uidToUsernameMap.get(currentUserId)
            selectedMemberDisplayNames.add(if (currentUserDisplayName != null) currentUserDisplayName else "Me")

            for (checkBox in checkBoxes) {
                if (checkBox.isChecked()) {
                    val displayName = checkBox.getText().toString()
                    val uid = usernameToUidMap.get(displayName)
                    if (uid != null) {
                        selectedMemberUids.add(uid)
                        selectedMemberDisplayNames.add(displayName)
                    }
                }
            }

            if (selectedMemberUids.size <= 1) {
                Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveGroupToDatabase(groupName, selectedMemberUids, selectedMemberDisplayNames)
            dialog.dismiss()
        })

        dialog.show()
    }

    private fun saveGroupToDatabase(
        groupName: kotlin.String?,
        memberUids: kotlin.collections.MutableList<kotlin.String?>?,
        memberDisplayNames: kotlin.collections.MutableList<kotlin.String?>?
    ) {
        val groupsRef: DatabaseReference = DeclareDatabase.getDBRefGroups().child(currentUserId)
        val groupId: kotlin.String? = groupsRef.push().getKey()

        if (groupId != null) {
            // Store member UIDs and display names
            val newGroup =
                PayerGroup(groupId, groupName, memberUids, currentUserId, memberDisplayNames)
            groupsRef.child(groupId).setValue(newGroup)
                .addOnSuccessListener({ aVoid ->
                    Toast.makeText(this, "Group created successfully", Toast.LENGTH_SHORT).show()
                    // Reload groups to show the new group
                    loadExistingGroups()
                })
                .addOnFailureListener({ e ->
                    Toast.makeText(this, "Failed to create group", Toast.LENGTH_SHORT).show()
                    android.util.Log.e(
                        "FirebaseDatabase",
                        "Failed to save group: " + e.getMessage()
                    )
                })
        }
    }

    private fun showEditGroupDialog(group: PayerGroup, groupView: android.view.View) {
        if (usernames == null || usernames!!.size <= 1) {
            Toast.makeText(this, "No users available to edit group", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = android.app.AlertDialog.Builder(this)
        val dialogView: android.view.View =
            LayoutInflater.from(this).inflate(R.layout.dialog_edit_group, null)
        builder.setView(dialogView)

        val dialog = builder.create()

        val groupNameEditText: EditText =
            dialogView.findViewById<EditText>(R.id.editGroupNameEditText)
        val usersCheckboxContainer: LinearLayout =
            dialogView.findViewById<LinearLayout>(R.id.editUsersCheckboxContainer)
        val cancelBtn = dialogView.findViewById<android.widget.Button>(R.id.cancelEditGroupBtn)
        val saveBtn = dialogView.findViewById<android.widget.Button>(R.id.saveEditGroupBtn)

        // Pre-fill the group name
        groupNameEditText.setText(group.getGroupName())

        val checkBoxes: kotlin.collections.MutableList<CheckBox> = java.util.ArrayList<CheckBox>()

        // Add checkboxes for each user (skip "Select a payor:" and current user)
        for (i in 1..<usernames!!.size) {
            val username = usernames!!.get(i)
            val uid = usernameToUidMap.get(username)

            // Skip the current user in the UI
            if (uid != null && uid == currentUserId) {
                continue
            }

            val checkBox: CheckBox = CheckBox(this)
            checkBox.setText(username)
            checkBox.setTextColor(getResources().getColor(R.color.darkBlue))
            checkBox.setPadding(8, 8, 8, 8)

            // Check if this user is already in the group (by UID or by display name for legacy data)
            var isInGroup = false
            if (uid != null && group.getMembers() != null && group.getMembers().contains(uid)) {
                isInGroup = true
            } else if (group.getMemberDisplayNames() != null && group.getMemberDisplayNames()
                    .contains(username)
            ) {
                isInGroup = true
            } else if (group.getMembers() != null && group.getMembers().contains(username)) {
                // Legacy: check if username is directly in members (old data format)
                isInGroup = true
            }
            checkBox.setChecked(isInGroup)

            checkBoxes.add(checkBox)
            usersCheckboxContainer.addView(checkBox)
        }

        cancelBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> dialog.dismiss() })

        saveBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            val groupName = groupNameEditText.getText().toString().trim { it <= ' ' }
            if (TextUtils.isEmpty(groupName)) {
                Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMemberUids: kotlin.collections.MutableList<kotlin.String?> =
                java.util.ArrayList<kotlin.String?>()
            val selectedMemberDisplayNames: kotlin.collections.MutableList<kotlin.String?> =
                java.util.ArrayList<kotlin.String?>()

            // Automatically include current user
            selectedMemberUids.add(currentUserId)
            val currentUserDisplayName = uidToUsernameMap.get(currentUserId)
            selectedMemberDisplayNames.add(if (currentUserDisplayName != null) currentUserDisplayName else "Me")

            for (checkBox in checkBoxes) {
                if (checkBox.isChecked()) {
                    val displayName = checkBox.getText().toString()
                    val uid = usernameToUidMap.get(displayName)
                    if (uid != null) {
                        selectedMemberUids.add(uid)
                        selectedMemberDisplayNames.add(displayName)
                    }
                }
            }

            if (selectedMemberUids.size <= 1) {
                Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            updateGroupInDatabase(
                group.getGroupId(),
                groupName,
                selectedMemberUids,
                selectedMemberDisplayNames,
                groupView
            )
            dialog.dismiss()
        })

        dialog.show()
    }

    private fun updateGroupInDatabase(
        groupId: kotlin.String?,
        groupName: kotlin.String?,
        memberUids: kotlin.collections.MutableList<kotlin.String?>?,
        memberDisplayNames: kotlin.collections.MutableList<kotlin.String?>,
        groupView: android.view.View
    ) {
        val groupRef: DatabaseReference = DeclareDatabase.getDBRefGroups()
            .child(currentUserId)
            .child(groupId)

        val updatedGroup =
            PayerGroup(groupId, groupName, memberUids, currentUserId, memberDisplayNames)
        groupRef.setValue(updatedGroup)
            .addOnSuccessListener({ aVoid ->
                Toast.makeText(this, "Group updated successfully", Toast.LENGTH_SHORT).show()
                // Update the group view immediately
                val groupNameTV: TextView = groupView.findViewById<TextView>(R.id.groupName)
                val groupMembersTV: TextView = groupView.findViewById<TextView>(R.id.groupMembers)
                groupNameTV.setText(groupName)
                val membersText = "Members: " + java.lang.String.join(", ", memberDisplayNames)
                groupMembersTV.setText(membersText)

                // If this group is currently selected, update the selected group object
                if (selectedGroup != null && selectedGroup!!.getGroupId() == groupId) {
                    selectedGroup!!.setGroupName(groupName)
                    selectedGroup!!.setMembers(memberUids)
                    selectedGroup!!.setMemberDisplayNames(memberDisplayNames)
                    calculateAndDisplayIndividualPayment()
                }
            })
            .addOnFailureListener({ e ->
                Toast.makeText(this, "Failed to update group", Toast.LENGTH_SHORT).show()
                android.util.Log.e("FirebaseDatabase", "Failed to update group: " + e.getMessage())
            })
    }

    private fun showRemoveGroupConfirmation(group: PayerGroup, groupView: android.view.View?) {
        val builder = android.app.AlertDialog.Builder(this)
        val dialogView: android.view.View =
            LayoutInflater.from(this).inflate(R.layout.dialog_remove_group, null)
        builder.setView(dialogView)

        val dialog = builder.create()

        val groupNameTV: TextView = dialogView.findViewById<TextView>(R.id.groupNameToRemove)
        val cancelBtn = dialogView.findViewById<android.widget.Button>(R.id.cancelRemoveBtn)
        val confirmBtn = dialogView.findViewById<android.widget.Button>(R.id.confirmRemoveBtn)

        groupNameTV.setText(group.getGroupName())

        cancelBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? -> dialog.dismiss() })

        confirmBtn.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            removeGroupFromDatabase(group, groupView)
            dialog.dismiss()
        })

        dialog.show()
    }

    private fun removeGroupFromDatabase(group: PayerGroup, groupView: android.view.View?) {
        val groupRef: DatabaseReference = DeclareDatabase.getDBRefGroups()
            .child(currentUserId)
            .child(group.getGroupId())

        groupRef.removeValue()
            .addOnSuccessListener({ aVoid ->
                Toast.makeText(this, "Group removed successfully", Toast.LENGTH_SHORT).show()
                groupsContainer.removeView(groupView)
                groupViews!!.remove(groupView)
                payerGroups!!.remove(group)
            })
            .addOnFailureListener({ e ->
                Toast.makeText(this, "Failed to remove group", Toast.LENGTH_SHORT).show()
                android.util.Log.e("FirebaseDatabase", "Failed to remove group: " + e.getMessage())
            })
    }

    private fun showPayorTooltip() {
        if (btnAdd == null) return

        // Dismiss existing tooltip if any
        dismissPayorTooltip()

        // Inflate tooltip view from XML
        val tooltipView: android.view.View =
            LayoutInflater.from(this).inflate(R.layout.tooltip_add_payor, null)

        // Create PopupWindow - non-focusable so keyboard stays in front
        payorTooltipPopup = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false // Not focusable - allows keyboard to stay on top
        )
        payorTooltipPopup.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        payorTooltipPopup.setOutsideTouchable(false)
        payorTooltipPopup.setTouchable(false) // Don't intercept touch events
        payorTooltipPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED) // Allow input method to appear on top


        // Set low elevation so keyboard appears above tooltip
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            payorTooltipPopup.setElevation(0f)
        }

        // Measure tooltip to get its dimensions
        tooltipView.measure(
            android.view.View.MeasureSpec.UNSPECIFIED,
            android.view.View.MeasureSpec.UNSPECIFIED
        )
        val tooltipHeight = tooltipView.getMeasuredHeight()
        val tooltipWidth = tooltipView.getMeasuredWidth()

        // Post to ensure the button is laid out before showing tooltip
        btnAdd!!.post(java.lang.Runnable {
            if (payorTooltipPopup != null && btnAdd!!.isAttachedToWindow()) {
                // Position tooltip at upper left corner of the button
                // offsetX = negative tooltip width to position left of the button's left edge
                // offsetY = negative (button height + tooltip height) to position above the button
                val offsetX = -tooltipWidth
                val offsetY = -(btnAdd!!.getHeight() + tooltipHeight)
                payorTooltipPopup.showAsDropDown(btnAdd, offsetX, offsetY, Gravity.START)
            }
        })
    }

    private fun dismissPayorTooltip() {
        if (payorTooltipPopup != null && payorTooltipPopup.isShowing()) {
            payorTooltipPopup.dismiss()
            payorTooltipPopup = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissPayorTooltip()
    }

    override fun dispatchTouchEvent(event: MotionEvent): kotlin.Boolean {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            val v = getCurrentFocus()
            if (v is EditText) {
                val location = IntArray(2)
                v.getLocationOnScreen(location)
                val x: kotlin.Float = event.getRawX()
                val y: kotlin.Float = event.getRawY()

                // Check if touch is outside the focused EditText
                if (x < location[0] || x > location[0] + v.getWidth() || y < location[1] || y > location[1] + v.getHeight()) {
                    hideKeyboard(v)
                    v.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun hideKeyboard(view: android.view.View) {
        val imm =
            getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager?
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0)
        }
    }
}
