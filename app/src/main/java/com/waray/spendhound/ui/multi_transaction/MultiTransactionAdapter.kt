package com.waray.spendhound.ui.multi_transaction

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.R
import com.waray.spendhound.User
import com.waray.spendhound.databinding.ItemTransactionMultiBinding

class MultiTransactionAdapter(
    private val onAmountChanged: () -> Unit,
    private val onValidationChanged: () -> Unit
) : RecyclerView.Adapter<MultiTransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<TransactionEntry>()
    private var isMultiplePayorsMode = false
    private var groupMembers = listOf<User>()
    private val expandedSplits = mutableSetOf<Int>()

    fun setTransactions(newTransactions: List<TransactionEntry>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    fun setMembers(members: List<User>) {
        this.groupMembers = members
        notifyDataSetChanged()
    }

    fun setMode(isMultiple: Boolean) {
        isMultiplePayorsMode = isMultiple
        notifyDataSetChanged()
    }

    fun addRow(entry: TransactionEntry) {
        transactions.add(entry)
        notifyItemInserted(transactions.size - 1)
    }

    fun removeRow(position: Int) {
        if (position in transactions.indices) {
            transactions.removeAt(position)
            expandedSplits.remove(position)
            notifyItemRemoved(position)
            onAmountChanged()
        }
    }

    fun getTransactions() = transactions

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionMultiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position], position)
    }

    override fun getItemCount() = transactions.size

    inner class TransactionViewHolder(private val binding: ItemTransactionMultiBinding) : RecyclerView.ViewHolder(binding.root) {
        
        private var amountWatcher: TextWatcher? = null
        private var titleWatcher: TextWatcher? = null

        fun bind(entry: TransactionEntry, position: Int) {
            binding.etTitle.setText(entry.title)
            binding.etAmount.setText(if (entry.amount > 0) entry.amount.toString() else "")
            
            binding.layoutPayerRow.isVisible = isMultiplePayorsMode
            binding.btnToggleSplit.isVisible = isMultiplePayorsMode
            
            // Setup Category Spinner
            val categories = listOf("General", "Food", "Transport", "Bills", "Entertainment")
            val catAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, categories)
            catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCategory.adapter = catAdapter
            binding.spinnerCategory.setSelection(categories.indexOf(entry.category).coerceAtLeast(0))
            binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) { entry.category = categories[pos] }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            if (isMultiplePayorsMode) {
                setupPayorLogic(entry, position)
            }

            // Amount Listener
            binding.etAmount.removeTextChangedListener(amountWatcher)
            amountWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    entry.amount = s.toString().toDoubleOrNull() ?: 0.0
                    if (!expandedSplits.contains(position) && entry.payors.size == 1) {
                        entry.payors[0].amount = entry.amount
                    }
                    updateRemainingAmount(entry)
                    onAmountChanged()
                }
            }
            binding.etAmount.addTextChangedListener(amountWatcher)

            binding.etTitle.removeTextChangedListener(titleWatcher)
            titleWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { entry.title = s.toString() }
            }
            binding.etTitle.addTextChangedListener(titleWatcher)
        }

        private fun setupPayorLogic(entry: TransactionEntry, position: Int) {
            val memberNames = groupMembers.map { it.username ?: "Unknown" }
            val memberAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, memberNames)
            memberAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerSinglePayor.adapter = memberAdapter

            val isExpanded = expandedSplits.contains(position)
            binding.spinnerSinglePayor.isVisible = !isExpanded
            binding.layoutMultiPayorContainer.isVisible = isExpanded
            binding.tvRemainingAmount.isVisible = isExpanded
            binding.btnToggleSplit.text = if (isExpanded) "Cancel Split" else "Split Payors"

            // Initial selection for Single Payor
            if (!isExpanded && entry.payors.isNotEmpty()) {
                val currentPayorId = entry.payors[0].userId
                val index = groupMembers.indexOfFirst { it.id == currentPayorId }
                if (index != -1) binding.spinnerSinglePayor.setSelection(index)
            }

            binding.spinnerSinglePayor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    if (!expandedSplits.contains(position)) {
                        val member = groupMembers[pos]
                        entry.payors = mutableListOf(PayorEntry(member.id!!, member.username!!, entry.amount))
                        onValidationChanged()
                    }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            binding.btnToggleSplit.setOnClickListener {
                if (expandedSplits.contains(position)) {
                    expandedSplits.remove(position)
                    // Reset to single payor (first member or current selection)
                    val selectedPos = binding.spinnerSinglePayor.selectedItemPosition.coerceAtLeast(0)
                    if (groupMembers.isNotEmpty()) {
                        val member = groupMembers[selectedPos]
                        entry.payors = mutableListOf(PayorEntry(member.id!!, member.username!!, entry.amount))
                    }
                } else {
                    expandedSplits.add(position)
                    // Initialize multi-payor list if empty
                    if (entry.payors.size <= 1) {
                        val currentSingle = entry.payors.firstOrNull()
                        entry.payors = groupMembers.map { member ->
                            val amt = if (member.id == currentSingle?.userId) entry.amount else 0.0
                            PayorEntry(member.id!!, member.username!!, amt)
                        }.toMutableList()
                    }
                }
                notifyItemChanged(position)
                onValidationChanged()
            }

            if (isExpanded) {
                renderMultiPayorInputs(entry)
            }
        }

        private fun renderMultiPayorInputs(entry: TransactionEntry) {
            binding.layoutMultiPayorContainer.removeAllViews()
            entry.payors.forEachIndexed { index, payor ->
                val inputLayout = TextInputLayout(itemView.context, null, com.google.android.material.R.attr.textInputStyle).apply {
                    hint = payor.username
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 8, 0, 0) }
                }
                val editText = TextInputEditText(inputLayout.context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(if (payor.amount > 0) payor.amount.toString() else "")
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            payor.amount = s.toString().toDoubleOrNull() ?: 0.0
                            updateRemainingAmount(entry)
                            onValidationChanged()
                        }
                    })
                }
                inputLayout.addView(editText)
                binding.layoutMultiPayorContainer.addView(inputLayout)
            }
            updateRemainingAmount(entry)
        }

        private fun updateRemainingAmount(entry: TransactionEntry) {
            val totalPaid = entry.payors.sumOf { it.amount }
            val remaining = entry.amount - totalPaid
            binding.tvRemainingAmount.text = "Remaining: ${CurrencyUtils.formatAmountWithCurrency(remaining)}"
            binding.tvRemainingAmount.setTextColor(if (remaining == 0.0) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        }
    }
}
