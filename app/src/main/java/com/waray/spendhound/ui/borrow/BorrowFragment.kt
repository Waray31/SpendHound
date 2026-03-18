package com.waray.spendhound.ui.borrow

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.waray.spendhound.BalanceHelper
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BorrowTransaction
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.DebtTransactionAdapter
import com.waray.spendhound.LenderAdapter
import com.waray.spendhound.MainActivity
import com.waray.spendhound.OwedTransaction
import com.waray.spendhound.OwedTransactionAdapter
import com.waray.spendhound.R
import com.waray.spendhound.User
import com.waray.spendhound.UserBalance
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Collections
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

class BorrowFragment : Fragment() {
    private var datePickerButton: Button? = null
    var owedTV: TextView? = null
    var debtTV: TextView? = null
    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var pendingTabTV: TextView? = null
    private var noOwedTextView: View? = null
    private var noDebtTextView: View? = null
    private var debtRecyclerList: RecyclerView? = null
    private var owedRecyclerList: RecyclerView? = null
    private var loadingOverlay: View? = null

    var debtSortedMonths: MutableList<String?>? = null
    var owedSortedMonths: MutableList<String?>? = null
    var selectedMonth: String? = null
    private var selectedStatusTab = "All"
    private var owedDebtClicked = false
    var currentNickname: String? = ""
    private var mAuth: Auth? = null

    private var globalLoadingOverlay: View? = null
    private var selectedLenderName = ""
    private val selectedCalendar: Calendar = Calendar.getInstance()

