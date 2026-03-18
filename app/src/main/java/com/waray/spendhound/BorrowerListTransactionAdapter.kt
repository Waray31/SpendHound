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

class BorrowerListTransactionAdapter(
    private val context: Context?,
    private val transactionList: MutableList<BorrowerListTransaction>,
    private val pathList: MutableList<Array<String?>?>,
    private val statusUpdatedListener: OnTransactionStatusUpdatedListener?,
    acceptAllBorrowerBtn: Button,
    declineAllBorrowerBtn: Button
) : RecyclerView.Adapter<BorrowerListTransactionAdapter.ViewHolder>() {
    private val adapterScope = CoroutineScope(Dispatchers.Main)

    interface OnTransactionStatusUpdatedListener {
        fun onTransactionStatusUpdated()
    }

    init {
        acceptAllBorrowerBtn.setOnClickListener {
            handleAllTransactions("Accept")
        }
        declineAllBorrowerBtn.setOnClickListener {
            handleAllTransactions("Decline")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView: View =
            LayoutInflater.from(context).inflate(R.layout.borrower_row_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactionList[position]
        holder.hoursAgoTV.text = transaction.getDate()
        holder.borrowerNameTV.text = transaction.getBorrowee()
        holder.amountBorrowedTV.text = transaction.getBorrowedAmountStr()

        holder.borrowerImg.setImageResource(R.drawable.placeholder_profile_image)
        holder.borrowerImg.tag = transaction.getBorrowee()

        setProfileImage(holder.borrowerImg, transaction.getBorrowee())

        holder.acceptBorrowerBtn.setOnClickListener {
            showConfirmationDialog("Accept", transaction, pathList[position]!!, position)
        }
        holder.declineBorrowerBtn.setOnClickListener {
            showConfirmationDialog("Decline", transaction, pathList[position]!!, position)
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
            val status = if ("Accept".equals(action, ignoreCase = true)) "Unpaid" else "Declined"
            updateTransactionStatus(transaction, path, position, status)
            dialog.dismiss()
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateTransactionStatus(
        transaction: BorrowerListTransaction,
        path: Array<String?>,
        position: Int,
        status: String?
    ) {
        val borrowId = path[2] ?: return
        adapterScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.update({
                        set("status", status)
                    }) {
                        filter { eq("borrowId", borrowId) }
                    }
                }
                transaction.setStatus(status)
                transactionList[position] = transaction
                notifyDataSetChanged()
                statusUpdatedListener?.onTransactionStatusUpdated()
            } catch (e: Exception) {
                Log.e("BorrowerListTransactionAdapter", "Update failed", e)
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
            val status = if (action == "Accept") "Unpaid" else "Declined"
            updateAllTransactionStatus(status)
            dialog.dismiss()
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateAllTransactionStatus(status: String?) {
        adapterScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transactionList.indices.forEach { i ->
                        val borrowId = pathList[i]?.get(2) ?: return@forEach
                        DeclareDatabase.borrowsTable.update({
                            set("status", status)
                        }) {
                            filter { eq("borrowId", borrowId) }
                        }
                    }
                }
                transactionList.forEach { it.setStatus(status) }
                notifyDataSetChanged()
                statusUpdatedListener?.onTransactionStatusUpdated()
            } catch (e: Exception) {
                Log.e("BorrowerListTransactionAdapter", "Update all failed", e)
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val borrowerImg: ImageView = itemView.findViewById(R.id.borrowerImg)
        val hoursAgoTV: TextView = itemView.findViewById(R.id.hoursAgoTV)
        val borrowerNameTV: TextView = itemView.findViewById(R.id.borrowerNameTV)
        val amountBorrowedTV: TextView = itemView.findViewById(R.id.amountBorrowedTV)
        val acceptBorrowerBtn: Button = itemView.findViewById(R.id.acceptBorrowerBtn)
        val declineBorrowerBtn: Button = itemView.findViewById(R.id.declineBorrowerBtn)
    }

    private fun setProfileImage(imageView: ImageView, borrowerName: String?) {
        if (borrowerName == null) return
        adapterScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("profileImageUrl")) {
                        filter { eq("username", borrowerName) }
                    }.decodeSingleOrNull<User>()
                }
                val imageUrl = user?.profileImageUrl
                if (imageUrl != null && borrowerName == imageView.tag && context != null) {
                    Glide.with(context).load(imageUrl).placeholder(R.drawable.placeholder_profile_image).into(imageView)
                }
            } catch (e: Exception) {
                Log.e("BorrowerListTransactionAdapter", "Profile image error", e)
            }
        }
    }
}
