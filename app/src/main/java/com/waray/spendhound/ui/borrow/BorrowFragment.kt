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
import androidx.recyclerview.widget.RecyclerView
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
import kotlin.math.abs
import kotlin.math.max
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

    var selectedMonth: String? = null
    private var selectedStatusTab = "All"
    private var owedDebtClicked = false
    var currentNickname: String? = ""
    private var mAuth: Auth? = null

    private var globalLoadingOverlay: View? = null
    private val selectedCalendar: Calendar = Calendar.getInstance()

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var pendingLoads = 0

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
    }

    private fun setupDatePicker() {
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
        val sdf = SimpleDateFormat("MMMM-yyyy", Locale.ENGLISH)
        return sdf.format(cal.time)
    }

    private fun updateDatePickerButtonText() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
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
                })
            } else {
                mainActivity.getOwedListMonthly(selectedMonth ?: "", selectedStatusTab, object : MainActivity.OwedNumCallback {
                    override fun onOwedNumReceived(owedNum: Int) {
                        OwedSize(owedNum)
                    }
                })
            }
        } else {
            if (selectedMonth == "All") {
                mainActivity.getDebtList(selectedStatusTab, object : MainActivity.DebtNumCallback {
                    override fun onDebtNumReceived(debtNum: Int) {
                        DebtSize(debtNum)
                    }
                })
            } else {
                mainActivity.getDebtListMonthly(selectedMonth ?: "", selectedStatusTab, object : MainActivity.DebtNumCallback {
                    override fun onDebtNumReceived(debtNum: Int) {
                        DebtSize(debtNum)
                    }
                })
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
        pendingLoads++
        loadingOverlay?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        pendingLoads = max(0, pendingLoads - 1)
        if (pendingLoads == 0) {
            loadingOverlay?.visibility = View.GONE
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
