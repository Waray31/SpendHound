package com.waray.spendhound

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.target.Target
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.ConcurrentHashMap

class PayorAdapter(
    private val payorsUids: MutableList<String?>?,
    private val payorsNames: MutableList<String?>?,
    amountsPaid: MutableList<Double?>,
    private val individualPayment: Double,
    private val onPayorClickListener: OnPayorClickListener?
) : RecyclerView.Adapter<PayorViewHolder?>() {
    var amountsPaid: MutableList<Double?>?
        private set
    private var originalAmountsPaid: MutableList<Double?>
    private var loadingCompleteListener: OnLoadingCompleteListener? = null
    private var dataChangedListener: OnDataChangedListener? = null
    private val loadedPositions: MutableSet<Int?> = HashSet<Int?>()
    private var isEditMode = false

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
        if ((payorsUids == null || payorsUids.isEmpty()) && loadingCompleteListener != null) {
            loadingCompleteListener!!.onLoadingComplete()
        }
    }

    fun setOnDataChangedListener(listener: OnDataChangedListener?) {
        this.dataChangedListener = listener
    }

    init {
        this.amountsPaid = ArrayList<Double?>(amountsPaid)
        this.originalAmountsPaid = ArrayList<Double?>(amountsPaid)
    }

    /**
     * Proactively starts loading all images for this transaction.
     * Ensures the loading overlay stays visible until all images are ready.
     */
    fun startLoadingAllImages(context: Context) {
        if (payorsUids == null || payorsUids.isEmpty()) {
            if (loadingCompleteListener != null) loadingCompleteListener!!.onLoadingComplete()
            return
        }

        // We don't clear loadedPositions here because onBindViewHolder might have already started/finished some loads
        for (i in payorsUids.indices) {
            val pos = i
            val uid = payorsUids.get(pos)
            val cachedUrl: String? = sDownloadUrlCache.get(uid)

            if (cachedUrl != null) {
                preloadProfileImage(context, cachedUrl, pos)
            } else {
                val pStorageRef: StorageReference =
                    FirebaseStorage.getInstance().getReference("profile_images").child(uid)
                pStorageRef.getDownloadUrl().addOnSuccessListener({ uri ->
                    val url: String? = uri.toString()
                    sDownloadUrlCache.put(uid, url)
                    preloadProfileImage(context, url, pos)
                }).addOnFailureListener({ e -> checkLoadingComplete(pos) })
            }
        }
    }

    private fun preloadProfileImage(context: Context, url: String?, position: Int) {
        Glide.with(context)
            .load(url)
            .circleCrop() // Consistent with onBindViewHolder for cache sharing
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    checkLoadingComplete(position)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    checkLoadingComplete(position)
                    return false
                }
            })
            .preload()
    }

    fun setEditMode(editMode: Boolean) {
        this.isEditMode = editMode
        if (!editMode) {
            this.amountsPaid = ArrayList<Double?>(originalAmountsPaid)
        }
        loadedPositions.clear() // Reset loading state if re-binding everything
        notifyDataSetChanged()
        notifyDataChanged()
    }

    fun saveChanges() {
        this.originalAmountsPaid = ArrayList<Double?>(amountsPaid)
        this.isEditMode = false
        loadedPositions.clear()
        notifyDataSetChanged()
        notifyDataChanged()
    }

    fun hasChanges(): Boolean {
        if (amountsPaid!!.size != originalAmountsPaid.size) return true
        for (i in amountsPaid!!.indices) {
            if (!amountsPaid!!.get(i)!!.equals(originalAmountsPaid.get(i))) {
                return true
            }
        }
        return false
    }

    private fun notifyDataChanged() {
        if (dataChangedListener != null) {
            dataChangedListener!!.onDataChanged(hasChanges())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PayorViewHolder {
        val view: View = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_payor_horizontal, parent, false)
        return PayorViewHolder(view)
    }

    override fun onBindViewHolder(holder: PayorViewHolder, position: Int) {
        val uid = payorsUids!!.get(position)
        val name =
            if (payorsNames != null && position < payorsNames.size) payorsNames.get(position) else "User"
        val paid =
            (if (amountsPaid != null && position < amountsPaid.size) amountsPaid.get(position) else 0.0)!!

        holder.payorName.setText(name)
        holder.payorPayment.setText(
            CurrencyUtils.formatAmount(paid) + "/" + CurrencyUtils.formatAmount(
                individualPayment
            )
        )

        updateStatusUI(holder, paid)

        if (isEditMode) {
            holder.editButtonsLayout.setVisibility(View.VISIBLE)

            if (paid <= 0) {
                holder.unpaidBtn.setVisibility(View.GONE)
                holder.paidBtn.setVisibility(View.VISIBLE)
                holder.partialBtn.setVisibility(View.VISIBLE)
            } else if (paid >= individualPayment) {
                holder.unpaidBtn.setVisibility(View.VISIBLE)
                holder.paidBtn.setVisibility(View.GONE)
                holder.partialBtn.setVisibility(View.VISIBLE)
            } else {
                holder.unpaidBtn.setVisibility(View.VISIBLE)
                holder.paidBtn.setVisibility(View.VISIBLE)
                holder.partialBtn.setVisibility(View.VISIBLE)
            }

            holder.unpaidBtn.setOnClickListener(View.OnClickListener { v: View? ->
                amountsPaid!!.set(position, 0.0)
                notifyItemChanged(position)
                notifyDataChanged()
            })

            holder.paidBtn.setOnClickListener(View.OnClickListener { v: View? ->
                amountsPaid!!.set(position, individualPayment)
                notifyItemChanged(position)
                notifyDataChanged()
            })

            holder.partialBtn.setOnClickListener(View.OnClickListener { v: View? ->
                if (onPayorClickListener != null) {
                    onPayorClickListener.onPartialClick(position, amountsPaid!!.get(position)!!)
                }
            })
        } else {
            holder.editButtonsLayout.setVisibility(View.GONE)
        }

        val cachedUrl: String? = sDownloadUrlCache.get(uid)
        if (cachedUrl != null) {
            loadGlideImage(holder, cachedUrl, position)
        } else {
            val pStorageRef: StorageReference =
                FirebaseStorage.getInstance().getReference("profile_images").child(uid)
            pStorageRef.getDownloadUrl().addOnSuccessListener({ uri ->
                val url: String? = uri.toString()
                sDownloadUrlCache.put(uid, url)
                loadGlideImage(holder, url, position)
            }).addOnFailureListener({ e ->
                holder.payorImage.setImageResource(R.drawable.placeholder_profile_image)
                checkLoadingComplete(position)
            })
        }

        if (!isEditMode && onPayorClickListener != null) {
            holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                onPayorClickListener.onPayorClick(
                    position,
                    paid
                )
            })
        } else {
            holder.itemView.setOnClickListener(null)
        }
    }

    private fun loadGlideImage(holder: PayorViewHolder, url: String?, position: Int) {
        Glide.with(holder.itemView.getContext())
            .load(url)
            .placeholder(R.drawable.placeholder_profile_image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    checkLoadingComplete(position)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    checkLoadingComplete(position)
                    return false
                }
            })
            .into(holder.payorImage)
    }

    private fun updateStatusUI(holder: PayorViewHolder, paid: Double) {
        if (paid <= 0) {
            holder.payorStatus.setText("Unpaid")
            holder.payorStatus.setTextColor(
                holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark)
            )
        } else if (paid < individualPayment) {
            holder.payorStatus.setText("Paid Partially")
            holder.payorStatus.setTextColor(
                holder.itemView.getContext().getResources()
                    .getColor(android.R.color.holo_orange_dark)
            )
        } else {
            holder.payorStatus.setText("Paid")
            holder.payorStatus.setTextColor(
                holder.itemView.getContext().getResources()
                    .getColor(android.R.color.holo_green_dark)
            )
        }
    }

    fun updatePartialAmount(index: Int, amount: Double) {
        if (index < amountsPaid!!.size) {
            amountsPaid!!.set(index, amount)
            notifyItemChanged(index)
            notifyDataChanged()
        }
    }

    @Synchronized
    private fun checkLoadingComplete(position: Int) {
        loadedPositions.add(position)
        if (loadedPositions.size >= this.itemCount && loadingCompleteListener != null) {
            loadingCompleteListener!!.onLoadingComplete()
        }
    }

    val itemCount: Int
        get() = if (payorsUids != null) payorsUids.size else 0

    internal class PayorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var payorImage: ImageView
        var payorName: TextView
        var payorPayment: TextView
        var payorStatus: TextView
        var editButtonsLayout: View
        var unpaidBtn: Button
        var paidBtn: Button
        var partialBtn: Button

        init {
            payorImage = itemView.findViewById<ImageView>(R.id.payorProfileImage)
            payorName = itemView.findViewById<TextView>(R.id.payorNameTextView)
            payorPayment = itemView.findViewById<TextView>(R.id.payorPaymentTextView)
            payorStatus = itemView.findViewById<TextView>(R.id.payorStatusTextView)
            editButtonsLayout = itemView.findViewById<View>(R.id.editButtonsLayout)
            unpaidBtn = itemView.findViewById<Button>(R.id.unpaid_btn)
            paidBtn = itemView.findViewById<Button>(R.id.paid_btn)
            partialBtn = itemView.findViewById<Button>(R.id.partial_btn)
        }
    }

    companion object {
        // Publicly accessible cache for download URLs to reduce Firebase Storage calls
        val sDownloadUrlCache: MutableMap<String?, String?> = ConcurrentHashMap<String?, String?>()

        /**
         * Pre-caches profile images for a list of UIDs.
         * This can be called before the adapter is even created or expanded to reduce wait time.
         */
        fun preCacheUids(context: Context?, uids: MutableList<String?>?) {
            if (uids == null || context == null) return
            for (uid in uids) {
                if (uid == null) continue
                val cachedUrl: String? = sDownloadUrlCache.get(uid)
                if (cachedUrl != null) {
                    preloadOnly(context, cachedUrl)
                } else {
                    FirebaseStorage.getInstance().getReference("profile_images").child(uid)
                        .getDownloadUrl().addOnSuccessListener({ uri ->
                            val url: String? = uri.toString()
                            sDownloadUrlCache.put(uid, url)
                            preloadOnly(context, url)
                        })
                }
            }
        }

        private fun preloadOnly(context: Context, url: String?) {
            Glide.with(context)
                .load(url)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload()
        }
    }
}
