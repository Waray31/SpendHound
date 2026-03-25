package com.waray.spendhound

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.util.Pair
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTransactionActivity : AppCompatActivity() {

    private var transactionTypeSpinner: Spinner? = null
    private var paymentAmountET: EditText? = null
    private var transactionDetailET: EditText? = null
    private var individualPaymentTV: TextView? = null
    private var addPayorsBtn: Button? = null
    private var saveTransactionBtn: Button? = null
    private var progressOverlay: View? = null
    private var payorsContainer: LinearLayout? = null
    private var dateRangePickerBtn: Button? = null

    private var mAuth: Auth? = null
    private var currentUserId: String? = null
    private var currentUserNumericId: Long? = null
    private var posterDisplayName: String? = null

    private var transactionType: String = ""
    private var paymentAmount: Double = 0.0
    private var transactionDetail: String = ""
    private var contributors: MutableList<String> = mutableListOf()
    private var amountPaidList: MutableList<Double> = mutableListOf()
    private var individualPayment: Double = 0.0
    private var selectedGroup: PayerGroup? = null

    private var startDate: Long = System.currentTimeMillis()
    private var endDate: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        mAuth = DeclareDatabase.auth
        currentUserId = mAuth?.currentUserOrNull()?.id

        initViews()
        setupListeners()
        fetchCurrentUser()
        updateDateDisplay()
    }

    private fun initViews() {
        transactionTypeSpinner = findViewById(R.id.transactionType)
        paymentAmountET = findViewById(R.id.paymentAmount)
        transactionDetailET = findViewById(R.id.editTextTextMultiLine)
        individualPaymentTV = findViewById(R.id.individualPayment)
        addPayorsBtn = findViewById(R.id.btnAdd)
        saveTransactionBtn = findViewById(R.id.addTransactionbtn)
        progressOverlay = findViewById(R.id.progressBar)
        payorsContainer = findViewById(R.id.container)

        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.transactionTypes_String,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        transactionTypeSpinner?.adapter = adapter
    }

    private fun setupListeners() {
        dateRangePickerBtn?.setOnClickListener {
            showDateRangePicker()
        }

        addPayorsBtn?.setOnClickListener {
            showAddPayorDialog()
        }

        saveTransactionBtn?.setOnClickListener {
            validateAndSave()
        }

        paymentAmountET?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateIndividualPayment()
            }
        })
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select Date Range")
        builder.setSelection(Pair(startDate, endDate))

        val picker = builder.build()
        picker.show(supportFragmentManager, "DATE_RANGE_PICKER")

        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection.first ?: startDate
            endDate = selection.second ?: selection.first ?: endDate
            updateDateDisplay()
        }
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val startStr = sdf.format(startDate)
        val endStr = sdf.format(endDate)
        dateRangePickerBtn?.text = if (startDate == endDate) startStr else "$startStr - $endStr"
    }

    private fun calculateIndividualPayment() {
        val amountStr = paymentAmountET?.text.toString().trim()
        paymentAmount = amountStr.toDoubleOrNull() ?: 0.0
        val totalPeople = contributors.size + 1 // +1 for the creator
        individualPayment = if (totalPeople > 0) paymentAmount / totalPeople else 0.0
        individualPaymentTV?.text = CurrencyUtils.formatAmountWithCurrency(individualPayment)
    }

    private fun fetchCurrentUser() {
        val authId = currentUserId ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()

                currentUserNumericId = user?.id
                posterDisplayName = user?.username
            } catch (e: Exception) {
                Log.e("AddTransaction", "Error fetching user: ${e.message}")
            }
        }
    }

    private fun showAddPayorDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Payor")
        val input = EditText(this)
        input.hint = "Enter name"
        builder.setView(input)
        builder.setPositiveButton("Add") { dialog, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                contributors.add(name)
                amountPaidList.add(0.0)
                updatePayorsList()
                calculateIndividualPayment()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun updatePayorsList() {
        payorsContainer?.removeAllViews()
        for (i in contributors.indices) {
            val textView = TextView(this)
            textView.text = "${contributors[i]} (Paid: ${CurrencyUtils.formatAmountWithCurrency(amountPaidList[i])})"
            textView.setPadding(8, 8, 8, 8)
            payorsContainer?.addView(textView)
        }
    }

    private fun validateAndSave() {
        val selectedPos = transactionTypeSpinner?.selectedItemPosition ?: 0
        if (selectedPos == 0) {
            Toast.makeText(this, "Please select a bill type", Toast.LENGTH_SHORT).show()
            return
        }

        transactionType = transactionTypeSpinner?.selectedItem?.toString() ?: ""
        val amountStr = paymentAmountET?.text.toString().trim()
        transactionDetail = transactionDetailET?.text.toString().trim()

        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
            return
        }

        paymentAmount = amountStr.toDoubleOrNull() ?: 0.0
        if (paymentAmount <= 0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show()
            return
        }

        progressOverlay?.isVisible = true
        saveTransaction()
    }

    @OptIn(InternalSerializationApi::class)
    private fun saveTransaction() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDate

        val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(cal.time)
        val currentDay = SimpleDateFormat("dd", Locale.getDefault()).format(cal.time)
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Calendar.getInstance().time)

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val createdAt = isoFormat.format(cal.time)

        val transaction = Transaction(
            transactionType = transactionType,
            paymentAmount = paymentAmount,
            transactionDetail = transactionDetail,
            contributors = contributors,
            amountPaidList = amountPaidList,
            creatorId = currentUserNumericId,
            individualPayment = individualPayment,
            monthYear = currentMonthYear,
            day = currentDay,
            timeKey = currentTime,
            status = 0,
            groupId = selectedGroup?.groupId,
            posterDisplayName = posterDisplayName,
            usernamePost = currentUserId,
            timestamp = startDate,
            createdAt = createdAt
        )

        lifecycleScope.launch {
            try {
                DeclareDatabase.transactionsTable.insert(transaction)
                progressOverlay?.isVisible = false
                Toast.makeText(this@AddTransactionActivity, "Transaction added successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Log.e("AddTransaction", "Error saving transaction: ${e.message}")
                progressOverlay?.isVisible = false
                Toast.makeText(this@AddTransactionActivity, "Error saving transaction", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
