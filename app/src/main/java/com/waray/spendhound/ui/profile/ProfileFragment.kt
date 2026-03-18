package com.waray.spendhound.ui.profile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BorrowTransaction
import com.waray.spendhound.BreakdownAdapter
import com.waray.spendhound.BreakdownItem
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.LoginActivity
import com.waray.spendhound.MigrationCallback
import com.waray.spendhound.MigrationHelper
import com.waray.spendhound.PayorAdapter
import com.waray.spendhound.R
import com.waray.spendhound.Transaction
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Objects
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
    private val monthSpinner: Spinner? = null
    var mAuth: Auth? = null
    var sortedMonths: MutableList<String?>? = null
    private var currentNickname: String? = ""
    var monthYear: String? = null
    private var totalIndividualPayment = 0
    private var totalPaymentList = 0
    private var balance = 0
    private var unpaid = 0
    
    private var currentOwe = 0
    private var currentDebt = 0
    private var balanceUnpaidLayout: View? = null
    private var oweDebtLayout: View? = null
    private var balanceUnpaidDrawable: Drawable? = null
    private var oweDebtDrawable: Drawable? = null
    private var balanceUnpaidDrawableTransparent: Drawable? = null
    private var oweDebtDrawableTransparent: Drawable? = null
    private var profileLogout: Button? = null
    private var btnAdminSettings: Button? = null
    private var breakdownBtn: Button? = null

    private var loadingOverlay_profile: View? = null
    private var pendingLoads = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_profile, container, false)

        loadingOverlay_profile = view.findViewById<View?>(R.id.loadingOverlay_profile)
        loadingOverlay_profile?.visibility = View.VISIBLE

        profileImageView = view.findViewById<ImageView>(R.id.profileImageView)
        nicknameTextView = view.findViewById<TextView>(R.id.nicknameTextView)
        nicknameEditText = view.findViewById<EditText>(R.id.nicknameEditText)
        editNickname = view.findViewById<ImageView>(R.id.editNickname)
        saveNickname = view.findViewById<ImageView>(R.id.saveNickname)
        totalBalancedTextView = view.findViewById<TextView>(R.id.totalBalancedTextView)
        totalTextView = view.findViewById<TextView>(R.id.totalTextView)
        balanceTextView = view.findViewById<TextView>(R.id.balanceTextView)
        unpaidTextView = view.findViewById<TextView>(R.id.unpaidTextView)
        oweTextView = view.findViewById<TextView>(R.id.oweTextView)
        debtTextView = view.findViewById<TextView>(R.id.debtTextView)
        balanceUnpaidLayout = view.findViewById<View>(R.id.balanceUnpaidLayout)
        oweDebtLayout = view.findViewById<View>(R.id.oweDebtLayout)
        profileLogout = view.findViewById<Button>(R.id.profileLogout)
        btnAdminSettings = view.findViewById<Button>(R.id.btnAdminSettings)
        breakdownBtn = view.findViewById<Button>(R.id.breakdown_btn)

        balanceUnpaidDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        balanceUnpaidDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        oweDebtDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.round_border_glassy)
        oweDebtDrawableTransparent = ContextCompat.getDrawable(requireContext(), R.drawable.transparent_background)
        balanceUnpaidLayout?.foreground = balanceUnpaidDrawable

        balanceTextView?.setBackgroundResource(R.drawable.button_background_visible)
        balanceTextView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))

        mAuth = DeclareDatabase.auth

        profileImageView?.let { setProfileImage(it) }
        loadNickname()
        setupEditNickname()
        setupSaveNickname()
        TotalBalanceUnpaid()
        setupUnpaidButton()
        setupBalanceButton()
        setupOweButton()
        setupDebtButton()
        fetchDebt()
        fetchOwe()
        setupProfileImageViewClick()
        setupProfileLogoutButton()
        setupAdminSettingsButton()
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

    private fun loadNickname() {
        showLoading()
        val currentUserID = mAuth?.currentUserOrNull()?.id ?: return hideLoading()
        val usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID)
        usersRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentNickname = dataSnapshot.getValue(String::class.java)
                    nicknameTextView?.text = currentNickname
                }
                hideLoading()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.message)
                hideLoading()
            }
        })
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
        val userId = mAuth?.currentUserOrNull()?.id ?: return
        val userRef = DeclareDatabase.getDatabaseReference().child(userId)
        userRef.child("username").setValue(updatedNickname)
    }

    private fun fetchOwe() {
        showLoading()
        val databaseReference = DeclareDatabase.getDBRefBorrows()
        val currentUserId = mAuth?.currentUserOrNull()?.id ?: return hideLoading()

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                currentOwe = 0
                for (monthSnapshot in dataSnapshot.children) {
                    for (daySnapshot in monthSnapshot.children) {
                        for (borrowSnapshot in daySnapshot.children) {
                            val borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction::class.java)
                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() == currentUserId) {
                                try {
                                    currentOwe += borrowNowTransaction.getBorrowedAmountStr().toInt()
                                } catch (e: NumberFormatException) {
                                    Log.e("ProfileFragment", "Error parsing amount: " + e.message)
                                }
                            }
                        }
                    }
                }
                hideLoading()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.message)
                hideLoading()
            }
        })
    }

    private fun fetchDebt() {
        showLoading()
        val databaseReference = DeclareDatabase.getDBRefBorrows()
        val currentUserId = mAuth?.currentUserOrNull()?.id ?: return hideLoading()

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                currentDebt = 0
                for (monthSnapshot in dataSnapshot.children) {
                    for (daySnapshot in monthSnapshot.children) {
                        for (borrowSnapshot in daySnapshot.children) {
                            val borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction::class.java)
                            if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() == currentUserId) {
                                try {
                                    currentDebt += borrowNowTransaction.getBorrowedAmountStr().toInt()
                                } catch (e: NumberFormatException) {
                                    Log.e("ProfileFragment", "Error parsing amount: " + e.message)
                                }
                            }
                        }
                    }
                }
                hideLoading()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.message)
                hideLoading()
            }
        })
    }

    private fun TotalBalanceUnpaid() {
        showLoading()
        val transRef = DeclareDatabase.getDBRefTransaction()

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                totalIndividualPayment = 0
                totalPaymentList = 0

                for (monthSnapshot in dataSnapshot.children) {
                    for (daySnapshot in monthSnapshot.children) {
                        for (timeSnapshot in daySnapshot.children) {
                            val transaction = timeSnapshot.getValue(Transaction::class.java) ?: continue
                            val individualPayment = transaction.getTotalIndividualPayment()
                            
                            val payorsSnapshot = timeSnapshot.child("payorsList")
                            var userIndex = -1
                            var idx = 0
                            for (payorSnapshot in payorsSnapshot.children) {
                                val payorUsername = payorSnapshot.getValue(String::class.java)
                                if (payorUsername == currentNickname) {
                                    userIndex = idx
                                    totalIndividualPayment += individualPayment.toInt()
                                    break
                                }
                                idx++
                            }

                            if (userIndex != -1) {
                                val amountsSnapshot = timeSnapshot.child("amountsPaidList")
                                idx = 0
                                for (amountSnapshot in amountsSnapshot.children) {
                                    if (idx == userIndex) {
                                        totalPaymentList += (amountSnapshot.getValue(Int::class.java) ?: 0)
                                        break
                                    }
                                    idx++
                                }
                            }
                        }
                    }
                }
                
                if (totalPaymentList > totalIndividualPayment) {
                    balance = totalPaymentList - totalIndividualPayment
                    unpaid = 0
                } else {
                    unpaid = totalIndividualPayment - totalPaymentList
                    balance = 0
                }

                totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(balance.toDouble())
                totalTextView?.text = "Total Balance:"
                hideLoading()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.message)
                hideLoading()
            }
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

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(balance.toDouble())
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

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(unpaid.toDouble())
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

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(currentOwe.toDouble())
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

            totalBalancedTextView?.text = CurrencyUtils.formatAmountWithCurrency(currentDebt.toDouble())
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

    private fun setupAdminSettingsButton() {
        btnAdminSettings?.setOnClickListener { showAdminLoginDialog() }
    }

    private fun showAdminLoginDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_admin_login, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.etAdminUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etAdminPassword)

        AlertDialog.Builder(requireContext())
            .setTitle("Admin Access")
            .setView(dialogView)
            .setPositiveButton("Login") { _, _ ->
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (username == "admin" && password == "admin") {
                    showAdminPanelDialog()
                } else {
                    Toast.makeText(requireContext(), "Invalid credentials", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminPanelDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_admin_panel, null)
        val btnRunMigration = dialogView.findViewById<Button>(R.id.btnRunMigration)
        val btnMigrateBalances = dialogView.findViewById<Button>(R.id.btnMigrateBalances)
        val btnMigrateBorrowIndex = dialogView.findViewById<Button>(R.id.btnMigrateBorrowIndex)
        val btnMigrateExistingUsers = dialogView.findViewById<Button>(R.id.btnMigrateExistingUsers)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvMigrationStatus)

        val adminDialog = AlertDialog.Builder(requireContext())
            .setTitle("Admin Panel")
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        val callback = object : MigrationCallback {
            override fun onComplete(migratedCount: Int) {
                tvStatus.text = "Status: Migration complete! $migratedCount records."
                Toast.makeText(requireContext(), "Migration complete", Toast.LENGTH_SHORT).show()
            }
            override fun onError(error: String?) {
                tvStatus.text = "Status: Error - $error"
            }
        }

        btnRunMigration.setOnClickListener { MigrationHelper.runAllMigrations(callback) }
        btnMigrateBalances.setOnClickListener { MigrationHelper.migrateUserBalances(callback) }
        btnMigrateBorrowIndex.setOnClickListener { MigrationHelper.migrateUserBorrowsIndex(callback) }
        btnMigrateExistingUsers.setOnClickListener { MigrationHelper.migrateExistingUsers(callback) }

        adminDialog.show()
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
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(balance.toDouble())
                loadBalanceBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
            BreakdownItem.Category.UNPAID -> {
                categoryTitle.text = "Total Unpaid"
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(unpaid.toDouble())
                loadUnpaidBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
            BreakdownItem.Category.OWE -> {
                categoryTitle.text = "Total Owed"
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(currentOwe.toDouble())
                loadOweBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
            BreakdownItem.Category.DEBT -> {
                categoryTitle.text = "Total Debt"
                totalAmount.text = CurrencyUtils.formatAmountWithCurrency(currentDebt.toDouble())
                loadDebtBreakdown(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar)
            }
        }
    }

    private fun loadBalanceBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        DeclareDatabase.getDBRefTransaction().addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()
                for (monthSnapshot in dataSnapshot.children) {
                    val monthKey = monthSnapshot.key
                    for (daySnapshot in monthSnapshot.children) {
                        val dayKey = daySnapshot.key
                        val dateStr = if (dayKey != null && monthKey != null) "$monthKey $dayKey" else "Unknown Date"
                        for (timeSnapshot in daySnapshot.children) {
                            val transaction = timeSnapshot.getValue(Transaction::class.java) ?: continue
                            val individualPayment = transaction.getTotalIndividualPayment()
                            val payorsSnapshot = timeSnapshot.child("payorsList")
                            var userIndex = -1
                            var idx = 0
                            for (payorSnapshot in payorsSnapshot.children) {
                                if (payorSnapshot.getValue(String::class.java) == currentNickname) {
                                    userIndex = idx
                                    break
                                }
                                idx++
                            }
                            if (userIndex != -1) {
                                val amountsSnapshot = timeSnapshot.child("amountsPaidList")
                                idx = 0
                                var userPayment = 0.0
                                for (amountSnapshot in amountsSnapshot.children) {
                                    if (idx == userIndex) {
                                        userPayment = (amountSnapshot.getValue(Int::class.java) ?: 0).toDouble()
                                        break
                                    }
                                    idx++
                                }
                                val transactionBalance = userPayment - individualPayment
                                if (transactionBalance > 0) {
                                    items.add(BreakdownItem(BreakdownItem.Category.BALANCE, dateStr, "Transaction", transactionBalance, "Completed", transaction.getTransactionType() ?: ""))
                                }
                            }
                        }
                    }
                }
                updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No balance transactions found")
            }
            override fun onCancelled(databaseError: DatabaseError) {
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        })
    }

    private fun loadUnpaidBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        DeclareDatabase.getDBRefTransaction().addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()
                for (monthSnapshot in dataSnapshot.children) {
                    val monthKey = monthSnapshot.key
                    for (daySnapshot in monthSnapshot.children) {
                        val dayKey = daySnapshot.key
                        val dateStr = if (dayKey != null && monthKey != null) "$monthKey $dayKey" else "Unknown Date"
                        for (timeSnapshot in daySnapshot.children) {
                            val transaction = timeSnapshot.getValue(Transaction::class.java) ?: continue
                            val individualPayment = transaction.getTotalIndividualPayment()
                            val payorsSnapshot = timeSnapshot.child("payorsList")
                            var userIndex = -1
                            var idx = 0
                            for (payorSnapshot in payorsSnapshot.children) {
                                if (payorSnapshot.getValue(String::class.java) == currentNickname) {
                                    userIndex = idx
                                    break
                                }
                                idx++
                            }
                            if (userIndex != -1) {
                                val amountsSnapshot = timeSnapshot.child("amountsPaidList")
                                idx = 0
                                var userPayment = 0
                                for (amountSnapshot in amountsSnapshot.children) {
                                    if (idx == userIndex) {
                                        userPayment = amountSnapshot.getValue(Int::class.java) ?: 0
                                        break
                                    }
                                    idx++
                                }
                                val transactionUnpaid = individualPayment - userPayment
                                if (transactionUnpaid > 0) {
                                    items.add(BreakdownItem(BreakdownItem.Category.UNPAID, dateStr, "Transaction", transactionUnpaid, "Pending", transaction.getTransactionType() ?: ""))
                                }
                            }
                        }
                    }
                }
                updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No unpaid transactions found")
            }
            override fun onCancelled(databaseError: DatabaseError) {
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        })
    }

    private fun loadOweBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        DeclareDatabase.getDBRefBorrows().addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()
                for (monthSnapshot in dataSnapshot.children) {
                    for (daySnapshot in monthSnapshot.children) {
                        for (borrowSnapshot in daySnapshot.children) {
                            val bnt = borrowSnapshot.getValue(BorrowNowTransaction::class.java)
                            if (bnt != null && bnt.getLenderID() == mAuth?.currentUserOrNull()?.id) {
                                items.add(BreakdownItem(BreakdownItem.Category.OWE, bnt.getDate() ?: "Unknown Date", "From: ${bnt.getBorrowerName() ?: "Unknown"}", bnt.getBorrowedAmountStr().toDouble(), bnt.getStatus() ?: "Pending"))
                            }
                        }
                    }
                }
                updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No owed amounts found")
            }
            override fun onCancelled(databaseError: DatabaseError) {
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        })
    }

    private fun loadDebtBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        DeclareDatabase.getDBRefBorrows().addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()
                for (monthSnapshot in dataSnapshot.children) {
                    for (daySnapshot in monthSnapshot.children) {
                        for (borrowSnapshot in daySnapshot.children) {
                            val bnt = borrowSnapshot.getValue(BorrowNowTransaction::class.java)
                            if (bnt != null && bnt.getBorrowerID() == mAuth?.currentUserOrNull()?.id) {
                                items.add(BreakdownItem(BreakdownItem.Category.DEBT, bnt.getDate() ?: "Unknown Date", "To: ${bnt.getLender() ?: "Unknown"}", bnt.getBorrowedAmountStr().toDouble(), bnt.getStatus() ?: "Pending"))
                            }
                        }
                    }
                }
                updateBreakdownUI(items, adapter, recyclerView, emptyStateLayout, emptyStateText, progressBar, "No debt found")
            }
            override fun onCancelled(databaseError: DatabaseError) {
                progressBar.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateText.text = "Error loading data"
            }
        })
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
        loadingOverlay_profile?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        pendingLoads = max(0, pendingLoads - 1)
        if (pendingLoads == 0) {
            loadingOverlay_profile?.visibility = View.GONE
        }
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_IMAGE_PICK = 2
    }
}
