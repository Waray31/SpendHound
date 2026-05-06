package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PayerListTransactionAdapter(
    private val context: Context?,
    private val transactionList: MutableList<BorrowerListTransaction>,
    private val pathList: MutableList<Array<String?>?>,
    private val statusUpdatedListener: OnTransactionStatusUpdatedListener?,
    confirmAllPayerBtn: Button,
    denyAllPayerBtn: Button
) : RecyclerView.Adapter<PayerListTransactionAdapter.ViewHolder>() {
    private val adapterScope = CoroutineScope(Dispatchers.Main)

    interface OnTransactionStatusUpdatedListener {
        fun onTransactionStatusUpdated()
    }

    init {
        confirmAllPayerBtn.setOnClickListener {
            handleAllTransactions("Confirm")
        }
        denyAllPayerBtn.setOnClickListener {
            handleAllTransactions("Deny")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView: View =
            LayoutInflater.from(context).inflate(R.layout.payer_row_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactionList[position]
        holder.hoursAgoTV.text = transaction.date
        holder.payerNameTV.text = transaction.borrowee
        holder.amountPaidTV.text = transaction.borrowedAmountStr

        holder.payerImg.setImageResource(R.drawable.ic_profile_silhouette)
        holder.payerImg.tag = transaction.borrowee

        setProfileImage(holder.payerImg, transaction.borrowee)

        holder.confirmPayerBtn.setOnClickListener {
            showConfirmationDialog("Confirm", transaction, pathList[position]!!, position)
        }
        holder.denyPayerBtn.setOnClickListener {
            showConfirmationDialog("Deny", transaction, pathList[position]!!, position)
        }
    }

    override fun getItemCount(): Int = transactionList.size

    private fun showConfirmationDialog(
        action: String?,
        transaction: BorrowerListTransaction,
        path: Array<String?>,
        position: Int
    ) {
        val dialogView: View = LayoutInflater.from(context).inflate(R.layout.dialog_borrowerlistconfirmation, null)
        val builder = AlertDialog.Builder(context!!)
        builder.setView(dialogView)

        val confirmAction: TextView = dialogView.findViewById(R.id.confirmAction)
        val payNowConfirmBtn = dialogView.findViewById<Button>(R.id.payNowConfirmBtn)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        confirmAction.text = action
        val dialog = builder.create()

        payNowConfirmBtn.setOnClickListener {
            val statusInt = if ("Confirm".equals(action, ignoreCase = true)) 3 else 5 // 3=Paid, 5=Payment Denied
            updateTransactionStatus(transaction, path, position, statusInt)
            dialog.dismiss()
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun getStatusStr(statusInt: Int): String {
        return when (statusInt) {
            1 -> "For Lender Approval"
            2 -> "Pending Payment"
            3 -> "Paid"
            4 -> "Declined"
            5 -> "Payment Denied"
            6 -> "Removed"
            7 -> "Paid Partially"
            else -> "Unpaid"
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateTransactionStatus(
        transaction: BorrowerListTransaction,
        path: Array<String?>,
        position: Int,
        statusInt: Int
    ) {
        val borrowId = path[2] ?: return
        adapterScope.launch {
            try {
                if (statusInt == 3) { // Paid
                    val borrow = withContext(Dispatchers.IO) {
                        DeclareDatabase.borrowsTable.select {
                            filter { eq("id", borrowId.toLong()) }
                        }.decodeSingleOrNull<BorrowNowTransaction>()
                    }

                    if (borrow != null && borrow.getStatus() != "Paid") {
                        val amount = borrow.borrowedAmount ?: 0.0
                        val borrowerID = borrow.getBorrowerID()
                        val lenderID = borrow.getLenderID()

                        if (borrowerID != null) {
                            BalanceHelper.updateTotaldebt(borrowerID, -amount, null)
                        }
                        if (lenderID != null) {
                            BalanceHelper.updateTotalreceivable(lenderID, -amount, null)
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update({
                        set("status", statusInt)
                    }) {
                        filter { eq("id", borrowId.toLong()) }
                    }
                }

                transaction.status = getStatusStr(statusInt)
                transactionList[position] = transaction
                notifyDataSetChanged()
                statusUpdatedListener?.onTransactionStatusUpdated()
            } catch (e: Exception) {
                Log.e("PayerListTransactionAdapter", "Update failed", e)
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val payerImg: ImageView = itemView.findViewById(R.id.payerImg)
        val hoursAgoTV: TextView = itemView.findViewById(R.id.hoursAgoTV)
        val payerNameTV: TextView = itemView.findViewById(R.id.payerNameTV)
        val amountPaidTV: TextView = itemView.findViewById(R.id.amountPaidTV)
        val confirmPayerBtn: Button = itemView.findViewById(R.id.confirmPayerBtn)
        val denyPayerBtn: Button = itemView.findViewById(R.id.denyPayerBtn)
    }

    private fun handleAllTransactions(action: String) {
        if (transactionList.isEmpty()) return
        showAllConfirmationDialog(action)
    }

    private fun showAllConfirmationDialog(action: String) {
        val dialogView: View = LayoutInflater.from(context).inflate(R.layout.dialog_borrowerlistconfirmation, null)
        val builder = AlertDialog.Builder(context!!)
        builder.setView(dialogView)

        val confirmAction: TextView = dialogView.findViewById(R.id.confirmAction)
        val payNowConfirmBtn = dialogView.findViewById<Button>(R.id.payNowConfirmBtn)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        confirmAction.text = action
        val dialog = builder.create()

        payNowConfirmBtn.setOnClickListener {
            val statusInt = if (action == "Confirm") 3 else 5
            updateAllTransactionStatus(statusInt)
            dialog.dismiss()
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateAllTransactionStatus(statusInt: Int) {
        adapterScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transactionList.indices.forEach { i ->
                        val borrowId = pathList[i]?.get(2) ?: return@forEach
                        
                        if (statusInt == 3) {
                            val borrow = DeclareDatabase.borrowsTable.select {
                                filter { eq("id", borrowId.toLong()) }
                            }.decodeSingleOrNull<BorrowNowTransaction>()

                            if (borrow != null && borrow.getStatus() != "Paid") {
                                val amount = borrow.borrowedAmount ?: 0.0
                                val borrowerID = borrow.getBorrowerID()
                                val lenderID = borrow.getLenderID()

                                if (borrowerID != null) {
                                    BalanceHelper.updateTotaldebt(borrowerID, -amount, null)
                                }
                                if (lenderID != null) {
                                    BalanceHelper.updateTotalreceivable(lenderID, -amount, null)
                                }
                            }
                        }

                        DeclareDatabase.borrowsTable.update({
                            set("status", statusInt)
                        }) {
                            filter { eq("id", borrowId.toLong()) }
                        }
                    }
                }
                transactionList.forEach { it.status = getStatusStr(statusInt) }
                notifyDataSetChanged()
                statusUpdatedListener?.onTransactionStatusUpdated()
            } catch (e: Exception) {
                Log.e("PayerListTransactionAdapter", "Update all failed", e)
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setProfileImage(imageView: ImageView, payerName: String?) {
        if (payerName == null) return
        adapterScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("profile_image_url")) {
                        filter { eq("username", payerName) }
                    }.decodeSingleOrNull<User>()
                }
                val imageUrl = user?.profileImageUrl
                if (imageUrl != null && payerName == imageView.tag && context != null) {
                    Glide.with(context).load(imageUrl).placeholder(R.drawable.ic_profile_silhouette).into(imageView)
                }
            } catch (e: Exception) {
                Log.e("PayerListTransactionAdapter", "Profile image error", e)
            }
        }
    }
}
