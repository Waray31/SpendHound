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

class ProfileFragment : Fragment() {
    private val viewModel: ProfileViewModel by viewModels()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.profile.collectLatest { data ->
                data ?: return@collectLatest
                val nickname = data.nickname
                if (nicknameTextView?.text != nickname) nicknameTextView?.text = nickname
                nicknameSkeleton?.visibility = View.GONE
                nicknameTextView?.visibility = View.VISIBLE
                userStatsSkeletonLayout?.visibility = View.GONE
                userStatsLayout?.visibility = View.VISIBLE
                transactionsCountTextView?.text = data.transactionsCount.toString()
                groupsCountTextView?.text = data.groupsCount.toString()
                if (totalTextView?.text == getString(R.string.label_borrowed)) {
                    totalBalancedTextView?.text = data.activeBorrowsCount.toString()
                }
                // Load profile image via Coil (disk cache handles 10-min freshness)
                profileImageView?.let { setProfileImage(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groups.collectLatest { items ->
                groupsSkeletonLayout?.visibility = View.GONE
                if (items.isEmpty()) {
                    emptyGroupsLayout?.visibility = View.VISIBLE
                    groupsRecyclerView?.visibility = View.GONE
                    breakdownBtn?.text = "Create group"
                } else {
                    emptyGroupsLayout?.visibility = View.GONE
                    groupsRecyclerView?.visibility = View.VISIBLE
                    breakdownBtn?.text = getString(R.string.label_see_all_groups)
                    profileGroupsAdapter?.updateItems(items)
                }
            }
        }
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

        // Get CardView reference
        val cardView = view?.findViewById<androidx.cardview.widget.CardView>(R.id.profileCardView)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Remove updated_at from column list as it doesn't exist in the DB schema
                val user = DeclareDatabase.usersTable.select(Columns.list("user_id", "profile_image_url", "created_at")) {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()

                val profileUrl = user?.profileImageUrl
                if (profileUrl == null || profileUrl == "placeholder_profile_image") {
                    withContext(Dispatchers.Main) {
                        // No uploaded image - remove tint, set orange background, and add padding
                        imageView.setImageResource(R.drawable.placeholder_profile_image)
                        imageView.imageTintList = null
                        imageView.setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                        cardView?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.orange))
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
                            listener(
                                onSuccess = { _, _ ->
                                    // Successfully loaded image - remove tint, remove padding, and set orange background
                                    imageView.imageTintList = null
                                    imageView.setPadding(0, 0, 0, 0)
                                    cardView?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.orange))
                                },
                                onError = { _, _ ->
                                    // Error loading image - remove tint, add padding, and set orange background
                                    imageView.imageTintList = null
                                    imageView.setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                                    cardView?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.orange))
                                }
                            )
                        }
                    } else {
                        // No valid URL - remove tint, add padding, and set orange background
                        imageView.setImageResource(R.drawable.placeholder_profile_image)
                        imageView.imageTintList = null
                        imageView.setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                        cardView?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.orange))
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "setProfileImage error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Error - remove tint, add padding, and set orange background
                    imageView.setImageResource(R.drawable.placeholder_profile_image)
                    imageView.imageTintList = null
                    imageView.setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                    cardView?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.orange))
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
        val authId = mAuth?.currentUserOrNull()?.id ?: return
        // Show skeletons only on first load (no data yet)
        val isFirstLoad = nicknameTextView?.text.isNullOrBlank() || nicknameTextView?.visibility == View.GONE
        if (isFirstLoad) {
            nicknameSkeleton?.visibility = View.VISIBLE
            nicknameTextView?.visibility = View.GONE
            userStatsSkeletonLayout?.visibility = View.VISIBLE
            userStatsLayout?.visibility = View.GONE
        }
        // Resolve userId then delegate to ViewModel (emits cache first, network if stale)
        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id", "username")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                user?.id?.let { userId ->
                    viewModel.load(userId, authId)
                    // Also fetch balance for the legacy balance/owe/debt buttons
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


    // loadTopGroups is now handled by ProfileViewModel + observeViewModel()


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

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
