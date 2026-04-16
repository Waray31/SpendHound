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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import com.waray.spendhound.utils.RefreshCooldownManager
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

    private var globalLoadingOverlay: View? = null

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var pendingLoads = 0
    private var isTabClickEnabled = true
    private var loadingManager: LoadingManager? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
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

        getCurrentNickname()
        setupViews()
        setupDateRangeSpinner()
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

        loadingManager = LoadingManager(loadingOverlay, viewLifecycleOwner.lifecycle) { isLoading ->
            (activity as? MainActivity)?.navView?.menu?.findItem(R.id.navigation_borrow)?.isEnabled = !isLoading
            isTabClickEnabled = !isLoading
        }
    }

    private var customDateActive = false

    private fun setupDateRangeSpinner() {
        val options = mutableListOf<String?>("This Month", "Last Month", "All", "Custom Date")
        val spinnerAdapter = com.waray.spendhound.SpinnerItemMonths(requireContext(), options)
        dateRangeSpinner?.adapter = spinnerAdapter
        setThisMonth()
        updateCurrentMonthText()

        currentMonthTextView?.setOnClickListener {
            if (customDateActive) showDateRangePickerDialog()
        }

        dateRangeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { customDateActive = false; setThisMonth(); updateCurrentMonthText(); applyFilters() }
                    1 -> { customDateActive = false; setLastMonth(); updateCurrentMonthText(); applyFilters() }
                    2 -> { customDateActive = false; setAllTime(); updateCurrentMonthText(); applyFilters() }
                    3 -> showDateRangePickerDialog()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

    }

    private fun setThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setLastMonth() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setAllTime() {
        startDate = 0L
        endDate = Long.MAX_VALUE
    }

    private fun showDateRangePickerDialog() {
        val safeStart = if (startDate == 0L || startDate == Long.MAX_VALUE)
            Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        else startDate
        val safeEnd = if (endDate == Long.MAX_VALUE) Calendar.getInstance().timeInMillis else endDate

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setSelection(Pair(safeStart, safeEnd))
            .build()
        picker.show(childFragmentManager, "DATE_RANGE_PICKER")
        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection.first ?: safeStart
            val selectedEnd = selection.second ?: selection.first ?: safeEnd
            endDate = selectedEnd + 86400000 - 1
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            val label = "${sdf.format(startDate)} - ${sdf.format(selectedEnd)}"
            customDateActive = true
            updateCurrentMonthText(customLabel = label)
            applyFilters()
        }
        picker.addOnCancelListener {
            if (!customDateActive) dateRangeSpinner?.setSelection(0)
        }
    }

    private fun updateCurrentMonthText(customLabel: String? = null) {
        val text = customLabel ?: when {
            startDate == 0L && endDate == Long.MAX_VALUE -> "All Time"
            else -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val start = sdf.format(startDate)
                val end = sdf.format(endDate)
                if (start == end) start else "$start - $end"
            }
        }
        currentMonthTextView?.text = text
    }

    private fun setupStatusTabs() {
        allTabTV?.let { setStatusTabSelected(it) }

        allTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "All"
            allTabTV?.let { setStatusTabSelected(it) }
            applyFilters()
        }

        paidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Paid"
            paidTabTV?.let { setStatusTabSelected(it) }
            applyFilters()
        }

        unpaidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Unpaid"
            unpaidTabTV?.let { setStatusTabSelected(it) }
            applyFilters()
        }

        pendingTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
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

    internal fun applyFilters() {
        val mainActivity = activity as? MainActivity ?: return
        showLoading()

        if (owedDebtClicked) {
             fetchOwedInRange(startDate, endDate, selectedStatusTab)
        } else {
             fetchDebtInRange(startDate, endDate, selectedStatusTab)
        }
    }

    private fun fetchOwedInRange(start: Long, end: Long, status: String) {
        val mainActivity = activity as? MainActivity ?: return
        // Since getOwedListMonthly uses monthYear string, we'll need to adapt it or implement a range fetch.
        // For simplicity, keeping the current architecture but filtering by timestamp locally if needed.
        // However, to keep it consistent with the request, let's use the range to filter.
        
        uiScope.launch {
            try {
                val currentUserIdLong = (activity as? MainActivity)?.currentNickname 
                // Using existing MainActivity logic but filtering by range
                mainActivity.getOwedList(status, object : MainActivity.OwedNumCallback {
                    override fun onOwedNumReceived(owedNum: Int) {
                        val filteredList = mainActivity.owedList.filter {
                            val timestamp = parseDateToLong(it.date)
                            timestamp in start..end
                        }
                        mainActivity.owedList = filteredList
                        OwedSize(filteredList.size)
                    }
                })
            } catch (e: Exception) {
                hideLoading()
            }
        }
    }

    private fun fetchDebtInRange(start: Long, end: Long, status: String) {
        val mainActivity = activity as? MainActivity ?: return
        uiScope.launch {
            try {
                mainActivity.getDebtList(status, object : MainActivity.DebtNumCallback {
                    override fun onDebtNumReceived(debtNum: Int) {
                        val filteredList = mainActivity.debtList.filter {
                            val timestamp = parseDateToLong(it.date)
                            timestamp in start..end
                        }
                        mainActivity.debtList = filteredList
                        DebtSize(filteredList.size)
                    }
                })
            } catch (e: Exception) {
                hideLoading()
            }
        }
    }

    private fun parseDateToLong(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
             try {
                val sdf2 = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault())
                sdf2.parse(dateStr)?.time ?: 0L
             } catch (e2: Exception) {
                 0L
             }
        }
    }

    private var lenderActionListener: OwedTransactionAdapter.OnLenderActionListener? = null
        get() = field ?: object : OwedTransactionAdapter.OnLenderActionListener {
            override fun onNotYetClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_not_yet_title), getString(R.string.confirm_not_yet_message), R.color.grey) {
                    updateTransactionStatus(transaction?.borrowId, "Unpaid", null)
                }
            }
            override fun onReceivedClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_received_title), getString(R.string.confirm_received_message), R.color.green) {
                    updateTransactionStatus(transaction?.borrowId, "Paid", null)
                }
            }
            override fun onDeclineClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_decline_title), getString(R.string.confirm_decline_message), R.color.red) {
                    updateTransactionStatus(transaction?.borrowId, "Declined", null)
                }
            }
            override fun onApprovedClicked(transaction: OwedTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_approve_title), getString(R.string.confirm_approve_message), R.color.green) {
                    updateTransactionStatus(transaction?.borrowId, "Unpaid", null)
                }
            }
        }

    private var borrowerActionListener: DebtTransactionAdapter.OnBorrowerActionListener? = null
        get() = field ?: object : DebtTransactionAdapter.OnBorrowerActionListener {
            override fun onPayClicked(transaction: BorrowTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_pay_title), getString(R.string.confirm_pay_message), R.color.green) {
                    updateTransactionStatusWithPaymentDate(transaction?.borrowId, "Pending Payment", null)
                }
            }
            override fun onRemoveClicked(transaction: BorrowTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_remove_title), getString(R.string.confirm_remove_message), R.color.red) {
                    updateTransactionStatus(transaction?.borrowId, "Removed", null)
                }
            }
            override fun onTryAgainClicked(transaction: BorrowTransaction?, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_try_again_title), getString(R.string.confirm_try_again_message), R.color.green) {
                    updateTransactionStatus(transaction?.borrowId, "For Lender Approval", null)
                }
            }
        }

    private fun handleOwedClick() {
        owedTV?.let { debtTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.VISIBLE
        debtRecyclerList?.visibility = View.GONE
        owedDebtClicked = true
        applyFilters()
    }

    private fun handleDebtClick() {
        debtTV?.let { owedTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.GONE
        debtRecyclerList?.visibility = View.VISIBLE
        owedDebtClicked = false
        applyFilters()
    }

    private fun setTabColors(activeTab: TextView, inactiveTab: TextView) {
        activeTab.setBackgroundResource(R.drawable.top_round_border)
        inactiveTab.setBackgroundResource(R.drawable.button_background_invisible)
        activeTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
        inactiveTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
    }

    fun getCurrentNickname() {
        val currentUserId = mAuth?.currentUserOrNull()?.id ?: return
        showLoading()
        uiScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("username")) {
                        filter {
                            eq("user_id", currentUserId.toLongOrNull() ?: 0L)
                        }
                    }.decodeSingleOrNull<User>()
                }
                currentNickname = user?.username
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error getting nickname: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    fun OwedSize(owedNum: Int) {
        noOwedTextView?.visibility = if (owedNum == 0) View.VISIBLE else View.GONE
        owedRecyclerList?.visibility = if (owedNum == 0) View.GONE else View.VISIBLE
        noDebtTextView?.visibility = View.GONE
        
        val mainActivity = activity as? MainActivity
        if (owedNum > 0 && mainActivity != null) {
            owedRecyclerList?.adapter = OwedTransactionAdapter(mainActivity.owedList, lenderActionListener)
        }
        hideLoading()
    }

    fun DebtSize(debtNum: Int) {
        noDebtTextView?.visibility = if (debtNum == 0) View.VISIBLE else View.GONE
        debtRecyclerList?.visibility = if (debtNum == 0) View.GONE else View.VISIBLE
        noOwedTextView?.visibility = View.GONE
        
        val mainActivity = activity as? MainActivity
        if (debtNum > 0 && mainActivity != null) {
            debtRecyclerList?.adapter = DebtTransactionAdapter(mainActivity.debtList, borrowerActionListener)
        }
        hideLoading()
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

    private fun getStatusInt(statusStr: String?): Int {
        return when (statusStr) {
            "For Lender Approval" -> 1
            "Pending Payment" -> 2
            "Paid" -> 3
            "Declined" -> 4
            "Payment Denied" -> 5
            "Removed" -> 6
            "Paid Partially" -> 7
            else -> 0
        }
    }

    fun updateTransactionStatus(borrowId: String?, newStatus: String?, onSuccess: Runnable?) {
        if (borrowId == null) return
        showLoading()
        uiScope.launch {
            try {
                val statusInt = getStatusInt(newStatus)
                val currentBorrow = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter {
                            eq("id", borrowId.toLongOrNull() ?: 0L)
                        }
                    }.decodeSingleOrNull<BorrowNowTransaction>()
                }
                if (statusInt == 3 && currentBorrow != null && currentBorrow.statusInt != 3) {
                    val amount = currentBorrow.borrowedAmount ?: 0.0
                    currentBorrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                    currentBorrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                }
                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update({
                        set("status", statusInt)
                    }) {
                        filter {
                            eq("id", borrowId.toLongOrNull() ?: 0L)
                        }
                    }
                }
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                applyFilters()
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error updating transaction status: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    fun updateTransactionStatusWithPaymentDate(borrowId: String?, newStatus: String?, onSuccess: Runnable?) {
        if (borrowId == null) return
        val paymentSentDate = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val dateStr = sdf.format(java.util.Date(paymentSentDate))

        showLoading()
        uiScope.launch {
            try {
                val statusInt = getStatusInt(newStatus)
                val currentBorrow = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter {
                            eq("id", borrowId.toLongOrNull() ?: 0L)
                        }
                    }.decodeSingleOrNull<BorrowNowTransaction>()
                }
                if (statusInt == 3 && currentBorrow != null && currentBorrow.statusInt != 3) {
                    val amount = currentBorrow.borrowedAmount ?: 0.0
                    currentBorrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                    currentBorrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                }
                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update({
                        set("status", statusInt)
                        set("payment_sent_date", dateStr)
                    }) {
                        filter {
                            eq("id", borrowId.toLongOrNull() ?: 0L)
                        }
                    }
                }
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                applyFilters()
            } catch (e: Exception) {
                Log.e("BorrowFragment", "Error updating transaction status with payment date: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    fun showToast(message: String?) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    private fun showLoading() {
        loadingManager?.showLoading()
        isTabClickEnabled = false
    }

    private fun hideLoading() {
        loadingManager?.hideLoading()
        if (pendingLoads == 0) {
            isTabClickEnabled = true
        }
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
