package com.waray.spendhound.ui.multi_transaction

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.MultiTransactionItem
import com.waray.spendhound.PayerContribution
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.PaymentConfigBottomSheet
import com.waray.spendhound.R
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.PendingTransaction
import com.waray.spendhound.databinding.ActivityAddTransactionsMultiBinding
import com.waray.spendhound.ui.multi_transaction.PayorEntry
import com.waray.spendhound.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StateBundle(
    val members: List<User>,
    val currentUser: User?,
    val isOnline: Boolean
)

class MultiTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionsMultiBinding
    private val viewModel: MultiTransactionViewModel by viewModels()
    private lateinit var adapter: MultiTransactionAdapter

    private var currentGroups: List<PayerGroup> = emptyList()
    private var currentMembers: List<User> = emptyList()
    private var editTransactionId: Long? = null
    private var editLocalId: String? = null
    private var isEditMode = false
    private var pendingGroupId: Long? = null
    private var latestMultiItems: List<MultiTransactionItem>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionsMultiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if in edit mode
        editTransactionId = intent.getLongExtra("TRANSACTION_ID", -1L).takeIf { it != -1L }
        editLocalId = intent.getStringExtra("LOCAL_ID")
        isEditMode = intent.getBooleanExtra("EDIT_MODE", false) && (editTransactionId != null || editLocalId != null)

        setupTransactionMode()
        setupToolbar()
        setupHistoryButton()
        setupRecyclerView()
        setupListeners()
        observeState()
        
        if (isEditMode) {
            loadTransactionForEdit()
        }
    }

    private fun setupTransactionMode() {
        // Use "Add Expenses" title
        binding.tvActivityTitle.text = if (isEditMode) getString(R.string.title_edit_transaction) else getString(R.string.title_add_transactions)
    }

    private fun setPaymentMode(isMultiple: Boolean) {
        // Payment mode toggle has been removed - this method is no longer used
        // Individual items now handle their own payment configuration via bottom sheets
        viewModel.setMultiplePayorsMode(isMultiple)
        validateSubmission()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupHistoryButton() {
        binding.btnHistory.isVisible = isEditMode
        binding.btnHistory.setOnClickListener {
            editTransactionId?.let { id ->
                ExpenseHistoryActivity.start(this, id)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MultiTransactionAdapter(
            onAmountChanged = { position, amount -> 
                viewModel.updateTransactionAmount(position, amount)
            },
            onTitleChanged = { position, title ->
                viewModel.updateItemTitle(position, title)
            },
            onCategoryChanged = { position, category ->
                viewModel.updateTransactionCategory(position, category)
                validateSubmission()
            },
            onValidationChanged = { validateSubmission() },
            onRemoveItem = { position -> viewModel.removeTransaction(position) },
            onPaymentConfigClick = { position, item -> showPaymentConfigBottomSheet(position, item) }
        )
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnAddRow.setOnClickListener {
            viewModel.addTransaction()
        }

        binding.etTransactionTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setTransactionTitle(s.toString())
                validateSubmission()
            }
        })

        binding.btnSubmit.setOnClickListener {
            val pos = binding.spinnerGroup.selectedItemPosition
            val selectedGroup = if (pos > 0) currentGroups.getOrNull(pos - 1) else null
            val groupId = selectedGroup?.groupId
            val requireTitle = adapter.getTransactions().size > 1
            
            if (groupId != null || !NetworkMonitor.isOnline.value) {
                if (isEditMode) {
                    if (editTransactionId != null && groupId != null) {
                        viewModel.updateTransaction(editTransactionId!!, groupId, requireTitle)
                    } else if (editLocalId != null) {
                        viewModel.updatePendingTransaction(editLocalId!!, groupId, requireTitle)
                    } else if (editTransactionId != null && groupId == null) {
                        Toast.makeText(this, "Group is required for synced transactions", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.submit(groupId, requireTitle)
                }
            } else {
                Toast.makeText(this, getString(R.string.error_select_group), Toast.LENGTH_SHORT).show()
            }
        }

        binding.spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val isOnline = NetworkMonitor.isOnline.value
                val isGroupSelected = pos > 0
                val shouldShowContent = isGroupSelected || !isOnline
                
                adapter.setGroupSelected(shouldShowContent)
                
                // When offline, we show the list even if no group is selected
                binding.rvTransactions.isVisible = shouldShowContent
                binding.btnAddRow.isVisible = shouldShowContent
                binding.summaryCard.isVisible = shouldShowContent
                
                if (!isGroupSelected && isOnline) {
                    binding.titleLabel.isVisible = false
                    binding.titleInputLayout.isVisible = false
                    binding.titleDivider.isVisible = false
                    validateSubmission()
                    return
                }
                
                if (isGroupSelected) {
                    currentGroups.getOrNull(pos - 1)?.groupId?.let { groupId ->
                        if (groupId == pendingGroupId) {
                            viewModel.onGroupSelected(groupId, resetTransactions = false)
                        } else if (pendingGroupId == null) {
                            // First time selecting a group for this session.
                            // If we already have items (e.g. from offline unassigned edit), preserve them.
                            viewModel.onGroupSelected(groupId, resetTransactions = false)
                            pendingGroupId = groupId
                        } else {
                            // Actual group change - reset items
                            adapter.resetToSingleItem()
                            viewModel.onGroupSelected(groupId)
                            pendingGroupId = groupId
                        }
                    }
                } else if (!isOnline) {
                    // Offline and no group selected
                    // Force a dummy member list containing at least the current user
                    viewModel.calculateTotals()
                }
                validateSubmission()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun trySelectPendingGroup() {
        val groupId = pendingGroupId ?: return
        if (currentGroups.isNotEmpty()) {
            val index = currentGroups.indexOfFirst { it.groupId == groupId }
            if (index != -1) {
                // Adjust for placeholder at index 0
                binding.spinnerGroup.setSelection(index + 1)
            }
        }
    }

    private fun setContentEnabled(enabled: Boolean) {
        binding.etTransactionTitle.isEnabled = enabled
        binding.spinnerGroup.isEnabled = enabled
        binding.btnAddRow.isClickable = enabled
        binding.rvTransactions.isEnabled = enabled
        // Re-validate to ensure submit button state is correct after loading
        if (enabled) {
            validateSubmission()
        } else {
            binding.btnSubmit.isEnabled = false
        }
    }

    private fun validateSubmission() {
        val transactions = adapter.getTransactions()
        val titleRequired = transactions.size > 1
        val titleFilled = !titleRequired || binding.etTransactionTitle.text?.isNotBlank() == true
        val isOnline = NetworkMonitor.isOnline.value
        val groupSelected = binding.spinnerGroup.selectedItemPosition > 0
        
        // Group is optional ONLY when offline
        val groupValid = groupSelected || !isOnline
        
        // If editing a pending transaction offline, we consider it a special case
        val isPendingOfflineEdit = !isOnline && editLocalId != null

        var allValid = transactions.isNotEmpty() && (titleFilled || isPendingOfflineEdit) && groupValid

        for (tx in transactions) {
            if (tx.amount <= 0 || tx.amount.isNaN()) { allValid = false; break }
            if (tx.category.isBlank()) { allValid = false; break }
            
            // Check if total payment equals the transaction amount - ONLY required when online
            // For pending offline edits, we relax the requirement further
            if (isOnline) {
                val totalPaid = tx.payors.sumOf { it.amount }
                if (totalPaid.isNaN() || Math.abs(totalPaid - tx.amount) >= 0.01) { allValid = false; break }
            }
        }

        binding.btnSubmit.isVisible = true
        binding.btnSubmit.isEnabled = allValid
        
        if (allValid) {
            binding.btnSubmit.setBackgroundResource(R.drawable.rounded_button)
        } else {
            binding.btnSubmit.setBackgroundResource(R.drawable.greyed_out_rounded_button)
        }
        
        // Debug logging
        android.util.Log.d("MultiTransaction", "Validation - allValid: $allValid, transactions: ${transactions.size}")
    }

    private fun updatePayorsChips(members: List<User>) {
        // Payment mode chips have been removed from the main activity
        // Individual items now handle their own payment configuration via bottom sheets
        // This method is no longer needed but kept for compatibility
    }

    private fun showPaymentConfigBottomSheet(position: Int, item: MultiTransactionItem) {
        val bottomSheet = PaymentConfigBottomSheet.newInstance(
            itemTitle = if (item.title.isNotEmpty()) item.title else getString(R.string.label_item_format, position + 1),
            itemAmount = item.amount,
            groupMembers = currentMembers,
            currentPayers = item.payers,
            currentParticipants = item.includedMembers,
            initialCoveredByMap = item.coveredByMap,
            splitMode = item.splitMode,
            customSplitMap = item.customSplitMap
        )
        
        bottomSheet.setOnConfirmWithCoversListener { payers, participants, coveredByMap, splitMode, customSplitMap ->
            // Update the item directly in adapter first
            val updatedItem = item.copy(
                payers = payers,
                includedMembers = participants,
                coveredByMap = coveredByMap,
                splitMode = splitMode,
                customSplitMap = customSplitMap
            )
            adapter.updateTransactionPayment(position, updatedItem)
            
            // Track the included members for this position
            adapter.updateIncludedMembers(position, participants)
            
            // Update ViewModel for consistency
            viewModel.updateItemPaymentConfig(position, payers, participants, coveredByMap, splitMode, customSplitMap)
            
            // Trigger validation after payment config changes
            validateSubmission()
        }
        
        bottomSheet.show(supportFragmentManager, "PaymentConfigBottomSheet")
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    viewModel.calculateTotals()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun updateTitleSectionVisibility(itemCount: Int) {
        val isOnline = NetworkMonitor.isOnline.value
        val isMulti = itemCount > 1
        
        // Group section visibility - only show when online
        binding.layoutGroupSection.isVisible = isOnline
        
        if (isOnline) {
            binding.titleCard.isVisible = true 
            binding.titleLabel.isVisible = isMulti
            binding.titleInputLayout.isVisible = isMulti
            binding.titleDivider.isVisible = isMulti
        } else {
            // Offline
            if (isMulti) {
                binding.titleCard.isVisible = true
                binding.titleLabel.isVisible = true
                binding.titleInputLayout.isVisible = true
                binding.titleDivider.isVisible = false // Hide divider when offline + multi
            } else {
                binding.titleCard.isVisible = false // Hide entirely if only 1 item offline
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.groups.collect { groups ->
                        currentGroups = groups
                        val names = mutableListOf(getString(R.string.placeholder_select_group))
                        names.addAll(groups.map { it.groupName ?: getString(R.string.placeholder_unnamed_group) })

                        val groupAdapter = ArrayAdapter(this@MultiTransactionActivity, android.R.layout.simple_spinner_item, names)
                        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinnerGroup.adapter = groupAdapter
                        
                        // Try to select pending group if we're in edit mode
                        trySelectPendingGroup()
                    }
                }
                launch {
                    combine(
                        viewModel.members,
                        viewModel.currentUser,
                        NetworkMonitor.isOnline
                    ) { members, currentUser, isOnline ->
                        StateBundle(members, currentUser, isOnline)
                    }.collect { bundle ->
                        val members = bundle.members
                        val currentUser = bundle.currentUser
                        val isOnline = bundle.isOnline
                        
                        // Update UI based on online status
                        val isGroupSelected = binding.spinnerGroup.selectedItemPosition > 0
                        val shouldShowContent = isGroupSelected || !isOnline
                        
                        binding.rvTransactions.isVisible = shouldShowContent
                        binding.btnAddRow.isVisible = shouldShowContent
                        binding.summaryCard.isVisible = shouldShowContent
                        
                        // Hide Group section when offline
                        binding.layoutGroupSection.isVisible = isOnline
                        
                        // Hide split summary in summary card when offline
                        binding.tvEachOwesLabel.isVisible = isOnline
                        binding.tvEachOwes.isVisible = isOnline
                        
                        adapter.setOnlineStatus(isOnline)
                        adapter.setGroupSelected(shouldShowContent)
                        
                        // If offline and no group members, use the current user as the only member
                        if (members.isEmpty() && !isOnline) {
                            if (currentUser != null) {
                                currentMembers = listOf(currentUser)
                                adapter.setMembers(currentMembers)
                            } else {
                                currentMembers = emptyList()
                                adapter.setMembers(emptyList())
                            }
                        } else {
                            currentMembers = members
                            adapter.setMembers(members)
                        }
                        
                        // Update UI section visibility
                        updateTitleSectionVisibility(adapter.itemCount)

                        viewModel.calculateTotals()
                        validateSubmission()
                    }
                }
                launch {
                    viewModel.transactions.collect { transactions ->
                        // Convert TransactionEntry to MultiTransactionItem for adapter
                        val multiItems = transactions.map { entry ->
                            val totalPaid = entry.payors.sumOf { it.amount }
                            val isPaymentComplete = Math.abs(totalPaid - entry.amount) < 0.01
                            
                            // Convert coveredByMap Long keys/values to String for UI
                            val stringCoveredByMap = entry.coveredByMap.map { 
                                it.key.toString() to it.value.toString() 
                            }.toMap()

                            // Convert customSplitMap Long keys to String for UI
                            val stringCustomSplitMap = entry.customSplitMap.map {
                                it.key.toString() to it.value
                            }.toMap()

                            MultiTransactionItem(
                                id = "",
                                title = entry.title,
                                amount = entry.amount,
                                category = entry.category,
                                payers = entry.payors.map { payor ->
                                    PayerContribution(
                                        payerId = payor.userId.toString(),
                                        payerName = payor.username,
                                        amount = payor.amount
                                    )
                                },
                                includedMembers = entry.includedMemberIds.map { it.toString() },
                                splitMode = entry.splitMode,
                                customSplitMap = stringCustomSplitMap,
                                isValid = entry.amount > 0 && entry.category.isNotEmpty() && entry.payors.isNotEmpty() && isPaymentComplete,
                                coveredByMap = stringCoveredByMap
                            )
                        }
                        
                        // Use post to avoid "Cannot call this method while RecyclerView is computing a layout or scrolling"
                        // We track the latest update to avoid race conditions with multiple posted tasks
                        latestMultiItems = multiItems
                        binding.rvTransactions.post {
                            if (latestMultiItems == multiItems) {
                                val oldSize = adapter.itemCount
                                adapter.setTransactions(multiItems)
                                
                                // Scroll to bottom if items were added
                                if (multiItems.size > oldSize && oldSize > 0) {
                                    binding.rvTransactions.smoothScrollToPosition(multiItems.size - 1)
                                }
                                
                                // Re-validate after adapter update
                                validateSubmission()
                            }
                        }
                        
                        val buttonText = if (isEditMode) {
                            getString(R.string.btn_update_expense)
                        } else {
                            if (transactions.size == 1) getString(R.string.btn_add_expense) else getString(R.string.btn_add_multiple_expenses_format, transactions.size)
                        }
                        binding.btnSubmit.text = buttonText
                        
                        // Handle title section visibility based on item count
                        updateTitleSectionVisibility(transactions.size)
                        
                        viewModel.calculateTotals()
                    }
                }
                launch {
                    viewModel.totalAmount.collect { total ->
                        binding.tvTotalAmount.text = CurrencyUtils.formatAmountWithCurrency(total)
                        validateSubmission()
                    }
                }
                launch {
                    viewModel.averageSplitPerIncludedMember.collect { averageSplit ->
                        binding.tvEachOwes.text = CurrencyUtils.formatAmountWithCurrency(averageSplit)
                    }
                }
                launch {
                    viewModel.isMultiplePayorsMode.collect { isMultiple ->
                        adapter.setMode(isMultiple)
                        validateSubmission()
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressOverlay.isVisible = loading
                        binding.appBarLayout.isEnabled = !loading
                        // Disable all interactive content during loading
                        setContentEnabled(!loading)
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        if (state is MultiTransactionViewModel.UiState.Loading) {
                            binding.progressOverlay.isVisible = true
                            setContentEnabled(false)
                        }
                        when (state) {
                            is MultiTransactionViewModel.UiState.Success -> {
                                Toast.makeText(this@MultiTransactionActivity, if (isEditMode) getString(R.string.toast_transaction_updated) else getString(R.string.toast_transactions_added), Toast.LENGTH_SHORT).show()
                                com.waray.spendhound.TransactionState.notifyChange()
                                finish()
                            }
                            is MultiTransactionViewModel.UiState.SuccessOffline -> {
                                Toast.makeText(this@MultiTransactionActivity, "Saved offline. Will sync when you're back online.", Toast.LENGTH_LONG).show()
                                com.waray.spendhound.TransactionState.notifyChange()
                                finish()
                            }
                            is MultiTransactionViewModel.UiState.Error -> {
                                binding.progressOverlay.isVisible = false
                                setContentEnabled(true)
                                Toast.makeText(this@MultiTransactionActivity, state.message, Toast.LENGTH_LONG).show()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
    
    private fun loadTransactionForEdit() {
        val txId = editTransactionId
        val localId = editLocalId
        
        lifecycleScope.launch {
            try {
                if (txId != null) {
                    // Load transaction data from database
                    val transaction = withContext(Dispatchers.IO) {
                        DeclareDatabase.transactionsTable.select {
                            filter { eq("id", txId) }
                        }.decodeSingleOrNull<com.waray.spendhound.ui.multi_transaction.TransactionFull>()
                    }
                    
                    if (transaction != null) {
                        // Set transaction title and pending group ID
                        binding.etTransactionTitle.setText(transaction.description)
                        pendingGroupId = transaction.groupId
                        
                        // Try to select group if groups are already loaded
                        trySelectPendingGroup()
                        
                        // Load transaction items, payors, and splits
                        val items = withContext(Dispatchers.IO) {
                            DeclareDatabase.transactionItemsTable.select {
                                filter { eq("transaction_id", txId) }
                            }.decodeList<TransactionItemFull>()
                        }
                        
                        val payors = withContext(Dispatchers.IO) {
                            DeclareDatabase.transactionPayorsTable.select {
                                filter { eq("transaction_id", txId) }
                            }.decodeList<TransactionPayorTable>()
                        }
                        
                        val splits = withContext(Dispatchers.IO) {
                            DeclareDatabase.transactionSplitsTable.select {
                                filter { eq("transaction_id", txId) }
                            }.decodeList<TransactionSplitTable>()
                        }
                        
                        // Load items immediately. Usernames will resolve if members are cached.
                        viewModel.loadExistingTransaction(transaction, items, payors, splits)
                        
                        // Wait for members in background to potentially refresh UI with correct names
                        launch {
                            viewModel.members.first { it.isNotEmpty() }
                            // Re-load if necessary to get correct usernames, or just let ViewModel handle it
                            viewModel.loadExistingTransaction(transaction, items, payors, splits)
                        }
                    }
                } else if (localId != null) {
                    val pending = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(this@MultiTransactionActivity).pendingTransactionDao().getAll().find { it.localId == localId }
                    }
                    if (pending != null) {
                        binding.etTransactionTitle.setText(pending.description)
                        pendingGroupId = pending.groupId
                        trySelectPendingGroup()
                        
                        // Load items immediately without waiting for members
                        viewModel.loadPendingTransactionForEdit(pending)
                        
                        // Separately ensure members are loaded if group exists
                        if (pendingGroupId != null) {
                            launch {
                                viewModel.members.first { it.isNotEmpty() }
                                // If needed, re-trigger calculateTotals or something
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiTransactionActivity", "Error loading transaction for edit", e)
                Toast.makeText(this@MultiTransactionActivity, getString(R.string.error_loading_transaction), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
