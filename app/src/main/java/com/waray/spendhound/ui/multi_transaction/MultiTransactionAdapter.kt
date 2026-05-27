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
    private val onTitleChanged: (Int, String) -> Unit,
    private val onCategoryChanged: (Int, String) -> Unit,
    private val onValidationChanged: () -> Unit,
    private val onRemoveItem: (Int) -> Unit,
    private val onPaymentConfigClick: (Int, MultiTransactionItem) -> Unit
) : RecyclerView.Adapter<MultiTransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<MultiTransactionItem>()
    private var groupMembers = listOf<User>()
    private var isGroupSelected = false
    
    // Track current EditText values, selected categories, and included members to preserve them across updates
    private val currentTitles = mutableMapOf<Int, String>()
    private val currentAmounts = mutableMapOf<Int, String>()
    private val currentCategories = mutableMapOf<Int, String>()
    private val currentIncludedMembers = mutableMapOf<Int, List<String>>()

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun safeNotify(action: () -> Unit) {
        val rv = recyclerView
        if (rv != null && (rv.isComputingLayout || rv.scrollState != RecyclerView.SCROLL_STATE_IDLE)) {
            rv.post { action() }
        } else {
            action()
        }
    }

    fun setTransactions(newTransactions: List<MultiTransactionItem>) {
        val oldSize = transactions.size
        val newSize = newTransactions.size
        
        if (newSize > oldSize) {
            // Items were added
            transactions.clear()
            transactions.addAll(newTransactions)
            
            safeNotify {
                notifyItemRangeInserted(oldSize, newSize - oldSize)
                if (oldSize == 1) notifyItemChanged(0)
            }
        } else if (newSize < oldSize) {
            // Items were removed - Clear tracking maps completely to be safe
            // and avoid jumbling when items shift positions.
            currentTitles.clear()
            currentAmounts.clear()
            currentCategories.clear()
            currentIncludedMembers.clear()

            transactions.clear()
            transactions.addAll(newTransactions)
            safeNotify {
                notifyDataSetChanged()
            }
        } else {
            // Same size, check for changes
            for (i in transactions.indices) {
                val oldItem = transactions[i]
                val newItem = newTransactions[i]
                
                if (oldItem != newItem) {
                    val titleChanged = oldItem.title != newItem.title
                    val amountChanged = oldItem.amount != newItem.amount
                    val categoryChanged = oldItem.category != newItem.category
                    val payersChanged = oldItem.payers != newItem.payers
                    val participantsChanged = oldItem.includedMembers != newItem.includedMembers
                    val validationChanged = oldItem.isValid != newItem.isValid

                    transactions[i] = newItem
                    
                    val typedAmount = currentAmounts[i]?.toDoubleOrNull() ?: 0.0
                    val isTypingAmount = Math.abs(typedAmount - newItem.amount) < 0.001
                    val isTypingTitle = currentTitles[i] == newItem.title
                    
                    // A change is "covered" if it's either not changed or matches what the user is currently typing.
                    // If covered, we don't want to re-bind the whole item because it causes focus loss/jumbling.
                    val titleCovered = !titleChanged || isTypingTitle
                    val amountCovered = !amountChanged || isTypingAmount
                    
                    if (titleCovered && amountCovered) {
                        // Title and Amount are safe. Check if we need to update other parts via payloads.
                        val payloads = mutableListOf<String>()
                        if (categoryChanged) payloads.add("CATEGORY")
                        if (payersChanged || participantsChanged) payloads.add("PAYMENT")
                        if (validationChanged) payloads.add("VALIDATION")
                        
                        if (payloads.isNotEmpty()) {
                            safeNotify { notifyItemChanged(i, payloads) }
                        }
                    } else {
                        // Changes are NOT covered by typing (e.g. external update or focus loss)
                        // Perform a full re-bind.
                        safeNotify { notifyItemChanged(i) }
                    }
                }
            }
        }
    }

    fun updateTransactionPayment(position: Int, updatedItem: MultiTransactionItem) {
        if (position in transactions.indices) {
            transactions[position] = updatedItem
            safeNotify { notifyItemChanged(position) }
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
        
        safeNotify { notifyDataSetChanged() }
    }

    fun clearCategorySelections() {
        // This will be called after notifyDataSetChanged to ensure views are bound
        // The setupCategoryChips method will handle clearing selections based on empty category
    }

    fun setMembers(members: List<User>) {
        this.groupMembers = members
        safeNotify { notifyDataSetChanged() }
    }

    fun setGroupSelected(isSelected: Boolean) {
        if (this.isGroupSelected != isSelected) {
            this.isGroupSelected = isSelected
            safeNotify { notifyDataSetChanged() }
        }
    }

    fun setMode(isMultiple: Boolean) {
        // Mode is now handled per-item via bottom sheets
        safeNotify { notifyDataSetChanged() }
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

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.applyPayloads(transactions[position], position, payloads)
        }
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
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    currentTitles.remove(currentPos)
                    currentAmounts.remove(currentPos)
                    currentCategories.remove(currentPos)
                    currentIncludedMembers.remove(currentPos)
                    onRemoveItem(currentPos)
                }
            }

            // Validation indicator and message
            updateValidation(item, position)

            // Payment summary
            updatePaymentSummary(item, position)

            // Payment chip click listener
            binding.layoutPaymentChip.setOnClickListener {
                val currentPos = adapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener
                
                // Get current amount from EditText instead of stored item amount
                val currentAmount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
                // Use tracked included members if available, otherwise use item's included members
                val includedMembers = currentIncludedMembers[currentPos] ?: item.includedMembers
                val updatedItem = item.copy(
                    amount = currentAmount,
                    includedMembers = includedMembers
                )
                onPaymentConfigClick(currentPos, updatedItem)
            }
            
            // Category chips
            setupCategoryChips(item, categoryToShow)

            // Amount listener
            binding.etAmount.removeTextChangedListener(amountWatcher)
            amountWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val currentPos = adapterPosition
                    if (currentPos == RecyclerView.NO_POSITION) return

                    val amountText = s.toString()
                    val newAmount = amountText.toDoubleOrNull() ?: 0.0
                    
                    // Track the current amount text
                    currentAmounts[currentPos] = amountText
                    
                    transactions[currentPos] = item.copy(amount = newAmount)
                    
                    // Enable/disable payment section based on amount and group selection
                    val hasAmount = newAmount > 0
                    val canConfigurePayment = hasAmount && isGroupSelected
                    binding.layoutPaymentChip.isClickable = canConfigurePayment
                    binding.layoutPaymentChip.isFocusable = canConfigurePayment
                    binding.layoutPaymentChip.alpha = if (canConfigurePayment) 1.0f else 0.5f
                    
                    updateValidation(transactions[currentPos], currentPos)
                    onAmountChanged(currentPos, newAmount)
                    onValidationChanged()
                }
            }
            binding.etAmount.addTextChangedListener(amountWatcher)
            
            // Initial payment section state based on current amount and group selection
            val hasAmount = (currentAmounts[position]?.toDoubleOrNull() ?: item.amount) > 0
            val canConfigurePayment = hasAmount && isGroupSelected
            binding.layoutPaymentChip.isClickable = canConfigurePayment
            binding.layoutPaymentChip.isFocusable = canConfigurePayment
            binding.layoutPaymentChip.alpha = if (canConfigurePayment) 1.0f else 0.5f

            // Title listener
            binding.etTitle.removeTextChangedListener(titleWatcher)
            titleWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val currentPos = adapterPosition
                    if (currentPos == RecyclerView.NO_POSITION) return

                    val titleText = s.toString()
                    
                    // Track the current title text
                    currentTitles[currentPos] = titleText
                    
                    transactions[currentPos] = item.copy(title = titleText)
                    onTitleChanged(currentPos, titleText)
                }
            }
            binding.etTitle.addTextChangedListener(titleWatcher)
        }

        fun applyPayloads(item: MultiTransactionItem, position: Int, payloads: List<Any>) {
            val flattenedPayloads = payloads.flatMap { if (it is List<*>) it else listOf(it) }
            
            if (flattenedPayloads.contains("CATEGORY")) {
                val categoryToShow = currentCategories[position] ?: item.category
                binding.tvItemNumber.text = "Item ${position + 1}${if (categoryToShow.isNotEmpty()) " — ${categoryToShow.lowercase()}" else ""}"
                setupCategoryChips(item, categoryToShow)
            }
            
            if (flattenedPayloads.contains("PAYMENT")) {
                updatePaymentSummary(item, position)
            }
            
            if (flattenedPayloads.contains("VALIDATION")) {
                updateValidation(item, position)
            }
        }

        private fun updatePaymentSummary(item: MultiTransactionItem, position: Int) {
            binding.tvPaymentSummary.text = item.getPaymentSummary()
            
            // Use tracked included members if available, otherwise use item's included members
            val includedMembers = currentIncludedMembers[position] ?: item.includedMembers
            
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

        private fun setupCategoryChips(item: MultiTransactionItem, currentCategory: String) {
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
                val currentPos = adapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return

                chipMap.forEach { (view, cat) ->
                    view.setBackgroundResource(
                        if (view == selected) R.drawable.bg_dark_chip_selected
                        else R.drawable.bg_profile_card
                    )
                }
                
                // Track the selected category
                currentCategories[currentPos] = category
                
                // Update item number display
                binding.tvItemNumber.text = "Item ${currentPos + 1} — ${category.lowercase()}"
                
                // Update transaction data
                transactions[currentPos] = item.copy(category = category)
                
                // Notify ViewModel of category change
                onCategoryChanged(currentPos, category)
                updateValidation(transactions[currentPos], currentPos)
            }
            
            chipMap.forEach { (view, category) ->
                view.setOnClickListener {
                    updateChipSelection(view, category)
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

        private fun updateValidation(item: MultiTransactionItem, position: Int) {
            val currentAmount = currentAmounts[position]?.toDoubleOrNull() ?: item.amount
            val hasAmountInput = currentAmount > 0
            val isPayorsConfigured = item.payers.isNotEmpty()
            val isPaymentComplete = item.isPaymentComplete()
            
            // Update validation display
            when {
                !isGroupSelected -> {
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Please input payer group first"
                }
                !hasAmountInput -> {
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Please input amount first"
                }
                !isPayorsConfigured -> {
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Select who paid for this item"
                }
                !isPaymentComplete -> {
                    binding.ivValidationError.isVisible = true
                    binding.tvValidationMessage.isVisible = true
                    binding.tvValidationMessage.text = "Payment total must equal item amount"
                }
                else -> {
                    binding.ivValidationError.isVisible = false
                    binding.tvValidationMessage.isVisible = false
                }
            }
        }
    }
}
