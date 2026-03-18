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
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

class BorrowNowActivity : AppCompatActivity() {
    private var lenderRecyclerView: RecyclerView? = null
    private var date: TextView? = null
    private var borrower: TextView? = null
    var currentNickname: String? = null
    var lender: String? = null
    var currentDate: String? = null
    var status: String? = null
    var borrowedAmountSTR: String? = null
    var borrowerID: String? = null
    var lenderID: String? = null
    private var borrowedAmount = 0
    private var dialogProgressBar: View? = null
    private var borrowBtn: Button? = null
    private var cancelBtn: Button? = null
    private var adapter: LenderAdapter? = null
    private var lenders: MutableList<User?>? = null
    private var mAuth: Auth? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_borrow_now)
        
        mAuth = DeclareDatabase.auth
        
        lenderRecyclerView = findViewById(R.id.lenderRecyclerView)
        date = findViewById(R.id.dialogBorrowDate)
        borrower = findViewById(R.id.dialogBorrower)
        dialogProgressBar = findViewById(R.id.dialogProgressBar)
        borrowBtn = findViewById(R.id.dialogBorrowBtn)
        cancelBtn = findViewById(R.id.dialogCancelBtn)
        status = "For Lender Approval"

        dialogProgressBar?.visibility = View.VISIBLE

        setDate()
        setupLenderRecyclerView()
        setupBorrowBtn()

        cancelBtn?.setOnClickListener { finish() }

        exitEditText()
        loadNickname()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupLenderRecyclerView() {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        lenderRecyclerView?.layoutManager = layoutManager

        lenders = ArrayList()
        adapter = LenderAdapter(lenders!!)
        lenderRecyclerView?.adapter = adapter

        val snapHelper: SnapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(lenderRecyclerView)

        lenderRecyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
                        val selectedLender = adapter?.getLenderAt(pos)
                        lender = selectedLender?.username
                    }
                }
            }
        })

        fetchLenders()
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

    private fun loadNickname() {
        val currentUserID = mAuth?.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("username")) {
                    filter {
                        eq("id", currentUserID)
                    }
                }.decodeSingleOrNull<User>()
                currentNickname = user?.username
                borrower?.text = currentNickname
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Error loading nickname: ${e.message}")
            }
        }
    }

    private fun fetchLenders() {
        lifecycleScope.launch {
            try {
                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                lenders?.clear()
                // Carousel padding
                lenders?.add(User("", "", "", UserBalance()))
                lenders?.add(User("", "", "", UserBalance()))

                for (user in users) {
                    if (user.username != null && user.username != currentNickname) {
                        lenders?.add(user)
                    }
                }

                lenders?.add(User("", "", "", UserBalance()))
                lenders?.add(User("", "", "", UserBalance()))

                adapter?.notifyDataSetChanged()

                adapter?.preloadAllImages(this@BorrowNowActivity) {
                    runOnUiThread {
                        dialogProgressBar?.visibility = View.GONE
                        if (lenders!!.size > 2) {
                            lenderRecyclerView?.scrollToPosition(2)
                            lenderRecyclerView?.post {
                                val firstUser = adapter?.getLenderAt(2)
                                lender = firstUser?.username
                                lenderRecyclerView?.let { updateLayoutEffect(it) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Error fetching lenders: ${e.message}")
                dialogProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun setDate() {
        val calendar = Calendar.getInstance()
        currentDate = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault()).format(calendar.time)
        date?.text = currentDate
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addBorrowTransaction() {
        val calendar = Calendar.getInstance()
        val currentMonthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)
        val timestamp = System.currentTimeMillis()

        val borrowId = UUID.randomUUID().toString()
        val currentUserId = mAuth?.currentUserOrNull()?.id

        if (currentUserId != null && !lender.isNullOrEmpty()) {
            borrowerID = currentUserId

            UserHelper.getUidByUsername(lender, object : UserHelper.UidCallback {
                override fun onUidRetrieved(uid: String?) {
                    lenderID = uid
                    if (lenderID == null) {
                        Toast.makeText(this@BorrowNowActivity, "Lender not found", Toast.LENGTH_SHORT).show()
                        dialogProgressBar?.visibility = View.GONE
                        return
                    }

                    val borrowNowTransaction = BorrowNowTransaction(
                        borrowId,
                        borrowerID,
                        lenderID,
                        currentNickname,
                        timestamp,
                        lender,
                        borrowedAmount.toDouble(),
                        status,
                        timestamp
                    )
                    borrowNowTransaction.setMonthYear(currentMonthYear)

                    lifecycleScope.launch {
                        try {
                            DeclareDatabase.borrowsTable.insert(borrowNowTransaction)
                            
                            BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null)
                            BalanceHelper.addLenderEntry(lenderID, borrowId, null)

                            BalanceHelper.updateTotaldebt(borrowerID, borrowedAmount.toDouble(), null)
                            BalanceHelper.updateTotalreceivable(lenderID, borrowedAmount.toDouble(), null)

                            Toast.makeText(this@BorrowNowActivity, "Borrowed successfully", Toast.LENGTH_SHORT).show()
                            dialogProgressBar?.visibility = View.GONE
                            finish()
                        } catch (e: Exception) {
                            Log.e("BorrowNowActivity", "Failed to Borrow: ${e.message}")
                            dialogProgressBar?.visibility = View.GONE
                            Toast.makeText(this@BorrowNowActivity, "Failed to Borrow", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(error: String?) {
                    Log.e("BorrowNowActivity", "Error getting lender ID: $error")
                    dialogProgressBar?.visibility = View.GONE
                    Toast.makeText(this@BorrowNowActivity, "Error retrieving lender info", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun setupBorrowBtn() {
        borrowBtn?.setOnClickListener {
            val borrowEditText: EditText = findViewById(R.id.dialogBorrowEditText)
            val amountStr = borrowEditText.text.toString()
            if (amountStr.isNotEmpty() && !lender.isNullOrEmpty()) {
                try {
                    borrowedAmount = amountStr.toInt()
                    borrowedAmountSTR = amountStr

                    if (borrowedAmount <= 0) {
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    dialogProgressBar?.visibility = View.VISIBLE
                    addBorrowTransaction()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please select a lender and enter amount", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        val borrowEditText: EditText? = findViewById(R.id.dialogBorrowEditText)
        borrowEditText?.setOnTouchListener { v, _ ->
            v.performClick()
            false
        }

        findViewById<View>(android.R.id.content)?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                borrowEditText?.let { hideKeyboard(it) }
            }
            false
        }
    }

    private fun hideKeyboard(editText: EditText) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }
}
