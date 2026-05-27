package com.waray.spendhound.ui.borrow

import android.annotation.SuppressLint
import android.app.Dialog
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.util.Pair
import com.waray.spendhound.BalanceHelper
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BorrowTransaction
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.DebtTransactionAdapter
import com.waray.spendhound.MainActivity
import com.waray.spendhound.OwedTransaction
import com.waray.spendhound.OwedTransactionAdapter
import com.waray.spendhound.R
import com.waray.spendhound.User
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import com.waray.spendhound.utils.LoadingManager

class BorrowFragment : Fragment() {
    private var dateRangeSpinner: android.widget.Spinner? = null
    private var currentMonthTextView: TextView? = null
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

    private var startDate: Long = 0L
    private var endDate: Long = 0L

    private var selectedStatusTab = "All"
    private var owedDebtClicked = true
    var currentNickname: String? = ""
    private var mAuth: Auth? = null
    private var currentUserNumericId: Long? = null

    private var isTabClickEnabled = true
    private var loadingManager: LoadingManager? = null

    private var fullOwedList: List<OwedTransaction> = emptyList()
    private var fullDebtList: List<BorrowTransaction> = emptyList()

    private var customDateActive = false

    private val viewModel: BorrowViewModel by viewModels()

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: View = inflater.inflate(R.layout.fragment_borrow, container, false)

        mAuth = DeclareDatabase.auth

        dateRangeSpinner = view.findViewById(R.id.dateRangeSpinner)
        currentMonthTextView = view.findViewById(R.id.currentMonthTextView)
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
        owedRecyclerList?.layoutManager = LinearLayoutManager(context)
        debtRecyclerList?.layoutManager = LinearLayoutManager(context)
        loadingManager = LoadingManager(loadingOverlay, viewLifecycleOwner.lifecycle) { isLoading ->
            (activity as? MainActivity)?.navView?.menu?.findItem(R.id.navigation_borrow)?.isEnabled = !isLoading
            isTabClickEnabled = !isLoading
        }

