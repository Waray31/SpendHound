package com.waray.spendhound

import android.content.Context
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import com.google.firebase.auth.FirebaseAuth
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


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_borrow_now)
        lenderRecyclerView = findViewById<RecyclerView>(R.id.lenderRecyclerView)
        date = findViewById<TextView>(R.id.dialogBorrowDate)
        borrower = findViewById<TextView>(R.id.dialogBorrower)
        dialogProgressBar = findViewById<View?>(R.id.dialogProgressBar)
        borrowBtn = findViewById<Button>(R.id.dialogBorrowBtn)
        cancelBtn = findViewById<Button?>(R.id.dialogCancelBtn)
        status = "For Lender Approval"
        usersRef = FirebaseDatabase.getInstance().getReference("users")

        // Show progress bar initially
        if (dialogProgressBar != null) {
            dialogProgressBar!!.setVisibility(View.VISIBLE)
        }

        setDate()
        setupLenderRecyclerView()
        borrowBtnClicked()

        if (cancelBtn != null) {
            cancelBtn!!.setOnClickListener(View.OnClickListener { v: View? -> finish() })
        }

        exitEditText()
        loadNickname()

        val actionBar = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupLenderRecyclerView() {
        val layoutManager: LinearLayoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        lenderRecyclerView.setLayoutManager(layoutManager)

        lenders = ArrayList<User?>()
        adapter = LenderAdapter(lenders)
        lenderRecyclerView.setAdapter(adapter)

        val snapHelper: SnapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(lenderRecyclerView)

        lenderRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
                        val selectedLender = adapter!!.getLenderAt(pos)
                        if (selectedLender != null) {
                            lender = selectedLender.getUsername()
                        }
                    }
                }
            }
        })

        this.users
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

    private fun loadNickname() {
        val currentUserID: String? =
            Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser()).getUid()
        val usersRef: DatabaseReference =
            DeclareDatabase.getDatabaseReference().child(currentUserID)
        usersRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentNickname = dataSnapshot.getValue(String::class.java)
                    borrower.setText(currentNickname)
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.getMessage())
            }
        })
    }

    val users: Unit
        get() {
            usersRef.addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    lenders!!.clear()
                    lenders!!.add(User("", "", "", "", UserBalance()))
                    lenders!!.add(User("", "", "", "", UserBalance()))

                    for (userSnapshot in dataSnapshot.getChildren()) {
                        val user: User? =
                            userSnapshot.getValue(User::class.java)
                        if (user != null && user.getUsername() != null && (user.getUsername() != currentNickname)) {
                            user.setUid(userSnapshot.getKey())
                            lenders!!.add(user)
                        }
                    }

                    lenders!!.add(User("", "", "", "", UserBalance()))
                    lenders!!.add(User("", "", "", "", UserBalance()))

                    adapter!!.notifyDataSetChanged()


                    // Preload all profile images before hiding progress bar
                    adapter!!.preloadAllImages(this@BorrowNowActivity, Runnable {
                        runOnUiThread(Runnable {
                            if (dialogProgressBar != null) {
                                dialogProgressBar!!.setVisibility(View.GONE)
                            }
                            // Initial selection and layout update
                            if (lenders!!.size > 2) {
                                lenderRecyclerView.scrollToPosition(2)
                                lenderRecyclerView.post(Runnable {
                                    val firstUser =
                                        adapter!!.getLenderAt(2)
                                    if (firstUser != null) {
                                        lender = firstUser.getUsername()
                                    }
                                    updateLayoutEffect(lenderRecyclerView)
                                })
                            }
                        })
                    })
                }

                public override fun onCancelled(databaseError: DatabaseError) {
                    Log.e(
                        "FirebaseDatabase",
                        "Database read error: " + databaseError.getMessage()
                    )
                    Toast.makeText(
                        getApplicationContext(),
                        "Failed to load users",
                        Toast.LENGTH_LONG
                    ).show()
                    if (dialogProgressBar != null) {
                        dialogProgressBar!!.setVisibility(View.GONE)
                    }
                }
            })
        }

    private fun setDate() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault())
        currentDate = dateFormat.format(calendar.getTime())
        date.setText(currentDate)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addBorrowTransaction() {
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
            Toast.makeText(
                this@BorrowNowActivity,
                "Failed to generate borrow ID",
                Toast.LENGTH_SHORT
            ).show()
            if (dialogProgressBar != null) dialogProgressBar!!.setVisibility(View.GONE)
            return
        }

        val borrowRef: DatabaseReference = dayRef.child(borrowId)

        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().getCurrentUser()
        if (currentUser != null && lender != null && !lender!!.isEmpty()) {
            borrowerID = currentUser.getUid()

            getUserIDByName(lender!!, object : UserIDCallback {
                override fun onUserIDRetrieved(getLenderID: String?) {
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
                        .addOnSuccessListener(object : OnSuccessListener<Void?> {
                            override fun onSuccess(unused: Void?) {
                                BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null)
                                BalanceHelper.addLenderEntry(lenderID, borrowId, null)

                                val amount = borrowedAmountSTR!!.toInt()
                                BalanceHelper.updateTotaldebt(borrowerID, amount, null)
                                BalanceHelper.updateTotalreceivable(lenderID, amount, null)

                                Toast.makeText(
                                    this@BorrowNowActivity,
                                    "Borrowed successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (dialogProgressBar != null) dialogProgressBar!!.setVisibility(
                                    View.GONE
                                )
                                finish()
                            }
                        }).addOnFailureListener(object : OnFailureListener {
                            override fun onFailure(e: Exception) {
                                if (dialogProgressBar != null) dialogProgressBar!!.setVisibility(
                                    View.GONE
                                )
                                Toast.makeText(
                                    this@BorrowNowActivity,
                                    "Failed to Borrow",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                }
            })
        }
    }

    private fun borrowBtnClicked() {
        borrowBtn!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val borrowEditText: EditText = findViewById<EditText>(R.id.dialogBorrowEditText)
                val borrowedAmountStr = borrowEditText.getText().toString()
                if (!borrowedAmountStr.isEmpty() && lender != null && !lender!!.isEmpty()) {
                    try {
                        borrowedAmount = borrowedAmountStr.toInt()
                        borrowedAmountSTR = borrowedAmount.toString()

                        if (borrowedAmount <= 0) {
                            Toast.makeText(
                                this@BorrowNowActivity,
                                "Please enter a valid amount",
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }

                        if (dialogProgressBar != null) dialogProgressBar!!.setVisibility(View.VISIBLE)
                        addBorrowTransaction()
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this@BorrowNowActivity, "Invalid amount", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(
                        this@BorrowNowActivity,
                        "Please select a lender and enter amount",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        val borrowEditText: EditText? = findViewById<EditText?>(R.id.dialogBorrowEditText)
        if (borrowEditText != null) {
            borrowEditText.setOnTouchListener(object : OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent?): Boolean {
                    v.performClick()
                    return false
                }
            })
        }

        val rootView = findViewById<View?>(android.R.id.content)
        if (rootView != null) {
            rootView.setOnTouchListener(object : OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    if (borrowEditText != null) hideKeyboard(borrowEditText)
                    return false
                }
            })
        }
    }

    fun getUserIDByName(name: String, callback: UserIDCallback) {
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
                Log.e("FirebaseDatabase", "Database error: " + databaseError.getMessage())
                callback.onUserIDRetrieved(null)
            }
        })
    }

    interface UserIDCallback {
        fun onUserIDRetrieved(getLenderID: String?)
    }

    private fun hideKeyboard(editText: EditText) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm != null) {
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)
        }
    }
}
