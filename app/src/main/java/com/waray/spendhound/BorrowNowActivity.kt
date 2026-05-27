package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class BorrowInsert(
    @SerialName("borrowed_amount") val borrowedAmount: Double,
    @SerialName("borrower_id")     val borrowerId: Long,
    @SerialName("lender_id")       val lenderId: Long,
    @SerialName("status")          val status: Int,
    @SerialName("created_at")      val createdAt: String
    // payback_date and note columns are missing from the current borrows table schema
)

class BorrowNowActivity : AppCompatActivity() {

    private var rvLenders: RecyclerView? = null
    private var rvBorrowers: RecyclerView? = null
    private var tvBorrowDate: TextView? = null
    private var tvPaybackDate: TextView? = null
    private var tvBorrower: TextView? = null
    private var tvLender: TextView? = null
    private var tvBorrowerLabel: TextView? = null
    private var tvBorrowFromLabel: TextView? = null
    private var tvActivityTitle: TextView? = null
    private var etAmount: EditText? = null
    private var etNote: EditText? = null
    private var layoutBorrower: View? = null
    private var layoutLender: View? = null
    private var borrowBtn: Button? = null
    private var cancelBtn: Button? = null
    private var closeBtn: View? = null

    private var loadingOverlay_borrowNow: LinearLayout? = null
    
    private var lenderAdapter: LenderChipAdapter? = null
    private var borrowerAdapter: LenderChipAdapter? = null
    private var allUsers: List<User> = emptyList()
    private var mAuth: Auth? = null

    private var currentUserNumericId: Long? = null
    private var currentUsername: String? = null
    private var selectedOtherUser: User? = null
    private var selectedPaybackDate: Long? = null
    private var borrowMode: String = "BORROW" // Default: "BORROW" or "LEND"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_borrow_now)

        mAuth = DeclareDatabase.auth
        borrowMode = intent.getStringExtra("BORROW_MODE") ?: "BORROW"

        rvLenders = findViewById(R.id.rvLenders)
        rvBorrowers = findViewById(R.id.rvBorrowers)
        tvBorrowDate = findViewById(R.id.dialogBorrowDate)
        tvPaybackDate = findViewById(R.id.dialogPaybackDate)
        tvBorrower = findViewById(R.id.dialogBorrower)
        tvLender = findViewById(R.id.dialogLender)
        tvBorrowerLabel = findViewById(R.id.tvBorrowerLabel)
        tvBorrowFromLabel = findViewById(R.id.tvBorrowFromLabel)
        tvActivityTitle = findViewById(R.id.tvActivityTitle)
        etAmount = findViewById(R.id.dialogBorrowEditText)
        etNote = findViewById(R.id.dialogNoteEditText)
        layoutBorrower = findViewById(R.id.layoutBorrower)
        layoutLender = findViewById(R.id.layoutLender)
        borrowBtn = findViewById(R.id.dialogBorrowBtn)
        cancelBtn = findViewById(R.id.dialogCancelBtn)
        closeBtn = findViewById(R.id.dialogCloseBtn)
        loadingOverlay_borrowNow = findViewById(R.id.loadingOverlay_borrowNow)

        setupUIForMode()
        setupRecyclerViews()
        setupDatePickers()
        setupBorrowBtn()
        
        cancelBtn?.setOnClickListener { finish() }
        closeBtn?.setOnClickListener { finish() }
        
