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
import androidx.fragment.app.viewModels
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
import com.waray.spendhound.GroupsState
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.AddCrewActivity
import com.waray.spendhound.CrewMember
import com.waray.spendhound.CrewMembersActivity
import com.waray.spendhound.DirectMessageActivity
import com.waray.spendhound.ui.profile.CrewMembersAdapter
import com.waray.spendhound.ui.profile.CrewViewModel
import com.waray.spendhound.ui.profile.PendingInvitesAdapter
import com.waray.spendhound.ui.profile.UserSearchAdapter
import com.waray.spendhound.ui.settings.SettingsActivity

class ProfileFragment : Fragment() {
    private val viewModel: ProfileViewModel by viewModels()
    private val crewViewModel: CrewViewModel by viewModels()
    private var profileImageView: ImageView? = null
    private var profileCardView: View? = null
    private var profileSkeleton: View? = null
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

    private var crewRecyclerView: RecyclerView? = null
    private var crewSkeletonLayout: LinearLayout? = null
    private var emptyCrewLayout: LinearLayout? = null
    private var btnAddCrew: TextView? = null
    private var btnSeeAllCrew: TextView? = null
    private var btnCrewNotification: android.widget.ImageButton? = null
    private var tvCrewPendingBadge: TextView? = null
    private var crewAdapter: CrewMembersAdapter? = null
    private var currentUserId: Long = -1L
    private var lastSeenCrewUpdate: Long = 0L
    private var lastSeenGroupsUpdate: Long = 0L
    private var lastSeenTransactionUpdate: Long = 0L
    private var hasLoadedOnce = false
    private var isTabClickEnabled = true

