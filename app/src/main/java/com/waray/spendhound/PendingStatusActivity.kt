package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PendingStatusActivity : AppCompatActivity(),
    BorrowerListTransactionAdapter.OnTransactionStatusUpdatedListener,
    PayerListTransactionAdapter.OnTransactionStatusUpdatedListener {
    private var borrowerListTV: TextView? = null
    private var payerListTV: TextView? = null
    private var allTV: TextView? = null
    private var borrowerListScrollView: ScrollView? = null
    private var payerListScrollView: ScrollView? = null
    private var backBtn: ImageView? = null
    private var borrowerImg: ImageView? = null
    private var payerImg: ImageView? = null
    private var borrowerListLinearLayout: LinearLayout? = null
    private var payerListLinearLayout: LinearLayout? = null
    private var borrowerListBtn: LinearLayout? = null
    private var payerListBtn: LinearLayout? = null
    var borrowerNum: Int = 0
    var payerNum: Int = 0
    var currentNickname2: String? = null
    private var borrowerListRecyclerView: RecyclerView? = null
    private var payerListRecyclerView: RecyclerView? = null
    private var adapter: BorrowerListTransactionAdapter? = null
    private var adapterPayer: PayerListTransactionAdapter? = null
    private var borrowerListTransactions: MutableList<BorrowerListTransaction> = ArrayList()
    private var payerListTransactions: MutableList<BorrowerListTransaction> = ArrayList()
    private var borrowerListPath: MutableList<Array<String?>?> = ArrayList()
    private var payerListPath: MutableList<Array<String?>?> = ArrayList()
    private var mAuth: Auth? = null

    var acceptAllBorrowerBtn: Button? = null
    var declineAllBorrowerBtn: Button? = null
    var acceptBorrowerBtn: Button? = null
    var declineBorrowerBtn: Button? = null
    var confirmPayerBtn: Button? = null
    var denyPayerBtn: Button? = null
    var confirmAllPayerBtn: Button? = null
    var denyAllPayerBtn: Button? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_status)

        mAuth = DeclareDatabase.auth

        borrowerListTV = findViewById(R.id.borrowerListTV)
        payerListTV = findViewById(R.id.payerListTV)
        borrowerListScrollView = findViewById(R.id.borrowerListScrollView)
        payerListScrollView = findViewById(R.id.payerListScrollView)
        backBtn = findViewById(R.id.backBtn)
        borrowerListLinearLayout = findViewById(R.id.borrowerListLinearLayout)
        payerListLinearLayout = findViewById(R.id.payerListLinearLayout)
        borrowerImg = findViewById(R.id.borrowerImg)
        acceptBorrowerBtn = findViewById(R.id.acceptBorrowerBtn)
        declineBorrowerBtn = findViewById(R.id.declineBorrowerBtn)
        acceptAllBorrowerBtn = findViewById(R.id.acceptAllBorrowerBtn)
        declineAllBorrowerBtn = findViewById(R.id.declineAllBorrowerBtn)
        allTV = findViewById(R.id.allTV)
        payerListBtn = findViewById(R.id.payerListBtn)
        borrowerListBtn = findViewById(R.id.borrowerListBtn)
        payerImg = findViewById(R.id.payerImg)
        confirmPayerBtn = findViewById(R.id.confirmPayerBtn)
        denyPayerBtn = findViewById(R.id.denyPayerBtn)
        confirmAllPayerBtn = findViewById(R.id.confirmAllPayerBtn)
        denyAllPayerBtn = findViewById(R.id.denyAllPayerBtn)

        setupBorrowerListTVClicked()
        setupPayerListTVClicked()
        setupBackButtonCLicked()

        fetchBorrowerList()
        fetchPayerList()
    }

    private fun setupBorrowerListTVClicked() {
        borrowerListTV?.setOnClickListener {
            payerListTV?.setBackgroundResource(R.drawable.button_background_invisible)
            borrowerListTV?.setBackgroundResource(R.drawable.top_round_border)
            payerListTV?.setTextColor(ContextCompat.getColor(this, R.color.whitest))
            borrowerListTV?.setTextColor(ContextCompat.getColor(this, R.color.darkBlue))
            borrowerListScrollView?.visibility = View.VISIBLE
            payerListScrollView?.visibility = View.GONE
            borrowerListLinearLayout?.visibility = View.VISIBLE
            payerListLinearLayout?.visibility = View.GONE
            borrowerListBtn?.visibility = View.VISIBLE
            payerListBtn?.visibility = View.GONE

            borrowerListTV?.isEnabled = false
            payerListTV?.isEnabled = true
        }
    }

    private fun setupPayerListTVClicked() {
        payerListTV?.setOnClickListener {
            borrowerListTV?.setBackgroundResource(R.drawable.button_background_invisible)
            payerListTV?.setBackgroundResource(R.drawable.top_round_border)
            borrowerListTV?.setTextColor(ContextCompat.getColor(this, R.color.whitest))
            payerListTV?.setTextColor(ContextCompat.getColor(this, R.color.darkBlue))
            payerListScrollView?.visibility = View.VISIBLE
            borrowerListScrollView?.visibility = View.GONE
            borrowerListLinearLayout?.visibility = View.GONE
            payerListLinearLayout?.visibility = View.VISIBLE
            borrowerListBtn?.visibility = View.GONE
            payerListBtn?.visibility = View.VISIBLE

            payerListTV?.isEnabled = false
            borrowerListTV?.isEnabled = true
        }
    }

    private fun fetchBorrowerList() {
        val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return
        
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter {
                            eq("lender_id", currentUserId)
                            eq("status", 1) // 1 = For Lender Approval
                        }
                    }.decodeList<BorrowNowTransaction>()
                }

                borrowerListTransactions.clear()
                borrowerListPath.clear()

                for (bnt in results) {
                    val borrowId = bnt.id?.toString() ?: ""
                    val borrowerName = bnt.borrowerName ?: "Unknown"
                    val borrowedAmountStr = CurrencyUtils.formatAmountWithCurrency(bnt.borrowedAmount ?: 0.0)
                    val timeDifferenceStr = calculateTimeDifference(bnt.timestamp)

                    borrowerListTransactions.add(BorrowerListTransaction(timeDifferenceStr, borrowerName, borrowedAmountStr, bnt.getStatus()))
                    borrowerListPath.add(arrayOf("", "", borrowId, ""))
                }

                adapter = BorrowerListTransactionAdapter(this@PendingStatusActivity, borrowerListTransactions, borrowerListPath, this@PendingStatusActivity, acceptAllBorrowerBtn!!, declineAllBorrowerBtn!!)
                borrowerListRecyclerView = findViewById(R.id.borrowerListRecyclerView)
                borrowerListRecyclerView?.adapter = adapter
                borrowerListRecyclerView?.layoutManager = LinearLayoutManager(this@PendingStatusActivity)
                
                borrowerNum = borrowerListTransactions.size
                updateBorrowerButtons(borrowerNum >= 2)
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching borrower list", e)
                showToast("Error fetching borrower list")
            }
        }
    }

    private fun updateBorrowerButtons(enabled: Boolean) {
        val color = if (enabled) R.color.yellow else R.color.grey
        val declineColor = if (enabled) R.color.red else R.color.grey
        
        acceptAllBorrowerBtn?.isEnabled = enabled
        declineAllBorrowerBtn?.isEnabled = enabled
        acceptAllBorrowerBtn?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, color))
        declineAllBorrowerBtn?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, declineColor))
    }

    private fun calculateTimeDifference(timestamp: Long): String {
        val secondsSinceDate = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            secondsSinceDate >= 31536000 -> "${secondsSinceDate / 31536000}y"
            secondsSinceDate >= 2592000 -> "${secondsSinceDate / 2592000}mo"
            secondsSinceDate >= 86400 -> "${secondsSinceDate / 86400}d"
            secondsSinceDate >= 3600 -> "${secondsSinceDate / 3600}h"
            secondsSinceDate >= 60 -> "${secondsSinceDate / 60}m"
            else -> "${secondsSinceDate}s"
        }
    }

    private fun fetchPayerList() {
        val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter {
                            eq("lender_id", currentUserId)
                            eq("status", 2) // 2 = Pending Payment
                        }
                    }.decodeList<BorrowNowTransaction>()
                }

                payerListTransactions.clear()
                payerListPath.clear()

                for (bnt in results) {
                    val borrowId = bnt.id?.toString() ?: ""
                    val borrowerName = bnt.borrowerName ?: "Unknown"
                    val borrowedAmountStr = CurrencyUtils.formatAmountWithCurrency(bnt.borrowedAmount ?: 0.0)
                    val timeDifferenceStr = calculateTimeDifference(bnt.timestamp)

                    payerListTransactions.add(BorrowerListTransaction(timeDifferenceStr, borrowerName, borrowedAmountStr, bnt.getStatus()))
                    payerListPath.add(arrayOf("", "", borrowId, ""))
                }

                adapterPayer = PayerListTransactionAdapter(this@PendingStatusActivity, payerListTransactions, payerListPath, this@PendingStatusActivity, confirmAllPayerBtn!!, denyAllPayerBtn!!)
                payerListRecyclerView = findViewById(R.id.payerListRecyclerView)
                payerListRecyclerView?.adapter = adapterPayer
                payerListRecyclerView?.layoutManager = LinearLayoutManager(this@PendingStatusActivity)
                
                payerNum = payerListTransactions.size
                updatePayerButtons(payerNum >= 2)
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching payer list", e)
                showToast("Error fetching payer list")
            }
        }
    }

    private fun updatePayerButtons(enabled: Boolean) {
        val color = if (enabled) R.color.yellow else R.color.grey
        val denyColor = if (enabled) R.color.red else R.color.grey
        
        confirmAllPayerBtn?.isEnabled = enabled
        denyAllPayerBtn?.isEnabled = enabled
        confirmAllPayerBtn?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, color))
        denyAllPayerBtn?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, denyColor))
    }

    private fun setupBackButtonCLicked() {
        backBtn?.setOnClickListener { finish() }
    }

    override fun onTransactionStatusUpdated() {
        fetchBorrowerList()
        fetchPayerList()
    }

    fun showToast(message: String?) {
        Toast.makeText(this@PendingStatusActivity, message, Toast.LENGTH_SHORT).show()
    }
}
