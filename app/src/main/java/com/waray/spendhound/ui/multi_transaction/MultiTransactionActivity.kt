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
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionsMultiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = MultiTransactionAdapter(
            onAmountChanged = { viewModel.calculateTotals() },
            onValidationChanged = { validateSubmission() }
        )
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewModel.removeTransaction(viewHolder.adapterPosition)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvTransactions)
    }

    private fun setupListeners() {
        binding.togglePaymentMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.setMultiplePayorsMode(checkedId == binding.btnMultiplePayors.id)
            }
        }

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
                viewModel.submit(selectedGroup.groupId!!)
            }
        }

        binding.spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                currentGroups.getOrNull(pos)?.groupId?.let { viewModel.onGroupSelected(it) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spinnerGlobalPayor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val member = currentMembers.getOrNull(pos)
                if (member != null && !viewModel.isMultiplePayorsMode.value) {
                    viewModel.updateGlobalPayors(listOf(PayorEntry(member.id!!, member.username!!)))
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun validateSubmission() {
        val transactions = adapter.getTransactions()
        val titleFilled = binding.etTransactionTitle.text?.isNotBlank() == true
        var allValid = transactions.isNotEmpty() && titleFilled

        for (tx in transactions) {
            if (tx.amount <= 0) { allValid = false; break }
            val totalPaid = tx.payors.sumOf { it.amount }
            if (Math.abs(tx.amount - totalPaid) > 0.01) { allValid = false; break }
        }

        binding.btnSubmit.isVisible = allValid
        binding.btnSubmit.isEnabled = allValid
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
                        
                        // Update Global Payor Spinner
                        val names = members.map { it.username ?: "Unknown" }
                        val memberAdapter = ArrayAdapter(this@MultiTransactionActivity, android.R.layout.simple_spinner_item, names)
                        memberAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinnerGlobalPayor.adapter = memberAdapter

                        viewModel.calculateTotals()
                    }
                }
                launch {
                    viewModel.transactions.collect { transactions ->
                        adapter.setTransactions(transactions)
                        binding.btnSubmit.text = "Add ${transactions.size} Transactions"
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
                        binding.layoutSinglePayor.isVisible = !isMultiple
                        adapter.setMode(isMultiple)
                        validateSubmission()
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressOverlay.isVisible = state is MultiTransactionViewModel.UiState.Loading
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
