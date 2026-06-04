package com.waray.spendhound

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.waray.spendhound.ui.multi_transaction.TransactionPayorInsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.query.Columns
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class TransactionSettlementActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val epsilon = 0.01

    private var instructionContainer: LinearLayout? = null
    private var addInstructionBtn: MaterialButton? = null
    private var assignedTV: TextView? = null
    private var remainingTV: TextView? = null
    private var instructionMessageTV: TextView? = null
    private var saveBtn: MaterialButton? = null
    private var btnShare: ImageButton? = null
    private var settlementInstructions: LinearLayout? = null
    private val instructionRows = mutableListOf<InstructionRowBinding>()

    // Receipt header views
    private var receiptTransactionNameTV: TextView? = null
    private var receiptDateAndMembersTV: TextView? = null
    private var receiptTotalAmountTV: TextView? = null
    private var receiptPerPersonTV: TextView? = null
    private var itemsTableContainer: LinearLayout? = null
    private var memberBreakdownRecyclerView: RecyclerView? = null
    private val userProfilesMap = mutableMapOf<Long, User>()

    private var transaction: RecentTransaction? = null
    private var isDetailsMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_settlement)

        val txJson = intent.getStringExtra("EXTRA_TRANSACTION_JSON")
        if (txJson != null) {
            transaction = try {
                Json.decodeFromString<RecentTransaction>(txJson)
            } catch (e: Exception) {
                null
            }
        }
        isDetailsMode = intent.getBooleanExtra("EXTRA_IS_DETAILS", false)

        val tx = transaction ?: run {
            Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews(tx)
        loadUserProfiles(tx)
    }

    private fun loadUserProfiles(tx: RecentTransaction) {
        val payorIds = tx.payorUserIds?.filterNotNull() ?: emptyList()
        val creatorId = tx.creatorNumericId?.toString()
        val allIdsStr = (payorIds + listOfNotNull(creatorId)).distinct()
        val allIdsLong = allIdsStr.mapNotNull { it.toLongOrNull() }
        
        if (allIdsLong.isEmpty()) return

        scope.launch {
            try {
                val users = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id", "profile_image_url")) {
                        filter { isIn("user_id", allIdsLong) }
                    }.decodeList<User>()
                }
                
                users.forEach { user ->
                    user.id?.let { id ->
                        val idStr = id.toString()
                        userProfilesMap[id] = user
                        val url = user.profileImageUrl ?: DeclareDatabase.profileImagesBucket.publicUrl("$idStr/$idStr.jpg")
                        PayorAdapter.sDownloadUrlCache[idStr] = url
                    }
                }
                
                // Refresh list to show images
                memberBreakdownRecyclerView?.adapter?.notifyDataSetChanged()
                
                // Re-build summary to show images in summary rows
                val summaryContainer = findViewById<LinearLayout>(R.id.settleSummaryContainer)
                if (summaryContainer != null && summaryContainer.visibility == View.VISIBLE) {
                    val amounts = tx.amountsPaidList?.map { it ?: 0.0 }?.toMutableList()
                        ?: MutableList(tx.payorUserIds?.size ?: 0) { 0.0 }
                    val plan = buildSettlementPlan(tx, amounts)
                    buildSummary(plan, summaryContainer)
                }
            } catch (e: Exception) {
                Log.e("TransactionSettlement", "Error loading user profiles: ${e.message}")
            }
        }
    }

    private fun initViews(tx: RecentTransaction) {
        val isSingle = tx.transactionItems.size == 1
        val title = if (isSingle) {
            tx.transactionItems[0].category ?: tx.mostRecentTransactionType ?: "Transaction"
        } else {
            tx.mostRecentTransactionType ?: "Transaction"
        }

        findViewById<TextView>(R.id.settleTitleTV).text = "Transaction Settlement"

        // Receipt Header
        receiptTransactionNameTV = findViewById(R.id.receiptTransactionNameTV)
        receiptDateAndMembersTV = findViewById(R.id.receiptDateAndMembersTV)
        receiptTotalAmountTV = findViewById(R.id.receiptTotalAmountTV)
        receiptPerPersonTV = findViewById(R.id.receiptPerPersonTV)
        itemsTableContainer = findViewById(R.id.itemsTableContainer)
        memberBreakdownRecyclerView = findViewById(R.id.memberBreakdownRecyclerView)

        receiptTransactionNameTV?.text = title
        val memberCount = tx.payorUserIds?.size ?: 0
        receiptDateAndMembersTV?.text = "${tx.mostRecentDate ?: ""}  •  $memberCount members"

        val totalBill = tx.transactionItems.sumOf { it.amount }
        receiptTotalAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(totalBill)

        val perPerson = if (memberCount > 0) totalBill / memberCount else 0.0
        receiptPerPersonTV?.text = "${CurrencyUtils.formatAmountWithCurrency(perPerson)} per person"

        buildItemsTable(tx)

        val amounts = tx.amountsPaidList?.map { it ?: 0.0 }?.toMutableList()
            ?: MutableList(tx.payorUserIds?.size ?: 0) { 0.0 }

        val summaryContainer = findViewById<LinearLayout>(R.id.settleSummaryContainer)
        instructionContainer = findViewById(R.id.settleInstructionContainer)
        addInstructionBtn = findViewById(R.id.settleAddInstructionBtn)
        assignedTV = findViewById(R.id.settleAssignedTV)
        remainingTV = findViewById(R.id.settleRemainingTV)
        instructionMessageTV = findViewById(R.id.settleInstructionMessageTV)
        saveBtn = findViewById(R.id.settleSaveBtn)
        settlementInstructions = findViewById(R.id.settlement_instructions)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnShare = findViewById(R.id.btnShare)
        btnShare?.setOnClickListener { showShareMenu() }

        memberBreakdownRecyclerView?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        memberBreakdownRecyclerView?.adapter = MemberBreakdownAdapter(tx, amounts)

        val plan = buildSettlementPlan(tx, amounts)
        buildSummary(plan, summaryContainer)
        setupInstructionSection(plan)

        val loadingLayout = findViewById<View>(R.id.settleLoadingLayout)
        val cancelBtn = findViewById<MaterialButton>(R.id.settleCancelBtn)
        val bottomActionContainer = findViewById<View>(R.id.bottomActionContainer)

        cancelBtn.setOnClickListener { finish() }

        saveBtn?.setOnClickListener {
            val validation = validateInstructions(plan)
            if (!validation.canSave) {
                Toast.makeText(this, validation.message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = buildUpdatedAmounts(tx, amounts)
            val instructions = instructionRows.map { it.instruction }
            loadingLayout.visibility = View.VISIBLE
            saveBtn?.isEnabled = false
            cancelBtn.isEnabled = false
            addInstructionBtn?.isEnabled = false

            saveTransactionChanges(
                transaction = tx,
                updatedAmounts = updated,
                instructions = instructions,
                onSuccess = {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show()
                    com.waray.spendhound.TransactionState.notifyChange()
                    finish()
                },
                onError = { msg ->
                    loadingLayout.visibility = View.GONE
                    cancelBtn.isEnabled = true
                    addInstructionBtn?.isEnabled = true
                    refreshInstructionFooter(plan)
                    Toast.makeText(this, "Update failed: $msg", Toast.LENGTH_SHORT).show()
                }
            )
        }

        val isSettled = tx.transactionStatus == "Settled"
        if (isSettled || isDetailsMode) {
            findViewById<TextView>(R.id.settleSummaryTitleTV)?.visibility = View.GONE
            findViewById<View>(R.id.settleSummaryDivider)?.visibility = View.GONE
            findViewById<TextView>(R.id.settlementTitleTV).visibility = View.GONE
            findViewById<TextView>(R.id.settlementDescriptionTV).visibility = View.GONE
            findViewById<View>(R.id.settlementDivider)?.visibility = View.GONE
            instructionContainer?.visibility = View.GONE
            addInstructionBtn?.visibility = View.GONE
            assignedTV?.visibility = View.GONE
            remainingTV?.visibility = View.GONE
            instructionMessageTV?.visibility = View.GONE
            saveBtn?.visibility = View.GONE
            cancelBtn.visibility = View.GONE
            settlementInstructions?.visibility = View.GONE
            summaryContainer.visibility = View.GONE
            bottomActionContainer.visibility = View.GONE
        }
    }

    private fun showShareMenu() {
        val popup = PopupMenu(this, btnShare!!)
        popup.menu.add("Share as image (PNG)")
        popup.menu.add("Share as PDF")
        popup.menu.add("Share to group")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Share as image (PNG)" -> shareAsImage()
                "Share as PDF" -> shareAsPdf()
                "Share to group" -> shareToGroup()
            }
            true
        }
        popup.show()
    }

    private fun shareAsImage() {
        val bitmap = getReceiptBitmap()
        val uri = saveBitmapToCache(bitmap)
        if (uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Image"))
        }
    }

    private fun shareAsPdf() {
        val bitmap = getReceiptBitmap()

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val file = File(cacheDir, "settlement_${System.currentTimeMillis()}.pdf")
        try {
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun shareToGroup() {
        val tx = transaction ?: return
        val gid = tx.groupId ?: -1L
        if (gid == -1L) {
            Toast.makeText(this, "No group associated with this transaction", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val uid = DeclareDatabase.auth.currentUserOrNull()?.id?.let { authId ->
                    DeclareDatabase.usersTable.select { filter { eq("auth_id", authId) } }.decodeSingleOrNull<User>()?.id
                } ?: return@launch

                val message = "Shared Transaction Settlement for ${tx.mostRecentDetails ?: "Transaction"}"

                DeclareDatabase.groupMessagesTable.insert(
                    GroupMessageInsert(
                        groupId = gid,
                        userId = uid,
                        message = message,
                        transactionId = tx.transactionId
                    )
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TransactionSettlementActivity, "Shared to group chat", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@TransactionSettlementActivity, GroupDetailActivity::class.java).apply {
                        putExtra(GroupDetailActivity.EXTRA_GROUP_ID, gid)
                        putExtra("SELECT_TAB", 1)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TransactionSettlementActivity, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getReceiptBitmap(): Bitmap {
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.mainScrollView)
        val zigzag = findViewById<View>(R.id.zigzagLayout)
        val settlementDivider = findViewById<View>(R.id.settlementDivider)
        
        val scrollContent = scrollView.getChildAt(0)
        val width = scrollContent.width
        
        // Determine the effective height: hide settlement instructions if pending
        val isPending = transaction?.transactionStatus == "Pending"
        var effectiveScrollHeight = scrollContent.height
        
        if (isPending && settlementDivider != null && settlementDivider.visibility == View.VISIBLE) {
            // Cut off at the start of the settlement section (just before the settlement divider)
            effectiveScrollHeight = settlementDivider.top
        }

        val height = effectiveScrollHeight + (zigzag?.height ?: 0)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        
        // Draw scroll content (clipped if needed)
        canvas.save()
        canvas.clipRect(0, 0, width, effectiveScrollHeight)
        scrollContent.draw(canvas)
        canvas.restore()
        
        // Draw zigzag below
        if (zigzag != null) {
            canvas.save()
            canvas.translate(0f, effectiveScrollHeight.toFloat())
            zigzag.draw(canvas)
            canvas.restore()
        }
        
        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        val imagesFolder = File(cacheDir, "images")
        try {
            imagesFolder.mkdirs()
            val file = File(imagesFolder, "settlement_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun buildItemsTable(tx: RecentTransaction) {
        val container = itemsTableContainer ?: return
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        tx.transactionItems.forEach { item ->
            val rowView = inflater.inflate(R.layout.item_transaction_item_row, container, false)
            val tvDesc = rowView.findViewById<TextView>(R.id.tvItemDescription)
            val llPaidBy = rowView.findViewById<LinearLayout>(R.id.llItemPaidBy)
            val tvAmount = rowView.findViewById<TextView>(R.id.tvItemAmount)

            tvDesc.text = item.itemDescription ?: item.category ?: "Item"
            
            val itemId = item.id ?: 0L
            val itemPayors = tx.rawPayorRows.filter { it.transactionItemsId == itemId && it.initialAmountPaid > 0.01 }

            if (itemPayors.isEmpty()) {
                val tv = TextView(this).apply {
                    text = "-"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@TransactionSettlementActivity, R.color.grey))
                    typeface = ResourcesCompat.getFont(this@TransactionSettlementActivity, R.font.montserratalternatess_regular)
                }
                llPaidBy.addView(tv)
            } else {
                itemPayors.forEach { payor ->
                    val tv = TextView(this).apply {
                        textSize = 10f
                        setTextColor(ContextCompat.getColor(this@TransactionSettlementActivity, R.color.black))
                        typeface = ResourcesCompat.getFont(this@TransactionSettlementActivity, R.font.montserratalternatess_regular)
                        
                        UserHelper.getUsernameById(payor.userId, object : UserHelper.UsernameCallback {
                            override fun onUsernameRetrieved(username: String?) {
                                val name = username ?: "Unknown"
                                text = if (itemPayors.size == 1) name else "$name - ${CurrencyUtils.formatAmountWithCurrency(payor.initialAmountPaid)}"
                            }
                            override fun onError(error: String?) {
                                text = "Unknown"
                            }
                        })
                    }
                    llPaidBy.addView(tv)
                }
            }
            
            tvAmount.text = CurrencyUtils.formatAmountWithCurrency(item.amount)
            tvAmount.gravity = android.view.Gravity.END
            
            container.addView(rowView)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val rect = Rect()
                focused.getGlobalVisibleRect(rect)
                if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                    focused.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupInstructionSection(plan: SettlementPlan) {
        instructionRows.clear()
        instructionContainer?.removeAllViews()

        if (plan.totalToAssign <= epsilon) {
            addInstructionBtn?.isEnabled = false
            instructionMessageTV?.text = "No payment instructions needed. This settlement is already balanced."
            refreshInstructionFooter(plan)
            settlementInstructions?.visibility = View.GONE
            return
        }

        addInstructionBtn?.isEnabled = true
        plan.instructions.forEach { addInstructionRow(plan, it) }
        if (instructionRows.isEmpty()) {
            settlementInstructions?.visibility = View.VISIBLE
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
            amountInput.setText(String.format(Locale.US, "%.2f", rowInstruction.amount))
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
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
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

        val remainingColor = when {
            validation.remaining > epsilon -> R.color.yellow
            validation.remaining < -epsilon -> R.color.red
            else -> R.color.green
        }
        remainingTV?.setTextColor(ContextCompat.getColor(this, remainingColor))
        instructionMessageTV?.text = validation.message
        instructionMessageTV?.setTextColor(
            ContextCompat.getColor(
                this,
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

        for (row in instructionRows) {
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

        for ((payerId, total) in payerAssigned) {
            val needed = plan.payerNeeds[payerId] ?: 0.0
            if (total > needed + epsilon) {
                val name = plan.payers.firstOrNull { it.id == payerId }?.name ?: "This payer"
                return ValidationResult(assigned, 0.0, false, "$name is assigned more than they still owe.")
            }
        }

        for ((receiverId, total) in receiverAssigned) {
            val receivable = plan.receiverNeeds[receiverId] ?: 0.0
            if (total > receivable + epsilon) {
                val name = plan.receivers.firstOrNull { it.id == receiverId }?.name ?: "This receiver"
                return ValidationResult(assigned, 0.0, false, "$name is assigned more than they should receive.")
            }
        }

        val remaining = plan.totalToAssign - assigned
        val message = when {
            remaining > epsilon -> "Warning: ${CurrencyUtils.formatAmountWithCurrency(remaining)} not assigned. You can save anyway."
            remaining < -epsilon -> "Warning: Assigned ${CurrencyUtils.formatAmountWithCurrency(-remaining)} more than needed. You can save anyway."
            else -> "Settlement plan is ready to save."
        }

        return ValidationResult(assigned, remaining.coerceAtLeast(0.0), true, message)
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
        val userOwedMap = transaction.rawSplitRows.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
        val payerTotals = mutableMapOf<Long, Double>()
        for (row in instructionRows) {
            val payerId = row.instruction.payerId ?: continue
            val amount = row.instruction.amount
            payerTotals[payerId] = (payerTotals[payerId] ?: 0.0) + amount
        }

        return (transaction.payorUserIds ?: emptyList()).mapIndexed { index, userIdStr ->
            val userId = userIdStr?.toLongOrNull()
            val basePaid = baseAmounts.getOrElse(index) { 0.0 }
            val owed = userOwedMap[userId] ?: 0.0
            val userRows = transaction.rawPayorRows.filter { it.userId == userId }
            val totalInitial = userRows.sumOf { it.initialAmountPaid }
            if (userRows.isNotEmpty() && totalInitial >= owed - epsilon) {
                totalInitial
            } else {
                basePaid + (payerTotals[userId] ?: 0.0)
            }
        }
    }

    private fun saveTransactionChanges(
        transaction: RecentTransaction,
        updatedAmounts: List<Double>,
        instructions: List<PaymentInstruction>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val id = transaction.transactionId ?: run { onError("Transaction ID not found"); return }

        scope.launch {
            try {
                val payorUserIds = transaction.payorUserIds ?: emptyList()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val batchTimestamp = sdf.format(java.util.Date())

                val payorToReceivers = mutableMapOf<Long, MutableList<Long>>()
                val receiverTotals = mutableMapOf<Long, Double>()
                instructions.forEach { instr ->
                    val payerId = instr.payerId ?: return@forEach
                    val receiverId = instr.receiverId ?: return@forEach
                    val amount = instr.amount
                    payorToReceivers.getOrPut(payerId) { mutableListOf() }.add(receiverId)
                    receiverTotals[receiverId] = (receiverTotals[receiverId] ?: 0.0) + amount
                }

                val userOwedMap = transaction.rawSplitRows.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
                payorUserIds.forEachIndexed { index, userIdStr ->
                    val userId = userIdStr?.toLongOrNull() ?: return@forEachIndexed
                    val newTotalAmount = updatedAmounts.getOrElse(index) { 0.0 }
                    val totalOwed = userOwedMap[userId] ?: 0.0
                    val excess = if (newTotalAmount > totalOwed + epsilon) newTotalAmount - totalOwed else 0.0
                    val status = when {
                        newTotalAmount <= epsilon -> 0
                        newTotalAmount >= totalOwed - epsilon -> 1
                        else -> 2
                    }
                    val paidTo = payorToReceivers[userId]?.singleOrNull()

                    val userRows = transaction.rawPayorRows.filter { it.userId == userId }
                    val totalInitial = userRows.sumOf { it.initialAmountPaid }
                    if (userRows.isNotEmpty() && totalInitial >= totalOwed - epsilon) return@forEachIndexed

                    withContext(Dispatchers.IO) {
                        val leadRow = userRows.firstOrNull { it.transactionItemsId == null } ?: userRows.firstOrNull()
                        if (leadRow != null) {
                            val otherRowsPaid = userRows.filter { it.id != leadRow.id }.sumOf { it.currentAmountPaid }
                            val amountForLeadRow = newTotalAmount - otherRowsPaid

                            DeclareDatabase.transactionPayorsTable.update({
                                set("current_amount_paid", amountForLeadRow)
                                set("excess_amount", excess)
                                set("status", status)
                                set("paid_to", paidTo)
                                set("updated_at", batchTimestamp)
                            }) { filter { eq("id", leadRow.id!!) } }

                            val otherRowIds = userRows.mapNotNull { it.id }.filter { it != leadRow.id }
                            if (otherRowIds.isNotEmpty()) {
                                DeclareDatabase.transactionPayorsTable.update({
                                    set("excess_amount", 0.0)
                                    set("updated_at", batchTimestamp)
                                }) { filter { isIn("id", otherRowIds) } }
                            }
                        } else {
                            DeclareDatabase.transactionPayorsTable.insert(
                                TransactionPayorInsert(
                                    transactionId = id,
                                    userId = userId,
                                    initialAmountPaid = 0.0,
                                    currentAmountPaid = newTotalAmount,
                                    excessAmount = excess,
                                    transactionItemsId = null,
                                    status = status,
                                    paidTo = paidTo,
                                    updatedAt = batchTimestamp
                                )
                            )
                        }
                    }
                }

                receiverTotals.forEach { (receiverId, amountReceived) ->
                    val userRows = transaction.rawPayorRows.filter { it.userId == receiverId }
                    val leadRow = userRows.firstOrNull { it.transactionItemsId == null } ?: userRows.firstOrNull()
                    if (leadRow != null) {
                        val totalExcess = userRows.map { it.excessAmount }.maxOrNull() ?: 0.0
                        withContext(Dispatchers.IO) {
                            DeclareDatabase.transactionPayorsTable.update({
                                set("excess_amount", totalExcess - amountReceived)
                                set("updated_at", batchTimestamp)
                            }) { filter { eq("id", leadRow.id!!) } }

                            val otherRowIds = userRows.mapNotNull { it.id }.filter { it != leadRow.id }
                            if (otherRowIds.isNotEmpty()) {
                                DeclareDatabase.transactionPayorsTable.update({
                                    set("excess_amount", 0.0)
                                    set("updated_at", batchTimestamp)
                                }) { filter { isIn("id", otherRowIds) } }
                            }
                        }
                    }
                }

                val allSettled = updatedAmounts.isNotEmpty() &&
                    updatedAmounts.indices.all { i ->
                        val uid = payorUserIds[i]?.toLongOrNull()
                        val owed = userOwedMap[uid] ?: 0.0
                        updatedAmounts[i] >= owed - epsilon
                    }

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
        val userOwedMap = tx.rawSplitRows.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
        return (tx.payorUserIds ?: emptyList()).mapIndexedNotNull { index, uid ->
            val userId = uid?.toLongOrNull() ?: return@mapIndexedNotNull null
            val userRows = tx.rawPayorRows.filter { it.userId == userId }
            val totalInitial = userRows.sumOf { it.initialAmountPaid }
            val owed = userOwedMap[userId] ?: 0.0
            val paid = amounts.getOrElse(index) { 0.0 }
            val effectivePaid = if (userRows.isNotEmpty() && totalInitial >= owed - epsilon) {
                totalInitial
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
        container: LinearLayout
    ) {
        container.removeAllViews()

        if (plan.instructions.isEmpty()) {
            container.visibility = View.VISIBLE
            val emptyText = TextView(container.context).apply {
                text = "No transfers needed."
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                setTextColor(ContextCompat.getColor(container.context, R.color.grey))
                typeface = ResourcesCompat.getFont(container.context, R.font.montserratalternatess_regular)
                setPadding(0, 4, 0, 4)
            }
            container.addView(emptyText)
            return
        }

        container.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        
        plan.instructions.forEach { instr ->
            val rowView = inflater.inflate(R.layout.item_settlement_summary_row, container, false)
            val ivPayer = rowView.findViewById<ImageView>(R.id.ivPayerAvatar)
            val tvPayer = rowView.findViewById<TextView>(R.id.tvPayerName)
            val ivReceiver = rowView.findViewById<ImageView>(R.id.ivReceiverAvatar)
            val tvReceiver = rowView.findViewById<TextView>(R.id.tvReceiverName)
            val tvAmount = rowView.findViewById<TextView>(R.id.tvSettlementAmount)
            val tvDesc = rowView.findViewById<TextView>(R.id.tvSettlementDescription)

            val payerName = resolveParticipantName(instr.payerId)
            val receiverName = resolveParticipantName(instr.receiverId)

            tvPayer.text = payerName
            tvReceiver.text = receiverName
            tvAmount.text = CurrencyUtils.formatAmountWithCurrency(instr.amount)
            tvDesc.text = "$payerName pays $receiverName to settle up"

            loadProfileImage(instr.payerId, ivPayer)
            loadProfileImage(instr.receiverId, ivReceiver)

            container.addView(rowView)
        }
    }

    private fun loadProfileImage(userId: Long?, imageView: ImageView) {
        if (userId == null) return
        val userIdStr = userId.toString()
        val cachedUrl = PayorAdapter.sDownloadUrlCache[userIdStr]
        val url = cachedUrl ?: DeclareDatabase.profileImagesBucket.publicUrl("$userIdStr/$userIdStr.jpg")

        imageView.load(url) {
            transformations(CircleCropTransformation())
            placeholder(R.drawable.ic_profile_silhouette)
            error(R.drawable.ic_profile_silhouette)
            crossfade(true)
        }
    }

    private fun resolveParticipantName(userId: Long?): String {
        val tx = transaction ?: return "User"
        val index = tx.payorUserIds?.indexOfFirst { it?.toLongOrNull() == userId } ?: -1
        return if (index >= 0) tx.payorsList?.getOrNull(index) ?: "User" else "User"
    }

    inner class MemberBreakdownAdapter(
        private val tx: RecentTransaction,
        private val amounts: MutableList<Double>
    ) : RecyclerView.Adapter<MemberBreakdownAdapter.VH>() {
        
        private val userOwedMap = tx.rawSplitRows.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_member_breakdown, parent, false))

        override fun getItemCount() = tx.payorUserIds?.size ?: 0

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = tx.payorsList?.getOrNull(position) ?: "User"
            val userId = tx.payorUserIds?.getOrNull(position)
            val uidLong = userId?.toLongOrNull()
            
            val userRows = tx.rawPayorRows.filter { it.userId == uidLong }
            val totalInitial = userRows.sumOf { it.initialAmountPaid }
            val currentExcess = userRows.sumOf { it.excessAmount }
            val owed = userOwedMap[uidLong] ?: 0.0
            val paid = amounts.getOrElse(position) { 0.0 }
            
            val effectivePaid = if (userRows.isNotEmpty() && totalInitial >= owed - epsilon) {
                totalInitial
            } else {
                paid
            }
            
            val diff = effectivePaid - owed

            // Received payment calculation (for creditors)
            val initialExcess = kotlin.math.max(0.0, totalInitial - owed)
            val receivedPayment = kotlin.math.max(0.0, initialExcess - currentExcess)

            // Adjust balance for creditors by subtracting already received payments
            val adjustedDiff = if (diff > epsilon) diff - receivedPayment else diff

            holder.tvMemberName.text = name
            holder.tvAmountPaid.text = CurrencyUtils.formatAmountWithCurrency(effectivePaid)
            holder.tvFairShare.text = CurrencyUtils.formatAmountWithCurrency(owed)
            
            if (receivedPayment > epsilon) {
                holder.llReceivedPayment.visibility = View.VISIBLE
                holder.tvReceivedPayment.text = CurrencyUtils.formatAmountWithCurrency(receivedPayment)
            } else {
                holder.llReceivedPayment.visibility = View.GONE
            }
            
            val balanceAmount = if (adjustedDiff < -epsilon) -adjustedDiff else if (adjustedDiff > epsilon) adjustedDiff else 0.0
            val balanceStr = (if (adjustedDiff < -epsilon) "-" else if (adjustedDiff > epsilon) "+" else "") + 
                           CurrencyUtils.formatAmountWithCurrency(balanceAmount)
            holder.tvDifference.text = balanceStr
            
            val balanceColor = when {
                adjustedDiff > epsilon -> R.color.green
                adjustedDiff < -epsilon -> R.color.red
                else -> R.color.black
            }
            holder.tvDifference.setTextColor(ContextCompat.getColor(this@TransactionSettlementActivity, balanceColor))

            // Status Badge: Settled, Pending, Unpaid
            val (statusText, statusColor, bgColor) = when {
                effectivePaid >= owed - epsilon && owed > epsilon -> Triple("Settled", R.color.whitest, R.color.green)
                effectivePaid > epsilon -> Triple("Pending", R.color.whitest, R.color.yellow)
                else -> Triple("Unpaid", R.color.whitest, R.color.red)
            }
            
            holder.tvStatusBadge.text = statusText
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(this@TransactionSettlementActivity, statusColor))
            holder.tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(this@TransactionSettlementActivity, bgColor)

            // Initials / Avatar
            holder.tvInitials.text = name.take(2).uppercase()
            
            val user = uidLong?.let { userProfilesMap[it] }
            val hasExplicitImage = !user?.profileImageUrl.isNullOrBlank()

            if (hasExplicitImage) {
                // Priority: If user has an explicit profile image URL, hide initials immediately
                holder.tvInitials.visibility = View.GONE
                holder.ivAvatar.visibility = View.VISIBLE
                holder.ivAvatar.load(user?.profileImageUrl) {
                    transformations(CircleCropTransformation())
                    listener(onError = { _, _ ->
                        // Fallback to initials if image load fails
                        holder.tvInitials.visibility = View.VISIBLE
                        holder.ivAvatar.visibility = View.GONE
                    })
                }
            } else {
                // No explicit image URL, show initials and try fallback bucket URL
                holder.tvInitials.visibility = View.VISIBLE
                holder.ivAvatar.visibility = View.GONE

                val bucketUrl = if (userId != null) DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg") else null
                if (bucketUrl != null) {
                    holder.ivAvatar.load(bucketUrl) {
                        transformations(CircleCropTransformation())
                        listener(onSuccess = { _, _ ->
                            holder.tvInitials.visibility = View.GONE
                            holder.ivAvatar.visibility = View.VISIBLE
                        })
                    }
                }
            }
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvInitials: TextView = view.findViewById(R.id.tvInitials)
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
            val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
            val tvAmountPaid: TextView = view.findViewById(R.id.tvAmountPaid)
            val tvFairShare: TextView = view.findViewById(R.id.tvFairShare)
            val llReceivedPayment: View = view.findViewById(R.id.llReceivedPayment)
            val tvReceivedPayment: TextView = view.findViewById(R.id.tvReceivedPayment)
            val tvDifference: TextView = view.findViewById(R.id.tvDifference)
        }
    }

    private data class ParticipantBalance(
        val userId: Long,
        val name: String,
        val paid: Double,
        val owed: Double,
        val credit: Double,
        val deficit: Double
    )

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
}
