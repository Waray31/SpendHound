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
import android.widget.ProgressBar
import android.widget.PopupWindow
import android.animation.ValueAnimator
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
import android.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

data class MemberPaymentState(
    val userId: String,
    val userName: String,
    val amountPaid: Double = 0.0,
    val isIncludedInSplit: Boolean = true,
    var customSplitAmount: Double? = null,
    val coveredByUserId: String? = null,
    val coveringUserIds: MutableList<String> = mutableListOf()
)

class PaymentConfigBottomSheet : BottomSheetDialogFragment() {
    
    private lateinit var tvItemTitle: TextView
    private lateinit var tvItemAmount: TextView
    private lateinit var tvIndividualShare: TextView
    private lateinit var tvRemainingAmount: TextView
    private lateinit var btnModeEqually: TextView
    private lateinit var btnModeExact: TextView
    private lateinit var layoutToggleContainer: View
    private lateinit var rvMembers: RecyclerView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnClose: ImageButton
    
    private lateinit var membersAdapter: MembersAdapter
    private var itemAmount: Double = 0.0
    private var splitMode: Int = 0 // 0: Equally, 1: Exact
    private var groupMembers: List<User> = emptyList()
    private var memberStates: MutableList<MemberPaymentState> = mutableListOf()
    
    private var onConfirmListener: ((List<PayerContribution>, List<String>) -> Unit)? = null
    private var onConfirmWithCoversListener: ((List<PayerContribution>, List<String>, Map<String, String>, Int, Map<String, Double>) -> Unit)? = null
    
