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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@Serializable
private data class BorrowInsert(
    @SerialName("borrowed_amount") val borrowedAmount: Double,
    @SerialName("borrower_id")     val borrowerId: Long,
    @SerialName("lender_id")       val lenderId: Long,
    @SerialName("status")          val status: Int,
    @SerialName("created_at")      val createdAt: String
)

class BorrowNowActivity : AppCompatActivity() {

    private var lenderRecyclerView: RecyclerView? = null
    private var date: TextView? = null
    private var borrower: TextView? = null
    private var dialogProgressBar: View? = null
    private var borrowBtn: Button? = null
    private var cancelBtn: Button? = null
    private var adapter: LenderAdapter? = null
    private var lenders: MutableList<User?>? = null
    private var mAuth: Auth? = null

    private var currentUserNumericId: Long? = null
    private var selectedLenderUser: User? = null

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

        dialogProgressBar?.visibility = View.VISIBLE

        setDate()
        setupLenderRecyclerView()
        setupBorrowBtn()
        cancelBtn?.setOnClickListener { finish() }
        exitEditText()
        loadCurrentUser()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadCurrentUser() {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("user_id", "username")) {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                currentUserNumericId = user?.id
                borrower?.text = user?.username
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Error loading current user: ${e.message}")
            }
        }
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
                        selectedLenderUser = adapter?.getLenderAt(pos)
                    }
                }
            }
        })

        fetchLenders()
    }

    private fun updateLayoutEffect(recyclerView: RecyclerView) {
        val midpoint = recyclerView.width / 2f
        val d1 = 0.9f * midpoint
        val s0 = 1.6f; val s1 = 1.0f
        val a0 = 1.0f; val a1 = 0.5f

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            recyclerView.layoutManager?.let { lm ->
                val childMidpoint = (lm.getDecoratedRight(child) + lm.getDecoratedLeft(child)) / 2f
                val d = min(d1, abs(midpoint - childMidpoint))
                child.scaleX = s0 + (s1 - s0) * d / d1
                child.scaleY = s0 + (s1 - s0) * d / d1
                child.alpha  = a0 + (a1 - a0) * d / d1
            }
        }
    }

    private fun fetchLenders() {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                lenders?.clear()
                lenders?.add(User(username = ""))
                lenders?.add(User(username = ""))
                for (user in users) {
                    if (!user.username.isNullOrEmpty() && user.authId != authId) {
                        lenders?.add(user)
                    }
                }
                lenders?.add(User(username = ""))
                lenders?.add(User(username = ""))

                adapter?.notifyDataSetChanged()
                adapter?.preloadAllImages(this@BorrowNowActivity) {
                    runOnUiThread {
                        dialogProgressBar?.visibility = View.GONE
                        if ((lenders?.size ?: 0) > 2) {
                            lenderRecyclerView?.scrollToPosition(2)
                            lenderRecyclerView?.post {
                                selectedLenderUser = adapter?.getLenderAt(2)
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
        date?.text = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun addBorrowTransaction(amount: Double) {
        val borrowerId = currentUserNumericId
        val lenderId   = selectedLenderUser?.id

        if (borrowerId == null) {
            toast("User session not found. Please try again.")
            dialogProgressBar?.visibility = View.GONE
            return
        }
        if (lenderId == null) {
            toast("Please select a lender.")
            dialogProgressBar?.visibility = View.GONE
            return
        }
        if (borrowerId == lenderId) {
            toast("You cannot borrow from yourself.")
            dialogProgressBar?.visibility = View.GONE
            return
        }

        val createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).format(Date())

        lifecycleScope.launch {
            try {
                DeclareDatabase.borrowsTable.insert(
                    BorrowInsert(
                        borrowedAmount = amount,
                        borrowerId     = borrowerId,
                        lenderId       = lenderId,
                        status         = 1, // For Lender Approval
                        createdAt      = createdAt
                    )
                )
                toast("Borrowed successfully!")
                finish()
            } catch (e: Exception) {
                Log.e("BorrowNowActivity", "Failed to borrow: ${e.message}")
                toast("Failed to borrow: ${e.message}")
            } finally {
                dialogProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun setupBorrowBtn() {
        borrowBtn?.setOnClickListener {
            val borrowEditText: EditText = findViewById(R.id.dialogBorrowEditText)
            val amountStr = borrowEditText.text.toString().trim()
            val amount = amountStr.toDoubleOrNull()

            when {
                amountStr.isEmpty()          -> toast("Please enter an amount")
                amount == null || amount <= 0 -> toast("Please enter a valid amount")
                selectedLenderUser == null   -> toast("Please select a lender")
                else -> {
                    dialogProgressBar?.visibility = View.VISIBLE
                    addBorrowTransaction(amount)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        val borrowEditText: EditText? = findViewById(R.id.dialogBorrowEditText)
        borrowEditText?.setOnTouchListener { v, _ -> v.performClick(); false }
        findViewById<View>(android.R.id.content)?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                borrowEditText?.let { hideKeyboard(it) }
            }
            false
        }
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
