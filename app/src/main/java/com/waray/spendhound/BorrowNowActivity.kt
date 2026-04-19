package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
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

@Serializable
private data class BorrowInsert(
    @SerialName("borrowed_amount") val borrowedAmount: Double,
    @SerialName("borrower_id")     val borrowerId: Long,
    @SerialName("lender_id")       val lenderId: Long,
    @SerialName("status")          val status: Int,
    @SerialName("created_at")      val createdAt: String,
    @SerialName("payback_date")    val paybackDate: String? = null,
    @SerialName("note")            val note: String? = null
)

class BorrowNowActivity : AppCompatActivity() {

    private var rvLenders: RecyclerView? = null
    private var tvBorrowDate: TextView? = null
    private var tvPaybackDate: TextView? = null
    private var tvBorrower: TextView? = null
    private var etAmount: EditText? = null
    private var etNote: EditText? = null
    private var borrowBtn: Button? = null
    private var cancelBtn: Button? = null
    private var closeBtn: View? = null
    
    private var lenderAdapter: LenderChipAdapter? = null
    private var allLenders: List<User> = emptyList()
    private var mAuth: Auth? = null

    private var currentUserNumericId: Long? = null
    private var selectedLenderUser: User? = null
    private var selectedPaybackDate: Long? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_borrow_now)

        mAuth = DeclareDatabase.auth

        rvLenders = findViewById(R.id.rvLenders)
        tvBorrowDate = findViewById(R.id.dialogBorrowDate)
        tvPaybackDate = findViewById(R.id.dialogPaybackDate)
        tvBorrower = findViewById(R.id.dialogBorrower)
        etAmount = findViewById(R.id.dialogBorrowEditText)
        etNote = findViewById(R.id.dialogNoteEditText)
        borrowBtn = findViewById(R.id.dialogBorrowBtn)
        cancelBtn = findViewById(R.id.dialogCancelBtn)
        closeBtn = findViewById(R.id.dialogCloseBtn)

        setupLenderRecyclerView()
        setupDatePickers()
        setupBorrowBtn()
        
        cancelBtn?.setOnClickListener { finish() }
        closeBtn?.setOnClickListener { finish() }
        
        exitEditText()
        loadCurrentUser()
        fetchLenders()
    }

    private fun loadCurrentUser() {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("user_id", "username")) {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                currentUserNumericId = user?.id
                tvBorrower?.text = user?.username
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Error loading current user: ${e.message}")
            }
        }
    }

    private fun setupLenderRecyclerView() {
        rvLenders?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun fetchLenders() {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                allLenders = users.filter { !it.username.isNullOrEmpty() && it.authId != authId }
                
                lenderAdapter = LenderChipAdapter(allLenders) { selected ->
                    selectedLenderUser = selected
                }
                rvLenders?.adapter = lenderAdapter
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Error fetching lenders: ${e.message}")
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
                tvPaybackDate?.setTextColor(getColor(R.color.black))
            }
        }
    }

    private fun setupBorrowBtn() {
        borrowBtn?.setOnClickListener {
            val amountStr = etAmount?.text.toString().trim()
            val amount = amountStr.toDoubleOrNull()
            val note = etNote?.text.toString().trim()

            when {
                amount == null || amount <= 0 -> toast("Please enter a valid amount")
                selectedLenderUser == null -> toast("Please select a lender")
                else -> addBorrowTransaction(amount, note)
            }
        }
    }

    private fun addBorrowTransaction(amount: Double, note: String) {
        val borrowerId = currentUserNumericId
        val lenderId = selectedLenderUser?.id ?: return

        if (borrowerId == null) {
            toast("User session not found")
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val createdAt = sdf.format(Date())
        val paybackStr = selectedPaybackDate?.let { sdf.format(Date(it)) }

        lifecycleScope.launch {
            try {
                DeclareDatabase.borrowsTable.insert(
                    BorrowInsert(
                        borrowedAmount = amount,
                        borrowerId = borrowerId,
                        lenderId = lenderId,
                        status = 1, // For Lender Approval
                        createdAt = createdAt,
                        paybackDate = paybackStr,
                        note = note.ifBlank { null }
                    )
                )
                toast("Borrow request sent!")
                finish()
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Failed to borrow: ${e.message}")
                toast("Failed to borrow: ${e.message}")
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
