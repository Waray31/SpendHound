package com.waray.spendhound

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class PayorAdapter(
    private val payorUserIds: MutableList<String?>?,
    private val payorsNames: MutableList<String?>?,
    amountsPaid: MutableList<Double?>,
    private val individualPayments: List<Double>,
    private val excessAmounts: List<Double>,
    private val onPayorClickListener: OnPayorClickListener?
) : RecyclerView.Adapter<PayorAdapter.PayorViewHolder>() {
    var amountsPaid: MutableList<Double?>? = null
        private set
    private var originalAmountsPaid: MutableList<Double?>
    private var loadingCompleteListener: OnLoadingCompleteListener? = null
    private var dataChangedListener: OnDataChangedListener? = null
    private val loadedPositions: MutableSet<Int> = HashSet()
    private var isEditMode = false
    private val scope = CoroutineScope(Dispatchers.Main)

    interface OnPayorClickListener {
        fun onPayorClick(index: Int, paid: Double)
        fun onPartialClick(index: Int, currentPaid: Double)
    }

    interface OnLoadingCompleteListener {
        fun onLoadingComplete()
    }

    interface OnDataChangedListener {
        fun onDataChanged(hasChanges: Boolean)
    }

    fun setOnLoadingCompleteListener(listener: OnLoadingCompleteListener?) {
        this.loadingCompleteListener = listener
        if (payorUserIds.isNullOrEmpty() && loadingCompleteListener != null) {
            loadingCompleteListener!!.onLoadingComplete()
        }
    }

    fun setOnDataChangedListener(listener: OnDataChangedListener?) {
        this.dataChangedListener = listener
    }

    init {
        this.amountsPaid = ArrayList(amountsPaid)
        this.originalAmountsPaid = ArrayList(amountsPaid)
    }

    fun startLoadingAllImages(context: Context) {
        if (payorUserIds.isNullOrEmpty()) {
            loadingCompleteListener?.onLoadingComplete()
            return
        }

        for (i in payorUserIds.indices) {
            val userId = payorUserIds[i] ?: continue
            val cachedUrl = sDownloadUrlCache[userId]

            if (cachedUrl != null) {
                preloadProfileImage(context, cachedUrl, i)
            } else {
                scope.launch {
                    try {
                        val uidLong = userId.toLongOrNull()
                        val user = withContext(Dispatchers.IO) {
                            if (uidLong != null) {
                                DeclareDatabase.usersTable.select { filter { eq("user_id", uidLong) } }.decodeSingleOrNull<User>()
                            } else null
                        }
                        val url = user?.profileImageUrl?.takeIf { it.isNotBlank() && it != "placeholder_profile_image" }
                            ?: withContext(Dispatchers.IO) {
                                DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                            }
                        sDownloadUrlCache[userId] = url
                        preloadProfileImage(context, url, i)
                    } catch (e: Exception) {
                        checkLoadingComplete(i)
                    }
                }
            }
        }
    }

    private fun preloadProfileImage(context: Context, url: String, position: Int) {
        // Coil preloading
        val request = coil.request.ImageRequest.Builder(context)
            .data(url)
            .transformations(CircleCropTransformation())
            .listener(
                onSuccess = { _, _ -> checkLoadingComplete(position) },
                onError = { _, _ -> checkLoadingComplete(position) }
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    fun setEditMode(editMode: Boolean) {
        this.isEditMode = editMode
        if (!editMode) {
            this.amountsPaid = ArrayList(originalAmountsPaid)
        }
        loadedPositions.clear()
        notifyDataSetChanged()
        notifyDataChanged()
    }

    fun saveChanges() {
        this.originalAmountsPaid = ArrayList(amountsPaid!!)
        this.isEditMode = false
        loadedPositions.clear()
        notifyDataSetChanged()
        notifyDataChanged()
    }

    fun hasChanges(): Boolean {
        if (amountsPaid!!.size != originalAmountsPaid.size) return true
        for (i in amountsPaid!!.indices) {
            if (amountsPaid!![i] != originalAmountsPaid[i]) return true
        }
        return false
    }

    private fun notifyDataChanged() {
        dataChangedListener?.onDataChanged(hasChanges())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PayorViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payor_horizontal, parent, false)
        return PayorViewHolder(view)
    }

    override fun onBindViewHolder(holder: PayorViewHolder, position: Int) {
        val userId = payorUserIds?.get(position)
        val name = if (payorsNames != null && position < payorsNames.size) payorsNames[position] ?: "User" else "User"
        val paid = if (amountsPaid != null && position < amountsPaid!!.size) amountsPaid!![position] ?: 0.0 else 0.0
        val individualPayment = individualPayments.getOrNull(position) ?: 0.0

        holder.payorName.text = name

        updateStatusUI(holder, paid, position)

        val diff = paid - individualPayment
        val excess = excessAmounts.getOrNull(position) ?: 0.0
        val epsilon = 0.01

        // Creditors show current excess (remaining receivable), debtors show diff (remaining debt)
        val balance = if (diff > epsilon) excess else diff

        if (kotlin.math.abs(balance) > epsilon) {
            holder.payorBalanceAmount.visibility = View.VISIBLE
            val sign = if (balance > epsilon) "+" else ""
            holder.payorBalanceAmount.text = "$sign${CurrencyUtils.formatAmountWithCurrency(balance)}"
            holder.payorBalanceAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, if (balance > epsilon) R.color.green else R.color.red))
        } else {
            holder.payorBalanceAmount.visibility = View.GONE
        }

        if (isEditMode) {
            holder.editButtonsLayout.visibility = View.VISIBLE
            holder.unpaidBtn.visibility = if (paid > 0) View.VISIBLE else View.GONE
            holder.paidBtn.visibility = if (paid < individualPayment) View.VISIBLE else View.GONE
            holder.partialBtn.visibility = View.VISIBLE

            holder.unpaidBtn.setOnClickListener {
                amountsPaid!![position] = 0.0
                notifyItemChanged(position)
                notifyDataChanged()
            }

            holder.paidBtn.setOnClickListener {
                amountsPaid!![position] = individualPayment
                notifyItemChanged(position)
                notifyDataChanged()
            }

            holder.partialBtn.setOnClickListener {
                onPayorClickListener?.onPartialClick(position, amountsPaid!![position]!!)
            }
        } else {
            holder.editButtonsLayout.visibility = View.GONE
        }

        if (userId != null) {
            val cachedUrl = sDownloadUrlCache[userId] ?: UserHelper.getCachedImageUrl(userId.toLongOrNull())
            if (cachedUrl != null) {
                loadCoilImage(holder, cachedUrl, position, name)
            } else {
                scope.launch {
                    try {
                        val uidLong = userId.toLongOrNull()
                        val user = withContext(Dispatchers.IO) {
                            if (uidLong != null) {
                                DeclareDatabase.usersTable.select { filter { eq("user_id", uidLong) } }.decodeSingleOrNull<User>()
                            } else null
                        }
                        val url = user?.profileImageUrl?.takeIf { it.isNotBlank() && it != "placeholder_profile_image" }
                            ?: withContext(Dispatchers.IO) {
                                DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                            }
                        sDownloadUrlCache[userId] = url
                        if (uidLong != null) {
                            user?.username?.let { UserHelper.updateCache(uidLong, it, url) }
                        }
                        loadCoilImage(holder, url, position, name)
                    } catch (e: Exception) {
                        holder.payorInitials.text = name.take(2).uppercase()
                        holder.payorInitials.visibility = View.VISIBLE
                        holder.payorImage.visibility = View.GONE
                        checkLoadingComplete(position)
                    }
                }
            }
        }

        if (!isEditMode) {
            holder.itemView.setOnClickListener { onPayorClickListener?.onPayorClick(position, paid) }
        } else {
            holder.itemView.setOnClickListener(null)
        }
    }

    private fun loadCoilImage(holder: PayorViewHolder, url: String?, position: Int, name: String) {
        holder.payorInitials.text = name.take(2).uppercase()
        holder.payorInitials.visibility = View.VISIBLE
        
        // Ensure image view is visible for Coil to load into it, but clear it first
        holder.payorImage.visibility = View.VISIBLE
        holder.payorImage.setImageDrawable(null)
        holder.payorImage.imageTintList = null

        if (url.isNullOrBlank()) {
            holder.payorImage.visibility = View.GONE
            checkLoadingComplete(position)
            return
        }

        holder.payorImage.load(url) {
            transformations(CircleCropTransformation())
            crossfade(true)
            listener(
                onSuccess = { _, _ ->
                    holder.payorInitials.visibility = View.GONE
                    holder.payorImage.visibility = View.VISIBLE
                    checkLoadingComplete(position)
                },
                onError = { _, _ ->
                    holder.payorInitials.visibility = View.VISIBLE
                    holder.payorImage.visibility = View.GONE
                    checkLoadingComplete(position)
                }
            )
        }
    }

    private fun updateStatusUI(holder: PayorViewHolder, paid: Double, position: Int) {
        val context = holder.itemView.context
        val individualPayment = individualPayments.getOrNull(position) ?: 0.0
        when {
            paid <= 0 -> {
                holder.payorStatus.text = "Unpaid"
                holder.payorStatus.setTextColor(ContextCompat.getColor(context, R.color.bright_red))
                holder.payorStatusBadge.visibility = View.VISIBLE
                holder.payorStatusBadge.setImageResource(R.drawable.ic_circle_checked)
                holder.payorStatusBadge.setColorFilter(ContextCompat.getColor(context, R.color.bright_red))
            }
            paid < individualPayment -> {
                holder.payorStatus.text = "Paid Partially"
                holder.payorStatus.setTextColor(ContextCompat.getColor(context, R.color.mid_orange))
                holder.payorStatusBadge.visibility = View.VISIBLE
                holder.payorStatusBadge.setImageResource(R.drawable.ic_circle_checked)
                holder.payorStatusBadge.setColorFilter(ContextCompat.getColor(context, R.color.mid_orange))
            }
            else -> {
                holder.payorStatus.text = "Paid"
                holder.payorStatus.setTextColor(ContextCompat.getColor(context, R.color.green))
                holder.payorStatusBadge.visibility = View.VISIBLE
                holder.payorStatusBadge.setImageResource(R.drawable.ic_circle_checked)
                holder.payorStatusBadge.setColorFilter(ContextCompat.getColor(context, R.color.green))
            }
        }
    }

    fun updatePartialAmount(index: Int, amount: Double) {
        if (index < amountsPaid!!.size) {
            amountsPaid!![index] = amount
            notifyItemChanged(index)
            notifyDataChanged()
        }
    }

    @Synchronized
    private fun checkLoadingComplete(position: Int) {
        loadedPositions.add(position)
        if (loadedPositions.size >= itemCount) {
            loadingCompleteListener?.onLoadingComplete()
        }
    }

    override fun getItemCount(): Int = payorUserIds?.size ?: 0

    class PayorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val payorImage: ImageView = itemView.findViewById(R.id.payorProfileImage)
        val payorInitials: TextView = itemView.findViewById(R.id.payorInitialsTextView)
        val payorStatusBadge: ImageView = itemView.findViewById(R.id.payorStatusBadge)
        val payorName: TextView = itemView.findViewById(R.id.payorNameTextView)

        val payorStatus: TextView = itemView.findViewById(R.id.payorStatusTextView)
        val payorBalanceAmount: TextView = itemView.findViewById(R.id.payorBalanceAmountTextView)
        val editButtonsLayout: View = itemView.findViewById(R.id.editButtonsLayout)
        val unpaidBtn: Button = itemView.findViewById(R.id.unpaid_btn)
        val paidBtn: Button = itemView.findViewById(R.id.paid_btn)
        val partialBtn: Button = itemView.findViewById(R.id.partial_btn)
    }

    companion object {
        val sDownloadUrlCache: MutableMap<String, String> = ConcurrentHashMap()

        fun preCacheUserIds(context: Context, userIds: MutableList<String?>) {
            val scope = CoroutineScope(Dispatchers.IO)
            for (userId in userIds) {
                if (userId == null) continue
                if (sDownloadUrlCache.containsKey(userId)) continue
                scope.launch {
                    try {
                        val url = DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                        sDownloadUrlCache[userId] = url
                    } catch (e: Exception) {}
                }
            }
        }

        private fun preloadOnly(context: Context, url: String) {
            val request = coil.request.ImageRequest.Builder(context)
                .data(url)
                .transformations(CircleCropTransformation())
                .build()
            context.imageLoader.enqueue(request)
        }
    }
}
