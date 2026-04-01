package com.waray.spendhound.ui.multi_transaction

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
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
    private val onValidationChanged: () -> Unit,
    private val onRemoveItem: (Int) -> Unit
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

            // 1. Item number
            binding.tvItemNumber.text = "Item ${position + 1}"

            // 2. Hide remove button when only 1 item
            binding.btnRemoveItem.isVisible = transactions.size > 1
            binding.btnRemoveItem.setOnClickListener {
                onRemoveItem(adapterPosition)
            }

            binding.layoutPayerRow.isVisible = isMultiplePayorsMode
            
            // 1. Category chips using CategorySpinnerAdapter icons
            val chipMap = mapOf(
                binding.catFood to "Foods",
                binding.catTransport to "Transportation",
                binding.catAccommodation to "House Necessity",
                binding.catGroceries to "Groceries",
                binding.catTravel to "Travel",
                binding.catShopping to "Online Shopping",
                binding.catElectricity to "Electricity",
                binding.catWater to "Water",
                binding.catRent to "Rent",
                binding.catInternet to "Internet",
                binding.catOthers to "Others"
            )
            fun updateChipSelection(selected: LinearLayout) {
                chipMap.keys.forEach { chip ->
                    chip.setBackgroundResource(
                        if (chip == selected) R.drawable.bg_category_chip_selected
                        else R.drawable.bg_category_chip
                    )
                }
            }
            chipMap.forEach { (chip, category) ->
                chip.setOnClickListener {
                    entry.category = category
                    updateChipSelection(chip)
                    onValidationChanged()
                }
            }
            val selectedChip = chipMap.entries.firstOrNull { it.value == entry.category }?.key
            if (selectedChip != null) updateChipSelection(selectedChip)

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
                    if (isMultiplePayorsMode) updateRemainingAmount(entry)
                    onAmountChanged()
                    onValidationChanged()
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
                            if (isMultiplePayorsMode) updateRemainingAmount(entry)
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
            if (!isMultiplePayorsMode) return
            val totalPaid = entry.payors.sumOf { it.amount }
            val remaining = entry.amount - totalPaid
            binding.tvRemainingAmount.text = "Remaining: ${CurrencyUtils.formatAmountWithCurrency(remaining)}"
            binding.tvRemainingAmount.setTextColor(if (remaining == 0.0) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        }
    }
}
