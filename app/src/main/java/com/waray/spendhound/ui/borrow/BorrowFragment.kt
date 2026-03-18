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
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.waray.spendhound.BalanceHelper
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BorrowTransaction
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.DebtNumCallback
import com.waray.spendhound.DebtTransactionAdapter
import com.waray.spendhound.LenderAdapter
import com.waray.spendhound.MainActivity
import com.waray.spendhound.OnBorrowerActionListener
import com.waray.spendhound.OnLenderActionListener
import com.waray.spendhound.OwedNumCallback
import com.waray.spendhound.OwedTransaction
import com.waray.spendhound.OwedTransactionAdapter
import com.waray.spendhound.R
import com.waray.spendhound.SpinnerItemMonths
import com.waray.spendhound.User
import com.waray.spendhound.UserBalance
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.Objects
import kotlin.math.abs
import kotlin.math.min

class BorrowFragment : Fragment() {
    private var monthYearSpinner: Spinner? = null
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

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_borrow, container, false)
        
        mAuth = DeclareDatabase.auth
        
        monthYearSpinner = view.findViewById(R.id.monthYearSpinner)
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
        setupSpinners()
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

    private fun setupSpinners() {
        monthYearSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>, selectedItemView: View?, position: Int, id: Long) {
                selectedMonth = parentView.getItemAtPosition(position) as? String
                applyFilters()
            }
            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
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
                mainActivity.getOwedList(selectedStatusTab, { owedNum -> OwedSize(owedNum) }, lenderActionListener)
            } else {
                mainActivity.getOwedListMonthly(selectedMonth, selectedStatusTab, { owedNum -> OwedSize(owedNum) }, lenderActionListener)
            }
        } else {
            if (selectedMonth == "All") {
                mainActivity.getDebtList(selectedStatusTab, { debtNum -> DebtSize(debtNum) }, borrowerActionListener)
            } else {
                mainActivity.getDebtListMonthly(selectedMonth, selectedStatusTab, { debtNum -> DebtSize(debtNum) }, borrowerActionListener)
            }
        }
    }

    private val lenderActionListener: OnLenderActionListener
        get() = object : OnLenderActionListener {
            override fun onNotYetClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_not_yet_title), getString(R.string.confirm_not_yet_message), R.color.grey) {
                    updateTransactionStatus(transaction.getBorrowId(), transaction.getMonthYear(), transaction.getDay(), "Unpaid", null)
                }
            }
            override fun onReceivedClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_received_title), getString(R.string.confirm_received_message), R.color.green) {
                    updateTransactionStatus(transaction.getBorrowId(), transaction.getMonthYear(), transaction.getDay(), "Paid", null)
                }
            }
            override fun onDeclineClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_decline_title), getString(R.string.confirm_decline_message), R.color.red) {
                    updateTransactionStatus(transaction.getBorrowId(), transaction.getMonthYear(), transaction.getDay(), "Declined", null)
                }
            }
            override fun onApprovedClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_approve_title), getString(R.string.confirm_approve_message), R.color.green) {
                    updateTransactionStatus(transaction.getBorrowId(), transaction.getMonthYear(), transaction.getDay(), "Unpaid", null)
                }
            }
        }

    private val borrowerActionListener: OnBorrowerActionListener
        get() = object : OnBorrowerActionListener {
            override fun onPayClicked(transaction: BorrowTransaction, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_pay_title), getString(R.string.confirm_pay_message), R.color.green) {
                    updateTransactionStatusWithPaymentDate(transaction.getBorrowId(), transaction.getMonthYear(), transaction.getDay(), "Pending Payment", null)
                }
            }
            override fun onRemoveClicked(transaction: BorrowTransaction, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_remove_title), getString(R.string.confirm_remove_message), R.color.red) {
                    updateTransactionStatus(transaction.getBorrowId(), transaction.getMonthYear(), transaction.getDay(), "Removed", null)
                }
            }
            override fun onTryAgainClicked(transaction: BorrowTransaction, position: Int) {
                showConfirmationDialog(getString(R.string.confirm_try_again_title), getString(R.string.confirm_try_again_message), R.color.green) {
                    updateTransactionStatus(transaction.getBorrowId(), transaction.getMonthYear(), transaction.getDay(), "For Lender Approval", null)
                }
            }
        }

    private fun handleOwedClick() {
        owedTV?.let { debtTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.VISIBLE
        debtRecyclerList?.visibility = View.GONE
        owedDebtClicked = true
        resetSpinners()
        OwedMonthlyFilterList()
    }

    private fun handleDebtClick() {
        debtTV?.let { owedTV?.let { it1 -> setTabColors(it, it1) } }
        owedRecyclerList?.visibility = View.GONE
        debtRecyclerList?.visibility = View.VISIBLE
        owedDebtClicked = false
        resetSpinners()
        DebtMonthlyFilterList()
    }

    private fun setTabColors(activeTab: TextView, inactiveTab: TextView) {
        activeTab.setBackgroundResource(R.drawable.top_round_border)
        inactiveTab.setBackgroundResource(R.drawable.button_background_invisible)
        activeTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
        inactiveTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
    }

    private fun resetSpinners() {
        monthYearSpinner?.setSelection(0)
    }

    fun DebtMonthlyFilterList() {
        val transRef = DeclareDatabase.getDBRefBorrows()
        val debtUniqueMonthYear = HashSet<String?>()
        val currentUserId = mAuth?.currentUserOrNull()?.id

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                debtUniqueMonthYear.add("All")
                for (monthSnapshot in dataSnapshot.children) {
                    val monthYear = monthSnapshot.key
                    for (daySnapshot in monthSnapshot.children) {
                        for (borrowSnapshot in daySnapshot.children) {
                            val borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction::class.java)
                            if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() == currentUserId) {
                                debtUniqueMonthYear.add(monthYear)
                            } else if (borrowSnapshot.key == currentNickname) {
                                debtUniqueMonthYear.add(monthYear)
                            }
                        }
                    }
                }
                debtSortedMonths = ArrayList(debtUniqueMonthYear)
                Collections.sort(debtSortedMonths!!)
                updateSpinnerAdapter(debtSortedMonths)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.message)
            }
        })
    }

    fun OwedMonthlyFilterList() {
        val transRef = DeclareDatabase.borrowsTable
        val owedUniqueMonthYear = HashSet<String?>()
        val currentUserId = mAuth?.currentUserOrNull()?.id

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                owedUniqueMonthYear.add("All")
                for (monthSnapshot in dataSnapshot.children) {
                    val monthYear = monthSnapshot.key
                    for (daySnapshot in monthSnapshot.children) {
                        for (borrowSnapshot in daySnapshot.children) {
                            val bnt = borrowSnapshot.getValue(BorrowNowTransaction::class.java)
                            if (bnt != null && bnt.getLenderID() == currentUserId) {
                                owedUniqueMonthYear.add(monthYear)
                            } else if (borrowSnapshot.key != currentNickname) {
                                for (timeSnapshot in borrowSnapshot.children) {
                                    val bt = timeSnapshot.getValue(BorrowTransaction::class.java)
                                    if (bt != null && bt.getBorrowee() == currentNickname) {
                                        owedUniqueMonthYear.add(monthYear)
                                    }
                                }
                            }
                        }
                    }
                }
                owedSortedMonths = ArrayList(owedUniqueMonthYear)
                Collections.sort(owedSortedMonths!!)
                updateSpinnerAdapter(owedSortedMonths)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.message)
            }
        })
    }

    private fun updateSpinnerAdapter(months: MutableList<String?>?) {
        monthYearSpinner?.setBackgroundResource(R.drawable.transparent_background)
        val adapter = SpinnerItemMonths(activity, months)
        monthYearSpinner?.adapter = adapter
    }

    fun getCurrentNickname() {
        val currentUserId = mAuth?.currentUserOrNull()?.id ?: return
        val usersRef = DeclareDatabase.getDatabaseReference().child(currentUserId)
        usersRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentNickname = dataSnapshot.getValue(String::class.java)
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.message)
            }
        })
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
        val borrowRef = DeclareDatabase.getDBRefBorrows().child(monthYear!!).child(day!!).child(borrowId!!)

        if ("Paid" == newStatus) {
            borrowRef.addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val borrow = dataSnapshot.getValue(BorrowNowTransaction::class.java)
                    if (borrow != null && borrow.getStatus() != "Paid") {
                        try {
                            val amount = borrow.getBorrowedAmountStr().toInt()
                            borrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                            borrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                        } catch (e: NumberFormatException) {}
                    }
                    borrowRef.child("status").setValue(newStatus).addOnSuccessListener {
                        onSuccess?.run()
                        showToast(getString(R.string.toast_status_updated))
                        applyFilters()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            borrowRef.child("status").setValue(newStatus).addOnSuccessListener {
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                applyFilters()
            }
        }
    }

    fun updateTransactionStatusWithPaymentDate(borrowId: String?, monthYear: String?, day: String?, newStatus: String?, onSuccess: Runnable?) {
        val borrowRef = DeclareDatabase.getDBRefBorrows().child(monthYear!!).child(day!!).child(borrowId!!)
        val paymentSentDate = System.currentTimeMillis()

        if ("Paid" == newStatus) {
            borrowRef.addListenerForSingleValueEvent(object : ValueEventListener() {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val borrow = dataSnapshot.getValue(BorrowNowTransaction::class.java)
                    if (borrow != null && borrow.getStatus() != "Paid") {
                        try {
                            val amount = borrow.getBorrowedAmountStr().toInt()
                            borrow.getBorrowerID()?.let { BalanceHelper.updateTotaldebt(it, -amount, null) }
                            borrow.getLenderID()?.let { BalanceHelper.updateTotalreceivable(it, -amount, null) }
                        } catch (e: NumberFormatException) {}
                    }
                    borrowRef.child("status").setValue(newStatus)
                    borrowRef.child("paymentSentDate").setValue(paymentSentDate).addOnSuccessListener {
                        onSuccess?.run()
                        showToast(getString(R.string.toast_status_updated))
                        applyFilters()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            borrowRef.child("status").setValue(newStatus)
            borrowRef.child("paymentSentDate").setValue(paymentSentDate).addOnSuccessListener {
                onSuccess?.run()
                showToast(getString(R.string.toast_status_updated))
                applyFilters()
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
        FirebaseDatabase.getInstance().getReference("users").addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                lenders.clear()
                lenders.add(User("", "", "", UserBalance()))
                lenders.add(User("", "", "", UserBalance()))
                for (userSnapshot in dataSnapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    if (user != null && user.username != null && user.username != currentNickname) {
                        user.id = userSnapshot.key
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
                                adapter.getLenderAt(2)?.let { selectedLenderName = it.username ?: "" }
                                updateLayoutEffect(recyclerView)
                            }
                        }
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                dialogProgressBar.visibility = View.GONE
            }
        })
    }

    private fun addBorrowTransaction(
        lender: String, borrowedAmountStr: String, currentDate: String?,
        dialog: Dialog, dialogProgressBar: View, borrowBtn: Button, cancelBtn: Button
    ) {
        val calendar = Calendar.getInstance()
        val cmy = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
        val cd = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)
        val timestamp = System.currentTimeMillis()

        val dayRef = DeclareDatabase.getDBRefBorrows().child(cmy).child(cd)
        val borrowId = dayRef.push().key ?: return run {
            showToast(getString(R.string.toast_borrow_failed))
            dialogProgressBar.visibility = View.GONE
            borrowBtn.isEnabled = true
            cancelBtn.isEnabled = true
            hideGlobalLoading()
        }

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
                val bnt = BorrowNowTransaction(borrowId, currentUserId, lenderID, currentNickname, currentDate, lender, borrowedAmountStr, "For Lender Approval", timestamp)
                dayRef.child(borrowId).setValue(bnt).addOnSuccessListener {
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
                }.addOnFailureListener {
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

    private fun getUserIDByName(name: String, callback: (String?) -> Unit) {
        FirebaseDatabase.getInstance().getReference("users").addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (userSnapshot in dataSnapshot.children) {
                    if (name == userSnapshot.child("username").getValue(String::class.java)) {
                        callback(userSnapshot.key)
                        return
                    }
                }
                callback(null)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                callback(null)
            }
        })
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