    companion object {
        fun newInstance(
            itemTitle: String,
            itemAmount: Double,
            groupMembers: List<User>,
            currentPayers: List<PayerContribution>,
            currentParticipants: List<String>,
            initialCoveredByMap: Map<String, String> = emptyMap(),
            splitMode: Int = 0,
            customSplitMap: Map<String, Double> = emptyMap()
        ): PaymentConfigBottomSheet {
            return PaymentConfigBottomSheet().apply {
                this.itemAmount = itemAmount
                this.groupMembers = groupMembers
                this.splitMode = splitMode
                
                // Initialize member states
                this.memberStates = groupMembers.map { member ->
                    val userIdStr = member.id.toString()
                    val existingPayer = currentPayers.find { it.payerId == userIdStr }
                    val isIncluded = currentParticipants.contains(userIdStr) || currentParticipants.isEmpty()
                    val coveredBy = initialCoveredByMap[userIdStr]
                    val customAmount = customSplitMap[userIdStr]
                    
                    MemberPaymentState(
                        userId = userIdStr,
                        userName = member.username ?: "Unknown",
                        amountPaid = existingPayer?.amount ?: 0.0,
                        isIncludedInSplit = isIncluded,
                        customSplitAmount = customAmount,
                        coveredByUserId = coveredBy
                    )
                }.toMutableList()
                
                // Build coveringUserIds lists
                this.memberStates.forEach { state ->
                    state.coveredByUserId?.let { covererId ->
                        this.memberStates.find { it.userId == covererId }?.coveringUserIds?.add(state.userId)
                    }
                }
                
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
            handleConfirm()
        }
    }

    private fun handleConfirm() {
        val payers = memberStates.filter { it.amountPaid > 0 }.map {
            PayerContribution(it.userId, it.userName, it.amountPaid)
        }
        val participants = memberStates.filter { it.isIncludedInSplit }.map { it.userId }
        
        val coveredByMap = mutableMapOf<String, String>()
        memberStates.forEach { state ->
            if (state.isIncludedInSplit && state.coveredByUserId != null) {
                coveredByMap[state.userId] = state.coveredByUserId
            }
        }

        val customSplitMap = mutableMapOf<String, Double>()
        if (splitMode == 1) {
            memberStates.forEach { state ->
                if (state.isIncludedInSplit) {
                    customSplitMap[state.userId] = state.customSplitAmount ?: 0.0
                }
            }
        }
        
        onConfirmWithCoversListener?.invoke(payers, participants, coveredByMap, splitMode, customSplitMap)
        onConfirmListener?.invoke(payers, participants)
        dismiss()
    }
    
    private fun initViews(view: View) {
        tvItemTitle = view.findViewById(R.id.tvItemTitle)
        tvItemAmount = view.findViewById(R.id.tvItemAmount)
        tvIndividualShare = view.findViewById(R.id.tvIndividualShare)
        tvRemainingAmount = view.findViewById(R.id.tvRemainingAmount)
        rvMembers = view.findViewById(R.id.rvMembers)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        btnClose = view.findViewById(R.id.btnClose)
        
        btnModeEqually = view.findViewById(R.id.btnModeEqually)
        btnModeExact = view.findViewById(R.id.btnModeExact)
        layoutToggleContainer = view.findViewById(R.id.layoutToggleContainer)

        btnModeEqually.setOnClickListener {
            if (splitMode != 0) {
                splitMode = 0
                updateSplitModeUI()
                validateAndUpdate()
            }
        }
        
        btnModeExact.setOnClickListener {
            if (splitMode != 1) {
                // Initialize with current equal shares when switching TO Exact
                val includedCount = memberStates.count { it.isIncludedInSplit }
                val baseShare = if (includedCount > 0) itemAmount / includedCount else 0.0
                
                memberStates.forEachIndexed { index, state ->
                    if (state.isIncludedInSplit && (state.customSplitAmount == null || state.customSplitAmount == 0.0)) {
                        var share = baseShare
                        if (state.coveredByUserId != null) {
                            share = 0.0
                        } else if (state.coveringUserIds.isNotEmpty()) {
                            share = baseShare * (1 + state.coveringUserIds.size)
                        }
                        memberStates[index].customSplitAmount = share
                    }
                }
                
                splitMode = 1
                updateSplitModeUI()
                validateAndUpdate()
            }
        }
    }


    private fun updateSplitModeUI() {
        if (splitMode == 0) {
            btnModeEqually.setBackgroundResource(R.drawable.toggle_selected_background)
            btnModeEqually.setTextColor(0xFFFFFFFF.toInt())
            btnModeExact.setBackgroundResource(android.R.color.transparent)
            btnModeExact.setTextColor(0xFF6C757D.toInt()) // grey
            
            val includedCount = memberStates.count { it.isIncludedInSplit }
            val baseShare = if (includedCount > 0) itemAmount / includedCount else 0.0
            tvIndividualShare.text = "Share: ₱${String.format("%.2f", baseShare)} each"
            tvIndividualShare.visibility = View.VISIBLE
        } else {
            btnModeExact.setBackgroundResource(R.drawable.toggle_selected_background)
            btnModeExact.setTextColor(0xFFFFFFFF.toInt())
            btnModeEqually.setBackgroundResource(android.R.color.transparent)
            btnModeEqually.setTextColor(0xFF6C757D.toInt()) // grey
            
            tvIndividualShare.visibility = View.GONE
        }
        membersAdapter.setSplitMode(splitMode)
    }
    
    private fun setupAdapter() {
        membersAdapter = MembersAdapter(memberStates, itemAmount, splitMode) { validateAndUpdate() }
        rvMembers.layoutManager = LinearLayoutManager(context)
        rvMembers.adapter = membersAdapter
    }
    
    private fun updateUI() {
        arguments?.let { args ->
            tvItemTitle.text = args.getString("itemTitle", "Item")
            tvItemAmount.text = "₱${String.format("%.2f", itemAmount)}"
        }
        updateSplitModeUI()
        validateAndUpdate()
    }
    
    private fun validateAndUpdate() {
        if (!isAdded) return

        val totalPaid = memberStates.sumOf { it.amountPaid }
        val remaining = itemAmount - totalPaid
        
        // Update individual share (base share for anyone included)
        if (splitMode == 0) {
            val includedCount = memberStates.count { it.isIncludedInSplit }
            val baseShare = if (includedCount > 0) itemAmount / includedCount else 0.0
            tvIndividualShare.text = "Share: ₱${String.format("%.2f", baseShare)} each"
        }
        
        // In Exact mode, "Remaining" represents the Split balance
        if (splitMode == 1) {
            val totalSplit = memberStates.sumOf { if (it.isIncludedInSplit) it.customSplitAmount ?: 0.0 else 0.0 }
            val remainingSplit = itemAmount - totalSplit
            tvRemainingAmount.text = "₱${String.format("%.2f", remainingSplit)}"
            val splitColor = if (Math.abs(remainingSplit) < 0.01) resources.getColor(R.color.green, null) else resources.getColor(R.color.red, null)
            tvRemainingAmount.setTextColor(splitColor)
        } else {
            tvRemainingAmount.text = "₱${String.format("%.2f", remaining)}"
            val paidColor = if (Math.abs(remaining) < 0.01) resources.getColor(R.color.green, null) else resources.getColor(R.color.red, null)
            tvRemainingAmount.setTextColor(paidColor)
        }
        
        // Enable confirm only when total paid equals the item amount
        // AND in Exact mode, total split also equals the item amount
        var isBalanced = Math.abs(remaining) < 0.01 && memberStates.any { it.amountPaid > 0 }
        if (splitMode == 1) {
            val totalSplit = memberStates.sumOf { if (it.isIncludedInSplit) it.customSplitAmount ?: 0.0 else 0.0 }
            isBalanced = isBalanced && Math.abs(itemAmount - totalSplit) < 0.01
        }
        
        btnConfirm.isEnabled = isBalanced
        btnConfirm.setBackgroundResource(if (isBalanced) R.drawable.rounded_button else R.drawable.greyed_out_rounded_button)
        
        membersAdapter.notifyDataSetChanged()
    }
    
    private fun setupKeyboardDismissal(view: View) {
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

    fun setOnConfirmWithCoversListener(listener: (List<PayerContribution>, List<String>, Map<String, String>, Int, Map<String, Double>) -> Unit) {
        this.onConfirmWithCoversListener = listener
    }
    
    fun setOnConfirmListener(listener: (List<PayerContribution>, List<String>) -> Unit) {
        this.onConfirmListener = listener
    }
}

class MembersAdapter(
    private val memberStates: MutableList<MemberPaymentState>,
    private val totalItemAmount: Double,
    private var splitMode: Int,
    private val onStateChanged: () -> Unit
) : RecyclerView.Adapter<MembersAdapter.ViewHolder>() {
    
    fun setSplitMode(mode: Int) {
        this.splitMode = mode
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
        val etMemberAmount: TextInputEditText = view.findViewById(R.id.etMemberAmount)
        val layoutMemberAmount: View = view.findViewById(R.id.layoutMemberAmount)
        val tvCoveredStatus: TextView = view.findViewById(R.id.tvCoveredStatus)
        val etCustomSplit: TextInputEditText = view.findViewById(R.id.etCustomSplit)
        val layoutCustomSplit: View = view.findViewById(R.id.layoutCustomSplit)
        val ivWarningIcon: ImageView = view.findViewById(R.id.ivWarningIcon)
        val layoutSplitToggle: View? = view.findViewById(R.id.layoutSplitToggle)
        val toggleThumb: View = view.findViewById(R.id.toggleThumb)
        val tvToggleLabel: TextView = view.findViewById(R.id.tvToggleLabel)
        val progressBarLongPress: ProgressBar = view.findViewById(R.id.progressBarLongPress)
        val tagCovering: TextView = view.findViewById(R.id.tagCovering)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member_combined, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val memberState = memberStates[position]
        
        // Remove existing listeners to avoid triggering during setText
        holder.etMemberAmount.tag?.let { holder.etMemberAmount.removeTextChangedListener(it as TextWatcher) }
        holder.etCustomSplit.tag?.let { holder.etCustomSplit.removeTextChangedListener(it as TextWatcher) }

        holder.tvMemberName.text = memberState.userName
        
        // Paid Amount
        val isCovered = memberState.coveredByUserId != null
        if (isCovered) {
            holder.layoutMemberAmount.visibility = View.GONE
            holder.tvCoveredStatus.visibility = View.VISIBLE
            val coverer = memberStates.find { it.userId == memberState.coveredByUserId }
            holder.tvCoveredStatus.text = "covered by ${coverer?.userName ?: "Someone"}"
        } else {
            holder.layoutMemberAmount.visibility = View.VISIBLE
            holder.tvCoveredStatus.visibility = View.GONE
            val paidText = if (memberState.amountPaid > 0) String.format("%.2f", memberState.amountPaid) else ""
            if (holder.etMemberAmount.text.toString() != paidText) {
                holder.etMemberAmount.setText(paidText)
            }
        }
        
        // Custom Split Amount
        val customSplitText = if (memberState.customSplitAmount != null && memberState.customSplitAmount!! > 0) String.format("%.2f", memberState.customSplitAmount) else ""
        if (holder.etCustomSplit.text.toString() != customSplitText) {
            holder.etCustomSplit.setText(customSplitText)
        }

        // Visibility based on split mode
        if (splitMode == 1) {
            // Exact Mode: Show input, hide all equal mode UI
            holder.layoutCustomSplit.visibility = View.VISIBLE
            holder.tagCovering.visibility = View.GONE
            holder.etCustomSplit.isEnabled = memberState.isIncludedInSplit
        } else {
            // Equal Mode: Hide input, show equal mode UI
            holder.layoutCustomSplit.visibility = View.GONE
            updateCoverUI(holder, memberState)
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

        
        // Paid Amount change listener
        val paidWatcher = holder.etMemberAmount.addTextChangedListener { text ->
            val amount = text.toString().toDoubleOrNull() ?: 0.0
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                if (memberStates[currentPos].amountPaid != amount) {
                    memberStates[currentPos] = memberStates[currentPos].copy(amountPaid = amount)
                    onStateChanged()
                }
            }
        }
        holder.etMemberAmount.tag = paidWatcher

        // Custom Split change listener
        val customSplitWatcher = holder.etCustomSplit.addTextChangedListener { text ->
            val amount = text.toString().toDoubleOrNull()
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                if (memberStates[currentPos].customSplitAmount != amount) {
                    memberStates[currentPos].customSplitAmount = amount
                    onStateChanged()
                }
            }
        }
        holder.etCustomSplit.tag = customSplitWatcher
        
        // Toggle click listener
        holder.layoutSplitToggle?.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                val currentState = memberStates[currentPos]
                val newState = !currentState.isIncludedInSplit
                
                var updatedState = currentState.copy(
                    isIncludedInSplit = newState,
                    coveringUserIds = currentState.coveringUserIds.toMutableList()
                )
                var stateChangedSignificantly = false
                
                // If toggled OUT
                if (!newState) {
                    updatedState = updatedState.copy(customSplitAmount = 0.0)
                    
                    // Case 1: They were covered by someone -> Uncover them
                    if (currentState.coveredByUserId != null) {
                        memberStates.find { it.userId == currentState.coveredByUserId }
                            ?.coveringUserIds?.remove(currentState.userId)
                        updatedState = updatedState.copy(coveredByUserId = null)
                        stateChangedSignificantly = true
                    }
                    
                    // Case 2: They were covering others -> Stop covering them
                    if (currentState.coveringUserIds.isNotEmpty()) {
                        currentState.coveringUserIds.forEach { childId ->
                            val childIdx = memberStates.indexOfFirst { it.userId == childId }
                            if (childIdx != -1) {
                                memberStates[childIdx] = memberStates[childIdx].copy(coveredByUserId = null)
                            }
                        }
                        updatedState.coveringUserIds.clear()
                        stateChangedSignificantly = true
                    }
                }
                
                memberStates[currentPos] = updatedState
                updateToggleAppearance(holder, newState)
                
                // Refresh list if coverage changed (to update other members' UI)
                if (stateChangedSignificantly) {
                    notifyDataSetChanged()
                } else {
                    val showWarn = updatedState.amountPaid > 0 && !newState
                    holder.ivWarningIcon.visibility = if (showWarn) View.VISIBLE else View.GONE
                }
                
                onStateChanged()
            }
        }

