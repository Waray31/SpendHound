package com.waray.spendhound

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import kotlinx.coroutines.launch

class TransactionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        if (transactionId == -1L) { finish(); return }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        loadTransaction(transactionId)
    }

    private fun loadTransaction(id: Long) {
        lifecycleScope.launch {
            try {
                val tx = DeclareDatabase.transactionsTable.select {
                    filter { eq("id", id) }
                }.decodeSingleOrNull<TransactionFull>() ?: run { finish(); return@launch }

                runOnUiThread {
                    findViewById<TextView>(R.id.tvDetailTitle).text = tx.description ?: "Transaction"
                    findViewById<TextView>(R.id.tvDetailAmount).text =
                        CurrencyUtils.formatAmountWithCurrency(tx.totalAmount)
                    findViewById<TextView>(R.id.tvDetailStatus).text = when (tx.status) {
                        1, 3 -> "Settled"
                        else -> "Pending"
                    }
                    findViewById<TextView>(R.id.tvDetailDate).text = tx.createdAt?.take(10) ?: ""
                }
            } catch (e: Exception) {
                Toast.makeText(this@TransactionDetailActivity, "Failed to load: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
