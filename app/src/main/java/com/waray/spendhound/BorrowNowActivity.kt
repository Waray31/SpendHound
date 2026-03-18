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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Objects
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
    private var usersRef: DatabaseReference? = null
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
        usersRef = FirebaseDatabase.getInstance().getReference("users")

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
                        lender = selectedLender?.getUsername()
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
        val usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID)
        usersRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentNickname = dataSnapshot.getValue(String::class.java)
                    borrower?.text = currentNickname
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.message)
            }
        })
    }

    private fun fetchLenders() {
        usersRef?.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                lenders?.clear()
                lenders?.add(User("", "", "", UserBalance()))
                lenders?.add(User("", "", "", UserBalance()))

                for (userSnapshot in dataSnapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    if (user != null && user.username != null && user.username != currentNickname) {
                        user.id = userSnapshot.key
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
                                lender = firstUser?.getUsername()
                                lenderRecyclerView?.let { updateLayoutEffect(it) }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.message)
                dialogProgressBar?.visibility = View.GONE
            }
        })
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
        val currentDay = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)
        val timestamp = System.currentTimeMillis()

        val databaseReference = DeclareDatabase.getDBRefBorrows()
        val dayRef = databaseReference.child(currentMonthYear).child(currentDay)

        val borrowId = dayRef.push().key ?: return run {
            Toast.makeText(this, "Failed to generate borrow ID", Toast.LENGTH_SHORT).show()
            dialogProgressBar?.visibility = View.GONE
        }

        val borrowRef = dayRef.child(borrowId)
        val currentUserId = mAuth?.currentUserOrNull()?.id

        if (currentUserId != null && !lender.isNullOrEmpty()) {
            borrowerID = currentUserId

            getUserIDByName(lender!!) { getLenderID ->
                lenderID = getLenderID

                val borrowNowTransaction = BorrowNowTransaction(
                    borrowId,
                    borrowerID,
                    lenderID,
                    currentNickname,
                    currentDate,
                    lender,
                    borrowedAmountSTR,
                    status,
                    timestamp
                )

                borrowRef.setValue(borrowNowTransaction)
                    .addOnSuccessListener {
                        BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null)
                        BalanceHelper.addLenderEntry(lenderID, borrowId, null)

                        val amount = borrowedAmountSTR?.toIntOrNull() ?: 0
                        BalanceHelper.updateTotaldebt(borrowerID, amount, null)
                        BalanceHelper.updateTotalreceivable(lenderID, amount, null)

                        Toast.makeText(this, "Borrowed successfully", Toast.LENGTH_SHORT).show()
                        dialogProgressBar?.visibility = View.GONE
                        finish()
                    }.addOnFailureListener {
                        dialogProgressBar?.visibility = View.GONE
                        Toast.makeText(this, "Failed to Borrow", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun setupBorrowBtn() {
        borrowBtn?.setOnClickListener {
            val borrowEditText: EditText = findViewById(R.id.dialogBorrowEditText)
            val amountStr = borrowEditText.text.toString()
            if (amountStr.isNotEmpty() && !lender.isNullOrEmpty()) {
                try {
                    borrowedAmount = amountStr.toInt()
                    borrowedAmountSTR = borrowedAmount.toString()

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

        findViewById<View>(android.R.id.content)?.setOnTouchListener { _, _ ->
            borrowEditText?.let { hideKeyboard(it) }
            false
        }
    }

    fun getUserIDByName(name: String, callback: (String?) -> Unit) {
        usersRef?.addListenerForSingleValueEvent(object : ValueEventListener() {
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

    private fun hideKeyboard(editText: EditText) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }
}
