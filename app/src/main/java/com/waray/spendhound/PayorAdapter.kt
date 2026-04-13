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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class PayorAdapter(
    private val payorUserIds: MutableList<String?>?,
    private val payorsNames: MutableList<String?>?,
    amountsPaid: MutableList<Double?>,
    private val individualPayment: Double,
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
                        val url = withContext(Dispatchers.IO) {
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
        Glide.with(context)
            .load(url)
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable?>?, isFirstResource: Boolean): Boolean {
                    checkLoadingComplete(position)
                    return false
                }
                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable?>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    checkLoadingComplete(position)
                    return false
                }
            })
            .preload()
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
        val name = if (payorsNames != null && position < payorsNames.size) payorsNames[position] else "User"
        val paid = if (amountsPaid != null && position < amountsPaid!!.size) amountsPaid!![position] ?: 0.0 else 0.0

        holder.payorName.text = name


        updateStatusUI(holder, paid)

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
            val cachedUrl = sDownloadUrlCache[userId]
            if (cachedUrl != null) {
                loadGlideImage(holder, cachedUrl, position)
            } else {
                scope.launch {
                    try {
                        val url = withContext(Dispatchers.IO) {
                            DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                        }
                        sDownloadUrlCache[userId] = url
                        loadGlideImage(holder, url, position)
                    } catch (e: Exception) {
                        holder.payorImage.setImageResource(R.drawable.placeholder_profile_image)
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

    private fun loadGlideImage(holder: PayorViewHolder, url: String?, position: Int) {
        Glide.with(holder.itemView.context)
            .load(url)
            .placeholder(R.drawable.placeholder_profile_image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable?>?, isFirstResource: Boolean): Boolean {
                    checkLoadingComplete(position)
                    return false
                }
                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable?>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    checkLoadingComplete(position)
                    return false
                }
            })
            .into(holder.payorImage)
    }

    private fun updateStatusUI(holder: PayorViewHolder, paid: Double) {
        val context = holder.itemView.context
        when {
            paid <= 0 -> {
                holder.payorStatus.text = "Unpaid"
                holder.payorStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                holder.payorStatusBadge.visibility = View.GONE
            }
            paid < individualPayment -> {
                holder.payorStatus.text = "Paid Partially"
                holder.payorStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                holder.payorStatusBadge.visibility = View.VISIBLE
                holder.payorStatusBadge.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
            }
            else -> {
                holder.payorStatus.text = "Paid"
                holder.payorStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                holder.payorStatusBadge.visibility = View.VISIBLE
                holder.payorStatusBadge.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_green_dark))
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
        val payorStatusBadge: ImageView = itemView.findViewById(R.id.payorStatusBadge)
        val payorName: TextView = itemView.findViewById(R.id.payorNameTextView)

        val payorStatus: TextView = itemView.findViewById(R.id.payorStatusTextView)
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
            Glide.with(context)
                .load(url)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload()
        }
    }
}
