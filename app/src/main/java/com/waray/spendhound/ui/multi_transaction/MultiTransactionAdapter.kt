package com.waray.spendhound.ui.multi_transaction

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.MultiTransactionItem
import com.waray.spendhound.PayerContribution
import com.waray.spendhound.PaymentConfigBottomSheet
import com.waray.spendhound.R
import com.waray.spendhound.User
import com.waray.spendhound.databinding.ItemTransactionMultiBinding

class MultiTransactionAdapter(
    private val onAmountChanged: () -> Unit,
    private val onValidationChanged: () -> Unit,
    private val onRemoveItem: (Int) -> Unit,
    private val onPaymentConfigClick: (Int, MultiTransactionItem) -> Unit
) : RecyclerView.Adapter<MultiTransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<MultiTransactionItem>()
    private var groupMembers = listOf<User>()

    fun setTransactions(newTransactions: List<MultiTransactionItem>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    fun setMembers(members: List<User>) {
        this.groupMembers = members
        notifyDataSetChanged()
    }

    fun setMode(isMultiple: Boolean) {
        // Mode is now handled per-item via bottom sheets
        notifyDataSetChanged()
    }

    fun getTransactions(): List<TransactionEntry> {
        // Convert MultiTransactionItem back to TransactionEntry for compatibility
        return transactions.map { item ->
            TransactionEntry(
                title = item.title,
                amount = item.amount,
                category = item.category,
                payors = item.payers.map { payer ->
                    PayorEntry(
                        userId = payer.payerId.toLongOrNull() ?: 0L,
                        username = payer.payerName,
                        amount = payer.amount
                    )
                }.toMutableList()
            )
        }
    }

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

        fun bind(item: MultiTransactionItem, position: Int) {
            binding.etTitle.setText(item.title)
            binding.etAmount.setText(if (item.amount > 0) item.amount.toString() else "")

            // Item number
            binding.tvItemNumber.text = "Item ${position + 1}${if (item.category.isNotEmpty()) " — ${item.category.lowercase()}" else ""}"

            // Hide remove button when only 1 item
            binding.btnRemoveItem.isVisible = transactions.size > 1
            binding.btnRemoveItem.setOnClickListener {
                onRemoveItem(adapterPosition)
            }

            // Validation indicator
            binding.ivValidationError.isVisible = !item.isValid

            // Payment summary
            updatePaymentSummary(item)

            // Payment chip click listener
            binding.layoutPaymentChip.setOnClickListener {
                onPaymentConfigClick(adapterPosition, item)
            }
            
            // Category chips
            setupCategoryChips(item, position)

            // Amount listener
            binding.etAmount.removeTextChangedListener(amountWatcher)
            amountWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newAmount = s.toString().toDoubleOrNull() ?: 0.0
                    transactions[adapterPosition] = item.copy(amount = newAmount)
                    updateValidation(adapterPosition)
                    onAmountChanged()
                    onValidationChanged()
                }
            }
            binding.etAmount.addTextChangedListener(amountWatcher)

            // Title listener
            binding.etTitle.removeTextChangedListener(titleWatcher)
            titleWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    transactions[adapterPosition] = item.copy(title = s.toString())
                }
            }
            binding.etTitle.addTextChangedListener(titleWatcher)
        }

        private fun updatePaymentSummary(item: MultiTransactionItem) {
            binding.tvPaymentSummary.text = item.getPaymentSummary()
            binding.tvParticipantSummary.text = item.getParticipantSummary(groupMembers.size)
            
            // Update text color based on configuration state
            val textColor = if (item.payers.isEmpty()) {
                binding.root.context.getColor(R.color.grey)
            } else {
                binding.root.context.getColor(R.color.darkBlue)
            }
            binding.tvPaymentSummary.setTextColor(textColor)
        }

        private fun setupCategoryChips(item: MultiTransactionItem, position: Int) {
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
            
            fun updateChipSelection(selected: View) {
                chipMap.forEach { (view, category) ->
                    view.setBackgroundResource(
                        if (view == selected) R.drawable.bg_dark_chip_selected
                        else R.drawable.bg_dark_chip_outline
                    )
                }
                binding.tvItemNumber.text = "Item ${position + 1} — ${item.category.lowercase()}"
            }
            
            chipMap.forEach { (view, category) ->
                view.setOnClickListener {
                    transactions[adapterPosition] = item.copy(category = category)
                    updateChipSelection(view)
                    updateValidation(adapterPosition)
                    onValidationChanged()
                }
            }
            
            val selectedChip = chipMap.entries.firstOrNull { it.value == item.category }?.key
            if (selectedChip != null) updateChipSelection(selectedChip)
        }

        private fun updateValidation(position: Int) {
            val item = transactions[position]
            val isValid = item.amount > 0 && 
                         item.category.isNotEmpty() && 
                         item.payers.isNotEmpty() && 
                         item.isPaymentComplete()
            
            transactions[position] = item.copy(isValid = isValid)
            binding.ivValidationError.isVisible = !isValid
        }
    }
}
