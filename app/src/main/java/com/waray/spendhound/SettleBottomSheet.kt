package com.waray.spendhound

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.waray.spendhound.ui.multi_transaction.TransactionPayorInsert
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.query.Columns

class SettleBottomSheet : BottomSheetDialogFragment() {

    private val scope = CoroutineScope(Dispatchers.Main)

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
        val title = if (isSingle) tx.transactionItems[0].category ?: tx.mostRecentTransactionType ?: "Transaction"
                    else tx.mostRecentTransactionType ?: "Transaction"

        view.findViewById<TextView>(R.id.settleTitleTV).text = "Settle: $title"
        view.findViewById<TextView>(R.id.settleTotalTV).text = "Total: ${tx.mostRecentPaymentAmountStr}  •  Each owes: ${CurrencyUtils.formatAmountWithCurrency(tx.totalIndividualPayment)}"

        val amounts = tx.amountsPaidList?.map { it ?: 0.0 }?.toMutableList()
            ?: MutableList(tx.payorUserIds?.size ?: 0) { 0.0 }

        val recycler = view.findViewById<RecyclerView>(R.id.settlePayorsRecyclerView)
        val summaryContainer = view.findViewById<LinearLayout>(R.id.settleSummaryContainer)
        val adapter = SettlePayorAdapter(tx, amounts) { buildSummary(tx, amounts, summaryContainer) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        buildSummary(tx, amounts, summaryContainer)

        val loadingLayout = view.findViewById<View>(R.id.settleLoadingLayout)
        val saveBtn = view.findViewById<MaterialButton>(R.id.settleSaveBtn)
        val cancelBtn = view.findViewById<MaterialButton>(R.id.settleCancelBtn)

        cancelBtn.setOnClickListener { dismiss() }

        saveBtn.setOnClickListener {
            val updated = adapter.getAmounts()
            loadingLayout.visibility = View.VISIBLE
            saveBtn.isEnabled = false
            cancelBtn.isEnabled = false
            saveTransactionChanges(tx, updated,
                onSuccess = {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(requireContext(), "Transaction updated", Toast.LENGTH_SHORT).show()
                    onSettleSaved?.invoke()
                    dismiss()
                },
                onError = { msg ->
                    loadingLayout.visibility = View.GONE
                    saveBtn.isEnabled = true
                    cancelBtn.isEnabled = true
                    Toast.makeText(requireContext(), "Update failed: $msg", Toast.LENGTH_SHORT).show()
                }
            )
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

                    // Skip if already settled via initial excess payment
                    if (existingRow != null && existingRow.initialAmountPaid >= totalOwed) return@forEachIndexed

                    withContext(Dispatchers.IO) {
                        if (existingRow != null) {
                            val paid: Double = newTotalAmount
                            val exc: Double = excess
                            val st: Int = status
                            DeclareDatabase.transactionPayorsTable.update({
                                set("current_amount_paid", paid)
                                set("excess_amount", exc)
                                set("status", st)
                            }) { filter { eq("transaction_id", id); eq("user_id", userId) } }
                        } else {
                            DeclareDatabase.transactionPayorsTable.insert(
                                TransactionPayorInsert(
                                    transactionId = id,
                                    userId = userId,
                                    initialAmountPaid = newTotalAmount,
                                    currentAmountPaid = newTotalAmount,
                                    excessAmount = excess,
                                    transactionItemsId = transaction.rawSplitRows
                                        .firstOrNull { it.userId == userId }?.transactionItemsId ?: 0L,
                                    status = status
                                )
                            )
                        }
                    }
                }

                val allSettled = updatedAmounts.isNotEmpty() &&
                    updatedAmounts.all { it >= transaction.totalIndividualPayment }

                val txStatus: Int = if (allSettled) 3 else 2
                withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.update({
                        set("status", txStatus)
                    }) { filter { eq("id", id) } }
                }

                // Refresh user_balance for all involved users + creator
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

    private fun buildSummary(tx: RecentTransaction, amounts: MutableList<Double>, container: LinearLayout) {
        container.removeAllViews()
        val owed = tx.totalIndividualPayment
        if (owed <= 0) { container.visibility = View.GONE; return }

        val names = tx.payorsList ?: emptyList()
        val excessCreditors = ArrayDeque<Pair<String, Double>>()
        val excessDebtors   = ArrayDeque<Pair<String, Double>>()
        (tx.payorUserIds ?: emptyList()).forEachIndexed { i, uid ->
            val userId = uid?.toLongOrNull()
            val existingRow = tx.rawPayorRows.firstOrNull { it.userId == userId }
            // Use initialAmountPaid for already-settled excess payers, otherwise use current amounts
            val effectivePaid = if (existingRow != null && existingRow.initialAmountPaid >= owed)
                existingRow.initialAmountPaid
            else
                amounts.getOrElse(i) { 0.0 }
            val diff = effectivePaid - owed
            val name = names.getOrNull(i) ?: "User"
            when {
                diff >  0.01 -> excessCreditors.add(Pair(name,  diff))
                diff < -0.01 -> excessDebtors.add(Pair(name, -diff))
            }
        }

        if (excessCreditors.isEmpty() || excessDebtors.isEmpty()) { container.visibility = View.GONE; return }

        val totals = mutableMapOf<Pair<String, String>, Double>()
        var creditorName = excessCreditors.first().first
        var creditorAmt  = excessCreditors.first().second
        excessCreditors.removeFirst()
        var debtorName = excessDebtors.first().first
        var debtorAmt  = excessDebtors.first().second
        excessDebtors.removeFirst()

        while (true) {
            val transfer = minOf(creditorAmt, debtorAmt)
            val key = Pair(debtorName, creditorName)
            totals[key] = (totals[key] ?: 0.0) + transfer
            creditorAmt -= transfer
            debtorAmt   -= transfer
            if (creditorAmt < 0.01) {
                if (excessCreditors.isEmpty()) break
                creditorName = excessCreditors.first().first
                creditorAmt  = excessCreditors.first().second
                excessCreditors.removeFirst()
            }
            if (debtorAmt < 0.01) {
                if (excessDebtors.isEmpty()) break
                debtorName = excessDebtors.first().first
                debtorAmt  = excessDebtors.first().second
                excessDebtors.removeFirst()
            }
        }

        container.visibility = View.VISIBLE
        totals.forEach { (pair, amount) ->
            val tv = TextView(container.context).apply {
                text = "${pair.first} owes ${CurrencyUtils.formatAmountWithCurrency(amount)} to ${pair.second}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(container.context, R.color.darkBlue))
                typeface = ResourcesCompat.getFont(container.context, R.font.montserratalternatess_regular)
                setPadding(0, 4, 0, 4)
            }
            container.addView(tv)
        }
    }