        setupDateRangeSpinner()
        setupStatusTabs()
        setupClickListeners()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resolveUserThenLoad()
        observeViewModel()
    }

    private fun resolveUserThenLoad() {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        if (currentUserNumericId != null) { viewModel.load(currentUserNumericId!!); return }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("user_id", "username")) {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                withContext(Dispatchers.Main) {
                    currentUserNumericId = user?.id
                    currentNickname = user?.username
                    user?.id?.let { viewModel.load(it) }
                }
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error resolving user: ${e.message}")
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.borrowData.collectLatest { data ->
                data ?: return@collectLatest
                fullOwedList = data.owedList
                fullDebtList = data.debtList
                applyLocalFilters()
                loadingManager?.hideLoading()
                isTabClickEnabled = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only re-fetches from network if stale (5 min)
        currentUserNumericId?.let { viewModel.load(it) }
            ?: resolveUserThenLoad()
    }

    internal fun applyFilters(forceSkeleton: Boolean = false) {
        val userId = currentUserNumericId ?: return
        if (forceSkeleton || (if (owedDebtClicked) fullOwedList.isEmpty() else fullDebtList.isEmpty())) {
            loadingManager?.showLoading()
        }
        viewModel.invalidate(userId)
    }

    private fun applyLocalFilters() {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        if (owedDebtClicked) {
            val filtered = fullOwedList.filter { tx ->
                val ts = try { sdf.parse(tx.date ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                ts in startDate..endDate
            }.let { list ->
                if (selectedStatusTab == "All") list else list.filter { it.status.equals(selectedStatusTab, ignoreCase = true) }
            }
            showOwed(filtered)
        } else {
            val filtered = fullDebtList.filter { tx ->
                val ts = try { sdf.parse(tx.date ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                ts in startDate..endDate
            }.let { list ->
                if (selectedStatusTab == "All") list else list.filter { it.status.equals(selectedStatusTab, ignoreCase = true) }
            }
            showDebt(filtered)
        }
    }

    private fun showOwed(list: List<OwedTransaction>) {
        noOwedTextView?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        owedRecyclerList?.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        noDebtTextView?.visibility = View.GONE
        if (list.isNotEmpty()) owedRecyclerList?.adapter = OwedTransactionAdapter(list, lenderActionListener)
    }

    private fun showDebt(list: List<BorrowTransaction>) {
        noDebtTextView?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        debtRecyclerList?.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        noOwedTextView?.visibility = View.GONE
        if (list.isNotEmpty()) debtRecyclerList?.adapter = DebtTransactionAdapter(list, borrowerActionListener)
    }

    private fun setupDateRangeSpinner() {
        val options = mutableListOf<String?>("This Month", "Last Month", "All", "Custom Date")
        val spinnerAdapter = com.waray.spendhound.SpinnerItemMonths(requireContext(), options)
        dateRangeSpinner?.adapter = spinnerAdapter
        setThisMonth(); updateCurrentMonthText()
        currentMonthTextView?.setOnClickListener { if (customDateActive) showDateRangePickerDialog() }
        dateRangeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { customDateActive = false; setThisMonth(); updateCurrentMonthText(); applyLocalFilters() }
                    1 -> { customDateActive = false; setLastMonth(); updateCurrentMonthText(); applyLocalFilters() }
                    2 -> { customDateActive = false; setAllTime(); updateCurrentMonthText(); applyLocalFilters() }
                    3 -> showDateRangePickerDialog()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setLastMonth() {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setAllTime() { startDate = 0L; endDate = Long.MAX_VALUE }

    private fun showDateRangePickerDialog() {
        val safeStart = if (startDate == 0L || startDate == Long.MAX_VALUE) Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis else startDate
        val safeEnd = if (endDate == Long.MAX_VALUE) Calendar.getInstance().timeInMillis else endDate
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select Date Range").setSelection(Pair(safeStart, safeEnd)).build()
        picker.show(childFragmentManager, "DATE_RANGE_PICKER")
        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection.first ?: safeStart
            val selectedEnd = selection.second ?: selection.first ?: safeEnd
            endDate = selectedEnd + 86400000 - 1
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            customDateActive = true
            updateCurrentMonthText(customLabel = "${sdf.format(startDate)} - ${sdf.format(selectedEnd)}")
            applyLocalFilters()
        }
        picker.addOnCancelListener { if (!customDateActive) dateRangeSpinner?.setSelection(0) }
    }

    private fun updateCurrentMonthText(customLabel: String? = null) {
        val text = customLabel ?: when {
            startDate == 0L && endDate == Long.MAX_VALUE -> "All Time"
            else -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val start = sdf.format(startDate); val end = sdf.format(endDate)
                if (start == end) start else "$start - $end"
            }
        }
        currentMonthTextView?.text = text
    }

    private fun setupStatusTabs() {
        allTabTV?.let { setStatusTabSelected(it) }
        allTabTV?.setOnClickListener { if (!isTabClickEnabled) return@setOnClickListener; selectedStatusTab = "All"; allTabTV?.let { setStatusTabSelected(it) }; applyLocalFilters() }
        paidTabTV?.setOnClickListener { if (!isTabClickEnabled) return@setOnClickListener; selectedStatusTab = "Paid"; paidTabTV?.let { setStatusTabSelected(it) }; applyLocalFilters() }
        unpaidTabTV?.setOnClickListener { if (!isTabClickEnabled) return@setOnClickListener; selectedStatusTab = "Unpaid"; unpaidTabTV?.let { setStatusTabSelected(it) }; applyLocalFilters() }
        pendingTabTV?.setOnClickListener { if (!isTabClickEnabled) return@setOnClickListener; selectedStatusTab = "Pending"; pendingTabTV?.let { setStatusTabSelected(it) }; applyLocalFilters() }
    }

    private fun setStatusTabSelected(selectedTab: TextView) {
        listOf(allTabTV, paidTabTV, unpaidTabTV, pendingTabTV).forEach { 
            it?.setBackgroundResource(0)
            it?.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_unselected_text))
        }
        selectedTab.setBackgroundResource(R.drawable.spinner_border_grey)
        selectedTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_selected_text))
    }

    private fun setupClickListeners() {
        owedTV?.setOnClickListener { handleOwedClick() }
        debtTV?.setOnClickListener { handleDebtClick() }
    }

    private fun handleOwedClick() {
        owedTV?.let { debtTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.VISIBLE
        debtRecyclerList?.visibility = View.GONE
        owedDebtClicked = true
        applyLocalFilters()
    }

    private fun handleDebtClick() {
        debtTV?.let { owedTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.GONE
        debtRecyclerList?.visibility = View.VISIBLE
        owedDebtClicked = false
        applyLocalFilters()
    }

    private fun setTabColors(activeTab: TextView, inactiveTab: TextView) {
        activeTab.setBackgroundResource(R.drawable.top_round_border)
        inactiveTab.setBackgroundResource(R.drawable.button_background_invisible)
        activeTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_selected_text))
        inactiveTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
    }

    private val lenderActionListener: OwedTransactionAdapter.OnLenderActionListener = object : OwedTransactionAdapter.OnLenderActionListener {
        override fun onNotYetClicked(transaction: OwedTransaction?, position: Int) {
            showConfirmationDialog(getString(R.string.confirm_not_yet_title), getString(R.string.confirm_not_yet_message), R.color.grey) { updateTransactionStatus(transaction?.borrowId, "Unpaid", null) }
        }
        override fun onReceivedClicked(transaction: OwedTransaction?, position: Int) {
            showConfirmationDialog(getString(R.string.confirm_received_title), getString(R.string.confirm_received_message), R.color.green) { updateTransactionStatus(transaction?.borrowId, "Paid", null) }
        }
        override fun onDeclineClicked(transaction: OwedTransaction?, position: Int) {
            showConfirmationDialog(getString(R.string.confirm_decline_title), getString(R.string.confirm_decline_message), R.color.red) { updateTransactionStatus(transaction?.borrowId, "Declined", null) }
        }
        override fun onApprovedClicked(transaction: OwedTransaction?, position: Int) {
            showConfirmationDialog(getString(R.string.confirm_approve_title), getString(R.string.confirm_approve_message), R.color.green) { updateTransactionStatus(transaction?.borrowId, "Unpaid", null) }
        }
    }

    private val borrowerActionListener: DebtTransactionAdapter.OnBorrowerActionListener = object : DebtTransactionAdapter.OnBorrowerActionListener {
        override fun onPayClicked(transaction: BorrowTransaction?, position: Int) {
            showConfirmationDialog(getString(R.string.confirm_pay_title), getString(R.string.confirm_pay_message), R.color.green) { updateTransactionStatusWithPaymentDate(transaction?.borrowId, "Pending Payment", null) }
        }
        override fun onRemoveClicked(transaction: BorrowTransaction?, position: Int) {
            showConfirmationDialog(getString(R.string.confirm_remove_title), getString(R.string.confirm_remove_message), R.color.red) { updateTransactionStatus(transaction?.borrowId, "Removed", null) }
        }
        override fun onTryAgainClicked(transaction: BorrowTransaction?, position: Int) {
            showConfirmationDialog(getString(R.string.confirm_try_again_title), getString(R.string.confirm_try_again_message), R.color.green) { updateTransactionStatus(transaction?.borrowId, "For Lender Approval", null) }
        }
    }

    fun showConfirmationDialog(title: String?, message: String?, confirmBtnColor: Int, onConfirm: Runnable) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_action)
        dialog.setCancelable(true)
        dialog.window?.let { it.setBackgroundDrawable(ColorDrawable(Color.WHITE)); it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        dialog.findViewById<TextView>(R.id.dialogTitle).text = title
        dialog.findViewById<TextView>(R.id.dialogMessage).text = message
        dialog.findViewById<Button>(R.id.dialogConfirmBtn).backgroundTintList = ContextCompat.getColorStateList(requireContext(), confirmBtnColor)
        dialog.findViewById<Button>(R.id.dialogCancelBtn).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.dialogConfirmBtn).setOnClickListener { dialog.dismiss(); onConfirm.run() }
        dialog.show()
    }

    private fun getStatusInt(statusStr: String?) = when (statusStr) {
        "For Lender Approval" -> 1; "Pending Payment" -> 2; "Paid" -> 3
        "Declined" -> 4; "Payment Denied" -> 5; "Removed" -> 6; "Paid Partially" -> 7; else -> 0
    }

    fun updateTransactionStatus(borrowId: String?, newStatus: String?, onSuccess: Runnable?) {
        if (borrowId == null) return
        val userId = currentUserNumericId ?: return
        loadingManager?.showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val statusInt = getStatusInt(newStatus)
                val currentBorrow = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select { filter { eq("id", borrowId.toLongOrNull() ?: 0L) } }.decodeSingleOrNull<BorrowNowTransaction>()
                }
                if (statusInt == 3 && currentBorrow != null && currentBorrow.statusInt != 3) {
                    val amount = currentBorrow.borrowedAmount ?: 0.0
                    currentBorrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                    currentBorrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                }
                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update({ set("status", statusInt) }) { filter { eq("id", borrowId.toLongOrNull() ?: 0L) } }
                }
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                viewModel.invalidate(userId)
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error updating status: ${e.message}")
            } finally {
                loadingManager?.hideLoading()
            }
        }
    }

    fun updateTransactionStatusWithPaymentDate(borrowId: String?, newStatus: String?, onSuccess: Runnable?) {
        if (borrowId == null) return
        val userId = currentUserNumericId ?: return
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val dateStr = sdf.format(java.util.Date())
        loadingManager?.showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val statusInt = getStatusInt(newStatus)
                val currentBorrow = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select { filter { eq("id", borrowId.toLongOrNull() ?: 0L) } }.decodeSingleOrNull<BorrowNowTransaction>()
                }
                if (statusInt == 3 && currentBorrow != null && currentBorrow.statusInt != 3) {
                    val amount = currentBorrow.borrowedAmount ?: 0.0
                    currentBorrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                    currentBorrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                }
                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update({ set("status", statusInt); set("payment_sent_date", dateStr) }) { filter { eq("id", borrowId.toLongOrNull() ?: 0L) } }
                }
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                viewModel.invalidate(userId)
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error updating status with payment date: ${e.message}")
            } finally {
                loadingManager?.hideLoading()
            }
        }
    }

    fun showToast(message: String?) { context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() } }
}
