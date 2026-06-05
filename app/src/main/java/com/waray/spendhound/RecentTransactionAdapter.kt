package com.waray.spendhound

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.waray.spendhound.ui.multi_transaction.MultiTransactionActivity
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecentTransactionAdapter(
    val recentTransactionList: ArrayList<RecentTransaction>?,
    private val onSettleRefresh: (() -> Unit)? = null,
    private var clickListener: OnTransactionClickListener? = null,
    private val onLongPress: ((RecentTransaction, View) -> Unit)? = null
) : RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder>() {

    private val scope = CoroutineScope(Dispatchers.Main)

    // Cache: authId -> numeric user_id
    private var cachedCurrentNumericId: Long? = null

    fun interface OnTransactionClickListener {
        fun onTransactionClick(transaction: RecentTransaction?)
    }

    constructor(recentTransactionList: ArrayList<RecentTransaction>?) : this(recentTransactionList, null, null)

    fun setOnTransactionClickListener(listener: OnTransactionClickListener?) {
        this.clickListener = listener
    }

    fun preloadAllImages(context: Context?) {
        if (recentTransactionList == null || context == null) return
        for (transaction in recentTransactionList) {
            val userIds = transaction.payorUserIds
            if (userIds != null && userIds.isNotEmpty()) {
                PayorAdapter.preCacheUserIds(context, userIds)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = recentTransactionList?.get(position) ?: return

        holder.dateTextView.text = transaction.mostRecentDate
        val isSingle = transaction.transactionItems.size == 1
        holder.typeTextView.text = if (isSingle)
            transaction.transactionItems[0].category ?: transaction.mostRecentTransactionType ?: holder.itemView.context.getString(R.string.label_transaction_placeholder)
        else
            transaction.mostRecentTransactionType ?: holder.itemView.context.getString(R.string.label_transaction_placeholder)

        holder.amountTextView.text = transaction.mostRecentPaymentAmountStr
        holder.iconImageView.setImageResource(getIconForItems(transaction.transactionItems))

        // Archived badge visibility
        holder.archivedBadge.visibility = if (transaction.isArchived) View.VISIBLE else View.GONE
        
        // Apply archived styling
        if (transaction.isArchived) {
            holder.itemView.alpha = 0.6f
        } else {
            holder.itemView.alpha = 1.0f
        }

        // Status: Settled = green, Pending = yellow
        val status = transaction.transactionStatus
        holder.detailsTextView.text = when(status) {
            "Settled" -> holder.itemView.context.getString(R.string.status_settled)
            "Pending" -> holder.itemView.context.getString(R.string.status_pending)
            else -> status
        }
        holder.detailsTextView.setTextColor(
            ContextCompat.getColor(holder.itemView.context,
                if (status == "Settled") R.color.green else R.color.yellow)
        )

        // Unread indicator logic
        if (transaction.isUnread) {
            holder.mainContent.setBackgroundResource(R.drawable.bg_unread_transaction)
        } else {
            holder.mainContent.setBackgroundResource(0)
        }

        val isExpanded = transaction.isExpanded
        holder.expandableLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            holder.loadingOverlay.visibility = View.VISIBLE
            holder.createdByTextView.text = transaction.createdBy ?: holder.itemView.context.getString(R.string.label_unknown)

            if (!transaction.groupName.isNullOrEmpty()) {
                holder.onGroupLabel.visibility = View.VISIBLE
                holder.expandedGroupName.visibility = View.VISIBLE
                holder.expandedGroupName.text = transaction.groupName
            } else {
                holder.onGroupLabel.visibility = View.GONE
                holder.expandedGroupName.visibility = View.GONE
            }

            if (isSingle) {
                val item = transaction.transactionItems[0]
                holder.itemsTableHeader.visibility = View.GONE
                holder.itemsTableContainer.visibility = View.GONE
                holder.dividerBelowItems.visibility = View.GONE
                
                val desc = item.itemDescription?.takeIf { it.isNotBlank() }
                val category = item.category?.takeIf { it.isNotBlank() }
                
                // If there's a custom description, show it.
                // If no description but there's a category, and it's NOT the same as the title, show it.
                // (Title usually IS the category for single items, so we check)
                val title = holder.typeTextView.text.toString()
                
                if (desc != null) {
                    holder.tvSingleDescription.text = "Description: $desc"
                    holder.tvSingleDescription.visibility = View.VISIBLE
                } else if (category != null && category != title) {
                    holder.tvSingleDescription.text = "Category: $category"
                    holder.tvSingleDescription.visibility = View.VISIBLE
                } else {
                    holder.tvSingleDescription.visibility = View.GONE
                }
            } else {
                holder.itemsTableHeader.visibility = View.VISIBLE
                holder.itemsTableContainer.visibility = View.VISIBLE
                holder.dividerBelowItems.visibility = View.VISIBLE
                holder.tvSingleDescription.visibility = View.GONE
                buildItemsTable(holder, transaction)
            }
            setupPayors(holder, transaction)
        } else {
            holder.loadingOverlay.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            Log.i("TX_DEBUG", "Adapter: Item clicked for transaction ID=${transaction.transactionId}")
            clickListener?.onTransactionClick(transaction)
        }
        
        // Long press for popup menu
        holder.itemView.setOnLongClickListener {
            onLongPress?.invoke(transaction, holder.itemView)
            true
        }
    }

    private fun setupPayors(holder: ViewHolder, transaction: RecentTransaction) {
        val payorUserIds   = transaction.payorUserIds
        val payorsNames    = transaction.payorsList
        val amountsPaid    = transaction.amountsPaidList
        val userOwedMap = transaction.rawSplitRows.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
        val individualPayments = payorUserIds?.map { uid -> userOwedMap[uid?.toLongOrNull()] ?: 0.0 } ?: emptyList()
        val excessAmounts = payorUserIds?.map { uid ->
            transaction.rawPayorRows.filter { it.userId == uid?.toLongOrNull() }.sumOf { it.excessAmount }
        } ?: emptyList()

        if (payorUserIds == null) {
            holder.loadingOverlay.visibility = View.GONE
            return
        }

        val payorAdapter = PayorAdapter(
            payorUserIds, payorsNames,
            amountsPaid ?: mutableListOf(),
            individualPayments,
            excessAmounts,
            object : PayorAdapter.OnPayorClickListener {
                override fun onPayorClick(index: Int, paid: Double) {}
                override fun onPartialClick(index: Int, currentPaid: Double) {}
            })

        payorAdapter.setOnLoadingCompleteListener(object : PayorAdapter.OnLoadingCompleteListener {
            override fun onLoadingComplete() {
                holder.loadingOverlay.visibility = View.GONE
            }
        })

        payorAdapter.setOnDataChangedListener(object : PayorAdapter.OnDataChangedListener {
            override fun onDataChanged(hasChanges: Boolean) {}
        })

        holder.payorsRecyclerView.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.payorsRecyclerView.adapter = payorAdapter
        payorAdapter.startLoadingAllImages(holder.itemView.context)

        // Load creator image and update text
        loadCreatorImage(holder, transaction)

        // Resolve creator check asynchronously using numeric ID
        resolveIsCreator(transaction) { isCreator ->
            if (isCreator) {
                holder.createdByTextView.text = holder.itemView.context.getString(R.string.label_you)
            } else {
                holder.createdByTextView.text = transaction.createdBy ?: holder.itemView.context.getString(R.string.label_unknown)
            }
        }
    }

    /** Resolves whether the current user is the creator using numeric user_id comparison. */
    private fun resolveIsCreator(transaction: RecentTransaction, callback: (Boolean) -> Unit) {
        val creatorId = transaction.creatorNumericId ?: run { callback(false); return }

        // Use cached value if available
        if (cachedCurrentNumericId != null) {
            callback(cachedCurrentNumericId == creatorId)
            return
        }

        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: run { callback(false); return }

        scope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                cachedCurrentNumericId = user?.id
                callback(cachedCurrentNumericId == creatorId)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    /** Loads the creator's profile image into createdByImageView. */
    private fun loadCreatorImage(holder: ViewHolder, transaction: RecentTransaction) {
        val creatorUserId = transaction.createdByUserId
        val name = transaction.createdBy ?: holder.itemView.context.getString(R.string.label_unknown)
        
        if (creatorUserId.isNullOrBlank()) {
            holder.createdByInitials.text = name.take(2).uppercase()
            holder.createdByInitials.visibility = View.VISIBLE
            holder.createdByImageView.visibility = View.GONE
            return
        }

        val cachedUrl = PayorAdapter.sDownloadUrlCache[creatorUserId] ?: UserHelper.getCachedImageUrl(creatorUserId.toLongOrNull())
        if (cachedUrl != null) {
            loadCoilImageForCreator(holder, cachedUrl, name)
        } else {
            scope.launch {
                try {
                    val uidLong = creatorUserId.toLongOrNull()
                    val user = withContext(Dispatchers.IO) {
                        if (uidLong != null) {
                            DeclareDatabase.usersTable.select { filter { eq("user_id", uidLong) } }.decodeSingleOrNull<User>()
                        } else null
                    }
                    val url = user?.profileImageUrl?.takeIf { it.isNotBlank() && it != "placeholder_profile_image" }
                        ?: withContext(Dispatchers.IO) {
                            DeclareDatabase.profileImagesBucket.publicUrl("$creatorUserId/$creatorUserId.jpg")
                        }
                    PayorAdapter.sDownloadUrlCache[creatorUserId] = url
                    if (uidLong != null) {
                        user?.username?.let { UserHelper.updateCache(uidLong, it, url) }
                    }
                    loadCoilImageForCreator(holder, url, name)
                } catch (e: Exception) {
                    holder.createdByInitials.text = name.take(2).uppercase()
                    holder.createdByInitials.visibility = View.VISIBLE
                    holder.createdByImageView.visibility = View.GONE
                }
            }
        }
    }

    /** Loads image using Coil for the creator profile. */
    private fun loadCoilImageForCreator(holder: ViewHolder, url: String, name: String) {
        holder.createdByInitials.text = name.take(2).uppercase()
        holder.createdByInitials.visibility = View.VISIBLE
        
        holder.createdByImageView.visibility = View.VISIBLE
        holder.createdByImageView.setImageDrawable(null)
        holder.createdByImageView.imageTintList = null

        holder.createdByImageView.load(url) {
            transformations(CircleCropTransformation())
            listener(
                onSuccess = { _, _ ->
                    holder.createdByInitials.visibility = View.GONE
                },
                onError = { _, _ ->
                    holder.createdByInitials.visibility = View.VISIBLE
                    holder.createdByImageView.visibility = View.GONE
                }
            )
        }
    }

    private fun buildItemsTable(holder: ViewHolder, transaction: RecentTransaction) {
        holder.itemsTableContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)
        for (item in transaction.transactionItems) {
            val row = inflater.inflate(R.layout.item_transaction_item_row, holder.itemsTableContainer, false)
            
            row.findViewById<TextView>(R.id.tvItemDescription).text = item.itemDescription?.takeIf { it.isNotBlank() } ?: item.category ?: "-"
            
            val llPaidBy = row.findViewById<LinearLayout>(R.id.llItemPaidBy)
            llPaidBy.removeAllViews()

            val itemId = item.id ?: 0L
            val itemPayors = transaction.rawPayorRows.filter { it.transactionItemsId == itemId && it.initialAmountPaid > 0.01 }

            if (itemPayors.isEmpty()) {
                val tv = TextView(holder.itemView.context).apply {
                    text = "-"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.grey))
                    typeface = ResourcesCompat.getFont(context, R.font.montserratalternatess_regular)
                }
                llPaidBy.addView(tv)
            } else {
                for (payor in itemPayors) {
                    val tv = TextView(holder.itemView.context).apply {
                        textSize = 10f
                        setTextColor(ContextCompat.getColor(context, R.color.darkBlue))
                        typeface = ResourcesCompat.getFont(context, R.font.montserratalternatess_regular)
                        
                        val cachedName = UserHelper.getCachedUsername(payor.userId)
                        if (cachedName != null) {
                            text = if (itemPayors.size == 1) cachedName else "$cachedName - ${CurrencyUtils.formatAmountWithCurrency(payor.initialAmountPaid)}"
                        } else {
                            UserHelper.getUsernameById(payor.userId, object : UserHelper.UsernameCallback {
                                override fun onUsernameRetrieved(username: String?) {
                                    val name = username ?: "Unknown"
                                    text = if (itemPayors.size == 1) name else "$name - ${CurrencyUtils.formatAmountWithCurrency(payor.initialAmountPaid)}"
                                }
                                override fun onError(error: String?) {
                                    val name = "Unknown"
                                    text = if (itemPayors.size == 1) name else "$name - ${CurrencyUtils.formatAmountWithCurrency(payor.initialAmountPaid)}"
                                }
                            })
                        }
                    }
                    llPaidBy.addView(tv)
                }
            }
            
            row.findViewById<TextView>(R.id.tvItemAmount).text = CurrencyUtils.formatAmountWithCurrency(item.amount)
            
            holder.itemsTableContainer.addView(row)
        }
    }

    private fun getIconForItems(items: List<TransactionItemFull>): Int {
        if (items.isEmpty()) return R.drawable.others
        val dominant = if (items.size == 1) items[0] else items.maxByOrNull { it.amount }
        return getCategoryIcon(dominant?.category)
    }

    private fun getCategoryIcon(category: String?): Int = when (category) {
        "Electricity"     -> R.drawable.lightning_bolt
        "Water"           -> R.drawable.faucet
        "Rent"            -> R.drawable.house
        "Internet"        -> R.drawable.internet
        "Online Shopping" -> R.drawable.online_shopping
        "Travel"          -> R.drawable.travel
        "Groceries"       -> R.drawable.groceries
        "Foods"           -> R.drawable.hamburger
        "House Necessity" -> R.drawable.necessities
        "Transportation"  -> R.drawable.vehicles
        else              -> R.drawable.others
    }


    override fun getItemCount(): Int = recentTransactionList?.size ?: 0

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView           = itemView.findViewById(R.id.dateTextView)
        val typeTextView: TextView           = itemView.findViewById(R.id.transactionTypeTextView)
        val detailsTextView: TextView        = itemView.findViewById(R.id.detailsTextView)
        val amountTextView: TextView         = itemView.findViewById(R.id.paymentAmountTextView)
        val iconImageView: ImageView         = itemView.findViewById(R.id.iconImageView)
        val mainContent: View                = itemView.findViewById(R.id.main_content)
        val expandableLayout: View           = itemView.findViewById(R.id.expandable_layout)
        val itemsTableHeader: View           = itemView.findViewById(R.id.itemsTableHeader)
        val itemsTableContainer: LinearLayout = itemView.findViewById(R.id.itemsTableContainer)
        val dividerBelowItems: View          = itemView.findViewById(R.id.dividerBelowItems)
        val tvSingleDescription: TextView    = itemView.findViewById(R.id.tvSingleDescription)
        val createdByImageView: ImageView    = itemView.findViewById(R.id.createdByImageView)
        val createdByInitials: TextView      = itemView.findViewById(R.id.createdByInitialsTextView)
        val createdByTextView: TextView      = itemView.findViewById(R.id.createdByTextView)
        val payorsRecyclerView: RecyclerView = itemView.findViewById(R.id.payorsRecyclerView)
        val loadingOverlay: View             = itemView.findViewById(R.id.loadingOverlay_transaction)
        val archivedBadge: TextView          = itemView.findViewById(R.id.archivedBadge)
        val onGroupLabel: TextView           = itemView.findViewById(R.id.onGroupLabel)
        val expandedGroupName: TextView      = itemView.findViewById(R.id.expandedGroupName)
    }
}