    // ── Inner adapter ──────────────────────────────────────────────────────────

    inner class SettlePayorAdapter(
        private val tx: RecentTransaction,
        private val amounts: MutableList<Double>,
        private val onToggleChanged: () -> Unit
    ) : RecyclerView.Adapter<SettlePayorAdapter.VH>() {

        // 0 = unpaid, 1 = settled, 2 = pending  (mirrors DB status)
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

        fun getAmounts(): List<Double> = amounts.mapIndexed { i, amt ->
            if (statuses[i] == 1) tx.totalIndividualPayment else amt
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_settle_payor_row, parent, false))

        override fun getItemCount() = tx.payorUserIds?.size ?: 0

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = tx.payorsList?.getOrNull(position) ?: "User"
            val userId = tx.payorUserIds?.getOrNull(position)
            val owed = tx.totalIndividualPayment
            val paid = amounts.getOrElse(position) { 0.0 }

            holder.nameTV.text = name
            holder.owedTV.text = "${CurrencyUtils.formatAmountWithCurrency(paid)} / ${CurrencyUtils.formatAmountWithCurrency(owed)}"
            holder.amountInput.setText(if (paid > 0) String.format("%.2f", paid) else "")
            holder.amountInput.hint = "0.00"

            selectToggle(holder, statuses[position])

            holder.unpaidBtn.setOnClickListener {
                statuses[position] = 0
                amounts[position] = 0.0
                holder.amountInput.setText("")
                selectToggle(holder, 0)
                onToggleChanged()
            }
            holder.pendingBtn.setOnClickListener {
                statuses[position] = 2
                selectToggle(holder, 2)
                onToggleChanged()
            }
            holder.paidBtn.setOnClickListener {
                statuses[position] = 1
                amounts[position] = owed
                selectToggle(holder, 1)
                onToggleChanged()
            }
            holder.amountInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    amounts[position] = holder.amountInput.text.toString().toDoubleOrNull() ?: 0.0
                    onToggleChanged()
                }
            }

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
                    } catch (e: Exception) {
                        holder.avatar.setImageResource(R.drawable.placeholder_profile_image)
                    }
                }
            }
        }

        private fun selectToggle(holder: VH, status: Int) {
            val ctx = holder.itemView.context
            val dim   = Color.parseColor("#adb5bd")
            val white = ContextCompat.getColor(ctx, R.color.whitest)

            listOf(holder.unpaidBtn, holder.pendingBtn, holder.paidBtn).forEach {
                it.background = null
                it.setTextColor(dim)
            }

            when (status) {
                0 -> {
                    holder.unpaidBtn.setBackgroundResource(R.drawable.toggle_selected_background)
                    holder.unpaidBtn.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.red)
                    holder.unpaidBtn.setTextColor(white)
                    holder.amountInput.visibility = View.VISIBLE
                }
                2 -> {
                    holder.pendingBtn.setBackgroundResource(R.drawable.toggle_selected_background)
                    holder.pendingBtn.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.yellow)
                    holder.pendingBtn.setTextColor(white)
                    holder.amountInput.visibility = View.VISIBLE
                }
                1 -> {
                    holder.paidBtn.setBackgroundResource(R.drawable.toggle_selected_background)
                    holder.paidBtn.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.green)
                    holder.paidBtn.setTextColor(white)
                    holder.amountInput.visibility = View.GONE
                }
            }
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val avatar: ImageView     = view.findViewById(R.id.settlePayorImage)
            val nameTV: TextView      = view.findViewById(R.id.settlePayorName)
            val owedTV: TextView      = view.findViewById(R.id.settlePayorOwed)
            val amountInput: EditText = view.findViewById(R.id.settleAmountInput)
            val unpaidBtn: TextView   = view.findViewById(R.id.settleUnpaidBtn)
            val pendingBtn: TextView  = view.findViewById(R.id.settlePendingBtn)
            val paidBtn: TextView     = view.findViewById(R.id.settlePaidBtn)
        }
    }
}
