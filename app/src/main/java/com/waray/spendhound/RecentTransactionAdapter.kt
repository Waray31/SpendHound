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
            holder.unreadIndicator.visibility = View.VISIBLE
            holder.mainContent.setBackgroundResource(R.drawable.bg_unread_transaction)
        } else {
            holder.unreadIndicator.visibility = View.GONE
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
                if (desc != null) {
                    holder.tvSingleDescription.text = desc
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

        if (payorUserIds == null) {
            holder.loadingOverlay.visibility = View.GONE
            holder.editTransactionBtn.visibility = View.GONE
            return
        }

        val payorAdapter = PayorAdapter(
            payorUserIds, payorsNames,
            amountsPaid ?: mutableListOf(),
            individualPayments,
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
                holder.settlementLL.visibility = View.VISIBLE
                holder.editTransactionBtn.setOnClickListener {
                    val sheet = SettleBottomSheet().apply {
                        this.transaction = transaction
                        onSettleSaved = { onSettleRefresh?.invoke() }
                    }
                    val fm = (holder.itemView.context as? FragmentActivity)?.supportFragmentManager ?: return@setOnClickListener
                    sheet.show(fm, "SettleBottomSheet")
                }
            } else {
                holder.createdByTextView.text = transaction.createdBy ?: holder.itemView.context.getString(R.string.label_unknown)
                // Check if current user is a payor
                val currentUserId = cachedCurrentNumericId
                val userRow = transaction.rawPayorRows.firstOrNull { it.userId == currentUserId }
                if (userRow != null) {
                    val hasExcess = userRow.excessAmount > 0.0
                    holder.settlementLL.visibility = View.VISIBLE
                    holder.editTransactionBtn.text = if (hasExcess) holder.itemView.context.getString(R.string.btn_settle) else holder.itemView.context.getString(R.string.btn_details)
                    holder.editTransactionBtn.setOnClickListener {
                        val sheet = SettleBottomSheet().apply {
                            this.transaction = transaction
                            this.isDetailsMode = !hasExcess
                            onSettleSaved = { onSettleRefresh?.invoke() }
                        }
                        val fm = (holder.itemView.context as? FragmentActivity)?.supportFragmentManager ?: return@setOnClickListener
                        sheet.show(fm, "SettleBottomSheet")
                    }
                } else {
                    holder.settlementLL.visibility = View.GONE
                }
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
        if (creatorUserId.isNullOrBlank()) {
            holder.createdByImageView.setImageResource(R.drawable.ic_profile_silhouette)
            return
        }

        val cachedUrl = PayorAdapter.sDownloadUrlCache[creatorUserId]
        if (cachedUrl != null) {
            loadCoilImageForCreator(holder, cachedUrl)
        } else {
            scope.launch {
                try {
                    val url = withContext(Dispatchers.IO) {
                        DeclareDatabase.profileImagesBucket.publicUrl("$creatorUserId/$creatorUserId.jpg")
                    }
                    PayorAdapter.sDownloadUrlCache[creatorUserId] = url
                    loadCoilImageForCreator(holder, url)
                } catch (e: Exception) {
                    holder.createdByImageView.setImageResource(R.drawable.ic_profile_silhouette)
                    holder.createdByImageView.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.white)
                }
            }
        }
    }

    /** Loads image using Coil for the creator profile. */
    private fun loadCoilImageForCreator(holder: ViewHolder, url: String) {
        holder.createdByImageView.imageTintList = null
        holder.createdByImageView.load(url) {
            placeholder(R.drawable.ic_profile_silhouette)
            error(R.drawable.ic_profile_silhouette)
            transformations(CircleCropTransformation())
            listener(onError = { _, _ ->
                holder.createdByImageView.setImageResource(R.drawable.ic_profile_silhouette)
                holder.createdByImageView.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.white)
            })
        }
    }

    private fun buildItemsTable(holder: ViewHolder, transaction: RecentTransaction) {
        holder.itemsTableContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)
        for (item in transaction.transactionItems) {
            val row = inflater.inflate(R.layout.item_transaction_item_row, holder.itemsTableContainer, false)
            row.findViewById<ImageView>(R.id.ivItemCategory).setImageResource(getCategoryIcon(item.category))
            row.findViewById<TextView>(R.id.tvItemAmount).text = CurrencyUtils.formatAmountWithCurrency(item.amount)
            row.findViewById<TextView>(R.id.tvItemPaidBy).text = transaction.itemPayorMap[item.id] ?: "-"
            row.findViewById<TextView>(R.id.tvItemDescription).text = item.itemDescription?.takeIf { it.isNotBlank() } ?: "-"
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
        val createdByTextView: TextView      = itemView.findViewById(R.id.createdByTextView)
        val payorsRecyclerView: RecyclerView = itemView.findViewById(R.id.payorsRecyclerView)
        val loadingOverlay: View             = itemView.findViewById(R.id.loadingOverlay_transaction)
        val editTransactionBtn: TextView       = itemView.findViewById(R.id.editTransaction_btn)
        val settlementLL: View               = itemView.findViewById(R.id.settlement_LL)
        val unreadIndicator: View            = itemView.findViewById(R.id.unreadIndicator)
        val archivedBadge: TextView          = itemView.findViewById(R.id.archivedBadge)
        val onGroupLabel: TextView           = itemView.findViewById(R.id.onGroupLabel)
        val expandedGroupName: TextView      = itemView.findViewById(R.id.expandedGroupName)
    }
}
