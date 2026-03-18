package com.waray.spendhound

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.DataSnapshot

class PayerListTransactionAdapter(
    private val context: Context?,
    private val transactionList: MutableList<BorrowerListTransaction>,
    private val pathList: MutableList<Array<String?>?>,
    private val statusUpdatedListener: OnTransactionStatusUpdatedListener?,
    confirmAllPayerBtn: Button,
    denyAllPayerBtn: Button
) : RecyclerView.Adapter<PayerListTransactionAdapter.ViewHolder?>() {
    interface OnTransactionStatusUpdatedListener {
        fun onTransactionStatusUpdated()
    }

    init {
        confirmAllPayerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            handleAllTransactions(
                "Confirm"
            )
        })
        denyAllPayerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            handleAllTransactions(
                "Deny"
            )
        })
    }

    fun getBorrowerListTransaction(position: Int): BorrowerListTransaction? {
        return transactionList.get(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView: View =
            LayoutInflater.from(context).inflate(R.layout.payer_row_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactionList.get(position)
        holder.hoursAgoTV.setText(transaction.getDate())
        holder.payerNameTV.setText(transaction.getBorrowee())
        holder.amountPaidTV.setText(CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr()))

        // Set a placeholder image and a tag for the ImageView
        holder.payerImg.setImageResource(R.drawable.placeholder_profile_image)
        holder.payerImg.setTag(transaction.getBorrowee())

        // Load the profile image asynchronously
        setProfileImage(holder.payerImg, transaction.getBorrowee())

        holder.confirmPayerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            showConfirmationDialog(
                "Confirm",
                transaction,
                pathList.get(position)!!,
                position
            )
        })
        holder.denyPayerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            showConfirmationDialog(
                "Deny",
                transaction,
                pathList.get(position)!!,
                position
            )
        })
    }

    val itemCount: Int
        get() = transactionList.size

    private fun showConfirmationDialog(
        action: String?,
        transaction: BorrowerListTransaction,
        path: Array<String?>,
        position: Int
    ) {
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val dialogView: View = inflater.inflate(R.layout.dialog_borrowerlistconfirmation, null)

        val builder = AlertDialog.Builder(context!!)
        builder.setView(dialogView)

        val confirmAction: TextView = dialogView.findViewById<TextView>(R.id.confirmAction)
        val payNowConfirmBtn = dialogView.findViewById<Button>(R.id.payNowConfirmBtn)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        confirmAction.setText(action)

        val dialog = builder.create()

        payNowConfirmBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if ("Confirm".equals(action, ignoreCase = true)) {
                updateTransactionStatus(transaction, path, position, "Paid")
            } else if ("Deny".equals(action, ignoreCase = true)) {
                updateTransactionStatus(transaction, path, position, "Payment Denied")
            }
            dialog.dismiss()
        })

        closeButton.setOnClickListener(View.OnClickListener { v: View? -> dialog.dismiss() })

        dialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateTransactionStatus(
        transaction: BorrowerListTransaction,
        path: Array<String?>,
        position: Int,
        status: String?
    ) {
        val borrowRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("borrows")
            .child(path[0]).child(path[1]).child(path[2]).child(path[3])

        // If status is being changed to "Paid", decrement balance fields
        if ("Paid" == status) {
            borrowRef.addListenerForSingleValueEvent(object : ValueEventListener() {
                public override fun onDataChange(dataSnapshot: com.google.firebase.database.DataSnapshot) {
                    val borrow: BorrowNowTransaction? =
                        dataSnapshot.getValue(BorrowNowTransaction::class.java)
                    if (borrow != null && !("Paid" == borrow.getStatus())) {
                        // Only decrement if it wasn't already paid
                        try {
                            val amount = borrow.getBorrowedAmountStr().toInt()
                            val borrowerID = borrow.getBorrowerID()
                            val lenderID = borrow.getLenderID()

                            // Decrement balances (negative amount to subtract)
                            if (borrowerID != null) {
                                BalanceHelper.updateTotaldebt(borrowerID, -amount, null)
                            }
                            if (lenderID != null) {
                                BalanceHelper.updateTotalreceivable(lenderID, -amount, null)
                            }
                        } catch (e: NumberFormatException) {
                            Log.e(
                                "PayerListTransactionAdapter",
                                "Error parsing borrow amount: " + e.message
                            )
                        }
                    }

                    // Update status
                    transaction.setStatus(status)
                    borrowRef.child("status").setValue(status)
                        .addOnSuccessListener({ aVoid ->
                            transactionList.set(position, transaction)
                            notifyDataSetChanged()
                            if (statusUpdatedListener != null) {
                                statusUpdatedListener.onTransactionStatusUpdated()
                            }
                        })
                        .addOnFailureListener({ e ->
                            Toast.makeText(
                                context,
                                "Failed to update status: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                }

                public override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(
                        "PayerListTransactionAdapter",
                        "Failed to read borrow data: " + error.getMessage()
                    )
                    Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            // If not changing to "Paid", just update the status
            transaction.setStatus(status)
            borrowRef.child("status").setValue(status)
                .addOnSuccessListener({ aVoid ->
                    transactionList.set(position, transaction)
                    notifyDataSetChanged()
                    if (statusUpdatedListener != null) {
                        statusUpdatedListener.onTransactionStatusUpdated()
                    }
                })
                .addOnFailureListener({ e ->
                    Toast.makeText(
                        context,
                        "Failed to update status: " + e.getMessage(),
                        Toast.LENGTH_SHORT
                    ).show()
                })
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var payerImg: ImageView
        var hoursAgoTV: TextView
        var payerNameTV: TextView
        var amountPaidTV: TextView
        var confirmPayerBtn: Button
        var denyPayerBtn: Button

        init {
            payerImg = itemView.findViewById<ImageView>(R.id.payerImg)
            hoursAgoTV = itemView.findViewById<TextView>(R.id.hoursAgoTV)
            payerNameTV = itemView.findViewById<TextView>(R.id.payerNameTV)
            amountPaidTV = itemView.findViewById<TextView>(R.id.amountPaidTV)
            confirmPayerBtn = itemView.findViewById<Button>(R.id.confirmPayerBtn)
            denyPayerBtn = itemView.findViewById<Button>(R.id.denyPayerBtn)
        }
    }

    private fun handleAllTransactions(action: String) {
        showAllConfirmationDialog(action)
    }

    private fun showAllConfirmationDialog(action: String) {
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val dialogView: View = inflater.inflate(R.layout.dialog_borrowerlistconfirmation, null)

        val builder = AlertDialog.Builder(context!!)
        builder.setView(dialogView)

        val confirmAction: TextView = dialogView.findViewById<TextView>(R.id.confirmAction)
        val payNowConfirmBtn = dialogView.findViewById<Button>(R.id.payNowConfirmBtn)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        confirmAction.setText(action)

        val dialog = builder.create()

        payNowConfirmBtn.setOnClickListener(View.OnClickListener { v: View? ->
            updateAllTransactionStatus(if (action == "Confirm") "Paid" else "Payment Denied")
            dialog.dismiss()
        })

        closeButton.setOnClickListener(View.OnClickListener { v: View? -> dialog.dismiss() })

        dialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateAllTransactionStatus(status: String?) {
        for (i in transactionList.indices) {
            val transaction = transactionList.get(i)
            val path = pathList.get(i)!!
            val index = i
            val userRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("borrows")
                .child(path[0]).child(path[1]).child(path[2]).child(path[3])

            transaction.setStatus(status)
            userRef.child("status").setValue(status)
                .addOnSuccessListener({ aVoid ->
                    transactionList.set(index, transaction)
                    if (index == transactionList.size - 1) {
                        notifyDataSetChanged()
                        if (statusUpdatedListener != null) {
                            statusUpdatedListener.onTransactionStatusUpdated()
                        }
                    }
                })
                .addOnFailureListener({ e ->
                    Toast.makeText(
                        context,
                        "Failed to update status: " + e.getMessage(),
                        Toast.LENGTH_SHORT
                    ).show()
                })
        }
    }

    private fun setProfileImage(imageView: ImageView?, payerNameTV: String?) {
        if (imageView == null || payerNameTV == null) {
            Log.e("PayerListTransactionAdapter", "ImageView or payerNameTV is null.")
            return
        }

        val usersRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("users")
        val query: Query = usersRef.orderByChild("username").equalTo(payerNameTV)

        query.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (userSnapshot in dataSnapshot.getChildren()) {
                        val userId: String? =
                            userSnapshot.getKey() // Assuming the key is the userId
                        if (userId != null) {
                            val storageRef: StorageReference =
                                FirebaseStorage.getInstance().getReference("profile_images")
                                    .child(userId)
                            storageRef.getDownloadUrl().addOnSuccessListener({ uri ->
                                // Check if the tag is still valid before loading the image
                                if (payerNameTV == imageView.getTag() && context != null) {
                                    Glide.with(context).load(uri)
                                        .placeholder(R.drawable.placeholder_profile_image)
                                        .into(imageView)
                                }
                            }).addOnFailureListener({ e ->
                                Log.e(
                                    "FirebaseStorage",
                                    "Failed to get download URL: " + e.getMessage()
                                )
                                if (payerNameTV == imageView.getTag()) {
                                    imageView.setImageResource(R.drawable.placeholder_profile_image) // default image
                                }
                            })
                        }
                    }
                } else {
                    Log.e(
                        "PayerListTransactionAdapter",
                        "No user found with username: " + payerNameTV
                    )
                    if (payerNameTV == imageView.getTag()) {
                        imageView.setImageResource(R.drawable.placeholder_profile_image) // default image
                    }
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(
                    "FirebaseDatabase",
                    "Profile image query cancelled: " + databaseError.getMessage()
                )
                if (payerNameTV == imageView.getTag()) {
                    imageView.setImageResource(R.drawable.placeholder_profile_image) // default image
                }
            }
        })
    }
}
