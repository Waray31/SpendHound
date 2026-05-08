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
import com.waray.spendhound.MultiTransactionItem
import com.waray.spendhound.PayerContribution
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.PaymentConfigBottomSheet
import com.waray.spendhound.R
import com.waray.spendhound.User
import com.waray.spendhound.databinding.ActivityAddTransactionsMultiBinding
import com.waray.spendhound.ui.multi_transaction.PayorEntry
import kotlinx.coroutines.launch

class MultiTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionsMultiBinding
    private val viewModel: MultiTransactionViewModel by viewModels()
    private lateinit var adapter: MultiTransactionAdapter

    private var currentGroups: List<PayerGroup> = emptyList()
    private var currentMembers: List<User> = emptyList()
    private var isSingleTransactionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionsMultiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val transactionMode = intent.getStringExtra("TRANSACTION_MODE")
        isSingleTransactionMode = transactionMode == "SINGLE"

        setupTransactionMode()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupTransactionMode() {
        if (isSingleTransactionMode) {
            binding.btnAddRow.visibility = View.GONE
            binding.toolbar.title = "Add Transaction"
        } else {
            binding.btnAddRow.visibility = View.VISIBLE
            binding.toolbar.title = "Add Transactions"
        }
    }

    private fun setPaymentMode(isMultiple: Boolean) {
        // Payment mode toggle has been removed - this method is no longer used
        // Individual items now handle their own payment configuration via bottom sheets
        viewModel.setMultiplePayorsMode(isMultiple)
        validateSubmission()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = MultiTransactionAdapter(
            onAmountChanged = { viewModel.calculateTotals() },
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
            val selectedGroup = currentGroups.getOrNull(binding.spinnerGroup.selectedItemPosition)
            if (selectedGroup?.groupId != null) {
                val requireTitle = !isSingleTransactionMode
                viewModel.submit(selectedGroup.groupId!!, requireTitle)
            }
        }

        binding.spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                currentGroups.getOrNull(pos)?.groupId?.let { groupId ->
                    // Reset adapter to single item when group changes
                    adapter.resetToSingleItem()
                    viewModel.onGroupSelected(groupId)
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setContentEnabled(enabled: Boolean) {
        binding.etTransactionTitle.isEnabled = enabled
        binding.spinnerGroup.isEnabled = enabled
        binding.btnAddRow.isClickable = enabled
        binding.rvTransactions.isEnabled = enabled
        binding.btnSubmit.isEnabled = enabled && binding.btnSubmit.isVisible
    }

    private fun validateSubmission() {
        val transactions = adapter.getTransactions()
        val titleRequired = !isSingleTransactionMode
        val titleFilled = !titleRequired || binding.etTransactionTitle.text?.isNotBlank() == true
        var allValid = transactions.isNotEmpty() && titleFilled

        for (tx in transactions) {
            if (tx.amount <= 0) { allValid = false; break }
            if (tx.category.isBlank()) { allValid = false; break }
            val totalPaid = tx.payors.sumOf { it.amount }
            if (Math.abs(tx.amount - totalPaid) > 0.01) { allValid = false; break }
        }

        binding.btnSubmit.isVisible = allValid
        binding.btnSubmit.isEnabled = allValid
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

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.groups.collect { groups ->
                        currentGroups = groups
                        val names = groups.map { it.groupName ?: "Unnamed Group" }
                        val groupAdapter = ArrayAdapter(this@MultiTransactionActivity, android.R.layout.simple_spinner_item, names)
                        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinnerGroup.adapter = groupAdapter
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
                                includedMembers = currentMembers.map { it.id.toString() }, // Default all members included
                                isValid = entry.amount > 0 && entry.category.isNotEmpty() && entry.payors.isNotEmpty()
                            )
                        }
                        adapter.setTransactions(multiItems)
                        binding.btnSubmit.text = if (isSingleTransactionMode) "Add Transaction" else "Add ${transactions.size} Transactions"
                        viewModel.calculateTotals()
                        validateSubmission()
                    }
                }
                launch {
                    viewModel.totalAmount.collect { total ->
                        binding.tvTotalAmount.text = CurrencyUtils.formatAmountWithCurrency(total)
                        val memberCount = currentMembers.size.coerceAtLeast(1)
                        binding.tvEachOwes.text = CurrencyUtils.formatAmountWithCurrency(total / memberCount)
                        validateSubmission()
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
                                Toast.makeText(this@MultiTransactionActivity, "Transactions added!", Toast.LENGTH_SHORT).show()
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
}