    companion object {
        private const val REQUEST_EDIT_PROFILE = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        if (mAuth == null) { 
            mAuth = try { DeclareDatabase.auth } catch (e: Exception) { null }
        }
        val view: View = inflater.inflate(R.layout.fragment_profile, container, false)

        profileImageView = view.findViewById(R.id.profileImageView)
        profileCardView = view.findViewById(R.id.profileCardView)
        profileSkeleton = view.findViewById(R.id.profile_skeleton_layout)
        nicknameTextView = view.findViewById(R.id.nicknameTextView)
        nicknameSkeleton = view.findViewById(R.id.nickname_skeleton)
        userStatsSkeletonLayout = view.findViewById(R.id.userStats_skeletonLayout)
        userStatsLayout = view.findViewById(R.id.userStats_Layout)
        transactionsCountTextView = view.findViewById(R.id.transactionsCountTextView)
        groupsCountTextView = view.findViewById(R.id.groupsCountTextView)
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
        crewRecyclerView = view.findViewById(R.id.crewRecyclerView)
        crewSkeletonLayout = view.findViewById(R.id.crewSkeletonLayout)
        emptyCrewLayout = view.findViewById(R.id.emptyCrewLayout)
        btnAddCrew = view.findViewById(R.id.btnAddCrew)
        btnSeeAllCrew = view.findViewById(R.id.btnSeeAllCrew)
        btnCrewNotification = view.findViewById(R.id.btnCrewNotification)
        tvCrewPendingBadge = view.findViewById(R.id.tvCrewPendingBadge)

        val settingsBtn = view.findViewById<android.widget.ImageButton>(R.id.settings_btn)
        settingsBtn.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        balanceUnpaidDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        balanceUnpaidDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        oweDebtDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        oweDebtDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        balanceUnpaidLayout?.foreground = balanceUnpaidDrawable
        balanceTextView?.setBackgroundResource(R.drawable.button_background_visible)
        balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))

        mAuth = try { DeclareDatabase.auth } catch (e: Exception) { null }
        profileImageView?.isClickable = false
        setupRecyclerView(view)
        setupCrewSection()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize update timestamps to current global values to avoid redundant refresh on first load
        lastSeenCrewUpdate = com.waray.spendhound.CrewState.lastUpdateTimestamp
        lastSeenGroupsUpdate = GroupsState.lastUpdateTimestamp
        lastSeenTransactionUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
        // Apply cached StateFlow values synchronously — same frame, no coroutine gap
        // This is exactly why groups has no flash: its observer fires synchronously here
        applyCachedState()
        observeViewModel()
    }

    /**
     * Reads current StateFlow values synchronously and applies them to views immediately.
     * Called before observeViewModel() so there is zero blank frame on revisit.
     */
    private fun applyCachedState() {
        // Profile image — read from ViewModel memory, load from Coil memory/disk cache instantly
        val cachedProfile = viewModel.profile.value
        if (cachedProfile != null) {
            nicknameTextView?.text = cachedProfile.nickname
            nicknameSkeleton?.visibility = View.GONE
            nicknameTextView?.visibility = View.VISIBLE
            profileSkeleton?.visibility = View.GONE
            profileCardView?.visibility = View.VISIBLE
            userStatsSkeletonLayout?.visibility = View.GONE
            userStatsLayout?.visibility = View.VISIBLE
            transactionsCountTextView?.text = cachedProfile.transactionsCount.toString()
            groupsCountTextView?.text = cachedProfile.groupsCount.toString()

            val imageUrl = cachedProfile.profileImageUrl
            profileCardView?.visibility = View.VISIBLE
            (profileCardView as? androidx.cardview.widget.CardView)
                ?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.orange))
            if (!imageUrl.isNullOrBlank()) {
                profileImageView?.load(imageUrl) {
                    crossfade(false)
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_profile_silhouette)
                    error(R.drawable.ic_profile_silhouette)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCacheKey(imageUrl)
                    diskCacheKey(imageUrl)
                }
            }
            hasLoadedOnce = true
        }

        // Crew list — create adapter immediately (currentUserId not needed yet, updated later)
        // then bind cached data synchronously so there is zero blank frame
        val cachedCrew = crewViewModel.crewList.value
        if (cachedCrew.isNotEmpty()) {
            if (crewAdapter == null) {
                crewAdapter = CrewMembersAdapter(
                    currentUserId = currentUserId, // -1L placeholder, updated in loadNicknameAndData
                    onMessage = { otherUser, _ -> openDm(otherUser) },
                    onRemove = { crew -> confirmRemoveCrew(crew) }
                )
                crewRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
                crewRecyclerView?.adapter = crewAdapter
            }
            crewAdapter?.updateItems(cachedCrew.take(3))
            crewSkeletonLayout?.visibility = View.GONE
            emptyCrewLayout?.visibility = View.GONE
            crewRecyclerView?.visibility = View.VISIBLE
            hasLoadedOnce = true
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            crewViewModel.isLoading.collectLatest { loading ->
                if (loading) {
                    if (!hasLoadedOnce) {
                        crewSkeletonLayout?.visibility = View.VISIBLE
                        crewRecyclerView?.visibility = View.GONE
                        emptyCrewLayout?.visibility = View.GONE
                    }
                } else {
                    crewSkeletonLayout?.visibility = View.GONE
                    val list = crewViewModel.crewList.value
                    if (list.isNotEmpty()) {
                        hasLoadedOnce = true
                        emptyCrewLayout?.visibility = View.GONE
                        crewRecyclerView?.visibility = View.VISIBLE
                        crewAdapter?.updateItems(list.take(3))
                    } else if (!hasLoadedOnce) {
                        emptyCrewLayout?.visibility = View.VISIBLE
                        crewRecyclerView?.visibility = View.GONE
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            crewViewModel.crewList.collectLatest { list ->
                if (crewViewModel.isLoading.value) return@collectLatest
                crewSkeletonLayout?.visibility = View.GONE
                if (list.isNotEmpty()) {
                    hasLoadedOnce = true
                    emptyCrewLayout?.visibility = View.GONE
                    crewRecyclerView?.visibility = View.VISIBLE
                    crewAdapter?.updateItems(list.take(3))
                } else if (!hasLoadedOnce) {
                    emptyCrewLayout?.visibility = View.VISIBLE
                    crewRecyclerView?.visibility = View.GONE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            crewViewModel.pendingInvites.collectLatest { pending ->
                val count = pending.size
                tvCrewPendingBadge?.visibility = if (count > 0) View.VISIBLE else View.GONE
                tvCrewPendingBadge?.text = count.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.profile.collectLatest { data ->
                data ?: return@collectLatest
                val nickname = data.nickname
                if (nicknameTextView?.text != nickname) nicknameTextView?.text = nickname
                nicknameSkeleton?.visibility = View.GONE
                nicknameTextView?.visibility = View.VISIBLE
                profileSkeleton?.visibility = View.GONE
                profileCardView?.visibility = View.VISIBLE
                userStatsSkeletonLayout?.visibility = View.GONE
                userStatsLayout?.visibility = View.VISIBLE
                transactionsCountTextView?.text = data.transactionsCount.toString()
                groupsCountTextView?.text = data.groupsCount.toString()
                if (totalTextView?.text == getString(R.string.label_borrowed)) {
                    totalBalancedTextView?.text = data.activeBorrowsCount.toString()
                }
                val imageUrl = data.profileImageUrl
                profileCardView?.visibility = View.VISIBLE
                (profileCardView as? androidx.cardview.widget.CardView)
                    ?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.orange))
                if (!imageUrl.isNullOrBlank()) {
                    profileImageView?.load(imageUrl) {
                        crossfade(false)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_profile_silhouette)
                        error(R.drawable.ic_profile_silhouette)
                        memoryCachePolicy(CachePolicy.ENABLED)
                        diskCachePolicy(CachePolicy.ENABLED)
                        memoryCacheKey(imageUrl)
                        diskCacheKey(imageUrl)
                    }
                } else {
                    profileImageView?.setImageResource(R.drawable.ic_profile_silhouette)
                }
                hasLoadedOnce = true
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groups.collectLatest { items ->
                if (items == null) return@collectLatest
                groupsSkeletonLayout?.visibility = View.GONE
                if (items.isEmpty()) {
                    emptyGroupsLayout?.visibility = View.VISIBLE
                    groupsRecyclerView?.visibility = View.GONE
                } else {
                    emptyGroupsLayout?.visibility = View.GONE
                    groupsRecyclerView?.visibility = View.VISIBLE
                    profileGroupsAdapter?.updateItems(items.take(3))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.waray.spendhound.CrewState.lastUpdateTimestamp > lastSeenCrewUpdate) {
            lastSeenCrewUpdate = com.waray.spendhound.CrewState.lastUpdateTimestamp
            if (currentUserId != -1L) {
                crewViewModel.reloadCrew(currentUserId)
            }
        }
        
        val groupsNeedRefresh = GroupsState.lastUpdateTimestamp > lastSeenGroupsUpdate
        if (groupsNeedRefresh) {
            lastSeenGroupsUpdate = GroupsState.lastUpdateTimestamp
        }

        val txNeedRefresh = com.waray.spendhound.TransactionState.lastUpdateTimestamp > lastSeenTransactionUpdate
        if (txNeedRefresh) {
            lastSeenTransactionUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
        }
        
        loadNicknameAndData(forceGroupsRefresh = groupsNeedRefresh || txNeedRefresh)
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

    internal fun loadNicknameAndData(forceGroupsRefresh: Boolean = false) {
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        if (!hasLoadedOnce) {
            nicknameSkeleton?.visibility = View.VISIBLE
            nicknameTextView?.visibility = View.GONE
            profileSkeleton?.visibility = View.VISIBLE
            profileCardView?.visibility = View.GONE
            userStatsSkeletonLayout?.visibility = View.VISIBLE
            userStatsLayout?.visibility = View.GONE
            crewSkeletonLayout?.visibility = View.VISIBLE
            emptyCrewLayout?.visibility = View.GONE
            crewRecyclerView?.visibility = View.GONE
        }
        // On revisit hasLoadedOnce=true — applyCachedState() already restored views synchronously,
        // so we just trigger background refresh without touching visibility
        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id", "username")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                user?.id?.let { userId ->
                    currentUserId = userId
                    if (forceGroupsRefresh) {
                        viewModel.invalidate(userId, authId)
                    } else {
                        viewModel.load(userId, authId)
                    }
                    crewViewModel.loadCrew(userId)
                    if (crewAdapter == null) {
                        crewAdapter = CrewMembersAdapter(
                            currentUserId = userId,
                            onMessage = { otherUser, _ -> openDm(otherUser) },
                            onRemove = { crew -> confirmRemoveCrew(crew) }
                        )
                        crewRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
                        crewRecyclerView?.adapter = crewAdapter
                    } else {
                        crewAdapter?.updateCurrentUserId(userId)
                    }
                    val userBalance = withContext(Dispatchers.IO) {
                        DeclareDatabase.userBalanceTable.select {
                            filter { eq("user_id", userId) }
                        }.decodeSingleOrNull<UserBalance>()
                    }
                    balance = userBalance?.balanceTotalGroup ?: 0.0
                    unpaid = userBalance?.unpaidTotalGroup ?: 0.0
                    currentOwe = userBalance?.receivableTotalIndividual ?: 0.0
                    currentDebt = userBalance?.unpaidTotalIndividual ?: 0.0
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error resolving user: ${e.message}")
            }
        }
    }

    private fun setupCrewSection() {
        btnAddCrew?.setOnClickListener { openAddCrewActivity() }
        btnSeeAllCrew?.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), CrewMembersActivity::class.java))
        }
        btnCrewNotification?.setOnClickListener { showPendingInvitesDialog() }
    }

    private fun openAddCrewActivity() {
        startActivity(Intent(requireContext(), AddCrewActivity::class.java).apply {
            if (currentUserId != -1L) putExtra(AddCrewActivity.EXTRA_OWNER_USER_ID, currentUserId)
        })
    }

    private fun showPendingInvitesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pending_invites, null)
        val rvPending = dialogView.findViewById<RecyclerView>(R.id.rvPendingInvites)
        val tvNoPending = dialogView.findViewById<TextView>(R.id.tvNoPending)
        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btnClosePending)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext()).setView(dialogView).create()
        btnClose.setOnClickListener { dialog.dismiss() }
        val pendingAdapter = PendingInvitesAdapter(
            onAccept = { crew -> crewViewModel.respondToInvite(crew.id!!, true, currentUserId); dialog.dismiss() },
            onDecline = { crew -> crewViewModel.respondToInvite(crew.id!!, false, currentUserId); dialog.dismiss() }
        )
        rvPending.layoutManager = LinearLayoutManager(requireContext())
        rvPending.adapter = pendingAdapter
        val pending = crewViewModel.pendingInvites.value
        if (pending.isEmpty()) { tvNoPending.visibility = View.VISIBLE; rvPending.visibility = View.GONE }
        else { tvNoPending.visibility = View.GONE; rvPending.visibility = View.VISIBLE; pendingAdapter.updateItems(pending) }
        dialog.show()
    }

    private fun confirmRemoveCrew(crew: CrewMember) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove crew member")
            .setMessage("Are you sure you want to remove this person from your crew?")
            .setPositiveButton("Remove") { _, _ -> crewViewModel.removeCrew(crew.id!!, currentUserId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDm(user: User) {
        startActivity(android.content.Intent(requireContext(), DirectMessageActivity::class.java).apply {
            putExtra(DirectMessageActivity.EXTRA_RECIPIENT_ID, user.id)
            putExtra(DirectMessageActivity.EXTRA_RECIPIENT_NAME, user.username)
            putExtra(DirectMessageActivity.EXTRA_RECIPIENT_AVATAR, user.profileImageUrl)
            putExtra(DirectMessageActivity.EXTRA_CURRENT_USER_ID, currentUserId)
        })
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
            totalTextView?.text = "Total Unpaid:"
        }
    }

    private fun setupOweButton() {
        oweTextView?.setOnClickListener {
            balanceUnpaidLayout?.foreground = balanceUnpaidDrawableTransparent
            oweDebtLayout?.foreground = oweDebtDrawable
            balanceTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            unpaidTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            unpaidTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            oweTextView?.setBackgroundResource(R.drawable.button_background_visible)
            oweTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
            debtTextView?.setBackgroundResource(R.drawable.button_background_invisible)
            debtTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(currentOwe)
            totalTextView?.text = "Total Owed To You:"
        }
    }

    private fun setupDebtButton() {
        debtTextView?.setOnClickListener {
            balanceUnpaidLayout?.foreground = balanceUnpaidDrawableTransparent
            oweDebtLayout?.foreground = oweDebtDrawable
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

    private fun setupProfileLogoutButton() {
        profileLogout?.setOnClickListener {
            lifecycleScope.launch {
                DeclareDatabase.auth.signOut()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }

    private fun setupBreakdownButton() {
        breakdownBtn?.setOnClickListener {
            startActivity(Intent(requireContext(), com.waray.spendhound.GroupsActivity::class.java))
        }
    }
}