    private val uiScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_borrow, container, false)
        
        mAuth = DeclareDatabase.auth
        
        datePickerButton = view.findViewById(R.id.datePickerButton)
        owedTV = view.findViewById(R.id.owedTV)
        debtTV = view.findViewById(R.id.debtTV)
        owedRecyclerList = view.findViewById(R.id.owedRecyclerList)
        noOwedTextView = view.findViewById(R.id.noOwedTextView)
        noDebtTextView = view.findViewById(R.id.noDebtTextView)
        debtRecyclerList = view.findViewById(R.id.debtRecyclerList)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        allTabTV = view.findViewById(R.id.allTabTV)
        paidTabTV = view.findViewById(R.id.paidTabTV)
        unpaidTabTV = view.findViewById(R.id.unpaidTabTV)
        pendingTabTV = view.findViewById(R.id.pendingTabTV)

        owedDebtClicked = true

        getCurrentNickname()
        setupViews()
        setupDatePicker()
        setupStatusTabs()
        setupClickListeners()

        val activity = getActivity() as? AppCompatActivity
        activity?.supportActionBar?.hide()

        globalLoadingOverlay = getActivity()?.findViewById(R.id.loadingOverlay)

        return view
    }

    private fun setupViews() {
        owedRecyclerList?.layoutManager = LinearLayoutManager(context)
        debtRecyclerList?.layoutManager = LinearLayoutManager(context)
        OwedMonthlyFilterList()
    }

    private fun setupDatePicker() {
        // Initialize selectedMonth with current date
        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        selectedMonth = formatMonthYear(year, month)
        
        updateDatePickerButtonText()
        datePickerButton?.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun showDatePickerDialog() {
        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
            val monthYear = formatMonthYear(selectedYear, selectedMonth)
            this.selectedMonth = monthYear
            updateDatePickerButtonText()
            applyFilters()
        }, year, month, day).show()
    }

    private fun formatMonthYear(year: Int, month: Int): String {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return sdf.format(cal.time)
    }

    private fun updateDatePickerButtonText() {
        val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val dateText = sdf.format(selectedCalendar.time)
        datePickerButton?.text = dateText
    }

    private fun setupStatusTabs() {
        allTabTV?.let { setStatusTabSelected(it) }

        allTabTV?.setOnClickListener {
            selectedStatusTab = "All"
            allTabTV?.let { setStatusTabSelected(it) }
            applyFilters()
        }

        paidTabTV?.setOnClickListener {
            selectedStatusTab = "Paid"
            paidTabTV?.let { setStatusTabSelected(it) }
            applyFilters()
        }

        unpaidTabTV?.setOnClickListener {
            selectedStatusTab = "Unpaid"
            unpaidTabTV?.let { setStatusTabSelected(it) }
            applyFilters()
        }

        pendingTabTV?.setOnClickListener {
            selectedStatusTab = "Pending"
            pendingTabTV?.let { setStatusTabSelected(it) }
            applyFilters()
        }
    }

    private fun setStatusTabSelected(selectedTab: TextView) {
        allTabTV?.setBackgroundResource(0)
        paidTabTV?.setBackgroundResource(0)
        unpaidTabTV?.setBackgroundResource(0)
        pendingTabTV?.setBackgroundResource(0)
        selectedTab.setBackgroundResource(R.drawable.bg_status_tab_selected)
    }

    private fun setupClickListeners() {
        owedTV?.setOnClickListener { handleOwedClick() }
        debtTV?.setOnClickListener { handleDebtClick() }
    }

    private fun applyFilters() {
        val mainActivity = activity as? MainActivity ?: return
        showLoading()

        if (owedDebtClicked) {
            if (selectedMonth == "All") {
                mainActivity.getOwedList(selectedStatusTab, object : MainActivity.OwedNumCallback {
                    override fun onOwedNumReceived(owedNum: Int) {
                        OwedSize(owedNum)
                    }
                }, lenderActionListener)
            } else {
                mainActivity.getOwedListMonthly(selectedMonth, selectedStatusTab, object : MainActivity.OwedNumCallback {
                    override fun onOwedNumReceived(owedNum: Int) {
                        OwedSize(owedNum)
                    }
                }, lenderActionListener)
            }
        } else {
            if (selectedMonth == "All") {
                mainActivity.getDebtList(selectedStatusTab, object : MainActivity.DebtNumCallback {
                    override fun onDebtNumReceived(debtNum: Int) {
                        DebtSize(debtNum)
                    }
                }, borrowerActionListener)
            } else {
                mainActivity.getDebtListMonthly(selectedMonth, selectedStatusTab, object : MainActivity.DebtNumCallback {
                    override fun onDebtNumReceived(debtNum: Int) {
                        DebtSize(debtNum)
                    }
                }, borrowerActionListener)
            }
        }
    }

    private var lenderActionListener: OwedTransactionAdapter.OnLenderActionListener? = null
        get() = field ?: object : OwedTransactionAdapter.OnLenderActionListener {
            override fun onNotYetClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_not_yet_title), getString(R.string.confirm_not_yet_message), R.color.grey) {
                    updateTransactionStatus(transaction?.getBorrowId(), transaction?.getMonthYear(), transaction?.getDay(), "Unpaid", null)
                }
            }
            override fun onReceivedClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_received_title), getString(R.string.confirm_received_message), R.color.green) {
                    updateTransactionStatus(transaction?.getBorrowId(), transaction?.getMonthYear(), transaction?.getDay(), "Paid", null)
                }
            }
            override fun onDeclineClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_decline_title), getString(R.string.confirm_decline_message), R.color.red) {
                    updateTransactionStatus(transaction?.getBorrowId(), transaction?.getMonthYear(), transaction?.getDay(), "Declined", null)
                }
            }
            override fun onApprovedClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_approve_title), getString(R.string.confirm_approve_message), R.color.green) {
                    updateTransactionStatus(transaction?.getBorrowId(), transaction?.getMonthYear(), transaction?.getDay(), "Unpaid", null)
                }
            }
        }

    private var borrowerActionListener: DebtTransactionAdapter.OnBorrowerActionListener? = null
        get() = field ?: object : DebtTransactionAdapter.OnBorrowerActionListener {
            override fun onPayClicked(transaction: BorrowTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_pay_title), getString(R.string.confirm_pay_message), R.color.green) {
                    updateTransactionStatusWithPaymentDate(transaction?.getBorrowId(), transaction?.getMonthYear(), transaction?.getDay(), "Pending Payment", null)
                }
            }
            override fun onRemoveClicked(transaction: BorrowTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_remove_title), getString(R.string.confirm_remove_message), R.color.red) {
                    updateTransactionStatus(transaction?.getBorrowId(), transaction?.getMonthYear(), transaction?.getDay(), "Removed", null)
                }
            }
            override fun onTryAgainClicked(transaction: BorrowTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_try_again_title), getString(R.string.confirm_try_again_message), R.color.green) {
                    updateTransactionStatus(transaction?.getBorrowId(), transaction?.getMonthYear(), transaction?.getDay(), "For Lender Approval", null)
                }
            }
        }

    private fun handleOwedClick() {
        owedTV?.let { debtTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.VISIBLE
        debtRecyclerList?.visibility = View.GONE
        owedDebtClicked = true
        OwedMonthlyFilterList()
    }

    private fun handleDebtClick() {
        debtTV?.let { owedTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.GONE
        debtRecyclerList?.visibility = View.VISIBLE
        owedDebtClicked = false
        DebtMonthlyFilterList()
    }

    private fun setTabColors(activeTab: TextView, inactiveTab: TextView) {
        activeTab.setBackgroundResource(R.drawable.top_round_border)
        inactiveTab.setBackgroundResource(R.drawable.button_background_invisible)
        activeTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
        inactiveTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
    }


    fun DebtMonthlyFilterList() {
        val currentUserId = mAuth?.currentUserOrNull()?.id
        uiScope.launch {
            val borrows = withContext(Dispatchers.IO) {
                DeclareDatabase.borrowsTable.select(columns = Columns.list("month_year", "borrower_id")) {
                    filter {
                        eq("borrower_id", currentUserId ?: "")
                    }
                }.decodeList<BorrowNowTransaction>()
            }
            debtSortedMonths = borrows.mapNotNull { it.month_year }.toSet().toMutableList()
            debtSortedMonths?.add(0, "All")
            Collections.sort(debtSortedMonths as MutableList<String>)
        }
    }

    fun OwedMonthlyFilterList() {
        val currentUserId = mAuth?.currentUserOrNull()?.id
        uiScope.launch {
            val borrows = withContext(Dispatchers.IO) {
                DeclareDatabase.borrowsTable.select(columns = Columns.list("month_year", "lender_id")) {
                    filter {
                        eq("lender_id", currentUserId ?: "")
                    }
                }.decodeList<BorrowNowTransaction>()
            }
            owedSortedMonths = borrows.mapNotNull { it.month_year }.toSet().toMutableList()
            owedSortedMonths?.add(0, "All")
            Collections.sort(owedSortedMonths as MutableList<String>)
        }
    }

    fun getCurrentNickname() {
        val currentUserId = mAuth?.currentUserOrNull()?.id ?: return
        uiScope.launch {
            val user = withContext(Dispatchers.IO) {
                DeclareDatabase.usersTable.select(Columns.list("username")) {
                    filter {
                        eq("id", currentUserId)
                    }
                }.decodeSingleOrNull<User>()
            }
            currentNickname = user?.username
        }
    }

    fun OwedSize(owedNum: Int) {
        hideLoading()
        noOwedTextView?.visibility = if (owedNum == 0) View.VISIBLE else View.GONE
        owedRecyclerList?.visibility = if (owedNum == 0) View.GONE else View.VISIBLE
        noDebtTextView?.visibility = View.GONE
    }

    fun DebtSize(debtNum: Int) {
        hideLoading()
        noDebtTextView?.visibility = if (debtNum == 0) View.VISIBLE else View.GONE
        debtRecyclerList?.visibility = if (debtNum == 0) View.GONE else View.VISIBLE
        noOwedTextView?.visibility = View.GONE
    }

    fun showConfirmationDialog(title: String?, message: String?, confirmBtnColor: Int, onConfirm: Runnable) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_action)
        dialog.setCancelable(true)
        dialog.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.WHITE))
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val dialogTitle: TextView = dialog.findViewById(R.id.dialogTitle)
        val dialogMessage: TextView = dialog.findViewById(R.id.dialogMessage)
        val cancelBtn = dialog.findViewById<Button>(R.id.dialogCancelBtn)
        val confirmBtn = dialog.findViewById<Button>(R.id.dialogConfirmBtn)

        dialogTitle.text = title
        dialogMessage.text = message
        confirmBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), confirmBtnColor)

        cancelBtn.setOnClickListener { dialog.dismiss() }
        confirmBtn.setOnClickListener {
            dialog.dismiss()
            onConfirm.run()
        }
        dialog.show()
    }

    fun updateTransactionStatus(borrowId: String?, monthYear: String?, day: String?, newStatus: String?, onSuccess: Runnable?) {
        if (borrowId == null) return
        uiScope.launch {
            try {
                val currentBorrow = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter {
                            eq("id", borrowId)
                        }
                    }.decodeSingleOrNull<BorrowNowTransaction>()
                }
                if (newStatus == "Paid" && currentBorrow != null && currentBorrow.getStatus() != "Paid") {
                    val amount = currentBorrow.getBorrowedAmount()?.toInt() ?: 0
                    currentBorrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                    currentBorrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                }
                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update(mapOf("status" to newStatus)) {
                        filter {
                            eq("id", borrowId)
                        }
                    }
                }
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                applyFilters()
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error updating transaction status: ${e.message}")
            }
        }
    }

    fun updateTransactionStatusWithPaymentDate(borrowId: String?, monthYear: String?, day: String?, newStatus: String?, onSuccess: Runnable?) {
        if (borrowId == null) return
        val paymentSentDate = System.currentTimeMillis()
        uiScope.launch {
            try {
                val currentBorrow = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter {
                            eq("id", borrowId)
                        }
                    }.decodeSingleOrNull<BorrowNowTransaction>()
                }
                if (newStatus == "Paid" && currentBorrow != null && currentBorrow.getStatus() != "Paid") {
                    val amount = currentBorrow.getBorrowedAmount()?.toInt() ?: 0
                    currentBorrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                    currentBorrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                }
                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update(mapOf("status" to newStatus, "paymentSentDate" to paymentSentDate)) {
                        filter {
                            eq("id", borrowId)
                        }
                    }
                }
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                applyFilters()
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error updating transaction status with payment date: ${e.message}")
            }
        }
    }

    private fun setupLenderRecyclerView(recyclerView: RecyclerView, dialogProgressBar: View) {
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.layoutManager = layoutManager
        val lenders = ArrayList<User?>()
        val adapter = LenderAdapter(lenders)
        recyclerView.adapter = adapter
        val snapHelper: SnapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateLayoutEffect(recyclerView)
            }
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(layoutManager)
                    centerView?.let {
                        val pos = layoutManager.getPosition(it)
                        adapter.getLenderAt(pos)?.let { lender -> selectedLenderName = lender.getUsername() ?: "" }
                    }
                }
            }
        })
        loadLenders(adapter, lenders, recyclerView, dialogProgressBar)
    }

    private fun updateLayoutEffect(recyclerView: RecyclerView) {
        val midpoint = recyclerView.width / 2f
        val d0 = 0f
        val d1 = 0.9f * midpoint
        val s0 = 1.6f
        val s1 = 1.0f
        val a0 = 1.0f
        val a1 = 0.5f

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            recyclerView.layoutManager?.let { lm ->
                val childMidpoint = (lm.getDecoratedRight(child) + lm.getDecoratedLeft(child)) / 2f
                val d = min(d1, abs(midpoint - childMidpoint))
                val scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0)
                val alpha = a0 + (a1 - a0) * (d - d0) / (d1 - d0)
                child.scaleX = scale
                child.scaleY = scale
                child.alpha = alpha
            }
        }
    }

    private fun loadLenders(adapter: LenderAdapter, lenders: MutableList<User?>, recyclerView: RecyclerView, dialogProgressBar: View) {
        uiScope.launch {
            try {
                val users = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select().decodeList<User>()
                }
                activity?.runOnUiThread {
                    lenders.clear()
                    lenders.add(User("", "", "", UserBalance()))
                    lenders.add(User("", "", "", UserBalance()))
                    for (user in users) {
                        if (user != null && user.getUsername() != null && user.getUsername() != currentNickname) {
                            lenders.add(user)
                        }
                    }
                    lenders.add(User("", "", "", UserBalance()))
                    lenders.add(User("", "", "", UserBalance()))
                    adapter.notifyDataSetChanged()
                    adapter.preloadAllImages(context) {
                        activity?.runOnUiThread {
                            dialogProgressBar.visibility = View.GONE
                            if (lenders.size > 2) {
                                recyclerView.scrollToPosition(2)
                                recyclerView.post {
                                    adapter.getLenderAt(2)?.let { selectedLenderName = it.getUsername() ?: "" }
                                    updateLayoutEffect(recyclerView)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error loading lenders: ${e.message}")
                activity?.runOnUiThread {
                    dialogProgressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun addBorrowTransaction(
        lender: String, borrowedAmountStr: String, currentDate: String?,
        dialog: Dialog, dialogProgressBar: View, borrowBtn: Button, cancelBtn: Button
    ) {
        val timestamp = System.currentTimeMillis()
        val borrowId = UUID.randomUUID().toString()
        val borrowedAmount = borrowedAmountStr.toDoubleOrNull() ?: 0.0

        val currentUserId = mAuth?.currentUserOrNull()?.id
        if (currentUserId != null) {
            getUserIDByName(lender) { lenderID ->
                if (lenderID == null) {
                    activity?.runOnUiThread {
                        showToast(getString(R.string.toast_borrow_failed))
                        dialogProgressBar.visibility = View.GONE
                        borrowBtn.isEnabled = true
                        cancelBtn.isEnabled = true
                        hideGlobalLoading()
                    }
                    return@getUserIDByName
                }
                val bnt = BorrowNowTransaction(borrowId, currentUserId, lenderID, currentNickname, currentDate?.toLongOrNull(), lender, borrowedAmount, "For Lender Approval", timestamp)
                uiScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            DeclareDatabase.borrowsTable.insert(bnt)
                        }
                        BalanceHelper.addBorrowerEntry(currentUserId, borrowId, null)
                        BalanceHelper.addLenderEntry(lenderID, borrowId, null)
                        val amount = borrowedAmountStr.toIntOrNull() ?: 0
                        BalanceHelper.updateTotaldebt(currentUserId, amount, null)
                        BalanceHelper.updateTotalreceivable(lenderID, amount, null)
                        activity?.runOnUiThread {
                            showToast(getString(R.string.toast_borrow_success))
                            dialog.dismiss()
                            applyFilters()
                        }
                    } catch (e: Exception) {
                        Log.e("BorrowFragment", "Error adding borrow transaction: ${e.message}")
                        activity?.runOnUiThread {
                            showToast(getString(R.string.toast_borrow_failed))
                            dialogProgressBar.visibility = View.GONE
                            borrowBtn.isEnabled = true
                            cancelBtn.isEnabled = true
                            hideGlobalLoading()
                        }
                    }
                }
            }
        }
    }

    private fun getUserIDByName(name: String, callback: (String?) -> Unit) {
        uiScope.launch {
            val user = withContext(Dispatchers.IO) {
                DeclareDatabase.usersTable.select(Columns.list("id")) {
                    filter {
                        eq("username", name)
                    }
                }.decodeSingleOrNull<User>()
            }
            callback(user?.id)
        }
    }

    fun showToast(message: String?) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    private fun showLoading() {
        loadingOverlay?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingOverlay?.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        applyFilters()
    }

    private fun showGlobalLoading() {
        globalLoadingOverlay?.visibility = View.VISIBLE
    }

    private fun hideGlobalLoading() {
        globalLoadingOverlay?.visibility = View.GONE
    }
}
