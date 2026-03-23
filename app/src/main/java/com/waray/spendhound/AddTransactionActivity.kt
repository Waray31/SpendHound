package com.waray.spendhound

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTransactionActivity : AppCompatActivity() {
    private var container: LinearLayout? = null
    private var groupsContainer: LinearLayout? = null
    private var btnAdd: Button? = null
    private var btnAddGroup: Button? = null
    private var addTransactionbtn: Button? = null
    private var transactionTypeSpinner: Spinner? = null
    private var transactionType: String? = null
    private var paymentAmountStr: String? = null
    private var multilineStr: String? = null
    private var progressBar: View? = null
    private var usernames: MutableList<String?>? = null
    private var mAuth: Auth? = null
    private var rows: MutableList<View>? = null
    private var groupViews: MutableList<View?>? = null
    private var payerGroups: MutableList<PayerGroup?>? = null
    private var payorsList: MutableList<String?>? = null
    private var payorsDisplayNames: MutableList<String?>? = null
    private var amountsPaidList: MutableList<Double?>? = null
    private var paymentAmount: Double? = null
    private var paymentAmountEditText: EditText? = null
    private var editTextTextMultiLine: EditText? = null
    private var individualPayment: TextView? = null
    private var totalIndividualPayment = 0.0
    private var currentUserId: String? = null
    private var currentUserNumericId: Long? = null
    private var selectedGroup: PayerGroup? = null
    private var selectedGroupView: View? = null
    private var payorTooltipPopup: PopupWindow? = null
    private var firstRow: View? = null
    private var groupMemberUsernames: MutableList<String?>? = null

    private var posterDisplayName: String? = null
    private var usernamePost: String? = null

    private val usernameToUidMap: MutableMap<String?, String?> = HashMap()
    private val uidToUsernameMap: MutableMap<String?, String?> = HashMap()
    private val usernameToNumericIdMap: MutableMap<String?, Long?> = HashMap()
    private val numericIdToUsernameMap: MutableMap<Long?, String?> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        mAuth = DeclareDatabase.auth

        val currentSupabaseUser = mAuth?.currentUserOrNull()
        if (currentSupabaseUser == null) {
            val intent = Intent(this@AddTransactionActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        currentUserId = currentSupabaseUser.id

        transactionTypeSpinner = findViewById(R.id.transactionType)
        val transactionTypes = resources.getStringArray(R.array.transactionTypes_String)
        val typesList = transactionTypes.toMutableList()
        val adapter = SpinnerItem(this, typesList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        transactionTypeSpinner?.adapter = adapter

        container = findViewById(R.id.container)
        groupsContainer = findViewById(R.id.groupsContainer)
        btnAdd = findViewById(R.id.btnAdd)
        btnAddGroup = findViewById(R.id.btnAddGroup)
        rows = ArrayList()
        groupViews = ArrayList()
        payerGroups = ArrayList()
        progressBar = findViewById(R.id.progressBar)

        paymentAmountEditText = findViewById(R.id.paymentAmount)
        editTextTextMultiLine = findViewById(R.id.editTextTextMultiLine)
        individualPayment = findViewById(R.id.individualPayment)

        fetchUsernamesAndSetupInitialRow()

        btnAdd?.isEnabled = false
        btnAdd?.alpha = 0.5f
        showPayorTooltip()

        btnAdd?.setOnClickListener {
            if (selectedGroup == null) {
                showPayorTooltip()
                return@setOnClickListener
            }
            if (groupMemberUsernames != null && rows!!.size < groupMemberUsernames!!.size - 1) {
                addRow()
            } else {
                Toast.makeText(this@AddTransactionActivity, "You can't add more payors.", Toast.LENGTH_SHORT).show()
            }
        }

        addTransactionbtn = findViewById(R.id.addTransactionbtn)
        addTransactionbtn?.setOnClickListener { addTransaction() }

        btnAddGroup?.setOnClickListener { showCreateGroupDialog() }

        setupIndividualPaymentCalculator()
    }

    private fun fetchUsernamesAndSetupInitialRow() {
        lifecycleScope.launch {
            try {
                val userList = DeclareDatabase.usersTable.select().decodeList<User>()
                usernames = ArrayList()
                usernames!!.add("Select a payor:")
                usernameToUidMap.clear()
                uidToUsernameMap.clear()
                usernameToNumericIdMap.clear()
                numericIdToUsernameMap.clear()

                for (user in userList) {
                    val username = user.username
                    val dbId = user.id
                    val authId = user.authId
                    if (username != null && dbId != null) {
                        usernames!!.add(username)
                        usernameToNumericIdMap[username] = dbId
                        numericIdToUsernameMap[dbId] = username
                        
                        if (authId != null) {
                            usernameToUidMap[username] = authId
                            uidToUsernameMap[authId] = username
                            if (authId == currentUserId) {
                                currentUserNumericId = dbId
                                posterDisplayName = username
                            }
                        }
                        UserHelper.updateCache(dbId, username)
                    }
                }
                addRow()
                if (rows!!.isNotEmpty()) {
                    firstRow = rows!![0]
                    firstRow?.visibility = View.GONE
                }
                btnAdd?.isEnabled = false
                btnAdd?.alpha = 0.5f
                
                loadExistingGroups()
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching users: ${e.message}")
            }
        }
    }

    private fun addRow() {
        val inflater = LayoutInflater.from(this)
        val row = inflater.inflate(R.layout.row_layout, container, false)

        val spinner: Spinner = row.findViewById(R.id.payor)
        val spinnerUsernames = if (!groupMemberUsernames.isNullOrEmpty()) groupMemberUsernames else usernames

        if (!spinnerUsernames.isNullOrEmpty()) {
            val adapter = SpinnerItem(this@AddTransactionActivity, spinnerUsernames!!)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        row.findViewById<Button>(R.id.closeBtn).setOnClickListener { removeRow(row) }
        ViewCompat.setBackground(row, ContextCompat.getDrawable(this, R.drawable.rounded_alternating_row))
        rows?.add(row)
        container?.addView(row)
    }

    private fun removeRow(row: View) {
        container?.removeView(row)
        rows?.remove(row)
    }

    private fun addTransaction() {
        progressBar?.visibility = View.VISIBLE
        transactionType = transactionTypeSpinner?.selectedItem?.toString()
        paymentAmountStr = paymentAmountEditText?.text?.toString()
        multilineStr = editTextTextMultiLine?.text?.toString()

        paymentAmount = paymentAmountStr?.toDoubleOrNull() ?: 0.0

        if (transactionType == "Select what kind of bill:" || transactionType == "Select a transaction:") {
            Toast.makeText(this, "Please select what kind of bill", Toast.LENGTH_SHORT).show()
            progressBar?.visibility = View.GONE
            return
        }

        if (paymentAmount == 0.0) {
            Toast.makeText(this, "Please enter a payment amount", Toast.LENGTH_SHORT).show()
            progressBar?.visibility = View.GONE
            return
        }

        if (selectedGroup == null) {
            Toast.makeText(this, "Please select a group first", Toast.LENGTH_SHORT).show()
            progressBar?.visibility = View.GONE
            return
        }

        payorsList = ArrayList()
        payorsDisplayNames = ArrayList()
        amountsPaidList = ArrayList()

        var hasManualPayorRows = false
        for (row in rows!!) {
            if (row.visibility == View.VISIBLE) {
                hasManualPayorRows = true
                break
            }
        }

        val groupMembers = selectedGroup!!.members ?: ArrayList()
        val groupMemberNames = selectedGroup!!.memberDisplayNames ?: ArrayList()

        if (hasManualPayorRows && rows!!.isNotEmpty()) {
            val manualPayments = HashMap<String?, Double>()
            val uniquePayors = HashSet<String?>()

            for (row in rows!!) {
                if (row.visibility != View.VISIBLE) continue

                val spinner: Spinner = row.findViewById(R.id.payor)
                val amountEditText: EditText = row.findViewById(R.id.amountPaid)

                val displayName = spinner.selectedItem.toString()
                val amountStr = amountEditText.text.toString().trim()

                if (displayName == "Select a payor:") {
                    Toast.makeText(this, "Please select a payor for all rows", Toast.LENGTH_SHORT).show()
                    progressBar?.visibility = View.GONE
                    return
                }

                if (amountStr.isEmpty()) {
                    Toast.makeText(this, "Please enter amount paid for: $displayName", Toast.LENGTH_SHORT).show()
                    progressBar?.visibility = View.GONE
                    return
                }

                val authId = usernameToUidMap[displayName]
                if (authId == null) {
                    Toast.makeText(this, "User not found: $displayName", Toast.LENGTH_SHORT).show()
                    progressBar?.visibility = View.GONE
                    return
                }

                if (!uniquePayors.add(authId)) {
                    Toast.makeText(this, "Duplicate payor detected: $displayName", Toast.LENGTH_SHORT).show()
                    progressBar?.visibility = View.GONE
                    return
                }

                val amount = amountStr.toDoubleOrNull() ?: 0.0
                if (amount <= 0) {
                    Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show()
                    progressBar?.visibility = View.GONE
                    return
                }
                manualPayments[authId] = amount
            }

            var sumOfAmounts = 0.0
            manualPayments.values.forEach { sumOfAmounts += it }

            if (Math.abs(sumOfAmounts - paymentAmount!!) > 0.01) {
                Toast.makeText(this, "Total doesn't match payment amount", Toast.LENGTH_LONG).show()
                progressBar?.visibility = View.GONE
                return
            }

            for (i in groupMembers.indices) {
                val memberId = groupMembers[i]
                val memberDisplayName = if (i < groupMemberNames.size) groupMemberNames[i] else numericIdToUsernameMap[memberId]
                val authId = usernameToUidMap[memberDisplayName]
                payorsList?.add(authId)
                payorsDisplayNames?.add(memberDisplayName ?: "Unknown User")
                amountsPaidList?.add(manualPayments[authId] ?: 0.0)
            }
            totalIndividualPayment = paymentAmount!! / groupMembers.size
        } else {
            val individualAmount = paymentAmount!! / groupMembers.size
            for (i in groupMembers.indices) {
                val memberId = groupMembers[i]
                val memberDisplayName = if (i < groupMemberNames.size) groupMemberNames[i] else numericIdToUsernameMap[memberId]
                val authId = usernameToUidMap[memberDisplayName]
                payorsList?.add(authId)
                payorsDisplayNames?.add(memberDisplayName ?: "Unknown User")
                amountsPaidList?.add(individualAmount)
            }
            totalIndividualPayment = individualAmount
        }

        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter {
                        eq("auth_id", currentUserId!!)
                    }
                }.decodeSingleOrNull<User>()

                if (user != null) {
                    posterDisplayName = user.username
                    usernamePost = currentUserId
                    saveTransaction()
                } else {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(this@AddTransactionActivity, "Username not found.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error getting user: ${e.message}")
                progressBar?.visibility = View.GONE
            }
        }
    }

    private fun saveTransaction() {
        val calendar = Calendar.getInstance()
        val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
        val currentDay = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(calendar.time)

        val transaction = Transaction(
            transactionType = transactionType,
            paymentAmount = paymentAmount!!,
            multilineStr = multilineStr,
            payorsList = payorsList,
            amountsPaidList = amountsPaidList,
            usernamePost = usernamePost,
            totalIndividualPayment = totalIndividualPayment,
            groupId = selectedGroup?.groupId,
            groupName = selectedGroup?.groupName,
            payorsDisplayNames = payorsDisplayNames,
            posterDisplayName = posterDisplayName,
            monthYear = currentMonthYear,
            day = currentDay,
            timeKey = currentTime
        )

        lifecycleScope.launch {
            try {
                DeclareDatabase.transactionsTable.insert(transaction)
                progressBar?.visibility = View.GONE
                Toast.makeText(this@AddTransactionActivity, "Transaction added successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@AddTransactionActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                Log.e("Supabase", "Error inserting transaction: ${e.message}")
                progressBar?.visibility = View.GONE
                Toast.makeText(this@AddTransactionActivity, "Failed to add transaction", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupIndividualPaymentCalculator() {
        paymentAmountEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { calculateAndDisplayIndividualPayment() }
        })
    }

    private fun calculateAndDisplayIndividualPayment() {
        val amount = paymentAmountEditText?.text?.toString()?.toDoubleOrNull() ?: 0.0
        val numberOfUsers = if (selectedGroup != null && !selectedGroup!!.members.isNullOrEmpty()) {
            selectedGroup!!.members!!.size
        } else if (!usernames.isNullOrEmpty() && usernames!!.size > 1) {
            usernames!!.size - 1
        } else 1

        totalIndividualPayment = amount / numberOfUsers
        individualPayment?.text = CurrencyUtils.formatAmountWithCurrency(totalIndividualPayment)
    }

    private fun loadExistingGroups() {
        if (currentUserNumericId == null) return
        lifecycleScope.launch {
            try {
                val groupList = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()
                groupsContainer?.removeAllViews()
                groupViews?.clear()
                payerGroups?.clear()

                for (group in groupList) {
                    // Populate display names from numeric IDs for local use
                    group.memberDisplayNames = group.members?.mapNotNull { numericIdToUsernameMap[it] }?.toMutableList()
                    
                    if (group.members?.contains(currentUserNumericId) == true) {
                        payerGroups?.add(group)
                        addGroupView(group)
                    }
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error loading groups: ${e.message}")
            }
        }
    }

    private fun addGroupView(group: PayerGroup) {
        val inflater = LayoutInflater.from(this)
        val groupView = inflater.inflate(R.layout.item_group, groupsContainer, false)

        groupView.findViewById<TextView>(R.id.groupName).text = group.groupName
        val membersTV: TextView = groupView.findViewById(R.id.groupMembers)
        
        val displayNames = group.memberDisplayNames
        if (!displayNames.isNullOrEmpty()) {
            membersTV.text = "Members: ${displayNames.joinToString(", ")}"
        }

        groupView.findViewById<Button>(R.id.editGroupBtn).setOnClickListener { showEditGroupDialog(group, groupView) }
        groupView.findViewById<Button>(R.id.removeGroupBtn).setOnClickListener { showRemoveGroupConfirmation(group, groupView) }
        groupView.setOnClickListener { selectGroup(group, groupView) }

        groupViews?.add(groupView)
        groupsContainer?.addView(groupView)
    }

    private fun selectGroup(group: PayerGroup, groupView: View) {
        if (selectedGroup?.groupId == group.groupId) {
            deselectGroup()
            return
        }

        selectedGroupView?.setBackgroundResource(R.drawable.rounded_border_transparent_bg)
        selectedGroup = group
        selectedGroupView = groupView
        groupView.setBackgroundResource(R.drawable.rounded_border_selected_bg)

        groupMemberUsernames = ArrayList()
        groupMemberUsernames?.add("Select a payor:")
        
        val displayNames = group.memberDisplayNames
        if (!displayNames.isNullOrEmpty()) {
            groupMemberUsernames?.addAll(displayNames)
        }

        btnAdd?.isEnabled = true
        btnAdd?.alpha = 1.0f
        dismissPayorTooltip()

        firstRow?.visibility = View.VISIBLE
        rows?.forEach { updateRowSpinnerWithGroupMembers(it) }
        calculateAndDisplayIndividualPayment()
    }

    private fun updateRowSpinnerWithGroupMembers(row: View) {
        val spinner: Spinner? = row.findViewById(R.id.payor)
        if (spinner != null && !groupMemberUsernames.isNullOrEmpty()) {
            val adapter = SpinnerItem(this@AddTransactionActivity, groupMemberUsernames!!)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }
    }

    private fun deselectGroup() {
        selectedGroupView?.setBackgroundResource(R.drawable.rounded_border_transparent_bg)
        selectedGroup = null
        selectedGroupView = null
        groupMemberUsernames = null

        btnAdd?.isEnabled = false
        btnAdd?.alpha = 0.5f
        showPayorTooltip()

        firstRow?.visibility = View.GONE
        while (rows!!.size > 1) {
            val row = rows!![rows!!.size - 1]
            container?.removeView(row)
            rows?.remove(row)
        }
        calculateAndDisplayIndividualPayment()
    }

    private fun showCreateGroupDialog() {
        if (usernames.isNullOrEmpty() || usernames!!.size <= 1) return

        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_group, null)
        builder.setView(dialogView)
        val dialog = builder.create()

        val nameET: EditText = dialogView.findViewById(R.id.groupNameEditText)
        val checkboxContainer: LinearLayout = dialogView.findViewById(R.id.usersCheckboxContainer)
        val checkBoxes = ArrayList<CheckBox>()

        // Add current user as a non-clickable checkbox so they see themselves
        val currentCheckBox = CheckBox(this)
        currentCheckBox.text = posterDisplayName ?: "Me"
        currentCheckBox.setTextColor(ContextCompat.getColor(this, R.color.darkBlue))
        currentCheckBox.setPadding(8, 8, 8, 8)
        currentCheckBox.isChecked = true
        currentCheckBox.isEnabled = false
        checkboxContainer.addView(currentCheckBox)

        for (i in 1 until usernames!!.size) {
            val username = usernames!![i]
            if (usernameToUidMap[username] == currentUserId) continue

            val checkBox = CheckBox(this)
            checkBox.text = username
            checkBox.setTextColor(ContextCompat.getColor(this, R.color.darkBlue))
            checkBox.setPadding(8, 8, 8, 8)
            checkBoxes.add(checkBox)
            checkboxContainer.addView(checkBox)
        }

        dialogView.findViewById<Button>(R.id.cancelGroupBtn).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.createGroupBtn).setOnClickListener {
            val name = nameET.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter group name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedIds = ArrayList<Long?>()
            val selectedNames = ArrayList<String?>()

            if (currentUserNumericId != null) {
                selectedIds.add(currentUserNumericId)
                selectedNames.add(posterDisplayName ?: "Me")
            }

            checkBoxes.forEach { if (it.isChecked) {
                val dName = it.text.toString()
                usernameToNumericIdMap[dName]?.let { id ->
                    selectedIds.add(id)
                    selectedNames.add(dName)
                }
            }}

            if (selectedIds.size <= 1) {
                Toast.makeText(this, "Select at least one member", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveGroupToDatabase(name, selectedIds, selectedNames)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun saveGroupToDatabase(name: String, ids: MutableList<Long?>, names: MutableList<String?>) {
        val newGroup = PayerGroup(
            groupName = name,
            members = ids,
            createdBy = currentUserNumericId
        )
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupsTable.insert(newGroup)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddTransactionActivity, "Group created successfully", Toast.LENGTH_SHORT).show()
                    loadExistingGroups()
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error saving group: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddTransactionActivity, "Failed to create group: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showEditGroupDialog(group: PayerGroup, groupView: View) {
        if (usernames.isNullOrEmpty() || usernames!!.size <= 1) return
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_group, null)
        builder.setView(dialogView)
        val dialog = builder.create()

        val nameET: EditText = dialogView.findViewById(R.id.editGroupNameEditText)
        nameET.setText(group.groupName)
        val checkboxContainer: LinearLayout = dialogView.findViewById(R.id.editUsersCheckboxContainer)
        val checkBoxes = ArrayList<CheckBox>()

        // Add current user as a non-clickable checkbox
        val currentCheckBox = CheckBox(this)
        currentCheckBox.text = posterDisplayName ?: "Me"
        currentCheckBox.setTextColor(ContextCompat.getColor(this, R.color.darkBlue))
        currentCheckBox.setPadding(8, 8, 8, 8)
        currentCheckBox.isChecked = true
        currentCheckBox.isEnabled = false
        checkboxContainer.addView(currentCheckBox)

        for (i in 1 until usernames!!.size) {
            val username = usernames!![i]
            if (usernameToUidMap[username] == currentUserId) continue

            val checkBox = CheckBox(this)
            checkBox.text = username
            checkBox.setTextColor(ContextCompat.getColor(this, R.color.darkBlue))
            checkBox.setPadding(8, 8, 8, 8)
            checkBoxes.add(checkBox)
            checkboxContainer.addView(checkBox)

            val isMember = group.members?.contains(usernameToNumericIdMap[username]) == true || 
                           group.memberDisplayNames?.contains(username) == true
            checkBox.isChecked = isMember
        }

        dialogView.findViewById<Button>(R.id.cancelEditGroupBtn).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.saveEditGroupBtn).setOnClickListener {
            val name = nameET.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener

            val selectedIds = ArrayList<Long?>()
            val selectedNames = ArrayList<String?>()
            
            if (currentUserNumericId != null) {
                selectedIds.add(currentUserNumericId)
                selectedNames.add(posterDisplayName ?: "Me")
            }

            checkBoxes.forEach { if (it.isChecked) {
                val dName = it.text.toString()
                usernameToNumericIdMap[dName]?.let { id ->
                    selectedIds.add(id)
                    selectedNames.add(dName)
                }
            }}

            updateGroupInDatabase(group.groupId, name, selectedIds, selectedNames, groupView)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateGroupInDatabase(id: Long?, name: String, uids: MutableList<Long?>, names: MutableList<String?>, groupView: View) {
        if (id == null) return
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupsTable.update({
                    set("group_name", name)
                    set("member_ids", uids)
                }) {
                    filter {
                        eq("group_id", id)
                    }
                }
                withContext(Dispatchers.Main) {
                    groupView.findViewById<TextView>(R.id.groupName).text = name
                    groupView.findViewById<TextView>(R.id.groupMembers).text = "Members: ${names.joinToString(", ")}"
                    Toast.makeText(this@AddTransactionActivity, "Group updated", Toast.LENGTH_SHORT).show()
                    loadExistingGroups()
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error updating group: ${e.message}")
            }
        }
    }

    private fun showRemoveGroupConfirmation(group: PayerGroup, groupView: View?) {
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remove_group, null)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialogView.findViewById<TextView>(R.id.groupNameToRemove).text = group.groupName
        dialogView.findViewById<Button>(R.id.cancelRemoveBtn).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.confirmRemoveBtn).setOnClickListener {
            lifecycleScope.launch {
                try {
                    DeclareDatabase.groupsTable.delete {
                        filter {
                            eq("group_id", group.groupId!!)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        groupsContainer?.removeView(groupView)
                        groupViews?.remove(groupView)
                        payerGroups?.remove(group)
                        if (selectedGroup?.groupId == group.groupId) {
                            deselectGroup()
                        }
                        Toast.makeText(this@AddTransactionActivity, "Group removed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("Supabase", "Error deleting group: ${e.message}")
                }
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showPayorTooltip() {
        if (btnAdd == null) return
        dismissPayorTooltip()
        val tooltipView = LayoutInflater.from(this).inflate(R.layout.tooltip_add_payor, null)
        payorTooltipPopup = PopupWindow(tooltipView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false)
        payorTooltipPopup?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val tw = tooltipView.measuredWidth
        val th = tooltipView.measuredHeight

        btnAdd?.post {
            payorTooltipPopup?.showAsDropDown(btnAdd, -tw, -(btnAdd!!.height + th), Gravity.START)
        }
    }

    private fun dismissPayorTooltip() {
        payorTooltipPopup?.dismiss()
        payorTooltipPopup = null
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissPayorTooltip()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                if (event.rawX < loc[0] || event.rawX > loc[0] + v.width || event.rawY < loc[1] || event.rawY > loc[1] + v.height) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
