package com.waray.spendhound

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import android.text.TextWatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.FrameLayout
import com.google.android.material.button.MaterialButton

data class MemberPaymentState(
    val userId: String,
    val userName: String,
    val amountPaid: Double = 0.0,
    val isIncludedInSplit: Boolean = true
)

class PaymentConfigBottomSheet : BottomSheetDialogFragment() {
    
    private lateinit var tvItemTitle: TextView
    private lateinit var tvItemAmount: TextView
    private lateinit var tvRemainingAmount: TextView
    private lateinit var rvMembers: RecyclerView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnClose: ImageButton
    
    private lateinit var membersAdapter: MembersAdapter
    
    private var itemAmount: Double = 0.0
    private var groupMembers: List<User> = emptyList()
    private var memberStates: MutableList<MemberPaymentState> = mutableListOf()
    
    private var onConfirmListener: ((List<PayerContribution>, List<String>) -> Unit)? = null
    
    companion object {
        fun newInstance(
            itemTitle: String,
            itemAmount: Double,
            groupMembers: List<User>,
            currentPayers: List<PayerContribution>,
            currentParticipants: List<String>
        ): PaymentConfigBottomSheet {
            return PaymentConfigBottomSheet().apply {
                this.itemAmount = itemAmount
                this.groupMembers = groupMembers
                
                // Initialize member states
                this.memberStates = groupMembers.map { member ->
                    val existingPayer = currentPayers.find { it.payerId == member.id.toString() }
                    val isIncluded = currentParticipants.contains(member.id.toString()) || currentParticipants.isEmpty()
                    
                    MemberPaymentState(
                        userId = member.id.toString(),
                        userName = member.username ?: "Unknown",
                        amountPaid = existingPayer?.amount ?: 0.0,
                        isIncludedInSplit = isIncluded
                    )
                }.toMutableList()
                
                arguments = Bundle().apply {
                    putString("itemTitle", itemTitle)
                    putDouble("itemAmount", itemAmount)
                }
            }
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_payment_config, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupAdapter()
        updateUI()
        setupKeyboardDismissal(view)
        
        btnClose.setOnClickListener { dismiss() }
        btnConfirm.setOnClickListener { 
            val payers = memberStates.filter { it.amountPaid > 0 }.map {
                PayerContribution(it.userId, it.userName, it.amountPaid)
            }
            val participants = memberStates.filter { it.isIncludedInSplit }.map { it.userId }
            
            onConfirmListener?.invoke(payers, participants)
            dismiss()
        }
    }
    
    private fun initViews(view: View) {
        tvItemTitle = view.findViewById(R.id.tvItemTitle)
        tvItemAmount = view.findViewById(R.id.tvItemAmount)
        tvRemainingAmount = view.findViewById(R.id.tvRemainingAmount)
        rvMembers = view.findViewById(R.id.rvMembers)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        btnClose = view.findViewById(R.id.btnClose)
    }
    
    private fun setupAdapter() {
        membersAdapter = MembersAdapter(memberStates) { validateAndUpdate() }
        rvMembers.layoutManager = LinearLayoutManager(context)
        rvMembers.adapter = membersAdapter
    }
    
    private fun updateUI() {
        arguments?.let { args ->
            tvItemTitle.text = args.getString("itemTitle", "Item")
            tvItemAmount.text = "₱${String.format("%.2f", itemAmount)}"
        }
        validateAndUpdate()
    }
    
    private fun validateAndUpdate() {
        if (!isAdded) return

        val totalPaid = memberStates.sumOf { it.amountPaid }
        val remaining = itemAmount - totalPaid
        
        tvRemainingAmount.text = "₱${String.format("%.2f", remaining)}"
        
        // Color coding: green when balanced, red when not
        val color = when {
            Math.abs(remaining) < 0.01 -> resources.getColor(android.R.color.holo_green_dark, null)
            else -> resources.getColor(android.R.color.holo_red_dark, null)
        }
        tvRemainingAmount.setTextColor(color)
        
        // Enable confirm when amounts are valid (don't require exact balance)
        // Allow confirmation if at least one person has paid something
        btnConfirm.isEnabled = memberStates.any { it.amountPaid > 0 }
        
        // Don't call notifyDataSetChanged() here - let individual items update themselves
    }
    
    private fun setupKeyboardDismissal(view: View) {
        // Set up touch listener to dismiss keyboard when touching outside EditText
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val focusedView = dialog?.currentFocus
                if (focusedView is EditText) {
                    val outRect = Rect()
                    focusedView.getGlobalVisibleRect(outRect)
                    if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        focusedView.clearFocus()
                        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
                    }
                }
            }
            false
        }
    }
    
    fun setOnConfirmListener(listener: (List<PayerContribution>, List<String>) -> Unit) {
        onConfirmListener = listener
    }
}

