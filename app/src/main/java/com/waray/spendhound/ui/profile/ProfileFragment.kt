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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BreakdownAdapter
import com.waray.spendhound.BreakdownItem
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.EditProfileActivity
import com.waray.spendhound.LoginActivity
import com.waray.spendhound.MainActivity
import com.waray.spendhound.PayorAdapter
import com.waray.spendhound.R
import com.waray.spendhound.Transaction
import com.waray.spendhound.User
import com.waray.spendhound.UserBalance
import com.waray.spendhound.utils.LoadingManager
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
    private var totalBalancedTextView: TextView? = null
    private var totalTextView: TextView? = null
    private var balanceTextView: TextView? = null
    private var unpaidTextView: TextView? = null
    private var oweTextView: TextView? = null
    private var debtTextView: TextView? = null
    private var editProfileTV: TextView? = null
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
    private var breakdownBtn: Button? = null

    private var imageSignature = System.currentTimeMillis()
    private lateinit var loadingManager: LoadingManager
    private var isTabClickEnabled = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_profile, container, false)

        profileImageView = view.findViewById(R.id.profileImageView)
        nicknameTextView = view.findViewById(R.id.nicknameTextView)
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

        balanceUnpaidDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        balanceUnpaidDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        oweDebtDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        oweDebtDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        balanceUnpaidLayout?.foreground = balanceUnpaidDrawable

        balanceTextView?.setBackgroundResource(R.drawable.button_background_visible)
        balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))

        mAuth = DeclareDatabase.auth

        val loadingOverlay = view.findViewById<View>(R.id.loadingOverlay_profile)
        loadingManager = LoadingManager(loadingOverlay, viewLifecycleOwner.lifecycle) { isLoading ->
            (activity as? MainActivity)?.navView?.menu?.findItem(R.id.navigation_profile)?.isEnabled = !isLoading
            isTabClickEnabled = !isLoading
        }

        profileImageView?.isClickable = false
        profileImageView?.let { setProfileImage(it) }
        loadNicknameAndData()
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

    private fun setProfileImage(imageView: ImageView) {
        if (!isAdded) return

        loadingManager.showLoading()
        val authId = mAuth?.currentUserOrNull()?.id ?: return loadingManager.hideLoading()

        val cachedUrl: String? = PayorAdapter.sDownloadUrlCache[authId]

        if (cachedUrl != null) {
            loadGlideProfileImage(imageView, cachedUrl)
        } else {
            lifecycleScope.launch {
                try {
                    val user = withContext(Dispatchers.IO) {
                        DeclareDatabase.usersTable.select(Columns.list("user_id", "profile_image_url")) {
                            filter { eq("auth_id", authId) }
                        }.decodeSingleOrNull<User>()
                    }
                    
                    val numericUserId = user?.id
                    val url = user?.profileImageUrl ?: if (numericUserId != null) {
                        DeclareDatabase.profileImagesBucket.publicUrl("$numericUserId/$numericUserId.jpg")
                    } else {
                        null
                    }
                    
                    if (url != null) {
                        PayorAdapter.sDownloadUrlCache[authId] = url
                        loadGlideProfileImage(imageView, url)
                    } else {
                        withContext(Dispatchers.Main) {
                            imageView.setImageResource(R.drawable.placeholder_profile_image)
                            loadingManager.hideLoading()
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        imageView.setImageResource(R.drawable.placeholder_profile_image)
                        loadingManager.hideLoading()
                    }
                }
            }
        }
    }

    private fun loadGlideProfileImage(imageView: ImageView, url: String?) {
        if (!isAdded) {
            loadingManager.hideLoading()
            return
        }

        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.placeholder_profile_image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(ObjectKey(imageSignature))
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    loadingManager.hideLoading()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    loadingManager.hideLoading()
                    return false
                }
            })
            .into(imageView)
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
        loadingManager.showLoading()
        val authId = mAuth?.currentUserOrNull()?.id
        Log.d("ProfileFragment", "loadNicknameAndData - authId: $authId")
        if (authId == null) {
            Log.e("ProfileFragment", "authId is null")
            loadingManager.hideLoading()
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
                if (user?.id != null) {
                    userBalance = withContext(Dispatchers.IO) {
                        Log.d("ProfileFragment", "Fetching user balance with user_id: ${user.id}")
                        DeclareDatabase.userBalanceTable.select(Columns.list(
                            "unpaid_total_group", "unpaid_total_individual",
                            "receivable_total_group", "receivable_total_individual",
                            "balance_total_group", "balance_total_individual"
                        )) {
                            filter { eq("user_id", user.id) }
                        }.decodeSingleOrNull<UserBalance>()
                    }
                    Log.d("ProfileFragment", "User balance fetched: $userBalance")
                }

                // Update UI on Main thread
                withContext(Dispatchers.Main) {
                    currentNickname = user?.username ?: ""
                    Log.d("ProfileFragment", "Setting nicknameTextView to: $currentNickname")
                    nicknameTextView?.text = currentNickname

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

                    // Update the main display with balance
                    totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(balance)
                    totalTextView?.text = "Total Balance:"

                    Log.d("ProfileFragment", "UI Updated - balance: $balance, unpaid: $unpaid, owe: $currentOwe, debt: $currentDebt")

                    loadingManager.hideLoading()
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error loading user data: ${e.message}", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingManager.hideLoading()
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

    private fun selectNewProfilePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == Activity.RESULT_OK) {
            loadNicknameAndData()
            imageSignature = System.currentTimeMillis()
            profileImageView?.let { setProfileImage(it) }
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
                            imageSignature = System.currentTimeMillis()
                            Glide.with(this)
                                .load(imageUri)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .signature(ObjectKey(imageSignature))
                                .into(profileImageView!!)
                        }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    val imageUri = data?.data
                    if (imageUri != null) {
                        imageSignature = System.currentTimeMillis()
                        Glide.with(this)
                            .load(imageUri)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .signature(ObjectKey(imageSignature))
                            .into(profileImageView!!)
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
        breakdownBtn?.setOnClickListener { showBreakdownDialog() }
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
