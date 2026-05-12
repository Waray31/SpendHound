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
    private val onAmountChanged: (Int, Double) -> Unit,
    private val onCategoryChanged: (Int, String) -> Unit,
    private val onValidationChanged: () -> Unit,
    private val onRemoveItem: (Int) -> Unit,
    private val onPaymentConfigClick: (Int, MultiTransactionItem) -> Unit
) : RecyclerView.Adapter<MultiTransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<MultiTransactionItem>()
    private var groupMembers = listOf<User>()
    
    // Track current EditText values, selected categories, and included members to preserve them across updates
    private val currentTitles = mutableMapOf<Int, String>()
    private val currentAmounts = mutableMapOf<Int, String>()
    private val currentCategories = mutableMapOf<Int, String>()
    private val currentIncludedMembers = mutableMapOf<Int, List<String>>()

    fun setTransactions(newTransactions: List<MultiTransactionItem>) {
        val oldSize = transactions.size
        val newSize = newTransactions.size
        
        if (newSize > oldSize) {
            // Items were added - preserve existing tracked values and initialize new items
            transactions.clear()
            transactions.addAll(newTransactions)
            
            notifyItemRangeInserted(oldSize, newSize - oldSize)
            // Update remove button visibility for all existing items
            if (oldSize == 1) {
                notifyItemChanged(0) // First item now needs remove button
            }
        } else if (newSize < oldSize) {
            // Items were removed - clean up tracked values for removed positions
            for (i in newSize until oldSize) {
                currentTitles.remove(i)
                currentAmounts.remove(i)
                currentCategories.remove(i)
                currentIncludedMembers.remove(i)
            }
            transactions.clear()
            transactions.addAll(newTransactions)
            notifyItemRangeRemoved(newSize, oldSize - newSize)
            // Update remove button visibility if we're down to 1 item
            if (newSize == 1) {
                notifyItemChanged(0) // Last item should hide remove button
            }
        } else {
            // Same size, only update payment-related changes without affecting EditTexts or categories
            for (i in transactions.indices) {
                if (transactions[i].payers != newTransactions[i].payers || 
                    transactions[i].includedMembers != newTransactions[i].includedMembers ||
                    transactions[i].isValid != newTransactions[i].isValid) {
                    transactions[i] = newTransactions[i]
                    notifyItemChanged(i)
                }
            }
        }
    }

    fun updateTransactionPayment(position: Int, updatedItem: MultiTransactionItem) {
        if (position in transactions.indices) {
            transactions[position] = updatedItem
            notifyItemChanged(position)
        }
    }

    fun updateIncludedMembers(position: Int, includedMembers: List<String>) {
        currentIncludedMembers[position] = includedMembers
    }

    fun resetToSingleItem() {
        // Clear all tracked data
        currentTitles.clear()
        currentAmounts.clear()
        currentCategories.clear()
        currentIncludedMembers.clear()
        
        // Reset to single empty item
        transactions.clear()
        transactions.add(MultiTransactionItem())
        
        notifyDataSetChanged()
    }

    fun clearCategorySelections() {
        // This will be called after notifyDataSetChanged to ensure views are bound
        // The setupCategoryChips method will handle clearing selections based on empty category
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
                }.toMutableList(),
                includedMemberIds = item.includedMembers.mapNotNull { it.toLongOrNull() }
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
            // Use tracked values if available, otherwise use item values
            val titleToShow = currentTitles[position] ?: item.title
            val amountToShow = currentAmounts[position] ?: (if (item.amount > 0) item.amount.toString() else "")
            val categoryToShow = currentCategories[position] ?: item.category
            
            // Only set EditText values if they're different to avoid clearing user input
            if (binding.etTitle.text.toString() != titleToShow) {
                binding.etTitle.setText(titleToShow)
            }
            if (binding.etAmount.text.toString() != amountToShow) {
                binding.etAmount.setText(amountToShow)
            }

            // Item number with category display
            binding.tvItemNumber.text = "Item ${position + 1}${if (categoryToShow.isNotEmpty()) " — ${categoryToShow.lowercase()}" else ""}"

            // Show remove button when there are multiple items
            binding.btnRemoveItem.isVisible = transactions.size > 1
            binding.btnRemoveItem.setOnClickListener {
                // Clear tracked values for this position
                currentTitles.remove(adapterPosition)
                currentAmounts.remove(adapterPosition)
                currentCategories.remove(adapterPosition)
                currentIncludedMembers.remove(adapterPosition)
                onRemoveItem(adapterPosition)
            }

            // Validation indicator and message
            val currentAmount = currentAmounts[position]?.toDoubleOrNull() ?: item.amount
            val hasAmountInput = currentAmount > 0
            val hasPaymentConfig = item.payers.isNotEmpty() && item.isPaymentComplete()
            
            when {
                !hasAmountInput -> {
                    // No amount inputted
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Please input amount first"
                }
                hasAmountInput && !hasPaymentConfig -> {
                    // Amount inputted but no payment config
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Select a payor and input payment"
                }
                else -> {
                    // Amount and payment config complete
                    binding.ivValidationError.isVisible = false
                    binding.tvValidationMessage.isVisible = false
                }
            }

            // Payment summary
            updatePaymentSummary(item)

            // Payment chip click listener
            binding.layoutPaymentChip.setOnClickListener {
                // Get current amount from EditText instead of stored item amount
                val currentAmount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
                // Use tracked included members if available, otherwise use item's included members
                val includedMembers = currentIncludedMembers[adapterPosition] ?: item.includedMembers
                val updatedItem = item.copy(
                    amount = currentAmount,
                    includedMembers = includedMembers
                )
                onPaymentConfigClick(adapterPosition, updatedItem)
            }
            
            // Category chips
            setupCategoryChips(item, position, categoryToShow)

            // Amount listener
            binding.etAmount.removeTextChangedListener(amountWatcher)
            amountWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newAmount = s.toString().toDoubleOrNull() ?: 0.0
                    val amountText = s.toString()
                    
                    // Track the current amount text
                    currentAmounts[adapterPosition] = amountText
                    
                    transactions[adapterPosition] = item.copy(amount = newAmount)
                    
                    // Enable/disable payment section based on amount
                    val hasAmount = newAmount > 0
                    binding.layoutPaymentChip.isClickable = hasAmount
                    binding.layoutPaymentChip.isFocusable = hasAmount
                    binding.layoutPaymentChip.alpha = if (hasAmount) 1.0f else 0.5f
                    
                    updateValidation(adapterPosition)
                    onAmountChanged(adapterPosition, newAmount)
                    onValidationChanged()
                }
            }
            binding.etAmount.addTextChangedListener(amountWatcher)
            
            // Initial payment section state based on current amount
            val hasAmount = (currentAmounts[position]?.toDoubleOrNull() ?: item.amount) > 0
            binding.layoutPaymentChip.isClickable = hasAmount
            binding.layoutPaymentChip.isFocusable = hasAmount
            binding.layoutPaymentChip.alpha = if (hasAmount) 1.0f else 0.5f

            // Title listener
            binding.etTitle.removeTextChangedListener(titleWatcher)
            titleWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val titleText = s.toString()
                    
                    // Track the current title text
                    currentTitles[adapterPosition] = titleText
                    
                    transactions[adapterPosition] = item.copy(title = titleText)
                }
            }
            binding.etTitle.addTextChangedListener(titleWatcher)
        }

        private fun updatePaymentSummary(item: MultiTransactionItem) {
            binding.tvPaymentSummary.text = item.getPaymentSummary()
            
            // Use tracked included members if available, otherwise use item's included members
            val includedMembers = currentIncludedMembers[adapterPosition] ?: item.includedMembers
            
            binding.tvParticipantSummary.text = when {
                includedMembers.isEmpty() -> "All members"
                includedMembers.size == groupMembers.size -> "All members"
                else -> "${includedMembers.size}/${groupMembers.size} members"
            }
            
            // Update text color based on configuration state
            val textColor = if (item.payers.isEmpty()) {
                binding.root.context.getColor(R.color.grey)
            } else {
                binding.root.context.getColor(R.color.darkBlue)
            }
            binding.tvPaymentSummary.setTextColor(textColor)
        }

        private fun setupCategoryChips(item: MultiTransactionItem, position: Int, currentCategory: String) {
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
            
            fun updateChipSelection(selected: View, category: String) {
                chipMap.forEach { (view, cat) ->
                    view.setBackgroundResource(
                        if (view == selected) R.drawable.bg_dark_chip_selected
                        else R.drawable.bg_profile_card
                    )
                }
                
                // Track the selected category
                currentCategories[adapterPosition] = category
                
                // Update item number display
                binding.tvItemNumber.text = "Item ${position + 1} — ${category.lowercase()}"
                
                // Update transaction data
                transactions[adapterPosition] = item.copy(category = category)
                
                // Notify ViewModel of category change
                onCategoryChanged(adapterPosition, category)
            }
            
            chipMap.forEach { (view, category) ->
                view.setOnClickListener {
                    updateChipSelection(view, category)
                    updateValidation(adapterPosition)
                }
            }
            
            // Set initial selection based on tracked category or item category
            if (currentCategory.isNotEmpty()) {
                val selectedChip = chipMap.entries.firstOrNull { it.value == currentCategory }?.key
                if (selectedChip != null) {
                    chipMap.forEach { (view, cat) ->
                        view.setBackgroundResource(
                            if (view == selectedChip) R.drawable.bg_dark_chip_selected
                            else R.drawable.bg_profile_card
                        )
                    }
                }
            } else {
                // No category selected - reset all chips to unselected state
                chipMap.forEach { (view, cat) ->
                    view.setBackgroundResource(R.drawable.bg_profile_card)
                }
            }
        }

        private fun updateValidation(position: Int) {
            val item = transactions[position]
            val currentAmount = currentAmounts[position]?.toDoubleOrNull() ?: item.amount
            val hasAmountInput = currentAmount > 0
            val hasPaymentConfig = item.payers.isNotEmpty() && item.isPaymentComplete()
            val isValid = hasAmountInput && item.category.isNotEmpty() && hasPaymentConfig
            
            transactions[position] = item.copy(isValid = isValid)
            
            // Update validation display
            when {
                !hasAmountInput -> {
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Please input amount first"
                }
                hasAmountInput && !hasPaymentConfig -> {
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Select a payor and input payment"
                }
                else -> {
                    binding.ivValidationError.isVisible = false
                    binding.tvValidationMessage.isVisible = false
                }
            }
        }
    }
}
