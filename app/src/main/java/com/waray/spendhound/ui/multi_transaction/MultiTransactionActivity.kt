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
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.R
import com.waray.spendhound.User
import com.waray.spendhound.databinding.ActivityAddTransactionsMultiBinding
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
            binding.titleSection.visibility = View.GONE
            binding.btnAddRow.visibility = View.GONE
            binding.toolbar.title = "Add Transaction"
        } else {
            binding.titleSection.visibility = View.VISIBLE
            binding.btnAddRow.visibility = View.VISIBLE
            binding.toolbar.title = "Add Transactions"
        }
    }

    private fun setPaymentMode(isMultiple: Boolean) {
        if (isMultiple) {
            binding.btnMultiplePayors.setBackgroundResource(R.drawable.bg_toggle_selected_orange)
            binding.btnMultiplePayors.setTypeface(ResourcesCompat.getFont(this, R.font.montserratalternatess_bold))
            binding.btnMultiplePayors.setTextColor(getColor(R.color.whitest))
            binding.btnSinglePayor.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnSinglePayor.setTypeface(ResourcesCompat.getFont(this, R.font.montserratalternatess_regular))
            binding.btnSinglePayor.setTextColor(getColor(R.color.white_70))
            binding.titleSection.visibility = View.VISIBLE
        } else {
            binding.btnSinglePayor.setBackgroundResource(R.drawable.bg_toggle_selected_orange)
            binding.btnSinglePayor.setTypeface(ResourcesCompat.getFont(this, R.font.montserratalternatess_bold))
            binding.btnSinglePayor.setTextColor(getColor(R.color.whitest))
            binding.btnMultiplePayors.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnMultiplePayors.setTypeface(ResourcesCompat.getFont(this, R.font.montserratalternatess_regular))
            binding.btnMultiplePayors.setTextColor(getColor(R.color.white_70))
            binding.titleSection.visibility = View.GONE
            binding.etTransactionTitle.text?.clear()
            viewModel.setTransactionTitle("")
        }
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
            onRemoveItem = { position -> viewModel.removeTransaction(position) }
        )
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSinglePayor.setOnClickListener { setPaymentMode(false) }
        binding.btnMultiplePayors.setOnClickListener { setPaymentMode(true) }

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
                val requireTitle = binding.titleSection.visibility == View.VISIBLE
                viewModel.submit(selectedGroup.groupId!!, requireTitle)
            }
        }

        binding.spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                currentGroups.getOrNull(pos)?.groupId?.let { viewModel.onGroupSelected(it) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setContentEnabled(enabled: Boolean) {
        binding.etTransactionTitle.isEnabled = enabled
        binding.spinnerGroup.isEnabled = enabled
        binding.btnSinglePayor.isClickable = enabled
        binding.btnMultiplePayors.isClickable = enabled
        binding.btnAddRow.isClickable = enabled
        binding.rvTransactions.isEnabled = enabled
        binding.btnSubmit.isEnabled = enabled && binding.btnSubmit.isVisible
    }

    private fun validateSubmission() {
        val transactions = adapter.getTransactions()
        val titleRequired = binding.titleSection.visibility == View.VISIBLE
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
        binding.layoutPayorsChips.removeAllViews()
        val selectedPayors = viewModel.globalPayors.value

        members.forEach { member ->
            val isSelected = selectedPayors.any { it.userId == member.id }
            val chipLayoutId = if (isSelected) R.layout.item_payor_chip_dark else R.layout.item_payor_chip_dark_outline
            val chipView = layoutInflater.inflate(chipLayoutId, binding.layoutPayorsChips, false)

            val tvInitial = chipView.findViewById<TextView>(R.id.tvInitial)
            val tvName = chipView.findViewById<TextView>(R.id.tvName)

            tvInitial?.text = member.username?.take(1)?.uppercase() ?: "?"
            tvName?.text = member.username ?: "Unknown"

            if (isSelected) {
                tvName.setTextColor(getColor(R.color.black))
            } else {
                tvName.setTextColor(getColor(R.color.darkBlue))
            }

            chipView.setOnClickListener {
                if (!viewModel.isMultiplePayorsMode.value) {
                    viewModel.updateGlobalPayors(listOf(PayorEntry(member.id!!, member.username!!)))
                    updatePayorsChips(members)
                    validateSubmission()
                }
            }
            binding.layoutPayorsChips.addView(chipView)
        }
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

                        // Update Payors Chips
                        updatePayorsChips(members)

                        viewModel.calculateTotals()
                    }
                }
                launch {
                    viewModel.transactions.collect { transactions ->
                        adapter.setTransactions(transactions)
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
                        // binding.layoutSinglePayor.isVisible = !isMultiple
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
