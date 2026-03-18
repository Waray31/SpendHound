package com.waray.spendhound.ui.borrow

import android.app.Dialog
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.waray.spendhound.User
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.Objects
import kotlin.math.abs
import kotlin.math.min

class BorrowFragment : Fragment() {
    // UI Components - Spinners
    private var monthYearSpinner: Spinner? = null

    // UI Components - TextViews
    var owedTV: TextView? = null
    var debtTV: TextView? = null

    // UI Components - Status Tabs
    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var pendingTabTV: TextView? = null

    // UI Components - Empty State Views
    private var noOwedTextView: View? = null
    private var noDebtTextView: View? = null

    // UI Components - RecyclerViews
    private var debtRecyclerList: RecyclerView? = null
    private var owedRecyclerList: RecyclerView? = null

    // UI Components - ProgressBar for loading state
    private var loadingOverlay: View? = null

    // Data
    var debtSortedMonths: MutableList<String?>? = null
    var owedSortedMonths: MutableList<String?>? = null
    var selectedMonth: String? = null
    private var selectedStatusTab = "All"
    private var owedDebtClicked = false
    var currentNickname: String? = ""

    // State tracking for better UX
    private var isLoading = false

    private var globalLoadingOverlay: View? = null

    private var selectedLenderName = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_borrow, container, false)
        monthYearSpinner = view.findViewById<Spinner>(R.id.monthYearSpinner)
        owedTV = view.findViewById<TextView>(R.id.owedTV)
        debtTV = view.findViewById<TextView>(R.id.debtTV)
        owedRecyclerList = view.findViewById<RecyclerView>(R.id.owedRecyclerList)
        noOwedTextView = view.findViewById<View>(R.id.noOwedTextView)
        noDebtTextView = view.findViewById<View>(R.id.noDebtTextView)
        debtRecyclerList = view.findViewById<RecyclerView>(R.id.debtRecyclerList)
        loadingOverlay = view.findViewById<View?>(R.id.loadingOverlay)

        // Status tabs
        allTabTV = view.findViewById<TextView>(R.id.allTabTV)
        paidTabTV = view.findViewById<TextView>(R.id.paidTabTV)
        unpaidTabTV = view.findViewById<TextView>(R.id.unpaidTabTV)
        pendingTabTV = view.findViewById<TextView>(R.id.pendingTabTV)

        owedDebtClicked = true

        getCurrentNickname()
        setupViews()
        setupSpinners()
        setupStatusTabs()
        setupClickListeners()

        // Get the hosting Activity and remove the ActionBar
        val activity: AppCompatActivity? = getActivity() as AppCompatActivity?
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide()
        }

        globalLoadingOverlay = getActivity()!!.findViewById<View?>(R.id.loadingOverlay)

        return view
    }

    private fun setupViews() {
        owedRecyclerList.setLayoutManager(LinearLayoutManager(getContext()))
        debtRecyclerList.setLayoutManager(LinearLayoutManager(getContext()))
        OwedMonthlyFilterList()
    }

    private fun setupSpinners() {
        monthYearSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                selectedMonth = parentView.getItemAtPosition(position) as String?
                applyFilters()
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
            }
        })
    }

    private fun setupStatusTabs() {
        setStatusTabSelected(allTabTV)

        allTabTV.setOnClickListener(View.OnClickListener { v: View? ->
            selectedStatusTab = "All"
            setStatusTabSelected(allTabTV)
            applyFilters()
        })

        paidTabTV.setOnClickListener(View.OnClickListener { v: View? ->
            selectedStatusTab = "Paid"
            setStatusTabSelected(paidTabTV)
            applyFilters()
        })

        unpaidTabTV.setOnClickListener(View.OnClickListener { v: View? ->
            selectedStatusTab = "Unpaid"
            setStatusTabSelected(unpaidTabTV)
            applyFilters()
        })

        pendingTabTV.setOnClickListener(View.OnClickListener { v: View? ->
            selectedStatusTab = "Pending"
            setStatusTabSelected(pendingTabTV)
            applyFilters()
        })
    }

    private fun setStatusTabSelected(selectedTab: TextView) {
        // Reset all tabs
        allTabTV.setBackgroundResource(0)
        paidTabTV.setBackgroundResource(0)
        unpaidTabTV.setBackgroundResource(0)
        pendingTabTV.setBackgroundResource(0)

        // Set selected tab background
        selectedTab.setBackgroundResource(R.drawable.bg_status_tab_selected)
    }

    private fun setupClickListeners() {
        owedTV.setOnClickListener(View.OnClickListener { v: View? -> handleOwedClick() })
        debtTV.setOnClickListener(View.OnClickListener { v: View? -> handleDebtClick() })
    }

    private fun applyFilters() {
        val mainActivity: MainActivity? = getActivity() as MainActivity?
        if (mainActivity == null) return

        showLoading()

        if (owedDebtClicked) {
            if (selectedMonth == "All") {
                mainActivity.getOwedList(
                    selectedStatusTab, OwedNumCallback { owedNum: Int -> this.OwedSize(owedNum) },
                    this.lenderActionListener
                )
            } else {
                mainActivity.getOwedListMonthly(
                    selectedMonth,
                    selectedStatusTab,
                    OwedNumCallback { owedNum: Int -> this.OwedSize(owedNum) },
                    this.lenderActionListener
                )
            }
        } else {
            if (selectedMonth == "All") {
                mainActivity.getDebtList(
                    selectedStatusTab, DebtNumCallback { debtNum: Int -> this.DebtSize(debtNum) },
                    this.borrowerActionListener
                )
            } else {
                mainActivity.getDebtListMonthly(
                    selectedMonth,
                    selectedStatusTab,
                    DebtNumCallback { debtNum: Int -> this.DebtSize(debtNum) },
                    this.borrowerActionListener
                )
            }
        }
    }

    private val lenderActionListener: OnLenderActionListener
        get() = object : OnLenderActionListener {
            override fun onNotYetClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(
                    getString(R.string.confirm_not_yet_title),
                    getString(R.string.confirm_not_yet_message),
                    R.color.grey,
                    Runnable {
                        updateTransactionStatus(
                            transaction.getBorrowId(),
                            transaction.getMonthYear(),
                            transaction.getDay(),
                            "Unpaid",
                            null
                        )
                    }
                )
            }

            override fun onReceivedClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(
                    getString(R.string.confirm_received_title),
                    getString(R.string.confirm_received_message),
                    R.color.green,
                    Runnable {
                        updateTransactionStatus(
                            transaction.getBorrowId(),
                            transaction.getMonthYear(),
                            transaction.getDay(),
                            "Paid",
                            null
                        )
                    }
                )
            }

            override fun onDeclineClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(
                    getString(R.string.confirm_decline_title),
                    getString(R.string.confirm_decline_message),
                    R.color.red,
                    Runnable {
                        updateTransactionStatus(
                            transaction.getBorrowId(),
                            transaction.getMonthYear(),
                            transaction.getDay(),
                            "Declined",
                            null
                        )
                    }
                )
            }

            override fun onApprovedClicked(transaction: OwedTransaction, position: Int) {
                showConfirmationDialog(
                    getString(R.string.confirm_approve_title),
                    getString(R.string.confirm_approve_message),
                    R.color.green,
                    Runnable {
                        updateTransactionStatus(
                            transaction.getBorrowId(),
                            transaction.getMonthYear(),
                            transaction.getDay(),
                            "Unpaid",
                            null
                        )
                    }
                )
            }
        }

    private val borrowerActionListener: OnBorrowerActionListener
        get() = object : OnBorrowerActionListener {
            override fun onPayClicked(transaction: BorrowTransaction, position: Int) {
                showConfirmationDialog(
                    getString(R.string.confirm_pay_title),
                    getString(R.string.confirm_pay_message),
                    R.color.green,
                    Runnable {
                        updateTransactionStatusWithPaymentDate(
                            transaction.getBorrowId(),
                            transaction.getMonthYear(),
                            transaction.getDay(),
                            "Pending Payment",
                            null
                        )
                    }
                )
            }

            override fun onRemoveClicked(transaction: BorrowTransaction, position: Int) {
                showConfirmationDialog(
                    getString(R.string.confirm_remove_title),
                    getString(R.string.confirm_remove_message),
                    R.color.red,
                    Runnable {
                        updateTransactionStatus(
                            transaction.getBorrowId(),
                            transaction.getMonthYear(),
                            transaction.getDay(),
                            "Removed",
                            null
                        )
                    }
                )
            }

            override fun onTryAgainClicked(transaction: BorrowTransaction, position: Int) {
                showConfirmationDialog(
                    getString(R.string.confirm_try_again_title),
                    getString(R.string.confirm_try_again_message),
                    R.color.green,
                    Runnable {
                        updateTransactionStatus(
                            transaction.getBorrowId(),
                            transaction.getMonthYear(),
                            transaction.getDay(),
                            "For Lender Approval",
                            null
                        )
                    }
                )
            }
        }

    private fun handleOwedClick() {
        setTabColors(owedTV, debtTV)
        owedRecyclerList.setVisibility(View.VISIBLE)
        debtRecyclerList.setVisibility(View.GONE)

        owedDebtClicked = true
        resetSpinners()
        OwedMonthlyFilterList()
    }

    private fun handleDebtClick() {
        setTabColors(debtTV, owedTV)
        owedRecyclerList.setVisibility(View.GONE)
        debtRecyclerList.setVisibility(View.VISIBLE)

        owedDebtClicked = false
        resetSpinners()
        DebtMonthlyFilterList()
    }

    private fun setTabColors(activeTab: TextView, inactiveTab: TextView) {
        activeTab.setBackgroundResource(R.drawable.top_round_border)
        inactiveTab.setBackgroundResource(R.drawable.button_background_invisible)
        activeTab.setTextColor(ContextCompat.getColor(getContext(), R.color.darkBlue))
        inactiveTab.setTextColor(ContextCompat.getColor(getContext(), R.color.whitest))
    }

    private fun resetSpinners() {
        monthYearSpinner.setSelection(0)
    }


    fun DebtMonthlyFilterList() {
        val transRef: DatabaseReference = DeclareDatabase.getDBRefBorrows()
        val debtUniqueMonthYear: MutableSet<String?> = HashSet<String?>()
        val currentUserId: String? =
            Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser()).getUid()

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                debtUniqueMonthYear.add("All")
                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val monthYear: String? = monthSnapshot.getKey()
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        for (borrowSnapshot in daySnapshot.getChildren()) {
                            val borrowNowTransaction: BorrowNowTransaction? =
                                borrowSnapshot.getValue(BorrowNowTransaction::class.java)

                            if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() != null) {
                                if (borrowNowTransaction.getBorrowerID() == currentUserId) {
                                    debtUniqueMonthYear.add(monthYear)
                                }
                            } else {
                                if (borrowSnapshot.getKey() == currentNickname) {
                                    debtUniqueMonthYear.add(monthYear)
                                }
                            }
                        }
                    }
                }

                debtSortedMonths = ArrayList<String?>(debtUniqueMonthYear)
                Collections.sort<String?>(debtSortedMonths)
                updateSpinnerAdapter(debtSortedMonths)
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(
                    "FirebaseDatabase",
                    "Database read error occurred: " + databaseError.getMessage()
                )
            }
        })
    }

    fun OwedMonthlyFilterList() {
        val transRef: DatabaseReference = DeclareDatabase.getDBRefBorrows()
        val owedUniqueMonthYear: MutableSet<String?> = HashSet<String?>()
        val currentUserId: String? =
            Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser()).getUid()

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                owedUniqueMonthYear.add("All")
                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val monthYear: String? = monthSnapshot.getKey()
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        for (borrowSnapshot in daySnapshot.getChildren()) {
                            val borrowNowTransaction: BorrowNowTransaction? =
                                borrowSnapshot.getValue(BorrowNowTransaction::class.java)

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null) {
                                if (borrowNowTransaction.getLenderID() == currentUserId) {
                                    owedUniqueMonthYear.add(monthYear)
                                }
                            } else {
                                if (borrowSnapshot.getKey() != currentNickname) {
                                    for (timeSnapshot in borrowSnapshot.getChildren()) {
                                        try {
                                            val borrowTransaction: BorrowTransaction? =
                                                timeSnapshot.getValue(BorrowTransaction::class.java)
                                            if (borrowTransaction != null && borrowTransaction.getBorrowee() == currentNickname) {
                                                owedUniqueMonthYear.add(monthYear)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                "BorrowFragment",
                                                "Error parsing legacy transaction: " + e.message
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                owedSortedMonths = ArrayList<String?>(owedUniqueMonthYear)
                Collections.sort<String?>(owedSortedMonths)
                updateSpinnerAdapter(owedSortedMonths)
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(
                    "FirebaseDatabase",
                    "Database read error occurred: " + databaseError.getMessage()
                )
            }
        })
    }

    private fun updateSpinnerAdapter(months: MutableList<String?>?) {
        monthYearSpinner.setBackgroundResource(R.drawable.transparent_background)
        val adapter: SpinnerItemMonths = SpinnerItemMonths(getActivity(), months)
        monthYearSpinner.setAdapter(adapter)
    }

    fun getCurrentNickname() {
        val currentUserID: String? =
            Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser()).getUid()
        val usersRef: DatabaseReference =
            DeclareDatabase.getDatabaseReference().child(currentUserID)
        usersRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentNickname = dataSnapshot.getValue(String::class.java)
                } else {
                    Log.d("FirebaseDatabase", "Nickname not found in database.")
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(
                    "FirebaseDatabase",
                    "Database read error occurred: " + databaseError.getMessage()
                )
            }
        })
    }

    fun OwedSize(owedNum: Int) {
        hideLoading()
        noOwedTextView!!.setVisibility(if (owedNum == 0) View.VISIBLE else View.GONE)
        owedRecyclerList.setVisibility(if (owedNum == 0) View.GONE else View.VISIBLE)
        noDebtTextView!!.setVisibility(View.GONE)
    }

    fun DebtSize(debtNum: Int) {
        hideLoading()
        noDebtTextView!!.setVisibility(if (debtNum == 0) View.VISIBLE else View.GONE)
        debtRecyclerList.setVisibility(if (debtNum == 0) View.GONE else View.VISIBLE)
        noOwedTextView!!.setVisibility(View.GONE)
    }

    fun showConfirmationDialog(
        title: String?,
        message: String?,
        confirmBtnColor: Int,
        onConfirm: Runnable
    ) {
        val dialog = Dialog(getContext()!!)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_action)
        dialog.setCancelable(true)

        if (dialog.getWindow() != null) {
            dialog.getWindow()!!.setBackgroundDrawable(ColorDrawable(Color.WHITE))
            dialog.getWindow()!!
                .setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val dialogTitle: TextView = dialog.findViewById<TextView>(R.id.dialogTitle)
        val dialogMessage: TextView = dialog.findViewById<TextView>(R.id.dialogMessage)
        val cancelBtn = dialog.findViewById<Button>(R.id.dialogCancelBtn)
        val confirmBtn = dialog.findViewById<Button>(R.id.dialogConfirmBtn)

        dialogTitle.setText(title)
        dialogMessage.setText(message)
        confirmBtn.setBackgroundTintList(
            ContextCompat.getColorStateList(
                getContext(),
                confirmBtnColor
            )
        )

        cancelBtn.setOnClickListener(View.OnClickListener { v: View? -> dialog.dismiss() })
        confirmBtn.setOnClickListener(View.OnClickListener { v: View? ->
            dialog.dismiss()
            onConfirm.run()
        })

        dialog.show()
    }

    fun updateTransactionStatus(
        borrowId: String?,
        monthYear: String?,
        day: String?,
        newStatus: String?,
        onSuccess: Runnable?
    ) {
        val borrowRef: DatabaseReference = DeclareDatabase.getDBRefBorrows()
            .child(monthYear)
            .child(day)
            .child(borrowId)

        if ("Paid" == newStatus) {
            borrowRef.addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val borrow: BorrowNowTransaction? =
                        dataSnapshot.getValue(BorrowNowTransaction::class.java)
                    if (borrow != null && !("Paid" == borrow.getStatus())) {
                        try {
                            val amount = borrow.getBorrowedAmountStr().toInt()
                            val borrowerID: String? = borrow.getBorrowerID()
                            val lenderID: String? = borrow.getLenderID()

                            if (borrowerID != null) {
                                BalanceHelper.updateTotaldebt(borrowerID, -amount, null)
                            }
                            if (lenderID != null) {
                                BalanceHelper.updateTotalreceivable(lenderID, -amount, null)
                            }
                        } catch (e: NumberFormatException) {
                            Log.e("BorrowFragment", "Error parsing borrow amount: " + e.message)
                        }
                    }

                    borrowRef.child("status").setValue(newStatus)
                        .addOnSuccessListener({ unused ->
                            if (onSuccess != null) {
                                onSuccess.run()
                            }
                            showToast(getString(R.string.toast_status_updated))
                            applyFilters()
                        })
                        .addOnFailureListener({ e ->
                            Log.e("BorrowFragment", "Failed to update status: " + e.getMessage())
                            showToast(getString(R.string.toast_status_update_failed))
                        })
                }

                public override fun onCancelled(error: DatabaseError) {
                    Log.e("BorrowFragment", "Failed to read borrow data: " + error.getMessage())
                    showToast(getString(R.string.toast_status_update_failed))
                }
            })
        } else {
            borrowRef.child("status").setValue(newStatus)
                .addOnSuccessListener({ unused ->
                    if (onSuccess != null) {
                        onSuccess.run()
                    }
                    showToast(getString(R.string.toast_status_updated))
                    applyFilters()
                })
                .addOnFailureListener({ e ->
                    Log.e("BorrowFragment", "Failed to update status: " + e.getMessage())
                    showToast(getString(R.string.toast_status_update_failed))
                })
        }
    }

    fun updateTransactionStatusWithPaymentDate(
        borrowId: String?,
        monthYear: String?,
        day: String?,
        newStatus: String?,
        onSuccess: Runnable?
    ) {
        val borrowRef: DatabaseReference = DeclareDatabase.getDBRefBorrows()
            .child(monthYear)
            .child(day)
            .child(borrowId)

        val paymentSentDate = System.currentTimeMillis()

        if ("Paid" == newStatus) {
            borrowRef.addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val borrow: BorrowNowTransaction? =
                        dataSnapshot.getValue(BorrowNowTransaction::class.java)
                    if (borrow != null && !("Paid" == borrow.getStatus())) {
                        try {
                            val amount = borrow.getBorrowedAmountStr().toInt()
                            val borrowerID: String? = borrow.getBorrowerID()
                            val lenderID: String? = borrow.getLenderID()

                            if (borrowerID != null) {
                                BalanceHelper.updateTotaldebt(borrowerID, -amount, null)
                            }
                            if (lenderID != null) {
                                BalanceHelper.updateTotalreceivable(lenderID, -amount, null)
                            }
                        } catch (e: NumberFormatException) {
                            Log.e("BorrowFragment", "Error parsing borrow amount: " + e.message)
                        }
                    }

                    borrowRef.child("status").setValue(newStatus)
                    borrowRef.child("paymentSentDate").setValue(paymentSentDate)
                        .addOnSuccessListener({ unused ->
                            if (onSuccess != null) {
                                onSuccess.run()
                            }
                            showToast(getString(R.string.toast_status_updated))
                            applyFilters()
                        })
                        .addOnFailureListener({ e ->
                            Log.e("BorrowFragment", "Failed to update status: " + e.getMessage())
                            showToast(getString(R.string.toast_status_update_failed))
                        })
                }

                public override fun onCancelled(error: DatabaseError) {
                    Log.e("BorrowFragment", "Failed to read borrow data: " + error.getMessage())
                    showToast(getString(R.string.toast_status_update_failed))
                }
            })
        } else {
            borrowRef.child("status").setValue(newStatus)
            borrowRef.child("paymentSentDate").setValue(paymentSentDate)
                .addOnSuccessListener({ unused ->
                    if (onSuccess != null) {
                        onSuccess.run()
                    }
                    showToast(getString(R.string.toast_status_updated))
                    applyFilters()
                })
                .addOnFailureListener({ e ->
                    Log.e("BorrowFragment", "Failed to update status: " + e.getMessage())
                    showToast(getString(R.string.toast_status_update_failed))
                })
        }
    }

    private fun showBorrowNowDialog() {
        val dialog = Dialog(getContext()!!)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_borrow_now)
        dialog.setCancelable(false)

        if (dialog.getWindow() != null) {
            dialog.getWindow()!!.setBackgroundDrawable(ColorDrawable(Color.WHITE))
            dialog.getWindow()!!
                .setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val dateTV: TextView = dialog.findViewById<TextView>(R.id.dialogBorrowDate)
        val borrowerTV: TextView = dialog.findViewById<TextView>(R.id.dialogBorrower)
        val lenderRecyclerView: RecyclerView =
            dialog.findViewById<RecyclerView>(R.id.lenderRecyclerView)
        val amountEditText: EditText = dialog.findViewById<EditText>(R.id.dialogBorrowEditText)
        val cancelBtn = dialog.findViewById<Button>(R.id.dialogCancelBtn)
        val borrowBtn = dialog.findViewById<Button>(R.id.dialogBorrowBtn)
        val dialogProgressBar = dialog.findViewById<View>(R.id.dialogProgressBar)

        dialogProgressBar.setVisibility(View.VISIBLE)

        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(calendar.getTime())
        dateTV.setText(currentDate)

        borrowerTV.setText(currentNickname)

        setupLenderRecyclerView(lenderRecyclerView, dialogProgressBar)

        cancelBtn.setOnClickListener(View.OnClickListener { v: View? -> dialog.dismiss() })

        borrowBtn.setOnClickListener(View.OnClickListener { v: View? ->
            val amountStr = amountEditText.getText().toString().trim { it <= ' ' }
            if (amountStr.isEmpty() || selectedLenderName.isEmpty()) {
                showToast(getString(R.string.toast_fill_all_fields))
                return@setOnClickListener
            }

            val amount: Int
            try {
                amount = amountStr.toInt()
                if (amount <= 0) {
                    showToast(getString(R.string.toast_fill_all_fields))
                    return@setOnClickListener
                }
            } catch (e: NumberFormatException) {
                showToast(getString(R.string.toast_fill_all_fields))
                return@setOnClickListener
            }

            borrowBtn.setEnabled(false)
            cancelBtn.setEnabled(false)
            dialogProgressBar.setVisibility(View.VISIBLE)
            showGlobalLoading()
            addBorrowTransaction(
                selectedLenderName,
                amount.toString(),
                currentDate,
                dialog,
                dialogProgressBar,
                borrowBtn,
                cancelBtn
            )
        })

        dialog.show()
    }

    private fun setupLenderRecyclerView(recyclerView: RecyclerView, dialogProgressBar: View) {
        val layoutManager: LinearLayoutManager =
            LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerView.setLayoutManager(layoutManager)

        val lenders: MutableList<User?> = ArrayList<User?>()
        val adapter: LenderAdapter = LenderAdapter(lenders)
        recyclerView.setAdapter(adapter)

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
                    val centerView: View? = snapHelper.findSnapView(layoutManager)
                    if (centerView != null) {
                        val pos: Int = layoutManager.getPosition(centerView)
                        val selectedLender: User? = adapter.getLenderAt(pos)
                        if (selectedLender != null) {
                            selectedLenderName = selectedLender.getUsername()
                        }
                    }
                }
            }
        })

        loadLenders(adapter, lenders, recyclerView, dialogProgressBar)
    }

    private fun updateLayoutEffect(recyclerView: RecyclerView) {
        val midpoint: Float = recyclerView.getWidth() / 2f
        val d0 = 0f
        val d1 = 0.9f * midpoint
        val s0 = 1.6f
        val s1 = 1.0f
        val a0 = 1.0f
        val a1 = 0.5f

        for (i in 0..<recyclerView.getChildCount()) {
            val child: View = recyclerView.getChildAt(i)
            val childMidpoint: Float = (recyclerView.getLayoutManager()
                .getDecoratedRight(child) + recyclerView.getLayoutManager()
                .getDecoratedLeft(child)) / 2f
            val d = min(d1, abs(midpoint - childMidpoint))
            val scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0)
            val alpha = a0 + (a1 - a0) * (d - d0) / (d1 - d0)
            child.setScaleX(scale)
            child.setScaleY(scale)
            child.setAlpha(alpha)
        }
    }

    private fun loadLenders(
        adapter: LenderAdapter,
        lenders: MutableList<User?>,
        recyclerView: RecyclerView,
        dialogProgressBar: View
    ) {
        val databaseReference: DatabaseReference = DeclareDatabase.getDatabaseReference()
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                lenders.clear()
                lenders.add(User("", "", "", "", UserBalance()))
                lenders.add(User("", "", "", "", UserBalance()))

                for (userSnapshot in dataSnapshot.getChildren()) {
                    val user: User? = userSnapshot.getValue(User::class.java)
                    if (user != null && user.getUsername() != null && (user.getUsername() != currentNickname)) {
                        user.setUid(userSnapshot.getKey())
                        lenders.add(user)
                    }
                }

                lenders.add(User("", "", "", "", UserBalance()))
                lenders.add(User("", "", "", "", UserBalance()))

                adapter.notifyDataSetChanged()

                adapter.preloadAllImages(getContext(), Runnable {
                    if (getActivity() != null) {
                        getActivity()!!.runOnUiThread(Runnable {
                            dialogProgressBar.setVisibility(View.GONE)
                            if (lenders.size > 2) {
                                recyclerView.scrollToPosition(2)
                                recyclerView.post(Runnable {
                                    val firstUser: User? = adapter.getLenderAt(2)
                                    if (firstUser != null) {
                                        selectedLenderName = firstUser.getUsername()
                                    }
                                    updateLayoutEffect(recyclerView)
                                })
                            }
                        })
                    }
                })
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e("BorrowFragment", "Database error: " + databaseError.getMessage())
                dialogProgressBar.setVisibility(View.GONE)
            }
        })
    }

    private fun addBorrowTransaction(
        lender: String, borrowedAmountStr: String, currentDate: String?,
        dialog: Dialog, dialogProgressBar: View, borrowBtn: Button, cancelBtn: Button
    ) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
        val dayFormat = SimpleDateFormat("dd", Locale.getDefault())

        val currentMonthYear = dateFormat.format(calendar.getTime())
        val currentDay = dayFormat.format(calendar.getTime())
        val timestamp = System.currentTimeMillis()

        val databaseReference: DatabaseReference = DeclareDatabase.getDBRefBorrows()
        val monthYearRef: DatabaseReference = databaseReference.child(currentMonthYear)
        val dayRef: DatabaseReference = monthYearRef.child(currentDay)

        val borrowId: String? = dayRef.push().getKey()
        if (borrowId == null) {
            showToast(getString(R.string.toast_borrow_failed))
            dialogProgressBar.setVisibility(View.GONE)
            borrowBtn.setEnabled(true)
            cancelBtn.setEnabled(true)
            hideGlobalLoading()
            return
        }

        val borrowRef: DatabaseReference = dayRef.child(borrowId)

        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().getCurrentUser()
        if (currentUser != null) {
            val borrowerID: String? = currentUser.getUid()

            getUserIDByName(lender, BorrowFragment.UserIDCallback { lenderID: String? ->
                if (lenderID == null) {
                    if (getActivity() != null) {
                        getActivity()!!.runOnUiThread(Runnable {
                            showToast(getString(R.string.toast_borrow_failed))
                            dialogProgressBar.setVisibility(View.GONE)
                            borrowBtn.setEnabled(true)
                            cancelBtn.setEnabled(true)
                            hideGlobalLoading()
                        })
                    }
                    return@getUserIDByName
                }
                val borrowNowTransaction: BorrowNowTransaction = BorrowNowTransaction(
                    borrowId,
                    borrowerID,
                    lenderID,
                    currentNickname,
                    currentDate,
                    lender,
                    borrowedAmountStr,
                    "For Lender Approval",
                    timestamp
                )
                borrowRef.setValue(borrowNowTransaction).addOnSuccessListener({ unused ->
                    BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null)
                    BalanceHelper.addLenderEntry(lenderID, borrowId, null)

                    val amount = borrowedAmountStr.toInt()
                    BalanceHelper.updateTotaldebt(borrowerID, amount, null)
                    BalanceHelper.updateTotalreceivable(lenderID, amount, null)
                    if (getActivity() != null) {
                        getActivity()!!.runOnUiThread(Runnable {
                            showToast(getString(R.string.toast_borrow_success))
                            dialog.dismiss()
                            applyFilters()
                        })
                    }
                }).addOnFailureListener({ e ->
                    if (getActivity() != null) {
                        getActivity()!!.runOnUiThread(Runnable {
                            showToast(getString(R.string.toast_borrow_failed))
                            dialogProgressBar.setVisibility(View.GONE)
                            borrowBtn.setEnabled(true)
                            cancelBtn.setEnabled(true)
                            hideGlobalLoading()
                        })
                    }
                })
            })
        }
    }

    private fun getUserIDByName(name: String, callback: UserIDCallback) {
        val usersRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("users")
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (userSnapshot in dataSnapshot.getChildren()) {
                    val userName: String? =
                        userSnapshot.child("username").getValue(String::class.java)
                    if (name == userName) {
                        callback.onUserIDRetrieved(userSnapshot.getKey())
                        return
                    }
                }
                callback.onUserIDRetrieved(null)
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e("BorrowFragment", "Database error: " + databaseError.getMessage())
                callback.onUserIDRetrieved(null)
            }
        })
    }

    private interface UserIDCallback {
        fun onUserIDRetrieved(userID: String?)
    }


    fun showToast(message: String?) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading() {
        isLoading = true
        if (loadingOverlay != null) {
            loadingOverlay!!.setVisibility(View.VISIBLE)
        }
    }

    private fun hideLoading() {
        isLoading = false
        if (loadingOverlay != null) {
            loadingOverlay!!.setVisibility(View.GONE)
        }
    }

    override fun onResume() {
        super.onResume()
        applyFilters()
    }

    private fun showGlobalLoading() {
        if (globalLoadingOverlay != null) {
            globalLoadingOverlay!!.setVisibility(View.VISIBLE)
        }
    }

    private fun hideGlobalLoading() {
        if (globalLoadingOverlay != null) {
            globalLoadingOverlay!!.setVisibility(View.GONE)
        }
    }
}
