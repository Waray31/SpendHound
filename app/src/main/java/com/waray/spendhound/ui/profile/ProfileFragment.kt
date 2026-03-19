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
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BreakdownAdapter
import com.waray.spendhound.BreakdownItem
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.LoginActivity
import com.waray.spendhound.PayorAdapter
import com.waray.spendhound.R
import com.waray.spendhound.Transaction
import com.waray.spendhound.User
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

class ProfileFragment : Fragment() {
    private var profileImageView: ImageView? = null
    private var nicknameTextView: TextView? = null
    private var totalBalancedTextView: TextView? = null
    private var balanceTextView: TextView? = null
    private var unpaidTextView: TextView? = null
    private var oweTextView: TextView? = null
    private var debtTextView: TextView? = null
    private var totalTextView: TextView? = null
    private var nicknameEditText: EditText? = null
    private var editNickname: ImageView? = null
    private var saveNickname: ImageView? = null
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

    private var loadingOverlayProfile: View? = null
    private var pendingLoads = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_profile, container, false)

        loadingOverlayProfile = view.findViewById(R.id.loadingOverlay_profile)
        loadingOverlayProfile?.visibility = View.VISIBLE

        profileImageView = view.findViewById(R.id.profileImageView)
        nicknameTextView = view.findViewById(R.id.nicknameTextView)
        nicknameEditText = view.findViewById(R.id.nicknameEditText)
        editNickname = view.findViewById(R.id.editNickname)
        saveNickname = view.findViewById(R.id.saveNickname)
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

        profileImageView?.let { setProfileImage(it) }
        loadNicknameAndData()
        setupEditNickname()
        setupSaveNickname()
        setupUnpaidButton()
        setupBalanceButton()
        setupOweButton()
        setupDebtButton()
        setupProfileImageViewClick()
        setupProfileLogoutButton()
        setupBreakdownButton()

        val activity: AppCompatActivity? = getActivity() as AppCompatActivity?
        activity?.supportActionBar?.hide()

        return view
    }

    private fun setProfileImage(imageView: ImageView) {
        if (!isAdded) return

        showLoading()
        val userId = mAuth?.currentUserOrNull()?.id ?: return hideLoading()

        val cachedUrl: String? = PayorAdapter.sDownloadUrlCache[userId]

        if (cachedUrl != null) {
            loadGlideProfileImage(imageView, cachedUrl)
        } else {
            lifecycleScope.launch {
                try {
                    val bucket = DeclareDatabase.profileImagesBucket
                    val url = bucket.publicUrl("$userId.jpg")
                    PayorAdapter.sDownloadUrlCache[userId] = url
                    loadGlideProfileImage(imageView, url)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        imageView.setImageResource(R.drawable.placeholder_profile_image)
                        hideLoading()
                    }
                }
            }
        }
    }

    private fun loadGlideProfileImage(imageView: ImageView, url: String?) {
        if (!isAdded) {
            hideLoading()
            return
        }

        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.placeholder_profile_image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    hideLoading()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    hideLoading()
                    return false
                }
            })
            .into(imageView)
    }

    private fun setupEditNickname() {
        editNickname?.setOnClickListener {
            switchToEditMode()
            nicknameEditText?.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(nicknameEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupSaveNickname() {
        saveNickname?.setOnClickListener {
            saveNickname()
            switchToDisplayMode()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(nicknameEditText?.windowToken, 0)
        }
    }

    private fun loadNicknameAndData() {
        showLoading()
        val currentUserID = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return hideLoading()
        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("username")) {
                        filter { eq("user_id", currentUserID) }
                    }.decodeSingleOrNull<User>()
                }
                currentNickname = user?.username ?: ""
                nicknameTextView?.text = currentNickname
                
                totalBalanceUnpaid()
                fetchDebt()
                fetchOwe()
            } catch (e: Exception) {
                Log.e("Supabase", "Error loading profile data: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    private fun switchToEditMode() {
        nicknameTextView?.visibility = View.GONE
        nicknameEditText?.visibility = View.VISIBLE
        nicknameEditText?.setText(currentNickname)
        editNickname?.visibility = View.GONE
        saveNickname?.visibility = View.VISIBLE
    }

    private fun switchToDisplayMode() {
        nicknameTextView?.visibility = View.VISIBLE
        nicknameEditText?.visibility = View.GONE
        currentNickname = nicknameEditText?.text.toString()
        nicknameTextView?.text = currentNickname
        editNickname?.visibility = View.VISIBLE
        saveNickname?.visibility = View.GONE
    }

    private fun saveNickname() {
        val updatedNickname = nicknameEditText?.text.toString()
        currentNickname = updatedNickname
        val userId = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.update({
                        set("username", updatedNickname)
                    }) {
                        filter { eq("user_id", userId) }
                    }
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error saving nickname: ${e.message}")
            }
        }
    }

    private fun fetchOwe() {
        val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return
        lifecycleScope.launch {
            try {
                val borrows = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter { 
                            eq("lender_id", currentUserId)
                            neq("status", 3) // Not Paid
                        }
                    }.decodeList<BorrowNowTransaction>()
                }
                currentOwe = borrows.sumOf { it.borrowedAmount ?: 0.0 }
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching owe: ${e.message}")
            }
        }
    }

    private fun fetchDebt() {
        val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return
        lifecycleScope.launch {
            try {
                val borrows = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter { 
                            eq("borrower_id", currentUserId)
                            neq("status", 3) // Not Paid
                        }
                    }.decodeList<BorrowNowTransaction>()
                }
                currentDebt = borrows.sumOf { it.borrowedAmount ?: 0.0 }
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching debt: ${e.message}")
            }
        }
    }

    private fun totalBalanceUnpaid() {
        lifecycleScope.launch {
            try {
                val transactions = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                }
                var totalIndividualPaymentSum = 0.0
                var totalPaymentListSum = 0.0

                for (transaction in transactions) {
                    val individualPayment = transaction.totalIndividualPayment
                    val payorsList = transaction.payorsDisplayNames ?: transaction.payorsList ?: emptyList()
                    val amountsPaidList = transaction.amountsPaidList ?: emptyList()

                    val userIndex = payorsList.indexOf(currentNickname)
                    if (userIndex != -1 && userIndex < amountsPaidList.size) {
                        totalIndividualPaymentSum += individualPayment
                        totalPaymentListSum += (amountsPaidList[userIndex] ?: 0.0)
                    }
                }

                if (totalPaymentListSum > totalIndividualPaymentSum) {
                    balance = totalPaymentListSum - totalIndividualPaymentSum
                    unpaid = 0.0
                } else {
                    unpaid = totalIndividualPaymentSum - totalPaymentListSum
                    balance = 0.0
                }

                totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(balance)
                totalTextView?.text = "Total Balance:"
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching balance/unpaid: ${e.message}")
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

    private fun setupProfileImageViewClick() {
        profileImageView?.setOnClickListener {
            showProfilePictureMenu()
        }
    }

    private fun showProfilePictureMenu() {
        val popupMenu = PopupMenu(requireContext(), profileImageView)
        popupMenu.menuInflater.inflate(R.menu.profile_picture_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_view_profile_photo -> {
                    showLargeProfilePhoto()
                    true
                }
                R.id.action_change_profile_photo -> {
                    showChangeProfilePhotoDialog()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showLargeProfilePhoto() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_photo, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialog_profile_photo)
        setProfileImage(imageView)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.show()
        imageView.setOnClickListener { dialog.dismiss() }
    }

    private fun selectNewProfilePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val extras = data?.extras
                    val imageBitmap = extras?.get("data") as? Bitmap
                    imageBitmap?.let {
                        val imageUri = getImageUri(requireContext(), it)
                        updateProfilePhoto(imageUri)
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    val imageUri = data?.data
                    updateProfilePhoto(imageUri)
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

    private fun showChangeProfilePhotoDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Change Profile Photo")
        val options = arrayOf("Take Photo", "Choose from Gallery")

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> selectNewProfilePhoto()
                1 -> {
                    val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(pickPhotoIntent, REQUEST_IMAGE_PICK)
                }
            }
        }
        builder.create().show()
    }

    private fun updateProfilePhoto(imageUri: Uri?) {
        showLoading()
        Glide.with(this)
            .load(imageUri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable?>?, isFirstResource: Boolean): Boolean {
                    hideLoading()
                    return false
                }
                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable?>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    hideLoading()
                    return false
                }
            })
            .into(profileImageView!!)

        uploadProfilePhoto(imageUri)
    }

    private fun uploadProfilePhoto(imageUri: Uri?) {
        val userId = mAuth?.currentUserOrNull()?.id ?: return hideLoading()
        
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(imageUri!!)?.use { it.readBytes() }
                }
                if (bytes != null) {
                    val bucket = DeclareDatabase.profileImagesBucket
                    val path = "$userId.jpg"
                    bucket.upload(path, bytes, upsert = true)
                    val publicUrl = bucket.publicUrl(path)
                    
                    PayorAdapter.sDownloadUrlCache[userId] = publicUrl
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(requireContext(), "Profile Photo Changed Successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                    val individualPayment = transaction.totalIndividualPayment
                    val payorsList = transaction.payorsDisplayNames ?: transaction.payorsList ?: emptyList()
                    val amountsPaidList = transaction.amountsPaidList ?: emptyList()
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
                    val individualPayment = transaction.totalIndividualPayment
                    val payorsList = transaction.payorsDisplayNames ?: transaction.payorsList ?: emptyList()
                    val amountsPaidList = transaction.amountsPaidList ?: emptyList()
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
        val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val borrows = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter { eq("lender_id", currentUserId) }
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
            } catch (e: Exception) {
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
        val currentUserId = mAuth?.currentUserOrNull()?.id?.toLongOrNull() ?: return
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val borrows = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select {
                        filter { eq("borrower_id", currentUserId) }
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
            } catch (e: Exception) {
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

    private fun showLoading() {
        pendingLoads++
        loadingOverlayProfile?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        pendingLoads = max(0, pendingLoads - 1)
        if (pendingLoads == 0) {
            loadingOverlayProfile?.visibility = View.GONE
        }
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_IMAGE_PICK = 2
    }
}
