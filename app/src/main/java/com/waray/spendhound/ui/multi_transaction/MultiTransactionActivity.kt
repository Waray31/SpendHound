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
import com.waray.spendhound.databinding.ActivityAddTransactionsMultiBinding
import com.waray.spendhound.ui.multi_transaction.PayorEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionsMultiBinding
    private val viewModel: MultiTransactionViewModel by viewModels()
    private lateinit var adapter: MultiTransactionAdapter

    private var currentGroups: List<PayerGroup> = emptyList()
    private var currentMembers: List<User> = emptyList()
    private var editTransactionId: Long? = null
    private var isEditMode = false
    private var pendingGroupId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionsMultiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if in edit mode
        editTransactionId = intent.getLongExtra("TRANSACTION_ID", -1L).takeIf { it != -1L }
        isEditMode = intent.getBooleanExtra("EDIT_MODE", false) && editTransactionId != null

        setupTransactionMode()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeState()
        
        if (isEditMode) {
            loadTransactionForEdit()
        }
    }

    private fun setupTransactionMode() {
        // Always show add row button and use "Add Transactions" title
        binding.btnAddRow.visibility = View.VISIBLE
        binding.tvActivityTitle.text = if (isEditMode) "Edit Transaction" else "Add Transactions"
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
            binding.rvTransactions.smoothScrollToPosition(adapter.itemCount - 1)
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
            if (pos > 0) {
                val selectedGroup = currentGroups.getOrNull(pos - 1)
                if (selectedGroup?.groupId != null) {
                    val requireTitle = adapter.getTransactions().size > 1
                    if (isEditMode) {
                        viewModel.updateTransaction(editTransactionId!!, selectedGroup.groupId!!, requireTitle)
                    } else {
                        viewModel.submit(selectedGroup.groupId!!, requireTitle)
                    }
                }
            } else {
                Toast.makeText(this, "Please select a group", Toast.LENGTH_SHORT).show()
            }
        }

        binding.spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                // Adjust for "Select a payer group" placeholder at index 0
                if (pos == 0) {
                    validateSubmission()
                    return
                }
                
                currentGroups.getOrNull(pos - 1)?.groupId?.let { groupId ->
                    if (isEditMode && groupId == pendingGroupId) {
                        // Initial load for edit mode - don't reset transactions
                        viewModel.onGroupSelected(groupId, resetTransactions = false)
                        pendingGroupId = null // Clear once matched
                    } else {
                        // Regular group change or manual selection
                        adapter.resetToSingleItem()
                        viewModel.onGroupSelected(groupId)
                    }
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
        val groupSelected = binding.spinnerGroup.selectedItemPosition > 0
        var allValid = transactions.isNotEmpty() && titleFilled && groupSelected

        for (tx in transactions) {
            if (tx.amount <= 0) { allValid = false; break }
            if (tx.category.isBlank()) { allValid = false; break }
            
            // Check if total payment equals the transaction amount
            val totalPaid = tx.payors.sumOf { it.amount }
            if (Math.abs(totalPaid - tx.amount) >= 0.01) { allValid = false; break }
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
            itemTitle = if (item.title.isNotEmpty()) item.title else "Item ${position + 1}",
            itemAmount = item.amount,
            groupMembers = currentMembers,
            currentPayers = item.payers,
            currentParticipants = item.includedMembers
        )
        
        bottomSheet.setOnConfirmListener { payers, participants ->
            // Update the item directly in adapter first
            val updatedItem = item.copy(
                payers = payers,
                includedMembers = participants
            )
            adapter.updateTransactionPayment(position, updatedItem)
            
            // Track the included members for this position
            adapter.updateIncludedMembers(position, participants)
            
            // Update ViewModel for consistency
            viewModel.updateItemPaymentConfig(position, payers, participants)
            
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
        // Find the title input section only (not the entire card)
        val titleLabel = findViewById<TextView>(R.id.titleLabel)
        val titleInputLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.titleInputLayout)
        val titleDivider = findViewById<View>(R.id.titleDivider)
        
        val visibility = if (itemCount > 1) View.VISIBLE else View.GONE
        titleLabel?.visibility = visibility
        titleInputLayout?.visibility = visibility
        titleDivider?.visibility = visibility
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.groups.collect { groups ->
                        currentGroups = groups
                        val names = mutableListOf("Select a payer group")
                        names.addAll(groups.map { it.groupName ?: "Unnamed Group" })

                        val groupAdapter = ArrayAdapter(this@MultiTransactionActivity, android.R.layout.simple_spinner_item, names)
                        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinnerGroup.adapter = groupAdapter
                        
                        // Try to select pending group if we're in edit mode
                        trySelectPendingGroup()
                    }
                }
                launch {
                    viewModel.members.collect { members ->
                        currentMembers = members
                        adapter.setMembers(members)
                        viewModel.calculateTotals()
                    }
                }
                launch {
                    viewModel.transactions.collect { transactions ->
                        // Convert TransactionEntry to MultiTransactionItem for adapter
                        val multiItems = transactions.map { entry ->
                            val totalPaid = entry.payors.sumOf { it.amount }
                            val isPaymentComplete = Math.abs(totalPaid - entry.amount) < 0.01
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
                                isValid = entry.amount > 0 && entry.category.isNotEmpty() && entry.payors.isNotEmpty() && isPaymentComplete
                            )
                        }
                        adapter.setTransactions(multiItems)
                        val buttonText = if (isEditMode) {
                            "Update Expense"
                        } else {
                            if (transactions.size == 1) "Add Expense" else "Add ${transactions.size} Expenses"
                        }
                        binding.btnSubmit.text = buttonText
                        
                        // Handle title section visibility based on item count
                        updateTitleSectionVisibility(transactions.size)
                        
                        viewModel.calculateTotals()
                        validateSubmission()
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
                                Toast.makeText(this@MultiTransactionActivity, if (isEditMode) "Transaction updated!" else "Transactions added!", Toast.LENGTH_SHORT).show()
                                com.waray.spendhound.TransactionState.notifyChange()
                                finish()
                            }
                            is MultiTransactionViewModel.UiState.Error -> {
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
        val txId = editTransactionId ?: return
        lifecycleScope.launch {
            try {
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
                    
                    // Wait for members to be loaded for the group before populating
                    // This ensures usernames are resolved correctly
                    launch {
                        viewModel.members.first { it.isNotEmpty() }
                        viewModel.loadExistingTransaction(transaction, items, payors, splits)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiTransactionActivity", "Error loading transaction for edit", e)
                Toast.makeText(this@MultiTransactionActivity, "Error loading transaction", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
