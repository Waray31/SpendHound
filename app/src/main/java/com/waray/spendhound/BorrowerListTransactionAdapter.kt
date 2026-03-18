package com.waray.spendhound

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.DataSnapshot

class BorrowerListTransactionAdapter(
    private val context: Context?,
    private val transactionList: MutableList<BorrowerListTransaction>,
    private val pathList: MutableList<Array<String?>?>,
    statusUpdatedListener: PendingStatusActivity?,
    acceptAllBorrowerBtn: Button,
    declineAllBorrowerBtn: Button
) : RecyclerView.Adapter<BorrowerListTransactionAdapter.ViewHolder?>() {
    private val statusUpdatedListener: OnTransactionStatusUpdatedListener?

    interface OnTransactionStatusUpdatedListener {
        fun onTransactionStatusUpdated()
    }

    init {
        this.statusUpdatedListener = statusUpdatedListener

        acceptAllBorrowerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            handleAllTransactions(
                "Accept"
            )
        })
        declineAllBorrowerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            handleAllTransactions(
                "Decline"
            )
        })
    }

    fun getBorrowerListTransaction(position: Int): BorrowerListTransaction? {
        return transactionList.get(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView: View =
            LayoutInflater.from(context).inflate(R.layout.borrower_row_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactionList.get(position)
        holder.hoursAgoTV.setText(transaction.getDate())
        holder.borrowerNameTV.setText(transaction.getBorrowee())
        holder.amountBorrowedTV.setText(CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr()))

        // Set a placeholder image and a tag for the ImageView
        holder.borrowerImg.setImageResource(R.drawable.placeholder_profile_image)
        holder.borrowerImg.setTag(transaction.getBorrowee())

        // Load the profile image asynchronously
        setProfileImage(holder.borrowerImg, transaction.getBorrowee())

        holder.acceptBorrowerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            showConfirmationDialog(
                "Accept",
                transaction,
                pathList.get(position)!!,
                position
            )
        })
        holder.declineBorrowerBtn.setOnClickListener(View.OnClickListener { v: View? ->
            showConfirmationDialog(
                "Decline",
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
            if ("Accept".equals(action, ignoreCase = true)) {
                updateTransactionStatus(transaction, path, position, "Unpaid")
            } else if ("Decline".equals(action, ignoreCase = true)) {
                updateTransactionStatus(transaction, path, position, "Declined")
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
        val userRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("borrows")
            .child(path[0]).child(path[1]).child(path[2]).child(path[3])

        transaction.setStatus(status)
        userRef.child("status").setValue(status)
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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var borrowerImg: ImageView
        var hoursAgoTV: TextView
        var borrowerNameTV: TextView
        var amountBorrowedTV: TextView
        var acceptBorrowerBtn: Button
        var declineBorrowerBtn: Button
        var acceptAllBorrowerBtn: Button
        var declineAllBorrowerBtn: Button

        init {
            borrowerImg = itemView.findViewById<ImageView>(R.id.borrowerImg)
            hoursAgoTV = itemView.findViewById<TextView>(R.id.hoursAgoTV)
            borrowerNameTV = itemView.findViewById<TextView>(R.id.borrowerNameTV)
            amountBorrowedTV = itemView.findViewById<TextView>(R.id.amountBorrowedTV)
            acceptBorrowerBtn = itemView.findViewById<Button>(R.id.acceptBorrowerBtn)
            declineBorrowerBtn = itemView.findViewById<Button>(R.id.declineBorrowerBtn)
            acceptAllBorrowerBtn = itemView.findViewById<Button>(R.id.acceptAllBorrowerBtn)
            declineAllBorrowerBtn = itemView.findViewById<Button>(R.id.declineAllBorrowerBtn)
        }
    }

    private fun handleAllTransactions(action: String) {
        showAllConfirmationDialog(action, transactionList.get(0), pathList.get(0)!!, 0, true)
    }

    private fun showAllConfirmationDialog(
        action: String,
        transaction: BorrowerListTransaction,
        path: Array<String?>,
        position: Int,
        isAll: Boolean
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
            if (isAll) {
                updateAllTransactionStatus(if (action == "Accept") "Unpaid" else "Declined")
            } else {
                if ("Accept".equals(action, ignoreCase = true)) {
                    updateTransactionStatus(transaction, path, position, "Unpaid")
                } else if ("Decline".equals(action, ignoreCase = true)) {
                    updateTransactionStatus(transaction, path, position, "Declined")
                }
            }
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

    /*public void AcceptDeclineAllBtnClicked(){
        acceptAllBorrowerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allTV.setVisibility(View.VISIBLE);
            }
        });
        declineAllBorrowerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allTV.setVisibility(View.VISIBLE);
            }
        });
    }*/
    private fun setProfileImage(imageView: ImageView?, borrowerNameTV: String?) {
        if (imageView == null || borrowerNameTV == null) {
            Log.e("BorrowerListTransactionAdapter", "ImageView or borrowerNameTV is null.")
            return
        }

        val usersRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("users")
        val query: Query = usersRef.orderByChild("username").equalTo(borrowerNameTV)

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
                                if (borrowerNameTV == imageView.getTag() && context != null) {
                                    Glide.with(context).load(uri)
                                        .placeholder(R.drawable.placeholder_profile_image)
                                        .into(imageView)
                                }
                            }).addOnFailureListener({ e ->
                                Log.e(
                                    "FirebaseStorage",
                                    "Failed to get download URL: " + e.getMessage()
                                )
                                if (borrowerNameTV == imageView.getTag()) {
                                    imageView.setImageResource(R.drawable.placeholder_profile_image) // default image
                                }
                            })
                        }
                    }
                } else {
                    if (borrowerNameTV == imageView.getTag()) {
                        imageView.setImageResource(R.drawable.placeholder_profile_image) // default image
                    }
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(
                    "FirebaseDatabase",
                    "Profile image query cancelled: " + databaseError.getMessage()
                )
                if (borrowerNameTV == imageView.getTag()) {
                    imageView.setImageResource(R.drawable.placeholder_profile_image) // default image
                }
            }
        })
    }
}
