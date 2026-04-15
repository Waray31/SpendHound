package com.waray.spendhound

import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.waray.spendhound.ui.multi_transaction.TransactionPayorInsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Typeface

class SettleBottomSheet : BottomSheetDialogFragment() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val epsilon = 0.01

    private var instructionContainer: LinearLayout? = null
    private var addInstructionBtn: MaterialButton? = null
    private var assignedTV: TextView? = null
    private var remainingTV: TextView? = null
    private var instructionMessageTV: TextView? = null
    private var saveBtn: MaterialButton? = null
    private var advancedContainer: LinearLayout? = null
    private var advancedToggleTV: TextView? = null

    private val instructionRows = mutableListOf<InstructionRowBinding>()

    var transaction: RecentTransaction? = null
    var onSettleSaved: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : BottomSheetDialog(requireContext(), theme) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    val focused = currentFocus
                    if (focused is EditText) {
                        val rect = Rect()
                        focused.getGlobalVisibleRect(rect)
                        if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(focused.windowToken, 0)
                            focused.clearFocus()
                        }
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_settle, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tx = transaction ?: return

        val isSingle = tx.transactionItems.size == 1
        val title = if (isSingle) {
            tx.transactionItems[0].category ?: tx.mostRecentTransactionType ?: "Transaction"
        } else {
            tx.mostRecentTransactionType ?: "Transaction"
        }

        view.findViewById<TextView>(R.id.settleTitleTV).text = "Settle: $title"
        styleSettleTotal(view.findViewById(R.id.settleTotalTV), tx)

        val amounts = tx.amountsPaidList?.map { it ?: 0.0 }?.toMutableList()
            ?: MutableList(tx.payorUserIds?.size ?: 0) { 0.0 }

        val summaryContainer = view.findViewById<LinearLayout>(R.id.settleSummaryContainer)
        instructionContainer = view.findViewById(R.id.settleInstructionContainer)
        addInstructionBtn = view.findViewById(R.id.settleAddInstructionBtn)
        assignedTV = view.findViewById(R.id.settleAssignedTV)
        remainingTV = view.findViewById(R.id.settleRemainingTV)
        instructionMessageTV = view.findViewById(R.id.settleInstructionMessageTV)
        saveBtn = view.findViewById(R.id.settleSaveBtn)
        advancedContainer = view.findViewById(R.id.settleAdvancedContainer)
        advancedToggleTV = view.findViewById(R.id.settleAdvancedToggleTV)
        val recycler = view.findViewById<RecyclerView>(R.id.settlePayorsRecyclerView)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = SettlePayorAdapter(tx, amounts)

        val plan = buildSettlementPlan(tx, amounts)
        val participants = buildParticipantBalances(tx, amounts)
        buildSummary(plan, participants, summaryContainer)
        setupInstructionSection(plan)
        setupAdvancedToggle()

        val loadingLayout = view.findViewById<View>(R.id.settleLoadingLayout)
        val cancelBtn = view.findViewById<MaterialButton>(R.id.settleCancelBtn)

        cancelBtn.setOnClickListener { dismiss() }

        saveBtn?.setOnClickListener {
            val validation = validateInstructions(plan)
            if (!validation.canSave) {
                Toast.makeText(requireContext(), validation.message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = buildUpdatedAmounts(tx, amounts)
            loadingLayout.visibility = View.VISIBLE
            saveBtn?.isEnabled = false
            cancelBtn.isEnabled = false
            addInstructionBtn?.isEnabled = false

            saveTransactionChanges(
                transaction = tx,
                updatedAmounts = updated,
                onSuccess = {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(requireContext(), "Transaction updated", Toast.LENGTH_SHORT).show()
                    onSettleSaved?.invoke()
                    dismiss()
                },
                onError = { msg ->
                    loadingLayout.visibility = View.GONE
                    cancelBtn.isEnabled = true
                    addInstructionBtn?.isEnabled = true
                    refreshInstructionFooter(plan)
                    Toast.makeText(requireContext(), "Update failed: $msg", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setupAdvancedToggle() {
        advancedToggleTV?.setOnClickListener {
            val expanded = advancedContainer?.visibility == View.VISIBLE
            advancedContainer?.visibility = if (expanded) View.GONE else View.VISIBLE
            advancedToggleTV?.text = if (expanded) "Show" else "Hide"
        }
    }

    private fun setupInstructionSection(plan: SettlementPlan) {
        instructionRows.clear()
        instructionContainer?.removeAllViews()

        if (plan.totalToAssign <= epsilon) {
            addInstructionBtn?.isEnabled = false
            instructionMessageTV?.text = "No payment instructions needed. This settlement is already balanced."
            refreshInstructionFooter(plan)
            return
        }

        addInstructionBtn?.isEnabled = true
        plan.instructions.forEach { addInstructionRow(plan, it) }
        if (instructionRows.isEmpty()) {
            addInstructionRow(plan, PaymentInstruction())
        }
        addInstructionBtn?.setOnClickListener { addInstructionRow(plan, PaymentInstruction()) }
        refreshInstructionFooter(plan)
    }

    private fun addInstructionRow(plan: SettlementPlan, instruction: PaymentInstruction) {
        val container = instructionContainer ?: return
        val payerOptions = plan.payers
        val receiverOptions = plan.receivers
        if (payerOptions.isEmpty() || receiverOptions.isEmpty()) return

        val rowView = LayoutInflater.from(container.context)
            .inflate(R.layout.item_settle_instruction_row, container, false)

        val payerSpinner = rowView.findViewById<Spinner>(R.id.settleInstructionPayerSpinner)
        val receiverSpinner = rowView.findViewById<Spinner>(R.id.settleInstructionReceiverSpinner)
        val amountInput = rowView.findViewById<TextInputEditText>(R.id.settleInstructionAmountInput)
        val removeBtn = rowView.findViewById<ImageButton>(R.id.settleInstructionRemoveBtn)

        bindSpinner(payerSpinner, payerOptions.map { it.name })
        bindSpinner(receiverSpinner, receiverOptions.map { it.name })

        val rowInstruction = PaymentInstruction(
            payerId = instruction.payerId ?: payerOptions.firstOrNull()?.id,
            receiverId = instruction.receiverId ?: receiverOptions.firstOrNull()?.id,
            amount = instruction.amount
        )

        if (rowInstruction.amount > epsilon) {
            amountInput.setText(String.format("%.2f", rowInstruction.amount))
        }

        val binding = InstructionRowBinding(rowView, payerSpinner, receiverSpinner, amountInput, removeBtn, plan, rowInstruction)
        instructionRows.add(binding)
        container.addView(rowView)

        payerSpinner.setSelection(indexOfParticipant(payerOptions, rowInstruction.payerId))
        receiverSpinner.setSelection(indexOfParticipant(receiverOptions, rowInstruction.receiverId))

        payerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.instruction.payerId = payerOptions.getOrNull(position)?.id
                refreshInstructionFooter(plan)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        receiverSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.instruction.receiverId = receiverOptions.getOrNull(position)?.id
                refreshInstructionFooter(plan)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        amountInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.instruction.amount = s?.toString()?.toDoubleOrNull() ?: 0.0
                refreshInstructionFooter(plan)
            }
        })

        removeBtn.setOnClickListener {
            instructionRows.remove(binding)
            container.removeView(rowView)
            refreshInstructionFooter(plan)
        }
    }

    private fun bindSpinner(spinner: Spinner, labels: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun indexOfParticipant(options: List<ParticipantOption>, id: Long?): Int {
        val index = options.indexOfFirst { it.id == id }
        return if (index >= 0) index else 0
    }

    private fun refreshInstructionFooter(plan: SettlementPlan) {
        val validation = validateInstructions(plan)
        assignedTV?.text = "Assigned: ${CurrencyUtils.formatAmountWithCurrency(validation.assigned)}"
        remainingTV?.text = "Remaining to assign: ${CurrencyUtils.formatAmountWithCurrency(validation.remaining.coerceAtLeast(0.0))}"

        val remainingColor = if (validation.canSave) R.color.green else R.color.red
        remainingTV?.setTextColor(ContextCompat.getColor(requireContext(), remainingColor))
        instructionMessageTV?.text = validation.message
        instructionMessageTV?.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (validation.canSave) R.color.grey else R.color.red
            )
        )

        saveBtn?.isEnabled = validation.canSave
        saveBtn?.alpha = if (validation.canSave) 1f else 0.5f
        addInstructionBtn?.visibility = if (shouldAllowMoreRows(plan)) View.VISIBLE else View.GONE
    }

    private fun validateInstructions(plan: SettlementPlan): ValidationResult {
        if (plan.totalToAssign <= epsilon) {
            return ValidationResult(assigned = 0.0, remaining = 0.0, canSave = true, message = "Nothing left to assign.")
        }

        if (instructionRows.isEmpty()) {
            return ValidationResult(assigned = 0.0, remaining = plan.totalToAssign, canSave = false, message = "Add at least one payment instruction.")
        }

        val payerAssigned = mutableMapOf<Long, Double>()
        val receiverAssigned = mutableMapOf<Long, Double>()
        var assigned = 0.0

        instructionRows.forEach { row ->
            val payerId = row.instruction.payerId
            val receiverId = row.instruction.receiverId
            val amount = row.instruction.amount

            if (payerId == null || receiverId == null || amount <= epsilon) {
                return ValidationResult(assigned, (plan.totalToAssign - assigned).coerceAtLeast(0.0), false, "Complete each instruction row with payer, receiver, and amount.")
            }
            if (payerId == receiverId) {
                return ValidationResult(assigned, (plan.totalToAssign - assigned).coerceAtLeast(0.0), false, "Payer and receiver must be different.")
            }
            if (!plan.payerNeeds.containsKey(payerId) || !plan.receiverNeeds.containsKey(receiverId)) {
                return ValidationResult(assigned, (plan.totalToAssign - assigned).coerceAtLeast(0.0), false, "Each row must use valid debtors and receivers from this settlement.")
            }

            val payerRemaining = getPayerRemaining(payerId, row)
            if (amount > payerRemaining + epsilon) {
                val name = plan.payers.firstOrNull { it.id == payerId }?.name ?: "This payer"
                return ValidationResult(assigned, (plan.totalToAssign - assigned).coerceAtLeast(0.0), false, "$name cannot pay more than ${CurrencyUtils.formatAmountWithCurrency(payerRemaining.coerceAtLeast(0.0))}.")
            }

            val receiverRemaining = getReceiverRemaining(receiverId, row)
            if (amount > receiverRemaining + epsilon) {
                val name = plan.receivers.firstOrNull { it.id == receiverId }?.name ?: "This receiver"
                return ValidationResult(assigned, (plan.totalToAssign - assigned).coerceAtLeast(0.0), false, "Payment to $name cannot exceed ${CurrencyUtils.formatAmountWithCurrency(receiverRemaining.coerceAtLeast(0.0))}.")
            }

            payerAssigned[payerId] = (payerAssigned[payerId] ?: 0.0) + amount
            receiverAssigned[receiverId] = (receiverAssigned[receiverId] ?: 0.0) + amount
            assigned += amount
        }

        payerAssigned.forEach { (payerId, total) ->
            val needed = plan.payerNeeds[payerId] ?: 0.0
            if (total > needed + epsilon) {
                val name = plan.payers.firstOrNull { it.id == payerId }?.name ?: "This payer"
                return ValidationResult(assigned, 0.0, false, "$name is assigned more than they still owe.")
            }
        }

        receiverAssigned.forEach { (receiverId, total) ->
            val receivable = plan.receiverNeeds[receiverId] ?: 0.0
            if (total > receivable + epsilon) {
                val name = plan.receivers.firstOrNull { it.id == receiverId }?.name ?: "This receiver"
                return ValidationResult(assigned, 0.0, false, "$name is assigned more than they should receive.")
            }
        }

        val remaining = plan.totalToAssign - assigned
        if (remaining > epsilon) {
            return ValidationResult(assigned, remaining, false, "Assign the remaining amount before saving.")
        }
        if (remaining < -epsilon) {
            return ValidationResult(assigned, 0.0, false, "Assigned amount exceeds the settlement total.")
        }

        return ValidationResult(assigned, 0.0, true, "Settlement plan is ready to save.")
    }

    private fun shouldAllowMoreRows(plan: SettlementPlan): Boolean {
        if (plan.totalToAssign <= epsilon) return false
        return instructionRows.size < plan.instructions.size
    }

    private fun getPayerRemaining(payerId: Long, currentRow: InstructionRowBinding): Double {
        val planNeed = currentRow.bindingPlan.payerNeeds[payerId] ?: 0.0
        val alreadyAssigned = instructionRows
            .filter { it !== currentRow && it.instruction.payerId == payerId }
            .sumOf { it.instruction.amount }
        return planNeed - alreadyAssigned
    }

    private fun getReceiverRemaining(receiverId: Long, currentRow: InstructionRowBinding): Double {
        val planNeed = currentRow.bindingPlan.receiverNeeds[receiverId] ?: 0.0
        val alreadyAssigned = instructionRows
            .filter { it !== currentRow && it.instruction.receiverId == receiverId }
            .sumOf { it.instruction.amount }
        return planNeed - alreadyAssigned
    }

    private fun buildUpdatedAmounts(transaction: RecentTransaction, baseAmounts: List<Double>): List<Double> {
        val payerTotals = mutableMapOf<Long, Double>()
        instructionRows.forEach { row ->
            val payerId = row.instruction.payerId ?: return@forEach
            val amount = row.instruction.amount
            payerTotals[payerId] = (payerTotals[payerId] ?: 0.0) + amount
        }

        return (transaction.payorUserIds ?: emptyList()).mapIndexed { index, userIdStr ->
            val userId = userIdStr?.toLongOrNull()
            val basePaid = baseAmounts.getOrElse(index) { 0.0 }
            val owed = transaction.totalIndividualPayment
            val existingRow = transaction.rawPayorRows.firstOrNull { it.userId == userId }
            if (existingRow != null && existingRow.initialAmountPaid >= owed) {
                existingRow.initialAmountPaid
            } else {
                basePaid + (payerTotals[userId] ?: 0.0)
            }
        }
    }

    private fun saveTransactionChanges(
        transaction: RecentTransaction,
        updatedAmounts: List<Double>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val id = transaction.transactionId ?: run { onError("Transaction ID not found"); return }

        scope.launch {
            try {
                val payorUserIds = transaction.payorUserIds ?: emptyList()

                payorUserIds.forEachIndexed { index, userIdStr ->
                    val userId = userIdStr?.toLongOrNull() ?: return@forEachIndexed
                    val newTotalAmount = updatedAmounts.getOrElse(index) { 0.0 }
                    val totalOwed = transaction.totalIndividualPayment
                    val excess = if (newTotalAmount > totalOwed) newTotalAmount - totalOwed else 0.0
                    val status = when {
                        newTotalAmount <= 0.0 -> 0
                        newTotalAmount >= totalOwed -> 1
                        else -> 2
                    }

                    val existingRow = transaction.rawPayorRows.firstOrNull { it.userId == userId }
                    if (existingRow != null && existingRow.initialAmountPaid >= totalOwed) return@forEachIndexed

                    withContext(Dispatchers.IO) {
                        if (existingRow != null) {
                            DeclareDatabase.transactionPayorsTable.update({
                                set("current_amount_paid", newTotalAmount)
                                set("excess_amount", excess)
                                set("status", status)
                            }) { filter { eq("transaction_id", id); eq("user_id", userId) } }
                        } else {
                            DeclareDatabase.transactionPayorsTable.insert(
                                TransactionPayorInsert(
                                    transactionId = id,
                                    userId = userId,
                                    initialAmountPaid = 0.0,
                                    currentAmountPaid = newTotalAmount,
                                    excessAmount = excess,
                                    transactionItemsId = null,
                                    status = status
                                )
                            )
                        }
                    }
                }

                val allSettled = updatedAmounts.isNotEmpty() &&
                    updatedAmounts.all { it >= transaction.totalIndividualPayment }

                val txStatus = if (allSettled) 3 else 2
                withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.update({
                        set("status", txStatus)
                    }) { filter { eq("id", id) } }
                }

                val involvedUserIds = ((transaction.payorUserIds ?: emptyList())
                    .mapNotNull { it?.toLongOrNull() } + listOfNotNull(transaction.creatorNumericId))
                    .distinct()
                involvedUserIds.forEach { uid ->
                    BalanceHelper.refreshUserBalance(uid)
                }

                withContext(Dispatchers.Main) {
                    transaction.amountsPaidList = updatedAmounts.map { it as Double? }.toMutableList()
                    transaction.transactionStatus = if (allSettled) "Settled" else "Pending"
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    private fun buildSettlementPlan(tx: RecentTransaction, amounts: List<Double>): SettlementPlan {
        val participants = buildParticipantBalances(tx, amounts)
        val debtors = participants.filter { it.deficit > epsilon }
        val creditors = participants.filter { it.credit > epsilon }
        val debtorQueue = ArrayDeque(debtors.map { TransferBalance(it.userId, it.name, it.deficit) })
        val creditorQueue = ArrayDeque(creditors.map { TransferBalance(it.userId, it.name, it.credit) })
        val instructions = mutableListOf<PaymentInstruction>()

        while (debtorQueue.isNotEmpty() && creditorQueue.isNotEmpty()) {
            val debtor = debtorQueue.first()
            val creditor = creditorQueue.first()
            val transfer = minOf(debtor.amount, creditor.amount)
            instructions.add(PaymentInstruction(debtor.userId, creditor.userId, transfer))
            debtor.amount -= transfer
            creditor.amount -= transfer
            if (debtor.amount < epsilon) debtorQueue.removeFirst()
            if (creditor.amount < epsilon) creditorQueue.removeFirst()
        }

        return SettlementPlan(
            instructions = instructions,
            payers = debtors.map { ParticipantOption(it.userId, it.name) },
            receivers = creditors.map { ParticipantOption(it.userId, it.name) },
            payerNeeds = debtors.associate { it.userId to it.deficit },
            receiverNeeds = creditors.associate { it.userId to it.credit },
            totalToAssign = instructions.sumOf { it.amount }
        )
    }

    private fun buildParticipantBalances(tx: RecentTransaction, amounts: List<Double>): List<ParticipantBalance> {
        val owed = tx.totalIndividualPayment
        return (tx.payorUserIds ?: emptyList()).mapIndexedNotNull { index, uid ->
            val userId = uid?.toLongOrNull() ?: return@mapIndexedNotNull null
            val existingRow = tx.rawPayorRows.firstOrNull { it.userId == userId }
            val paid = amounts.getOrElse(index) { 0.0 }
            val effectivePaid = if (existingRow != null && existingRow.initialAmountPaid >= owed) {
                existingRow.initialAmountPaid
            } else {
                paid
            }
            val diff = effectivePaid - owed
            ParticipantBalance(
                userId = userId,
                name = tx.payorsList?.getOrNull(index) ?: "User",
                paid = paid,
                owed = owed,
                credit = if (diff > epsilon) diff else 0.0,
                deficit = if (diff < -epsilon) -diff else 0.0
            )
        }
    }

    private fun buildSummary(
        plan: SettlementPlan,
        participants: List<ParticipantBalance>,
        container: LinearLayout
    ) {
        container.removeAllViews()

        if (plan.instructions.isEmpty()) {
            container.visibility = View.VISIBLE
            val emptyText = TextView(container.context).apply {
                text = "No transfers needed."
                textSize = 12f
                setTextColor(ContextCompat.getColor(container.context, R.color.grey))
                typeface = ResourcesCompat.getFont(container.context, R.font.montserratalternatess_regular)
                setPadding(0, 4, 0, 4)
            }
            container.addView(emptyText)
            return
        }

        container.visibility = View.VISIBLE
        val currentUserId = transaction?.creatorNumericId
        val participantById = participants.associateBy { it.userId }
        plan.instructions
            .groupBy { it.payerId }
            .toList()
            .sortedByDescending { (payerId, _) -> payerId == currentUserId }
            .forEach { (payerId, payerInstructions) ->
            val participant = payerId?.let { participantById[it] }
            val payerName = if (payerId == currentUserId) "You" else participant?.name ?: resolveParticipantName(payerId)
            val clauses = payerInstructions.map { instruction ->
                val receiverName = if (instruction.receiverId == currentUserId) {
                    "you"
                } else {
                    resolveParticipantName(instruction.receiverId)
                }
                SummaryClause(receiverName, instruction.amount)
            }
            val tv = TextView(container.context).apply {
                text = buildSummarySentence(payerName, clauses)
                textSize = 12f
                setTextColor(ContextCompat.getColor(container.context, R.color.darkBlue))
                typeface = ResourcesCompat.getFont(container.context, R.font.montserratalternatess_regular)
                setPadding(0, 4, 0, 4)
            }
            container.addView(tv)
        }
    }

    private fun buildSummarySentence(payerName: String, clauses: List<SummaryClause>): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        appendHighlighted(builder, payerName)
        builder.append(" owed ")
        clauses.forEachIndexed { index, clause ->
            if (index > 0) builder.append(" and ")
            if (clause.receiverName == "you") {
                appendHighlighted(builder, clause.receiverName)
                builder.append(" ")
                appendHighlighted(builder, CurrencyUtils.formatAmountWithCurrency(clause.amount))
            } else {
                appendHighlighted(builder, CurrencyUtils.formatAmountWithCurrency(clause.amount))
                builder.append(" to ")
                appendHighlighted(builder, clause.receiverName)
            }
        }
        return builder
    }

    private fun appendHighlighted(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text)
        builder.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.darkBlue)),
            start,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun styleSettleTotal(totalTV: TextView, tx: RecentTransaction) {
        val totalLabel = "Total: "
        val totalAmount = tx.mostRecentPaymentAmountStr ?: CurrencyUtils.formatAmountWithCurrency(0.0)
        val eachLabel = "  •  Each owes: "
        val eachAmount = CurrencyUtils.formatAmountWithCurrency(tx.totalIndividualPayment)
        val text = SpannableStringBuilder(totalLabel)
        val totalStart = text.length
        text.append(totalAmount)
        val totalEnd = text.length
        text.append(eachLabel)
        val eachStart = text.length
        text.append(eachAmount)
        val eachEnd = text.length

        fun applyAmountHighlight(start: Int, end: Int) {
            text.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.darkBlue)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        applyAmountHighlight(totalStart, totalEnd)
        applyAmountHighlight(eachStart, eachEnd)
        totalTV.text = text
    }

    private fun resolveParticipantName(userId: Long?): String {
        val tx = transaction ?: return "User"
        val index = tx.payorUserIds?.indexOfFirst { it?.toLongOrNull() == userId } ?: -1
        return if (index >= 0) tx.payorsList?.getOrNull(index) ?: "User" else "User"
    }

    inner class SettlePayorAdapter(
        private val tx: RecentTransaction,
        private val amounts: MutableList<Double>
    ) : RecyclerView.Adapter<SettlePayorAdapter.VH>() {

        private val statuses: MutableList<Int> = (tx.payorUserIds ?: emptyList()).mapIndexed { index, uid ->
            val userId = uid?.toLongOrNull()
            val paid = amounts.getOrElse(index) { 0.0 }
            val owed = tx.totalIndividualPayment
            val userRows = tx.rawPayorRows.filter { it.userId == userId }
            when {
                paid >= owed && owed > 0 -> 1
                userRows.isEmpty() -> 0
                userRows.all { it.status == 1 } -> 1
                userRows.any { it.currentAmountPaid > 0 } -> 2
                else -> 0
            }
        }.toMutableList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_settle_payor_row, parent, false))

        override fun getItemCount() = tx.payorUserIds?.size ?: 0

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = tx.payorsList?.getOrNull(position) ?: "User"
            val userId = tx.payorUserIds?.getOrNull(position)
            val owed = tx.totalIndividualPayment
            val paid = amounts.getOrElse(position) { 0.0 }
            val existingRow = userId?.toLongOrNull()?.let { uid -> tx.rawPayorRows.firstOrNull { it.userId == uid } }
            val effectivePaid = if (existingRow != null && existingRow.initialAmountPaid >= owed) {
                existingRow.initialAmountPaid
            } else {
                paid
            }
            val excessAmount = (effectivePaid - owed).takeIf { it > epsilon } ?: 0.0

            holder.nameTV.text = name
            holder.owedTV.text = "${CurrencyUtils.formatAmountWithCurrency(paid)} / ${CurrencyUtils.formatAmountWithCurrency(owed)}"
            if (excessAmount > 0.0) {
                holder.excessTV.text = "+${CurrencyUtils.formatAmountWithCurrency(excessAmount)}"
                holder.excessTV.visibility = View.VISIBLE
            } else {
                holder.excessTV.visibility = View.GONE
            }
            selectStatus(holder, statuses[position])

            val cachedUrl = PayorAdapter.sDownloadUrlCache[userId]
            if (cachedUrl != null) {
                Glide.with(holder.itemView.context).load(cachedUrl)
                    .circleCrop().diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_profile_image)
                    .into(holder.avatar)
            } else if (userId != null) {
                scope.launch {
                    try {
                        val url = withContext(Dispatchers.IO) {
                            DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                        }
                        PayorAdapter.sDownloadUrlCache[userId] = url
                        Glide.with(holder.itemView.context).load(url)
                            .circleCrop().diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.placeholder_profile_image)
                            .into(holder.avatar)
                    } catch (_: Exception) {
                        holder.avatar.setImageResource(R.drawable.placeholder_profile_image)
                    }
                }
            }
        }

        private fun selectStatus(holder: VH, status: Int) {
            val ctx = holder.itemView.context
            val white = ContextCompat.getColor(ctx, R.color.whitest)
            when (status) {
                0 -> {
                    holder.statusTV.text = "Unpaid"
                    holder.statusTV.setBackgroundResource(R.drawable.toggle_selected_background)
                    holder.statusTV.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.red)
                    holder.statusTV.setTextColor(white)
                }
                2 -> {
                    holder.statusTV.text = "Pending"
                    holder.statusTV.setBackgroundResource(R.drawable.toggle_selected_background)
                    holder.statusTV.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.yellow)
                    holder.statusTV.setTextColor(white)
                }
                else -> {
                    holder.statusTV.text = "Settled"
                    holder.statusTV.setBackgroundResource(R.drawable.toggle_selected_background)
                    holder.statusTV.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.green)
                    holder.statusTV.setTextColor(white)
                }
            }
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val avatar: ImageView = view.findViewById(R.id.settlePayorImage)
            val nameTV: TextView = view.findViewById(R.id.settlePayorName)
            val excessTV: TextView = view.findViewById(R.id.settlePayorExcess)
            val owedTV: TextView = view.findViewById(R.id.settlePayorOwed)
            val statusTV: TextView = view.findViewById(R.id.settlePayorStatus)
        }
    }

    private data class ParticipantBalance(
        val userId: Long,
        val name: String,
        val paid: Double,
        val owed: Double,
        val credit: Double,
        val deficit: Double
    ) {
        val netBalance: Double get() = credit - deficit
    }

    private data class ParticipantOption(val id: Long, val name: String)

    private data class PaymentInstruction(
        var payerId: Long? = null,
        var receiverId: Long? = null,
        var amount: Double = 0.0
    )

    private data class TransferBalance(
        val userId: Long,
        val name: String,
        var amount: Double
    )

    private data class SettlementPlan(
        val instructions: List<PaymentInstruction>,
        val payers: List<ParticipantOption>,
        val receivers: List<ParticipantOption>,
        val payerNeeds: Map<Long, Double>,
        val receiverNeeds: Map<Long, Double>,
        val totalToAssign: Double
    )

    private data class InstructionRowBinding(
        val root: View,
        val payerSpinner: Spinner,
        val receiverSpinner: Spinner,
        val amountInput: TextInputEditText,
        val removeBtn: ImageButton,
        val bindingPlan: SettlementPlan,
        val instruction: PaymentInstruction
    )

    private data class ValidationResult(
        val assigned: Double,
        val remaining: Double,
        val canSave: Boolean,
        val message: String
    )

    private data class SummaryClause(
        val receiverName: String,
        val amount: Double
    )
}
