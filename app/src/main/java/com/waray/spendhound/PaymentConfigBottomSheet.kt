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
import com.google.android.material.button.MaterialButton

data class MemberPaymentState(
    val userId: String,
    val userName: String,
    val amountPaid: Double = 0.0,
    val isIncludedInSplit: Boolean = true,
    val coveredByUserId: String? = null,
    val coveringUserIds: MutableList<String> = mutableListOf()
)

class PaymentConfigBottomSheet : BottomSheetDialogFragment() {
    
    private lateinit var tvItemTitle: TextView
    private lateinit var tvItemAmount: TextView
    private lateinit var tvIndividualShare: TextView
    private lateinit var tvRemainingAmount: TextView
    private lateinit var rvMembers: RecyclerView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnClose: ImageButton
    
    private lateinit var membersAdapter: MembersAdapter
    private var itemAmount: Double = 0.0
    private var groupMembers: List<User> = emptyList()
    private var memberStates: MutableList<MemberPaymentState> = mutableListOf()
    
    private var onConfirmListener: ((List<PayerContribution>, List<String>) -> Unit)? = null
    private var onConfirmWithCoversListener: ((List<PayerContribution>, List<String>, Map<String, String>) -> Unit)? = null
    
    companion object {
        fun newInstance(
            itemTitle: String,
            itemAmount: Double,
            groupMembers: List<User>,
            currentPayers: List<PayerContribution>,
            currentParticipants: List<String>,
            initialCoveredByMap: Map<String, String> = emptyMap()
        ): PaymentConfigBottomSheet {
            return PaymentConfigBottomSheet().apply {
                this.itemAmount = itemAmount
                this.groupMembers = groupMembers
                
                // Initialize member states
                this.memberStates = groupMembers.map { member ->
                    val userIdStr = member.id.toString()
                    val existingPayer = currentPayers.find { it.payerId == userIdStr }
                    val isIncluded = currentParticipants.contains(userIdStr) || currentParticipants.isEmpty()
                    val coveredBy = initialCoveredByMap[userIdStr]
                    
                    MemberPaymentState(
                        userId = userIdStr,
                        userName = member.username ?: "Unknown",
                        amountPaid = existingPayer?.amount ?: 0.0,
                        isIncludedInSplit = isIncluded,
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
            val payers = memberStates.filter { it.amountPaid > 0 }.map {
                PayerContribution(it.userId, it.userName, it.amountPaid)
            }
            val participants = memberStates.filter { it.isIncludedInSplit }.map { it.userId }
            val coveredByMap = memberStates
                .filter { it.coveredByUserId != null }
                .associate { it.userId to it.coveredByUserId!! }
            
            onConfirmWithCoversListener?.invoke(payers, participants, coveredByMap)
            onConfirmListener?.invoke(payers, participants)
            dismiss()
        }
    }
    
    private fun initViews(view: View) {
        tvItemTitle = view.findViewById(R.id.tvItemTitle)
        tvItemAmount = view.findViewById(R.id.tvItemAmount)
        tvIndividualShare = view.findViewById(R.id.tvIndividualShare)
        tvRemainingAmount = view.findViewById(R.id.tvRemainingAmount)
        rvMembers = view.findViewById(R.id.rvMembers)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        btnClose = view.findViewById(R.id.btnClose)
    }
    
    private fun setupAdapter() {
        membersAdapter = MembersAdapter(memberStates, itemAmount) { validateAndUpdate() }
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
        
        // Update individual share (base share for anyone included)
        val includedCount = memberStates.count { it.isIncludedInSplit }
        val baseShare = if (includedCount > 0) itemAmount / includedCount else 0.0
        tvIndividualShare.text = "₱${String.format("%.2f", baseShare)} each"
        
        tvRemainingAmount.text = "₱${String.format("%.2f", remaining)}"
        
        // Color coding: green when balanced, red when not
        val color = when {
            Math.abs(remaining) < 0.01 -> resources.getColor(android.R.color.holo_green_dark, null)
            else -> resources.getColor(android.R.color.holo_red_dark, null)
        }
        tvRemainingAmount.setTextColor(color)
        
        // Enable confirm only when total paid equals the item amount
        val isBalanced = Math.abs(remaining) < 0.01 && memberStates.any { it.amountPaid > 0 }
        btnConfirm.isEnabled = isBalanced
        
        // Update background based on enabled state
        if (isBalanced) {
            btnConfirm.setBackgroundResource(R.drawable.rounded_button)
        } else {
            btnConfirm.setBackgroundResource(R.drawable.greyed_out_rounded_button)
        }
        
        // Notify adapter to update badges and tags
        membersAdapter.notifyDataSetChanged()
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

    fun setOnConfirmWithCoversListener(listener: (List<PayerContribution>, List<String>, Map<String, String>) -> Unit) {
        this.onConfirmWithCoversListener = listener
    }
    
    fun setOnConfirmListener(listener: (List<PayerContribution>, List<String>) -> Unit) {
        this.onConfirmListener = listener
    }
}

class MembersAdapter(
    private val memberStates: MutableList<MemberPaymentState>,
    private val totalItemAmount: Double,
    private val onStateChanged: () -> Unit
) : RecyclerView.Adapter<MembersAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
        val etMemberAmount: TextInputEditText = view.findViewById(R.id.etMemberAmount)
        val ivWarningIcon: ImageView = view.findViewById(R.id.ivWarningIcon)
        val layoutSplitToggle: View? = view.findViewById(R.id.layoutSplitToggle)
        val toggleThumb: View = view.findViewById(R.id.toggleThumb)
        val tvToggleLabel: TextView = view.findViewById(R.id.tvToggleLabel)
        val progressBarLongPress: ProgressBar = view.findViewById(R.id.progressBarLongPress)
        val tvCoverSubtitle: TextView = view.findViewById(R.id.tvCoverSubtitle)
        val tagCovered: TextView = view.findViewById(R.id.tagCovered)
        val tagCovering: TextView = view.findViewById(R.id.tagCovering)
        val tvSplitBadge: TextView = view.findViewById(R.id.tvSplitBadge)
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

        // Update Cover Share UI
        updateCoverUI(holder, memberState)
        
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
        holder.layoutSplitToggle?.setOnClickListener {
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

        // Long press for Cover Share
        setupLongPress(holder, memberState)
    }

    private fun updateCoverUI(holder: ViewHolder, state: MemberPaymentState) {
        val includedCount = memberStates.count { it.isIncludedInSplit }
        val baseShare = if (includedCount > 0) totalItemAmount / includedCount else 0.0

        // Reset visibility
        holder.tvCoverSubtitle.visibility = View.GONE
        holder.tagCovered.visibility = View.GONE
        holder.tagCovering.visibility = View.GONE
        holder.tvSplitBadge.visibility = View.GONE

        if (!state.isIncludedInSplit) {
            holder.tvSplitBadge.visibility = View.GONE
            return
        }

        var finalShare = baseShare
        if (state.coveredByUserId != null) {
            finalShare = 0.0
            val coverer = memberStates.find { it.userId == state.coveredByUserId }
            val covererName = coverer?.userName ?: "Someone"
            holder.tvCoverSubtitle.visibility = View.VISIBLE
            holder.tvCoverSubtitle.text = "Share absorbed by $covererName"
            holder.tagCovered.visibility = View.VISIBLE
            holder.tagCovered.text = "covered by $covererName"
        } else if (state.coveringUserIds.isNotEmpty()) {
            finalShare = baseShare * (1 + state.coveringUserIds.size)
            holder.tagCovering.visibility = View.VISIBLE
            holder.tagCovering.text = "covering ${state.coveringUserIds.size}"
            holder.tvCoverSubtitle.visibility = View.VISIBLE
            holder.tvCoverSubtitle.text = "Own share + ${state.coveringUserIds.size} covered → ₱${String.format("%.2f", finalShare)}"
        }

        holder.tvSplitBadge.visibility = View.VISIBLE
        holder.tvSplitBadge.text = "₱${String.format("%.2f", finalShare)}"
        holder.tvSplitBadge.setTextColor(if (finalShare > 0) 0xFF2ECC71.toInt() else 0xFF6C757D.toInt())
    }

    private fun setupLongPress(holder: ViewHolder, state: MemberPaymentState) {
        var longPressAnimator: ValueAnimator? = null

        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!state.isIncludedInSplit) return@setOnTouchListener false
                    
                    // Rule 3: A member who is already covering someone cannot be covered by others.
                    if (state.coveringUserIds.isNotEmpty()) return@setOnTouchListener false
                    
                    holder.progressBarLongPress.visibility = View.VISIBLE
                    holder.progressBarLongPress.progress = 0
                    
                    longPressAnimator = ValueAnimator.ofInt(0, 100).apply {
                        duration = 550
                        addUpdateListener { animator ->
                            holder.progressBarLongPress.progress = animator.animatedValue as Int
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                if (holder.progressBarLongPress.progress == 100) {
                                    holder.progressBarLongPress.visibility = View.INVISIBLE
                                    showCoverMenu(holder, state)
                                }
                            }
                        })
                        start()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressAnimator?.cancel()
                    holder.progressBarLongPress.visibility = View.INVISIBLE
                    holder.progressBarLongPress.progress = 0
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showCoverMenu(holder: ViewHolder, state: MemberPaymentState) {
        val context = holder.itemView.context
        val inflater = LayoutInflater.from(context)
        val menuView = inflater.inflate(R.layout.layout_cover_menu, null)
        
        val popupWindow = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 10f

        val tvMenuTitle: TextView = menuView.findViewById(R.id.tvMenuTitle)
        tvMenuTitle.text = "Who covers ${state.userName}'s share?"

        val rvCoverMembers: RecyclerView = menuView.findViewById(R.id.rvCoverMembers)
        val eligibleMembers = memberStates.filter { it.userId != state.userId && it.isIncludedInSplit }
        
        rvCoverMembers.adapter = CoverMembersAdapter(eligibleMembers, state) { covererId ->
            updateCoverAssignment(state.userId, covererId)
            popupWindow.dismiss()
        }

        val btnRemoveCover: View = menuView.findViewById(R.id.btnRemoveCover)
        btnRemoveCover.setOnClickListener {
            updateCoverAssignment(state.userId, null)
            popupWindow.dismiss()
        }

        // To ensure the menu is fully visible when the row is at the bottom, 
        // we use Gravity.CENTER to try and center it or smart positioning.
        // showAsDropDown with y-offset can still go off-screen. 
        // Using Gravity.CENTER on the anchor's location is safer.
        
        menuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuHeight = menuView.measuredHeight
        val location = IntArray(2)
        holder.itemView.getLocationOnScreen(location)
        val screenHeight = context.resources.displayMetrics.heightPixels
        
        // If showing it above the row would push it off the top of the screen, show it below.
        // If showing it below would push it off the bottom, show it above.
        val yOffset = if (location[1] + menuHeight > screenHeight) {
            -menuHeight - holder.itemView.height
        } else {
            -holder.itemView.height / 2
        }

        popupWindow.showAsDropDown(holder.itemView, 0, yOffset)
    }

    private fun updateCoverAssignment(coveredUserId: String, covererUserId: String?) {
        val coveredMemberIndex = memberStates.indexOfFirst { it.userId == coveredUserId }
        if (coveredMemberIndex == -1) return
        
        val oldCovererUserId = memberStates[coveredMemberIndex].coveredByUserId
        
        // Remove from old coverer's list
        if (oldCovererUserId != null) {
            val oldCovererIndex = memberStates.indexOfFirst { it.userId == oldCovererUserId }
            if (oldCovererIndex != -1) {
                val oldCoverer = memberStates[oldCovererIndex]
                oldCoverer.coveringUserIds.remove(coveredUserId)
            }
        }

        // Update covered member
        memberStates[coveredMemberIndex] = memberStates[coveredMemberIndex].copy(
            coveredByUserId = covererUserId
        )

        // Add to new coverer's list
        if (covererUserId != null) {
            val newCovererIndex = memberStates.indexOfFirst { it.userId == covererUserId }
            if (newCovererIndex != -1) {
                val newCoverer = memberStates[newCovererIndex]
                if (!newCoverer.coveringUserIds.contains(coveredUserId)) {
                    newCoverer.coveringUserIds.add(coveredUserId)
                }
            }
        }

        onStateChanged()
    }

    private inner class CoverMembersAdapter(
        private val members: List<MemberPaymentState>,
        private val targetMember: MemberPaymentState,
        private val onMemberSelected: (String) -> Unit
    ) : RecyclerView.Adapter<CoverMembersAdapter.CoverViewHolder>() {

        inner class CoverViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cover_menu_member, parent, false)
            return CoverViewHolder(view)
        }

        override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
            val member = members[position]
            holder.tvMemberName.text = member.userName
            
            // Rule 2: A member who is already being covered by someone cannot cover others.
            val cannotCover = member.coveredByUserId != null 
            
            holder.ivCheck.visibility = if (targetMember.coveredByUserId == member.userId) View.VISIBLE else View.GONE
            
            if (cannotCover) {
                holder.tvMemberName.setTextColor(0xFFCED4DA.toInt())
                holder.tvStatus.visibility = View.VISIBLE
                holder.tvStatus.text = "already covered"
                holder.itemView.isEnabled = false
            } else {
                holder.tvMemberName.setTextColor(0xFF03071E.toInt())
                holder.tvStatus.visibility = View.GONE
                holder.itemView.isEnabled = true
                holder.itemView.setOnClickListener { onMemberSelected(member.userId) }
            }
        }

        override fun getItemCount() = members.size
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
            holder.layoutSplitToggle?.setBackgroundResource(R.drawable.bg_toggle_track_on)
            holder.tvToggleLabel.text = "IN"
            holder.tvToggleLabel.setTextColor(context.getColor(R.color.whitest))
            
            // Position text in the left half, centered
            labelParams.gravity = android.view.Gravity.CENTER_VERTICAL
            labelParams.leftMargin = (8 * density).toInt() // More margin to center in left space
            labelParams.rightMargin = 0
            holder.tvToggleLabel.layoutParams = labelParams

            // Move thumb to right - to the "most end"
            val maxTravel = toggleWidth - thumbWidth - (2 * density) // Account for 2dp padding to reach edge
            holder.toggleThumb.animate()
                .translationX(maxTravel.toFloat())
                .setDuration(200)
                .start()
        } else {
            // "Out" state - grey background, thumb on left, center "OUT" text in right space
            holder.layoutSplitToggle?.setBackgroundResource(R.drawable.bg_toggle_track)
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