        exitEditText()
        loadCurrentUser()
        fetchUsers()
    }

    private fun setupUIForMode() {
        if (borrowMode == "LEND") {
            tvActivityTitle?.text = "Lend money"
            borrowBtn?.text = "Lend"
            tvBorrowerLabel?.text = "Borrower"
            tvBorrowFromLabel?.text = "Lender"
            
            // In Lend mode, current user is lender. Select borrower from rvBorrowers.
            layoutBorrower?.visibility = View.GONE
            rvBorrowers?.visibility = View.VISIBLE
            
            rvLenders?.visibility = View.GONE
            layoutLender?.visibility = View.VISIBLE
        } else {
            tvActivityTitle?.text = "Borrow money"
            borrowBtn?.text = "Borrow"
            tvBorrowerLabel?.text = "Borrower"
            tvBorrowFromLabel?.text = "Borrow from"
            
            // In Borrow mode, current user is borrower. Select lender from rvLenders.
            layoutBorrower?.visibility = View.VISIBLE
            rvBorrowers?.visibility = View.GONE
            
            rvLenders?.visibility = View.VISIBLE
            layoutLender?.visibility = View.GONE
        }
    }

    private fun loadCurrentUser() {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("user_id", "username")) {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                currentUserNumericId = user?.id
                currentUsername = user?.username
                
                if (borrowMode == "LEND") {
                    tvLender?.text = currentUsername
                } else {
                    tvBorrower?.text = currentUsername
                }
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Error loading current user: ${e.message}")
            }
        }
    }

    private fun setupRecyclerViews() {
        rvLenders?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvBorrowers?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun fetchUsers() {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                allUsers = users.filter { !it.username.isNullOrEmpty() && it.authId != authId }
                
                val adapter = LenderChipAdapter(allUsers, null) { selected ->
                    selectedOtherUser = selected
                }
                
                if (borrowMode == "LEND") {
                    rvBorrowers?.adapter = adapter
                } else {
                    rvLenders?.adapter = adapter
                }
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Error fetching users: ${e.message}")
            }
        }
    }

    private fun setupDatePickers() {
        val today = Calendar.getInstance().timeInMillis
        tvBorrowDate?.text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(today)

        tvPaybackDate?.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Payback Date")
                .setSelection(selectedPaybackDate ?: (today + 86400000))
                .build()

            datePicker.show(supportFragmentManager, "PAYBACK_DATE_PICKER")
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedPaybackDate = selection
                tvPaybackDate?.text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(selection)
                tvPaybackDate?.setTextColor(getColor(R.color.color_input_text))
            }
        }
    }

    private fun setupBorrowBtn() {
        borrowBtn?.setOnClickListener {
            val amountStr = etAmount?.text.toString().trim()
            val amount = amountStr.toDoubleOrNull()

            when {
                amount == null || amount <= 0 -> toast("Please enter a valid amount")
                selectedOtherUser == null -> {
                    val msg = if (borrowMode == "LEND") "Please select a borrower" else "Please select a lender"
                    toast(msg)
                }
                else -> addBorrowTransaction(amount)
            }
        }
    }

    private fun addBorrowTransaction(amount: Double) {
        val currentId = currentUserNumericId
        val otherId = selectedOtherUser?.id ?: return

        if (currentId == null) {
            toast("User session not found")
            return
        }

        val borrowerId: Long
        val lenderId: Long
        val status: Int

        if (borrowMode == "LEND") {
            borrowerId = otherId
            lenderId = currentId
            status = 2 // Approved/Pending (Lender initiated)
        } else {
            borrowerId = currentId
            lenderId = otherId
            status = 1 // For Lender Approval
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val createdAt = sdf.format(Date())

        loadingOverlay_borrowNow?.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                DeclareDatabase.borrowsTable.insert(
                    BorrowInsert(
                        borrowedAmount = amount,
                        borrowerId = borrowerId,
                        lenderId = lenderId,
                        status = status,
                        createdAt = createdAt
                    )
                )
                toast(if (borrowMode == "LEND") "Lend transaction added!" else "Borrow request sent!")
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                loadingOverlay_borrowNow?.visibility = View.GONE
                Log.e("BorrowNowActivity", "Failed: ${e.message}")
                toast("Failed: ${e.message}")
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        findViewById<View>(android.R.id.content)?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val view = currentFocus
                if (view is EditText) {
                    hideKeyboard(view)
                    view.clearFocus()
                }
            }
            false
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
