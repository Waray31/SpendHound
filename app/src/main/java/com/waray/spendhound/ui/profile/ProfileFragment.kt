package com.waray.spendhound.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import com.waray.spendhound.utils.ImageUtils
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BreakdownAdapter
import com.waray.spendhound.BreakdownItem
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.EditProfileActivity
import com.waray.spendhound.LoginActivity
import com.waray.spendhound.MainActivity
import com.waray.spendhound.GroupMember
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import com.waray.spendhound.PayorAdapter
import com.waray.spendhound.R
import com.waray.spendhound.Transaction
import com.waray.spendhound.User
import com.waray.spendhound.UserBalance
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.MessageRead
import com.waray.spendhound.TransactionRead
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProfileFragment : Fragment() {
    private var profileImageView: ImageView? = null
    private var nicknameTextView: TextView? = null
    private var nicknameSkeleton: View? = null
    private var userStatsSkeletonLayout: LinearLayout? = null
    private var userStatsLayout: LinearLayout? = null
    private var transactionsCountTextView: TextView? = null
    private var groupsCountTextView: TextView? = null
    private var totalBalancedTextView: TextView? = null
    private var totalTextView: TextView? = null
    private var balanceTextView: TextView? = null
    private var unpaidTextView: TextView? = null
    private var oweTextView: TextView? = null
    private var debtTextView: TextView? = null
    private var editProfileTV: LinearLayout? = null
    var mAuth: Auth? = null
    private var currentNickname: String? = ""
    private var balance = 0.0
    private var unpaid = 0.0
    
    private var currentOwe = 0.0
    private var currentDebt = 0.0
    private var balanceUnpaidLayout: View? = null
    private var oweDebtLayout: View? = null
    private var balanceUnpaidDrawable: Drawable? = null
    private var oweDebtDrawable: Drawable? = null
    private var balanceUnpaidDrawableTransparent: Drawable? = null
    private var oweDebtDrawableTransparent: Drawable? = null
    private var profileLogout: Button? = null
    private var breakdownBtn: TextView? = null
    private var groupsRecyclerView: RecyclerView? = null
    private var groupsSkeletonLayout: LinearLayout? = null
    private var emptyGroupsLayout: LinearLayout? = null
    private var profileGroupsAdapter: ProfileGroupsAdapter? = null

    private var imageUpdatedAt: String? = null
    private var isTabClickEnabled = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_profile, container, false)

        profileImageView = view.findViewById(R.id.profileImageView)
        nicknameTextView = view.findViewById(R.id.nicknameTextView)
        nicknameSkeleton = view.findViewById(R.id.nickname_skeleton)
        userStatsSkeletonLayout = view.findViewById(R.id.userStats_skeletonLayout)
        userStatsLayout = view.findViewById(R.id.userStats_Layout)
        transactionsCountTextView = view.findViewById(R.id.transactionsCountTextView)
        groupsCountTextView = view.findViewById(R.id.groupsCountTextView)
        Log.d("ProfileFragment", "Views initialized - nicknameTextView: $nicknameTextView")
        editProfileTV = view.findViewById(R.id.editProfile_TV)
        totalBalancedTextView = view.findViewById(R.id.totalBalancedTextView)
        totalTextView = view.findViewById(R.id.totalTextView)
        balanceTextView = view.findViewById(R.id.balanceTextView)
        unpaidTextView = view.findViewById(R.id.unpaidTextView)
        oweTextView = view.findViewById(R.id.oweTextView)
        debtTextView = view.findViewById(R.id.debtTextView)
        balanceUnpaidLayout = view.findViewById(R.id.balanceUnpaidLayout)
        oweDebtLayout = view.findViewById(R.id.oweDebtLayout)
        profileLogout = view.findViewById(R.id.profileLogout)
        breakdownBtn = view.findViewById(R.id.breakdown_btn)
        groupsSkeletonLayout = view.findViewById(R.id.groupsSkeletonLayout)
        emptyGroupsLayout = view.findViewById(R.id.emptyGroupsLayout)

        balanceUnpaidDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        balanceUnpaidDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        oweDebtDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        oweDebtDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        balanceUnpaidLayout?.foreground = balanceUnpaidDrawable

        balanceTextView?.setBackgroundResource(R.drawable.button_background_visible)
        balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))

        mAuth = DeclareDatabase.auth

        profileImageView?.isClickable = false
        setupRecyclerView(view)
        setupEditProfileTV()
        setupUnpaidButton()
        setupBalanceButton()
        setupOweButton()
        setupDebtButton()
        setupProfileLogoutButton()
        setupBreakdownButton()

        val activity: AppCompatActivity? = getActivity() as AppCompatActivity?
        activity?.supportActionBar?.hide()

        return view
    }

    override fun onResume() {
        super.onResume()
        loadNicknameAndData()
    }

    private fun setProfileImage(imageView: ImageView) {
        if (!isAdded) return
        val authId = mAuth?.currentUserOrNull()?.id
        if (authId == null) {
            Log.e("ProfileFragment", "setProfileImage: authId is null")
            imageView.setImageResource(R.drawable.placeholder_profile_image)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Remove updated_at from column list as it doesn't exist in the DB schema
                val user = DeclareDatabase.usersTable.select(Columns.list("user_id", "profile_image_url", "created_at")) {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()

                val profileUrl = user?.profileImageUrl
                if (profileUrl == null || profileUrl == "placeholder_profile_image") {
                    withContext(Dispatchers.Main) {
                        imageView.setImageResource(R.drawable.placeholder_profile_image)
                    }
                    return@launch
                }

                // Use created_at if updated_at is missing, though profileUrl often has ?t= already
                val timestamp = user.createdAt
                val url = ImageUtils.bustCache(profileUrl, timestamp)

                withContext(Dispatchers.Main) {
                    if (url != null) {
                        imageView.load(url) {
                            crossfade(true)
                            placeholder(R.drawable.placeholder_profile_image)
                            error(R.drawable.placeholder_profile_image)
                            transformations(CircleCropTransformation())
                        }
                    } else {
                        imageView.setImageResource(R.drawable.placeholder_profile_image)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "setProfileImage error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(R.drawable.placeholder_profile_image)
                }
            }
        }
    }

    private fun setupRecyclerView(view: View) {
        groupsRecyclerView = view.findViewById(R.id.groupsRecyclerView)
        profileGroupsAdapter = ProfileGroupsAdapter()
        groupsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        groupsRecyclerView?.adapter = profileGroupsAdapter
    }

    private fun setupEditProfileTV() {
        editProfileTV?.setOnClickListener {
            startActivityForResult(
                Intent(requireContext(), EditProfileActivity::class.java),
                REQUEST_EDIT_PROFILE
            )
        }
    }

    internal fun loadNicknameAndData() {
        // Refresh profile image
        profileImageView?.let { setProfileImage(it) }

        // Only show skeletons if we don't have data yet
        val isFirstLoad = nicknameTextView?.text.isNullOrBlank() || nicknameTextView?.visibility == View.GONE
        if (isFirstLoad) {
            nicknameSkeleton?.visibility = View.VISIBLE
            nicknameTextView?.visibility = View.GONE
            userStatsSkeletonLayout?.visibility = View.VISIBLE
            userStatsLayout?.visibility = View.GONE
        }

        val authId = mAuth?.currentUserOrNull()?.id
        Log.d("ProfileFragment", "loadNicknameAndData - authId: $authId")
        if (authId == null) {
            Log.e("ProfileFragment", "authId is null")
            return
        }
        lifecycleScope.launch {
            try {
                // Step 1: Fetch user from users table
                val user = withContext(Dispatchers.IO) {
                    Log.d("ProfileFragment", "Fetching user with authId: $authId")
                    DeclareDatabase.usersTable.select(Columns.list("user_id", "username", "profile_image_url")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                Log.d("ProfileFragment", "User fetched: $user")
                Log.d("ProfileFragment", "Username: ${user?.username}")

                // Step 2: Fetch user balance from user_balance table using user_id
                var userBalance: UserBalance? = null
                var transactionsCount = 0
                var groupsCount = 0
                var activeBorrowsCount = 0

                if (user?.id != null) {
                    val userId = user.id
                    userBalance = withContext(Dispatchers.IO) {
                        Log.d("ProfileFragment", "Fetching user balance with user_id: $userId")
                        DeclareDatabase.userBalanceTable.select(Columns.list(
                            "unpaid_total_group", "unpaid_total_individual",
                            "receivable_total_group", "receivable_total_individual",
                            "balance_total_group", "balance_total_individual"
                        )) {
                            filter { eq("user_id", userId) }
                        }.decodeSingleOrNull<UserBalance>()
                    }

                    // Fetch Transactions Count (Involved in and Pending: status 2)
                    transactionsCount = withContext(Dispatchers.IO) {
                        try {
                            // 1. Get unique transaction IDs where user is involved via splits
                            val involvedSplitTxIds = DeclareDatabase.transactionSplitsTable.select(Columns.list("transaction_id")) {
                                filter { eq("user_id", userId) }
                            }.decodeList<TransactionSplitTable>().mapNotNull { it.transactionId }.toSet()

                            // 2. Fetch transactions that are both in that involved set AND have status 2 (pending)
                            if (involvedSplitTxIds.isEmpty()) {
                                0
                            } else {
                                val pendingInvolved = DeclareDatabase.transactionsTable.select(Columns.list("id")) {
                                    filter {
                                        isIn("id", involvedSplitTxIds.toList())
                                        eq("status", 2)
                                    }
                                }.decodeList<TransactionFull>()
                                pendingInvolved.size
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error counting transactions", e)
                            0
                        }
                    }

                    // Fetch Groups Count
                    groupsCount = withContext(Dispatchers.IO) {
                        try {
                            val members = DeclareDatabase.groupMembersTable.select(Columns.list("group_id")) {
                                filter { eq("user_id", userId) }
                            }.decodeList<GroupMember>()
                            members.size
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error counting groups", e)
                            0
                        }
                    }

                    // Fetch Active Borrows Count (Status 1: Approval, 2: Pending, 7: Partial)
                    activeBorrowsCount = withContext(Dispatchers.IO) {
                        try {
                            val borrows = DeclareDatabase.borrowsTable.select(Columns.list("id")) {
                                filter {
                                    eq("borrower_id", userId)
                                    or {
                                        eq("status", 1)
                                        eq("status", 2)
                                        eq("status", 7)
                                    }
                                }
                            }.decodeList<BorrowNowTransaction>()
                            borrows.size
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error counting borrows", e)
                            0
                        }
                    }
                    Log.d("ProfileFragment", "Data counts - Trans: $transactionsCount, Groups: $groupsCount, Borrows: $activeBorrowsCount")
                }

                // Update UI on Main thread
                withContext(Dispatchers.Main) {
                    val nickname = user?.username ?: ""
                    Log.d("ProfileFragment", "Setting nicknameTextView to: $nickname")
                    
                    // Only update if changed to avoid flicker
                    if (nicknameTextView?.text != nickname) {
                        nicknameTextView?.text = nickname
                    }

                    // Hide skeletons, show real content
                    nicknameSkeleton?.visibility = View.GONE
                    nicknameTextView?.visibility = View.VISIBLE
                    userStatsSkeletonLayout?.visibility = View.GONE
                    userStatsLayout?.visibility = View.VISIBLE

                    // Extract balance data from user_balance table
                    val unpaidGroup = userBalance?.unpaidTotalGroup ?: 0.0
                    val unpaidIndividual = userBalance?.unpaidTotalIndividual ?: 0.0
                    val receivableGroup = userBalance?.receivableTotalGroup ?: 0.0
                    val receivableIndividual = userBalance?.receivableTotalIndividual ?: 0.0
                    val balanceGroup = userBalance?.balanceTotalGroup ?: 0.0
                    val balanceIndividual = userBalance?.balanceTotalIndividual ?: 0.0

                    // Set balance and unpaid values
                    balance = balanceGroup  // Total balance to show
                    unpaid = unpaidGroup    // Total unpaid balance
                    currentOwe = receivableIndividual  // Total owed to user
                    currentDebt = unpaidIndividual     // Total debt of user

                    // Only update text if it's currently showing the "Total Balanced" view
                    // (Profile tab uses this text view for multiple things depending on which button is clicked)
                    // But usually on fresh load it shows active borrows
                    if (totalTextView?.text == getString(R.string.label_borrowed)) {
                        totalBalancedTextView?.text = activeBorrowsCount.toString()
                    }

                    // Update stats
                    transactionsCountTextView?.text = transactionsCount.toString()
                    groupsCountTextView?.text = groupsCount.toString()

                    user?.id?.let { loadTopGroups(it) }

                    Log.d("ProfileFragment", "UI Updated - balance: $balance, unpaid: $unpaid, owe: $currentOwe, debt: $currentDebt")
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error loading user data: ${e.message}", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Only show skeletons on error if we have NO data at all
                    if (nicknameTextView?.text.isNullOrBlank()) {
                        nicknameSkeleton?.visibility = View.VISIBLE
                        nicknameTextView?.visibility = View.GONE
                        userStatsSkeletonLayout?.visibility = View.VISIBLE
                        userStatsLayout?.visibility = View.GONE
                    }
                }
            }
        }
    }


    private fun loadTopGroups(userId: Long) {
        // Show skeleton while loading
        groupsSkeletonLayout?.visibility = View.VISIBLE
        groupsRecyclerView?.visibility = View.GONE
        emptyGroupsLayout?.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // 1. Get all groups user is member of
                val memberEntries = withContext(Dispatchers.IO) {
                    DeclareDatabase.groupMembersTable.select(Columns.list("group_id")) {
                        filter { eq("user_id", userId) }
                    }.decodeList<GroupMember>()
                }
                val groupIds = memberEntries.mapNotNull { it.groupId }
                if (groupIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        groupsSkeletonLayout?.visibility = View.GONE
                        groupsRecyclerView?.visibility = View.GONE
                        emptyGroupsLayout?.visibility = View.VISIBLE
                        breakdownBtn?.text = "Create group"
                        profileGroupsAdapter?.updateItems(emptyList())
                    }
                    return@launch
                }

                // 2. For each group, get activity and unread counts
                val profileGroupItems = withContext(Dispatchers.IO) {
                    groupIds.map { groupId ->
                        val group = DeclareDatabase.groupsTable.select {
                            filter { eq("group_id", groupId) }
                        }.decodeSingle<PayerGroup>()

                        val members = DeclareDatabase.groupMembersTable.select(Columns.list("user_id")) {
                            filter { eq("group_id", groupId) }
                        }.decodeList<GroupMember>()

                        // Optimized unread messages fetch using group_id
                        val lastReadMessage = DeclareDatabase.messageReadsTable.select(Columns.list("message_id")) {
                            filter {
                                eq("group_id", groupId)
                                eq("user_id", userId)
                            }
                            order("message_id", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(1)
                        }.decodeSingleOrNull<MessageRead>()

                        val unreadMessagesCount = if (lastReadMessage != null) {
                            DeclareDatabase.groupMessagesTable.select(Columns.list("id")) {
                                filter {
                                    eq("group_id", groupId)
                                    gt("id", lastReadMessage.messageId!!)
                                    neq("user_id", userId)
                                }
                            }.decodeList<GroupMessage>().size
                        } else {
                            DeclareDatabase.groupMessagesTable.select(Columns.list("id")) {
                                filter {
                                    eq("group_id", groupId)
                                    neq("user_id", userId)
                                }
                            }.decodeList<GroupMessage>().size
                        }

                        // Fetch unread transactions using transaction_reads
                        val readTxIds = DeclareDatabase.transactionReadsTable.select(Columns.list("transaction_id")) {
                            filter {
                                eq("group_id", groupId)
                                eq("user_id", userId)
                            }
                        }.decodeList<TransactionRead>().mapNotNull { it.transactionId }.toSet()

                        val unreadTransactionsCount = DeclareDatabase.transactionsTable.select(Columns.list("id")) {
                            filter {
                                eq("group_id", groupId)
                                neq("created_by", userId)
                            }
                        }.decodeList<TransactionFull>().filter { it.id !in readTxIds }.size

                        // Get latest activity timestamp (either message or transaction)
                        val latestMessage = DeclareDatabase.groupMessagesTable.select(Columns.list("created_at")) {
                            filter { eq("group_id", groupId) }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(1)
                        }.decodeSingleOrNull<GroupMessage>()

                        val latestTransaction = DeclareDatabase.transactionsTable.select(Columns.list("created_at")) {
                            filter { eq("group_id", groupId) }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(1)
                        }.decodeSingleOrNull<TransactionFull>()

                        val activityTime = listOfNotNull(latestMessage?.createdAt, latestTransaction?.createdAt).maxOrNull() ?: ""

                        ProfileGroupItem(group, members.size, unreadTransactionsCount, unreadMessagesCount) to activityTime
                    }
                }

                // 3. Sort by activity and take top 3
                val top3 = profileGroupItems
                    .sortedByDescending { it.second }
                    .take(3)
                    .map { it.first }

                withContext(Dispatchers.Main) {
                    groupsSkeletonLayout?.visibility = View.GONE
                    emptyGroupsLayout?.visibility = View.GONE
                    groupsRecyclerView?.visibility = View.VISIBLE
                    breakdownBtn?.text = getString(R.string.label_see_all_groups)
                    profileGroupsAdapter?.updateItems(top3)
                }

            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error loading top groups", e)
                withContext(Dispatchers.Main) {
                    groupsSkeletonLayout?.visibility = View.GONE
                }
            }
        }
    }


    private fun setupBalanceButton() {
        balanceTextView?.setOnClickListener {
            balanceUnpaidLayout?.foreground = balanceUnpaidDrawable
            oweDebtLayout?.foreground = oweDebtDrawableTransparent

            balanceTextView?.setBackgroundResource(R.drawable.button_background_visible)
            balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
            unpaidTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            unpaidTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            oweTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            oweTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            debtTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            debtTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(balance)
            totalTextView?.text = "Total Balance:"
        }
    }

    private fun setupUnpaidButton() {
        unpaidTextView?.setOnClickListener {
            balanceUnpaidLayout?.foreground = balanceUnpaidDrawable
            oweDebtLayout?.foreground = oweDebtDrawableTransparent

            balanceTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            unpaidTextView?.setBackgroundResource(R.drawable.button_background_visible)
            unpaidTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
            oweTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            oweTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            debtTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            debtTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(unpaid)
            totalTextView?.text = "Total Unpaid Balance:"
        }
    }

    private fun setupOweButton() {
        oweTextView?.setOnClickListener {
            oweDebtLayout?.foreground = oweDebtDrawable
            balanceUnpaidLayout?.foreground = balanceUnpaidDrawableTransparent

            balanceTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            unpaidTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            unpaidTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            oweTextView?.setBackgroundResource(R.drawable.button_background_visible)
            oweTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
            debtTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            debtTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(currentOwe)
            totalTextView?.text = "Total Owed Balance:"
        }
    }

    private fun setupDebtButton() {
        debtTextView?.setOnClickListener {
            oweDebtLayout?.foreground = oweDebtDrawable
            balanceUnpaidLayout?.foreground = balanceUnpaidDrawableTransparent

            balanceTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            unpaidTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            unpaidTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            oweTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            oweTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            debtTextView?.setBackgroundResource(R.drawable.button_background_visible)
            debtTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(currentDebt)
            totalTextView?.text = "Total Debt:"
        }
    }

    private fun formatDate(dateStr: String?): String {
        if (dateStr == null) return "Unknown"
        return try {
            // dateStr is typically ISO 8601 from Supabase: 2024-05-20T12:00:00.000Z
            val datePart = dateStr.split("T")[0] // 2024-05-20
            val parts = datePart.split("-")
            val year = parts[0]
            val month = parts[1]
            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthName = monthNames.getOrNull(month.toInt() - 1) ?: month
            "$monthName $year"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun selectNewProfilePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == Activity.RESULT_OK) {
            return
        }
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val extras = data?.extras
                    val imageBitmap = extras?.get("data") as? Bitmap
                    imageBitmap?.let { bmp ->
                        val imageUri = getImageUri(requireContext(), bmp)
                        if (imageUri != null) {
                            imageUpdatedAt = System.currentTimeMillis().toString()
                            profileImageView?.load(imageUri) {
                                crossfade(true)
                                transformations(CircleCropTransformation())
                            }
                        }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    val imageUri = data?.data
                    if (imageUri != null) {
                        imageUpdatedAt = System.currentTimeMillis().toString()
                        profileImageView?.load(imageUri) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                        }
                    }
                }
            }
        }
    }

    private fun getImageUri(context: Context, bitmap: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "Profile", null)
        return Uri.parse(path)
    }


    private fun setupProfileLogoutButton() {
        profileLogout?.setOnClickListener {
            lifecycleScope.launch {
                mAuth?.signOut()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Logout Successfully", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
            }
        }
    }

    private fun setupBreakdownButton() {
        breakdownBtn?.setOnClickListener {
            if (emptyGroupsLayout?.visibility == View.VISIBLE) {
                startActivity(Intent(requireContext(), com.waray.spendhound.CreateGroupActivity::class.java))
            } else {
                startActivity(Intent(requireContext(), com.waray.spendhound.GroupsActivity::class.java))
            }
        }
    }

    private fun showBreakdownDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_breakdown, null)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseBreakdown)
        val tabBalance = dialogView.findViewById<Button>(R.id.tabBalance)
        val tabUnpaid = dialogView.findViewById<Button>(R.id.tabUnpaid)
        val tabOwe = dialogView.findViewById<Button>(R.id.tabOwe)
        val tabDebt = dialogView.findViewById<Button>(R.id.tabDebt)
        val categoryTitle = dialogView.findViewById<TextView>(R.id.breakdownCategoryTitle)
        val totalAmount = dialogView.findViewById<TextView>(R.id.breakdownTotalAmount)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.breakdownRecyclerView)
        val emptyStateLayout = dialogView.findViewById<View>(R.id.emptyStateLayout)
        val emptyStateText = dialogView.findViewById<TextView>(R.id.emptyStateText)
        val progressBar = dialogView.findViewById<View>(R.id.breakdownProgressBar)

        val adapter = BreakdownAdapter(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val breakdownDialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        btnClose.setOnClickListener { breakdownDialog.dismiss() }

        val tabs = listOf(tabBalance, tabUnpaid, tabOwe, tabDebt)
        val categories = listOf(BreakdownItem.Category.BALANCE, BreakdownItem.Category.UNPAID, BreakdownItem.Category.OWE, BreakdownItem.Category.DEBT)

        tabs.forEachIndexed { index, button ->
            button.setOnClickListener {
                tabs.forEach { 
                    it.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.grey)
                    it.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
                }
                button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.yellow)
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))

                loadBreakdownData(categories[index], categoryTitle, totalAmount, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
        }

        loadBreakdownData(BreakdownItem.Category.BALANCE, categoryTitle, totalAmount, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
        breakdownDialog.show()
    }

    private fun loadBreakdownData(
        category: BreakdownItem.Category, categoryTitle: TextView, totalAmount: TextView,
        adapter: BreakdownAdapter, recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        val items = ArrayList<BreakdownItem?>()

        when (category) {
            BreakdownItem.Category.BALANCE -> {
                categoryTitle.text = "Total Balance"
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(balance)
                loadBalanceBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
            BreakdownItem.Category.UNPAID -> {
                categoryTitle.text = "Total Unpaid"
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(unpaid)
                loadUnpaidBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
            BreakdownItem.Category.OWE -> {
                categoryTitle.text = "Total Owed"
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(currentOwe)
                loadOweBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
            BreakdownItem.Category.DEBT -> {
                categoryTitle.text = "Total Debt"
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(currentDebt)
                loadDebtBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
        }
    }

    private fun loadBalanceBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val transactions = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                }
                items.clear()
                for (transaction in transactions) {
                    val individualPayment = transaction.individualPayment
                    val payorsList = transaction.payorsDisplayNames ?: transaction.contributors ?: emptyList()
                    val amountsPaidList = transaction.amountPaidList ?: emptyList()
                    val dateStr = transaction.monthYear ?: "Unknown Date"
                    val transactionType = transaction.transactionType ?: ""

                    val userIndex = payorsList.indexOf(currentNickname)
                    if (userIndex != -1 && userIndex < amountsPaidList.size) {
                        val userPayment = amountsPaidList[userIndex] ?: 0.0
                        val transactionBalance = userPayment - individualPayment
                        if (transactionBalance > 0) {
                            items.add(BreakdownItem(BreakdownItem.Category.BALANCE, dateStr, "Transaction", transactionBalance, "Completed", transactionType))
                        }
                    }
                }
                updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No balance transactions found")
            } catch (e: Exception) {
                Log.e("Supabase", "Error loading balance breakdown: ${e.message}", e)
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        }
    }

    private fun loadUnpaidBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val transactions = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                }
                items.clear()
                for (transaction in transactions) {
                    val individualPayment = transaction.individualPayment
                    val payorsList = transaction.payorsDisplayNames ?: transaction.contributors ?: emptyList()
                    val amountsPaidList = transaction.amountPaidList ?: emptyList()
                    val dateStr = transaction.monthYear ?: "Unknown Date"
                    val transactionType = transaction.transactionType ?: ""

                    val userIndex = payorsList.indexOf(currentNickname)
                    if (userIndex != -1 && userIndex < amountsPaidList.size) {
                        val userPayment = amountsPaidList[userIndex] ?: 0.0
                        val transactionUnpaid = individualPayment - userPayment
                        if (transactionUnpaid > 0) {
                            items.add(BreakdownItem(BreakdownItem.Category.UNPAID, dateStr, "Transaction", transactionUnpaid, "Pending", transactionType))
                        }
                    }
                }
                updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No unpaid transactions found")
            } catch (e: Exception) {
                Log.e("Supabase", "Error loading unpaid breakdown: ${e.message}", e)
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        }
    }

    private fun loadOweBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                
                if (user?.id != null) {
                    val borrows = withContext(Dispatchers.IO) {
                        DeclareDatabase.borrowsTable.select {
                            filter { eq("lender_id", user.id) }
                        }.decodeList<BorrowNowTransaction>()
                    }
                    items.clear()
                    for (borrow in borrows) {
                        val dateStr = borrow.createdAt ?: "Unknown Date"
                        val borrowerName = borrow.borrowerName ?: "Unknown"
                        val borrowedAmount = borrow.borrowedAmount ?: 0.0
                        val status = borrow.getStatus() ?: "Pending"
                        items.add(BreakdownItem(BreakdownItem.Category.OWE, dateStr, "From: $borrowerName", borrowedAmount, status))
                    }
                    updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No owed amounts found")
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error loading owe breakdown: ${e.message}", e)
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        }
    }

    private fun loadDebtBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                
                if (user?.id != null) {
                    val borrows = withContext(Dispatchers.IO) {
                        DeclareDatabase.borrowsTable.select {
                            filter { eq("borrower_id", user.id) }
                        }.decodeList<BorrowNowTransaction>()
                    }
                    items.clear()
                    for (borrow in borrows) {
                        val dateStr = borrow.createdAt ?: "Unknown Date"
                        val lenderName = borrow.lender ?: "Unknown"
                        val borrowedAmount = borrow.borrowedAmount ?: 0.0
                        val status = borrow.getStatus() ?: "Pending"
                        items.add(BreakdownItem(BreakdownItem.Category.DEBT, dateStr, "To: $lenderName", borrowedAmount, status))
                    }
                    updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No debt found")
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error loading debt breakdown: ${e.message}", e)
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        }
    }

    private fun updateBreakdownUI(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View, emptyMessage: String?
    ) {
        progressBar.visibility = View.GONE
        if (items.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            emptyStateText.text = emptyMessage
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            adapter.updateData(items)
        }
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_IMAGE_PICK = 2
        private const val REQUEST_EDIT_PROFILE = 3
    }
}