class MembersAdapter(
    private val memberStates: MutableList<MemberPaymentState>,
    private val onStateChanged: () -> Unit
) : RecyclerView.Adapter<MembersAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
        val etMemberAmount: TextInputEditText = view.findViewById(R.id.etMemberAmount)
        val ivWarningIcon: ImageView = view.findViewById(R.id.ivWarningIcon)
        val layoutSplitToggle: FrameLayout = view.findViewById(R.id.layoutSplitToggle)
        val toggleThumb: View = view.findViewById(R.id.toggleThumb)
        val tvToggleLabel: TextView = view.findViewById(R.id.tvToggleLabel)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member_combined, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val memberState = memberStates[position]
        
        // Remove existing listener to avoid triggering it during setText
        val oldWatcher = holder.etMemberAmount.tag as? TextWatcher
        if (oldWatcher != null) {
            holder.etMemberAmount.removeTextChangedListener(oldWatcher)
        }

        holder.tvMemberName.text = memberState.userName
        val amountText = if (memberState.amountPaid > 0) String.format("%.2f", memberState.amountPaid) else ""
        if (holder.etMemberAmount.text.toString() != amountText) {
            holder.etMemberAmount.setText(amountText)
        }
        
        // Show warning if paid but excluded from split
        val showWarning = memberState.amountPaid > 0 && !memberState.isIncludedInSplit
        holder.ivWarningIcon.visibility = if (showWarning) View.VISIBLE else View.GONE
        
        // Reset toggle to default state first
        resetToggleToDefault(holder)
        
        // Update toggle appearance
        updateToggleAppearance(holder, memberState.isIncludedInSplit)
        
        // Setup keyboard dismissal for this item
        setupItemKeyboardDismissal(holder)
        
        // Amount change listener
        val textWatcher = holder.etMemberAmount.addTextChangedListener { text ->
            val amount = text.toString().toDoubleOrNull() ?: 0.0
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                if (memberStates[currentPos].amountPaid != amount) {
                    memberStates[currentPos] = memberStates[currentPos].copy(amountPaid = amount)
                    onStateChanged()
                }
            }
        }
        holder.etMemberAmount.tag = textWatcher
        
        // Handle done action on keyboard
        holder.etMemberAmount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                holder.etMemberAmount.clearFocus()
                val imm = holder.itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(holder.etMemberAmount.windowToken, 0)
                true
            } else {
                false
            }
        }
        
        // Toggle click listener
        holder.layoutSplitToggle.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                try {
                    val currentState = memberStates[currentPos]
                    val newState = !currentState.isIncludedInSplit
                    memberStates[currentPos] = currentState.copy(isIncludedInSplit = newState)
                    
                    // Update only this item's appearance
                    updateToggleAppearance(holder, newState)
                    
                    // Update warning icon for this item only
                    val showWarning = memberStates[currentPos].amountPaid > 0 && !newState
                    holder.ivWarningIcon.visibility = if (showWarning) View.VISIBLE else View.GONE
                    
                    // Notify parent about state change (for totals calculation)
                    onStateChanged()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun setupItemKeyboardDismissal(holder: ViewHolder) {
        holder.itemView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val focusedView = holder.etMemberAmount
                if (focusedView.isFocused) {
                    val outRect = Rect()
                    focusedView.getGlobalVisibleRect(outRect)
                    if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        focusedView.clearFocus()
                        val imm = holder.itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
                    }
                }
            }
            false
        }
    }
    
    private fun resetToggleToDefault(holder: ViewHolder) {
        // Reset thumb position
        holder.toggleThumb.clearAnimation()
        holder.toggleThumb.translationX = 0f
        
        // Reset label position to center
        holder.tvToggleLabel.clearAnimation()
        val labelParams = holder.tvToggleLabel.layoutParams as FrameLayout.LayoutParams
        labelParams.gravity = android.view.Gravity.CENTER
        labelParams.leftMargin = 0
        labelParams.rightMargin = 0
        holder.tvToggleLabel.layoutParams = labelParams
    }
    
    private fun updateToggleAppearance(holder: ViewHolder, isIncluded: Boolean) {
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density
        val thumbWidth = (28 * density).toInt()
        val toggleWidth = (60 * density).toInt()
        
        // Reset any previous translations
        holder.tvToggleLabel.translationX = 0f
        
        val labelParams = holder.tvToggleLabel.layoutParams as FrameLayout.LayoutParams
        
        if (isIncluded) {
            // "In" state - orange background, thumb on right, center "IN" text in left space
            holder.layoutSplitToggle.setBackgroundResource(R.drawable.bg_toggle_track_on)
            holder.tvToggleLabel.text = "IN"
            holder.tvToggleLabel.setTextColor(context.getColor(R.color.whitest))
            
            // Position text in the left half, centered
            labelParams.gravity = android.view.Gravity.CENTER_VERTICAL
            labelParams.leftMargin = (14 * density).toInt() // More margin to center in left space
            labelParams.rightMargin = 0
            holder.tvToggleLabel.layoutParams = labelParams

            // Move thumb to right
            holder.toggleThumb.animate()
                .translationX(thumbWidth.toFloat())
                .setDuration(200)
                .start()
        } else {
            // "Out" state - grey background, thumb on left, center "OUT" text in right space
            holder.layoutSplitToggle.setBackgroundResource(R.drawable.bg_toggle_track)
            holder.tvToggleLabel.text = "OUT"
            holder.tvToggleLabel.setTextColor(context.getColor(R.color.grey))
            
            // Position text in the right half, centered
            labelParams.gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            labelParams.rightMargin = (8 * density).toInt() // Center in right space
            labelParams.leftMargin = 0
            holder.tvToggleLabel.layoutParams = labelParams

            // Move thumb to left
            holder.toggleThumb.animate()
                .translationX(0f)
                .setDuration(200)
                .start()
        }
    }
    
    override fun getItemCount() = memberStates.size
}