        // Long press for Cover Share (Only in Equal mode)
        if (splitMode == 0) {
            setupLongPress(holder, memberState)
        } else {
            holder.itemView.setOnTouchListener(null)
            holder.progressBarLongPress.visibility = View.GONE
        }
    }

    private fun updateCoverUI(holder: ViewHolder, state: MemberPaymentState) {
        holder.tagCovering.visibility = View.GONE

        if (!state.isIncludedInSplit) {
            return
        }

        if (state.coveredByUserId != null) {
            // tagCovered removed
        } else if (state.coveringUserIds.isNotEmpty()) {
            holder.tagCovering.visibility = View.VISIBLE
            holder.tagCovering.text = "covering ${state.coveringUserIds.size}"
        }
    }

    private fun setupLongPress(holder: ViewHolder, state: MemberPaymentState) {
        holder.itemView.setOnTouchListener { view, event ->
            if (!state.isIncludedInSplit) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holder.progressBarLongPress.visibility = View.VISIBLE
                    holder.progressBarLongPress.progress = 0
                    
                    val animator = ValueAnimator.ofInt(0, 100)
                    animator.duration = 800
                    animator.addUpdateListener { 
                        holder.progressBarLongPress.progress = it.animatedValue as Int
                    }
                    animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (holder.progressBarLongPress.progress == 100) {
                                showCoverMenu(holder, state)
                                holder.progressBarLongPress.visibility = View.INVISIBLE
                            }
                        }
                    })
                    animator.start()
                    holder.itemView.tag = animator
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (holder.itemView.tag as? ValueAnimator)?.cancel()
                    holder.progressBarLongPress.visibility = View.INVISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun showCoverMenu(holder: ViewHolder, state: MemberPaymentState) {
        val context = holder.itemView.context
        val view = LayoutInflater.from(context).inflate(R.layout.layout_cover_menu, null)
        val popupWindow = PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        
        val rvCoverMembers = view.findViewById<RecyclerView>(R.id.rvCoverMembers)
        val tvTitle = view.findViewById<TextView>(R.id.tvMenuTitle)
        
        val isCurrentlyCovered = state.coveredByUserId != null
        tvTitle.text = if (isCurrentlyCovered) "Uncover ${state.userName}?" else "Who will cover ${state.userName}?"
        
        if (isCurrentlyCovered) {
            view.findViewById<TextView>(R.id.btnRemoveCover).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    updateCoverAssignment(state.userId, null)
                    popupWindow.dismiss()
                }
            }
            rvCoverMembers.visibility = View.GONE
        } else {
            view.findViewById<View>(R.id.btnRemoveCover).visibility = View.GONE
            val eligibleMembers = memberStates.filter { it.userId != state.userId && it.isIncludedInSplit }
            rvCoverMembers.layoutManager = LinearLayoutManager(context)
            rvCoverMembers.adapter = CoverMembersAdapter(eligibleMembers, state) { covererId ->
                updateCoverAssignment(state.userId, covererId)
                popupWindow.dismiss()
            }
        }
        
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = view.measuredHeight
        popupWindow.showAsDropDown(holder.tvMemberName, 0, -popupHeight - holder.tvMemberName.height)
    }

    private fun updateCoverAssignment(coveredId: String, covererId: String?) {
        val coveredMember = memberStates.find { it.userId == coveredId } ?: return
        val oldCovererId = coveredMember.coveredByUserId
        
        // Remove from old coverer's list
        if (oldCovererId != null) {
            memberStates.find { it.userId == oldCovererId }?.coveringUserIds?.remove(coveredId)
        }
        
        // Update covered member
        val updatedCovered = coveredMember.copy(coveredByUserId = covererId)
        val coveredIdx = memberStates.indexOf(coveredMember)
        memberStates[coveredIdx] = updatedCovered
        
        // Add to new coverer's list
        if (covererId != null) {
            memberStates.find { it.userId == covererId }?.coveringUserIds?.add(coveredId)
        }
        
        notifyDataSetChanged()
        onStateChanged()
    }

    private fun setupItemKeyboardDismissal(holder: ViewHolder) {
        val actionListener = TextView.OnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
        holder.etMemberAmount.setOnEditorActionListener(actionListener)
        holder.etCustomSplit.setOnEditorActionListener(actionListener)
    }

    private fun resetToggleToDefault(holder: ViewHolder) {
        holder.toggleThumb.animate().translationX(0f).setDuration(0).start()
        holder.layoutSplitToggle?.setBackgroundResource(R.drawable.bg_toggle_track)
        holder.tvToggleLabel.text = "OUT"
    }

    private fun updateToggleAppearance(holder: ViewHolder, isIncluded: Boolean) {
        val track = holder.layoutSplitToggle ?: return
        val thumb = holder.toggleThumb
        val label = holder.tvToggleLabel
        
        val density = holder.itemView.resources.displayMetrics.density
        
        val layoutParams = thumb.layoutParams as FrameLayout.LayoutParams
        if (isIncluded) {
            track.setBackgroundResource(R.drawable.bg_toggle_track_on)
            label.text = "IN"
            layoutParams.gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            layoutParams.marginStart = 0
            layoutParams.marginEnd = (4 * density).toInt()
            
            label.layoutParams = (label.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setMargins((4 * density).toInt(), 0, 0, 0)
            }
            label.setTextColor(0xFFFFFFFF.toInt())
        } else {
            track.setBackgroundResource(R.drawable.bg_toggle_track)
            label.text = "OUT"
            layoutParams.gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            layoutParams.marginStart = (4 * density).toInt()
            layoutParams.marginEnd = 0

            label.layoutParams = (label.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                setMargins(0, 0, (4 * density).toInt(), 0)
            }
            label.setTextColor(0xFF6C757D.toInt())
        }
        thumb.layoutParams = layoutParams
        thumb.animate().cancel()
        thumb.translationX = 0f
    }

    override fun getItemCount() = memberStates.size

    inner class CoverMembersAdapter(
        private val members: List<MemberPaymentState>,
        private val targetMember: MemberPaymentState,
        private val onMemberSelected: (String) -> Unit
    ) : RecyclerView.Adapter<CoverMembersAdapter.CoverViewHolder>() {
        
        inner class CoverViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CoverViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_cover_menu_member, parent, false))

        override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
            val member = members[position]
            holder.tvMemberName.text = member.userName
            
            val isAlreadyCovering = member.coveringUserIds.isNotEmpty()
            holder.tvStatus.text = if (isAlreadyCovering) "Already covering ${member.coveringUserIds.size}" else "Available"
            holder.tvStatus.visibility = if (isAlreadyCovering) View.VISIBLE else View.GONE
            
            holder.itemView.setOnClickListener {
                onMemberSelected(member.userId)
            }
        }

        override fun getItemCount() = members.size
    }
}
