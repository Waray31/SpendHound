package com.waray.spendhound.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.target.Target
import com.google.firebase.auth.FirebaseAuth
import com.waray.spendhound.BreakdownItem
import com.waray.spendhound.Transaction
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
    var mAuth: FirebaseAuth? = null
    var sortedMonths: MutableList<String?>? = null
    private var currentNickname: String? = ""
    var monthYear: String? = null
    private var totalIndividualPayment = 0
    private var totalPaymentList = 0
    private var balance = 0
    private var unpaid = 0
    private val owe: Int
        get() {
            showLoading()
            val databaseReference: DatabaseReference = DeclareDatabase.getDBRefBorrows()
            val currentUserId: String? = FirebaseAuth.getInstance().getCurrentUser().getUid()

            databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
                @SuppressLint("NotifyDataSetChanged")
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    currentOwe = 0
                    for (monthSnapshot in dataSnapshot.getChildren()) {
                        for (daySnapshot in monthSnapshot.getChildren()) {
                            for (borrowSnapshot in daySnapshot.getChildren()) {
                                // Try new structure first: borrows/{month}/{day}/{borrowId}
                                val borrowNowTransaction: BorrowNowTransaction? =
                                    borrowSnapshot.getValue(BorrowNowTransaction::class.java)

                                if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null) {
                                    // New UID-based structure - check if current user is the lender
                                    if (borrowNowTransaction.getLenderID() == currentUserId) {
                                        try {
                                            val borrowedAmount =
                                                borrowNowTransaction.getBorrowedAmountStr().toInt()
                                            currentOwe += borrowedAmount
                                        } catch (e: NumberFormatException) {
                                            Log.e(
                                                "ProfileFragment",
                                                "Error parsing amount: " + e.message
                                            )
                                        }
                                    }
                                } else {
                                    // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                    for (timeSnapshot in borrowSnapshot.getChildren()) {
                                        try {
                                            val borrowTransaction: BorrowTransaction? =
                                                timeSnapshot.getValue(BorrowTransaction::class.java)
                                            if (borrowTransaction != null) {
                                                val borrowee: String? =
                                                    borrowTransaction.getBorrowee()
                                                val borrowedAmount =
                                                    borrowTransaction.getBorrowedAmountStr().toInt()
                                                if (currentNickname == borrowee) {
                                                    currentOwe += borrowedAmount
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                "ProfileFragment",
                                                "Error parsing legacy transaction: " + e.message
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    hideLoading()
                }

                public override fun onCancelled(databaseError: DatabaseError) {
                    // Handle database read error
                    val errorMessage =
                        "Database read error occurred: " + databaseError.getMessage()
                    Log.e("FirebaseDatabase", errorMessage)
                    hideLoading()
                }
            })
        }
    private val debt: Int
        get() {
            showLoading()
            val databaseReference: DatabaseReference = DeclareDatabase.getDBRefBorrows()
            val currentUserId: String? = FirebaseAuth.getInstance().getCurrentUser().getUid()

            databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
                @SuppressLint("NotifyDataSetChanged")
                public override fun onDataChange(dataSnapshot: DataSnapshot) {
                    currentDebt = 0
                    for (monthSnapshot in dataSnapshot.getChildren()) {
                        for (daySnapshot in monthSnapshot.getChildren()) {
                            for (borrowSnapshot in daySnapshot.getChildren()) {
                                // Try new structure first: borrows/{month}/{day}/{borrowId}
                                val borrowNowTransaction: BorrowNowTransaction? =
                                    borrowSnapshot.getValue(BorrowNowTransaction::class.java)

                                if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() != null) {
                                    // New UID-based structure - check if current user is the borrower
                                    if (borrowNowTransaction.getBorrowerID() == currentUserId) {
                                        try {
                                            val borrowedAmount =
                                                borrowNowTransaction.getBorrowedAmountStr().toInt()
                                            currentDebt += borrowedAmount
                                        } catch (e: NumberFormatException) {
                                            Log.e(
                                                "ProfileFragment",
                                                "Error parsing amount: " + e.message
                                            )
                                        }
                                    }
                                } else {
                                    // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                    val currentUserStr: String? = borrowSnapshot.getKey()
                                    if (currentUserStr == currentNickname) {
                                        for (timeSnapshot in borrowSnapshot.getChildren()) {
                                            try {
                                                val borrowTransaction: BorrowTransaction? =
                                                    timeSnapshot.getValue(BorrowTransaction::class.java)
                                                if (borrowTransaction != null) {
                                                    val borrowedAmount =
                                                        borrowTransaction.getBorrowedAmountStr()
                                                            .toInt()
                                                    currentDebt += borrowedAmount
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "ProfileFragment",
                                                    "Error parsing legacy transaction: " + e.message
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    hideLoading()
                }

                public override fun onCancelled(databaseError: DatabaseError) {
                    // Handle database read error
                    val errorMessage =
                        "Database read error occurred: " + databaseError.getMessage()
                    Log.e("FirebaseDatabase", errorMessage)
                    hideLoading()
                }
            })
        }
    private var i = 0
    private var e = 0
    private var o = 0
    private val currentBalance = 0
    private val currentUnpaid = 0
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
        if (loadingOverlay_profile != null) {
            loadingOverlay_profile!!.setVisibility(View.VISIBLE)
        }

        profileImageView = view.findViewById<ImageView>(R.id.profileImageView)
        setProfileImage(profileImageView!!)
        nicknameTextView = view.findViewById<TextView>(R.id.nicknameTextView)
        nicknameEditText = view.findViewById<EditText>(R.id.nicknameEditText)
        editNickname = view.findViewById<ImageView>(R.id.editNickname)
        saveNickname = view.findViewById<ImageView>(R.id.saveNickname)
        //monthSpinner = view.findViewById(R.id.monthSpinner);
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

        balanceUnpaidDrawable =
            ContextCompat.getDrawable(getContext(), R.drawable.round_border_glassy)
        balanceUnpaidDrawableTransparent =
            ContextCompat.getDrawable(getContext(), R.drawable.transparent_background)
        oweDebtDrawable = ContextCompat.getDrawable(getContext(), R.drawable.round_border_glassy)
        oweDebtDrawableTransparent =
            ContextCompat.getDrawable(getContext(), R.drawable.transparent_background)
        balanceUnpaidLayout!!.setForeground(balanceUnpaidDrawable)

        balanceTextView.setBackgroundResource(R.drawable.button_background_visible)
        balanceTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.yellow))

        mAuth = FirebaseAuth.getInstance()

        loadNickname()
        EditNickname()
        SaveNickname()
        //MonthlyFilter();
        TotalBalanceUnpaid()
        UnpaidButton()
        BalanceButton()
        OweButton()
        DebtButton()
        this.debt
        this.owe
        profileImageViewButton()
        ProfileLogoutButton()
        AdminSettingsButton()
        BreakdownButton()


        // Get the hosting Activity and remove the ActionBar
        val activity: AppCompatActivity? = getActivity() as AppCompatActivity?
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide()
        }



        return view
    }

    fun setProfileImage(imageView: ImageView) {
        if (!isAdded()) return

        showLoading()
        val userId: String? =
            Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser()).getUid()


        // Use the same static cache from PayorAdapter
        val cachedUrl: String? = PayorAdapter.Companion.sDownloadUrlCache.get(userId)

        if (cachedUrl != null) {
            loadGlideProfileImage(imageView, cachedUrl)
        } else {
            val storageRef: StorageReference =
                FirebaseStorage.getInstance().getReference("profile_images").child(userId)
            storageRef.getDownloadUrl().addOnSuccessListener({ uri ->
                if (isAdded()) {
                    val url: String? = uri.toString()
                    PayorAdapter.Companion.sDownloadUrlCache.put(userId, url)
                    loadGlideProfileImage(imageView, url)
                } else {
                    hideLoading()
                }
            }).addOnFailureListener({ e ->
                if (isAdded()) {
                    imageView.setImageResource(R.drawable.placeholder_profile_image)
                }
                hideLoading()
            })
        }
    }

    private fun loadGlideProfileImage(imageView: ImageView, url: String?) {
        if (!isAdded()) {
            hideLoading()
            return
        }

        Glide.with(requireContext())
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

    private fun EditNickname() {
        editNickname!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                // Switch to edit mode
                switchToEditMode()

                // Request focus on the EditText where the user can input their new nickname
                nicknameEditText.requestFocus()

                // Show the keyboard
                val imm =
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(nicknameEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        })
    }

    private fun SaveNickname() {
        saveNickname!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                // Save the updated nickname to your data source
                saveNickname()

                // Switch back to display mode
                switchToDisplayMode()

                // Hide the keyboard
                val imm =
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(nicknameEditText.getWindowToken(), 0)
            }
        })
    }

    private fun loadNickname() {
        showLoading()
        val currentUserID: String? =
            Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser()).getUid()
        val usersRef: DatabaseReference =
            DeclareDatabase.getDatabaseReference().child(currentUserID)
        usersRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Get the username from the dataSnapshot and assign it to usernamePost
                    currentNickname = dataSnapshot.getValue(String::class.java)
                    Log.d("FirebaseDatabase", "Nickname loaded: " + currentNickname)

                    // Update the TextView with the loaded nickname
                    nicknameTextView.setText(currentNickname)
                } else {
                    Log.d("FirebaseDatabase", "Nickname not found in database.")
                }
                hideLoading()
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                // Handle database read error
                val errorMessage = "Database read error occurred: " + databaseError.getMessage()
                Log.e("FirebaseDatabase", errorMessage)
                hideLoading()
            }
        })
    }

    private fun switchToEditMode() {
        // Hide the TextView and show the EditText for editing
        nicknameTextView.setVisibility(View.GONE)
        nicknameEditText.setVisibility(View.VISIBLE)

        // Set the EditText text to the current nickname
        nicknameEditText.setText(currentNickname)

        // Show the Save button and hide the Edit button
        editNickname!!.setVisibility(View.GONE)
        saveNickname!!.setVisibility(View.VISIBLE)
    }

    private fun switchToDisplayMode() {
        // Show the TextView and hide the EditText
        nicknameTextView.setVisibility(View.VISIBLE)
        nicknameEditText.setVisibility(View.GONE)

        // Update the TextView with the edited nickname
        currentNickname = nicknameEditText.getText().toString()
        nicknameTextView.setText(currentNickname)

        // Show the Edit button and hide the Save button
        editNickname!!.setVisibility(View.VISIBLE)
        saveNickname!!.setVisibility(View.GONE)
    }

    private fun saveNickname() {
        // Save the updated nickname to your data source (e.g., Firebase database)
        // Implement your database update logic here
        val updatedNickname = nicknameEditText.getText().toString()
        currentNickname = updatedNickname

        val userId: String? = FirebaseAuth.getInstance().getCurrentUser().getUid()
        val userRef: DatabaseReference = DeclareDatabase.getDatabaseReference().child(userId)
        userRef.child("username").setValue(updatedNickname)
    }

    fun TotalBalanceUnpaid() {
        showLoading()
        val transRef: DatabaseReference = DeclareDatabase.getDBRefTransaction()
        val currentYear =
            SimpleDateFormat("yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime())

        i = 0
        e = 1
        o = 0

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Initialize variables to hold total balance and unpaid amounts
                val totalBalance = 0
                val totalUnpaid = 0
                totalIndividualPayment = 0
                totalPaymentList = 0

                // Iterate over all months in the database
                for (monthSnapshot in dataSnapshot.getChildren()) {
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        for (timeSnapshot in daySnapshot.getChildren()) {
                            val transaction: Transaction? =
                                timeSnapshot.getValue(Transaction::class.java)
                            if (transaction != null) {
                                val individualPayment = transaction.getTotalIndividualPayment()
                                totalIndividualPayment =
                                    (totalIndividualPayment + individualPayment).toInt()
                            }
                            val payorsSnapshot: DataSnapshot = timeSnapshot.child("payorsList")
                            for (payorSnapshot in payorsSnapshot.getChildren()) {
                                val payorUsername: String? =
                                    payorSnapshot.getValue(String::class.java)
                                if (payorUsername != null && payorUsername == currentNickname) {
                                    i++
                                    o = i
                                } else {
                                    i++
                                }
                            }
                            val amountsPaidListSnapshot: DataSnapshot =
                                timeSnapshot.child("amountsPaidList")
                            for (amountSnapshot in amountsPaidListSnapshot.getChildren()) {
                                val paymentAmount: Int = amountSnapshot.getValue(Int::class.java)
                                if (e == o) {
                                    totalPaymentList += paymentAmount
                                    e = 100
                                } else {
                                    e++
                                }
                            }
                            i = 0
                            e = 1
                            o = 0
                        }
                    }
                }
                if (totalPaymentList == totalIndividualPayment) {
                    balance = 0
                    unpaid = 0
                } else if (totalIndividualPayment > totalPaymentList) {
                    unpaid = totalIndividualPayment - totalPaymentList
                    balance = 0
                } else if (totalIndividualPayment < totalPaymentList) {
                    balance = totalPaymentList - totalIndividualPayment
                    unpaid = 0
                } else {
                    balance = 0
                    unpaid = 0
                }

                totalBalancedTextView.setText(CurrencyUtils.formatAmountWithCurrency(balance.toDouble()))
                totalTextView.setText("Total Balance:")
                hideLoading()
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                // Handle database read error
                val errorMessage = "Database read error occurred: " + databaseError.getMessage()
                Log.e("FirebaseDatabase", errorMessage)
                hideLoading()
            }
        })
    }


    fun BalanceButton() {
        balanceTextView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                balanceUnpaidLayout!!.setForeground(balanceUnpaidDrawable)
                oweDebtLayout!!.setForeground(oweDebtDrawableTransparent)

                balanceTextView.setBackgroundResource(R.drawable.button_background_visible)
                balanceTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.yellow))
                unpaidTextView.setBackgroundResource(R.drawable.button_background_invisible)
                unpaidTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                oweTextView.setBackgroundResource(R.drawable.button_background_invisible)
                oweTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                debtTextView.setBackgroundResource(R.drawable.button_background_invisible)
                debtTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))

                totalBalancedTextView.setText(CurrencyUtils.formatAmountWithCurrency(balance.toDouble()))
                totalTextView.setText("Total Balance:")
            }
        })
    }

    private fun UnpaidButton() {
        unpaidTextView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                balanceUnpaidLayout!!.setForeground(balanceUnpaidDrawable)
                oweDebtLayout!!.setForeground(oweDebtDrawableTransparent)

                balanceTextView.setBackgroundResource(R.drawable.button_background_invisible)
                balanceTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                unpaidTextView.setBackgroundResource(R.drawable.button_background_visible)
                unpaidTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.yellow))
                oweTextView.setBackgroundResource(R.drawable.button_background_invisible)
                oweTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                debtTextView.setBackgroundResource(R.drawable.button_background_invisible)
                debtTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))

                totalBalancedTextView.setText(CurrencyUtils.formatAmountWithCurrency(unpaid.toDouble()))
                totalTextView.setText("Total Unpaid Balance:")
            }
        })
    }

    private fun OweButton() {
        oweTextView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                oweDebtLayout!!.setForeground(oweDebtDrawable)
                balanceUnpaidLayout!!.setForeground(balanceUnpaidDrawableTransparent)

                balanceTextView.setBackgroundResource(R.drawable.button_background_invisible)
                balanceTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                unpaidTextView.setBackgroundResource(R.drawable.button_background_invisible)
                unpaidTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                oweTextView.setBackgroundResource(R.drawable.button_background_visible)
                oweTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.yellow))
                debtTextView.setBackgroundResource(R.drawable.button_background_invisible)
                debtTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))

                totalBalancedTextView.setText(CurrencyUtils.formatAmountWithCurrency(currentOwe.toDouble()))
                totalTextView.setText("Total Owed Balance:")
            }
        })
    }

    private fun DebtButton() {
        debtTextView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                oweDebtLayout!!.setForeground(oweDebtDrawable)
                balanceUnpaidLayout!!.setForeground(balanceUnpaidDrawableTransparent)

                balanceTextView.setBackgroundResource(R.drawable.button_background_invisible)
                balanceTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                unpaidTextView.setBackgroundResource(R.drawable.button_background_invisible)
                unpaidTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                oweTextView.setBackgroundResource(R.drawable.button_background_invisible)
                oweTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.whitest))
                debtTextView.setBackgroundResource(R.drawable.button_background_visible)
                debtTextView.setTextColor(ContextCompat.getColor(getActivity(), R.color.yellow))

                totalBalancedTextView.setText(CurrencyUtils.formatAmountWithCurrency(currentDebt.toDouble()))
                totalTextView.setText("Total Debt:")
            }
        })
    }

    private fun profileImageViewButton() {
        profileImageView!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                showProfilePictureMenu()
            }
        })
    }

    private fun showProfilePictureMenu() {
        val popupMenu = PopupMenu(requireContext(), profileImageView)
        popupMenu.getMenuInflater().inflate(R.menu.profile_picture_menu, popupMenu.getMenu())

        popupMenu.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem): Boolean {
                val itemId = item.getItemId()
                if (itemId == R.id.action_view_profile_photo) {
                    showLargeProfilePhoto()
                    return true
                } else if (itemId == R.id.action_change_profile_photo) {
                    showChangeProfilePhotoDialog()
                    return true
                } else {
                    return false
                }
            }
        })

        popupMenu.show()
    }

    private fun showLargeProfilePhoto() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView: View = getLayoutInflater().inflate(R.layout.dialog_profile_photo, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialog_profile_photo)

        // Load the profile photo into the ImageView using the setProfileImage() method
        setProfileImage(imageView)

        builder.setView(dialogView)

        // Create and show the dialog
        val dialog = builder.create()
        dialog.show()

        // Set a click listener on the dialog to dismiss it when clicked
        imageView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
    }

    private fun selectNewProfilePhoto() {
        // Create an intent to capture an image from the camera
        val takePictureIntent: Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            // The user captured a photo with the camera
            // Handle the captured image here
            val extras: Bundle? = data.getExtras()
            val imageBitmap = extras.get("data")

            // Convert the Bitmap image to a Uri
            val imageUri = getImageUri(requireContext(), imageBitmap!!)

            // Update the profile photo with the captured image
            updateProfilePhoto(imageUri)
        } else if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            // The user selected a photo from the gallery
            val imageUri: Uri? = data.getData()
            // Update the profile photo with the selected image
            updateProfilePhoto(imageUri)
        }
    }

    // Helper method to convert Bitmap image to Uri
    private fun getImageUri(context: Context, bitmap: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path: String? =
            MediaStore.Images.Media.insertImage(context.getContentResolver(), bitmap, "Title", null)
        return Uri.parse(path)
    }


    private fun showChangeProfilePhotoDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Change Profile Photo")
        val options = arrayOf<String?>("Take Photo", "Choose from Gallery")

        builder.setItems(options, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                when (which) {
                    0 ->                         // Take photo option selected
                        selectNewProfilePhoto()

                    1 -> {
                        // Choose from gallery option selected
                        val pickPhotoIntent: Intent =
                            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        startActivityForResult(pickPhotoIntent, REQUEST_IMAGE_PICK)
                    }
                }
            }
        })

        builder.create().show()
    }

    private fun updateProfilePhoto(imageUri: Uri?) {
        showLoading()
        // Display the selected image in the ImageView
        Glide.with(requireContext())
            .load(imageUri)
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
            .into(profileImageView)

        // Upload the new profile photo to Firebase Storage
        uploadProfilePhoto(imageUri)
    }

    private fun uploadProfilePhoto(imageUri: Uri?) {
        val userId: String? =
            Objects.requireNonNull<T?>(FirebaseAuth.getInstance().getCurrentUser()).getUid()
        val storageRef: StorageReference =
            FirebaseStorage.getInstance().getReference("profile_images").child(userId)

        // Upload the image to Firebase Storage
        val uploadTask: UploadTask = storageRef.putFile(imageUri)
        uploadTask.addOnSuccessListener({ taskSnapshot ->
            // Image upload successful, get the download URL
            storageRef.getDownloadUrl().addOnSuccessListener({ downloadUri ->
                val newUrl: String? = downloadUri.toString()
                // Update cache with new URL
                PayorAdapter.Companion.sDownloadUrlCache.put(userId, newUrl)
                // Update the user's profile with the new photo URL
                updateProfileWithPhotoUrl(newUrl)
            }).addOnFailureListener({ e ->
                hideLoading()
                Toast.makeText(getActivity(), "Failed to get download URL", Toast.LENGTH_SHORT)
                    .show()
            })
        }).addOnFailureListener({ e ->
            hideLoading()
            Toast.makeText(getActivity(), "Upload failed", Toast.LENGTH_SHORT).show()
        })
    }

    private fun updateProfileWithPhotoUrl(photoUrl: String?) {
        // Update the user's profile with the new photo URL
        val profileUpdates: UserProfileChangeRequest? = Builder()
            .setPhotoUri(Uri.parse(photoUrl))
            .build()

        FirebaseAuth.getInstance().getCurrentUser().updateProfile(profileUpdates)
            .addOnCompleteListener({ task ->
                hideLoading()
                if (task.isSuccessful()) {
                    Toast.makeText(
                        getActivity(),
                        "Profile Photo Changed Successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(getActivity(), "Profile Update Failed", Toast.LENGTH_SHORT)
                        .show()
                }
            })
    }

    private fun ProfileLogoutButton() {
        profileLogout!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                Toast.makeText(getActivity(), "Logout Successfully", Toast.LENGTH_SHORT).show()
                val intent: Intent = Intent(getActivity(), LoginActivity::class.java)
                startActivity(intent)
                if (mAuth != null) {
                    mAuth.signOut() // Call signOut() method
                    // Add any additional code you want to execute after sign out, such as navigating to another activity or fragment
                } else {
                    Log.e("ProfileFragment", "FirebaseAuth instance is null")
                }
            }
        })
    }

    private fun AdminSettingsButton() {
        btnAdminSettings!!.setOnClickListener(View.OnClickListener { v: View? -> showAdminLoginDialog() })
    }

    private fun showAdminLoginDialog() {
        val dialogView: View =
            LayoutInflater.from(getContext()).inflate(R.layout.dialog_admin_login, null)
        val etUsername: EditText = dialogView.findViewById<EditText>(R.id.etAdminUsername)
        val etPassword: EditText = dialogView.findViewById<EditText>(R.id.etAdminPassword)

        AlertDialog.Builder(requireContext())
            .setTitle("Admin Access")
            .setView(dialogView)
            .setPositiveButton(
                "Login",
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    val username = etUsername.getText().toString().trim { it <= ' ' }
                    val password = etPassword.getText().toString().trim { it <= ' ' }
                    if (username == "admin" && password == "admin") {
                        Toast.makeText(getContext(), "Admin login successful", Toast.LENGTH_SHORT)
                            .show()
                        showAdminPanelDialog()
                    } else {
                        Toast.makeText(getContext(), "Invalid credentials", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminPanelDialog() {
        val dialogView: View =
            LayoutInflater.from(getContext()).inflate(R.layout.dialog_admin_panel, null)
        val btnRunMigration = dialogView.findViewById<Button>(R.id.btnRunMigration)
        val btnMigrateBalances = dialogView.findViewById<Button>(R.id.btnMigrateBalances)
        val btnMigrateBorrowIndex = dialogView.findViewById<Button>(R.id.btnMigrateBorrowIndex)
        val btnMigrateExistingUsers = dialogView.findViewById<Button>(R.id.btnMigrateExistingUsers)
        val tvStatus: TextView = dialogView.findViewById<TextView>(R.id.tvMigrationStatus)

        val adminDialog = AlertDialog.Builder(requireContext())
            .setTitle("Admin Panel")
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        // Run Full Migration
        btnRunMigration.setOnClickListener(View.OnClickListener { v: View? ->
            val progressDialog: ProgressDialog = ProgressDialog(getContext())
            progressDialog.setMessage("Running full migration...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            MigrationHelper.runAllMigrations(object : MigrationCallback {
                override fun onComplete(migratedCount: Int) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Migration complete! " + migratedCount + " records migrated.")
                    Toast.makeText(
                        getContext(),
                        "Migration complete: " + migratedCount + " records",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(error: String?) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Error - " + error)
                    Toast.makeText(getContext(), "Migration failed: " + error, Toast.LENGTH_LONG)
                        .show()
                }
            })
        })

        // Migrate User Balances Only
        btnMigrateBalances.setOnClickListener(View.OnClickListener { v: View? ->
            val progressDialog: ProgressDialog = ProgressDialog(getContext())
            progressDialog.setMessage("Migrating user balances...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            MigrationHelper.migrateUserBalances(object : MigrationCallback {
                override fun onComplete(migratedCount: Int) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Balances migrated! " + migratedCount + " users.")
                    Toast.makeText(
                        getContext(),
                        "Balances migrated: " + migratedCount + " users",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(error: String?) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Error - " + error)
                    Toast.makeText(getContext(), "Migration failed: " + error, Toast.LENGTH_LONG)
                        .show()
                }
            })
        })

        // Migrate Borrow Index Only
        btnMigrateBorrowIndex.setOnClickListener(View.OnClickListener { v: View? ->
            val progressDialog: ProgressDialog = ProgressDialog(getContext())
            progressDialog.setMessage("Migrating borrow index...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            MigrationHelper.migrateUserBorrowsIndex(object : MigrationCallback {
                override fun onComplete(migratedCount: Int) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Borrow index migrated! " + migratedCount + " records.")
                    Toast.makeText(
                        getContext(),
                        "Borrow index migrated: " + migratedCount + " records",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(error: String?) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Error - " + error)
                    Toast.makeText(getContext(), "Migration failed: " + error, Toast.LENGTH_LONG)
                        .show()
                }
            })
        })

        // Migrate Existing Users (Old Structure)
        btnMigrateExistingUsers.setOnClickListener(View.OnClickListener { v: View? ->
            val progressDialog: ProgressDialog = ProgressDialog(getContext())
            progressDialog.setMessage("Migrating existing users with old structure...\nThis includes:\n- Creating balances node\n- Initializing userBorrows")
            progressDialog.setCancelable(false)
            progressDialog.show()
            MigrationHelper.migrateExistingUsers(object : MigrationCallback {
                override fun onComplete(migratedCount: Int) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Existing users migrated! " + migratedCount + " users updated.")
                    Toast.makeText(
                        getContext(),
                        "Existing users migrated: " + migratedCount + " users",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(error: String?) {
                    progressDialog.dismiss()
                    tvStatus.setText("Status: Error - " + error)
                    Toast.makeText(getContext(), "Migration failed: " + error, Toast.LENGTH_LONG)
                        .show()
                }
            })
        })

        adminDialog.show()
    }

    private fun BreakdownButton() {
        breakdownBtn!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                showBreakdownDialog()
            }
        })
    }

    private fun showBreakdownDialog() {
        val dialogView: View =
            LayoutInflater.from(getContext()).inflate(R.layout.dialog_breakdown, null)

        // Find views
        val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseBreakdown)
        val tabBalance = dialogView.findViewById<Button>(R.id.tabBalance)
        val tabUnpaid = dialogView.findViewById<Button>(R.id.tabUnpaid)
        val tabOwe = dialogView.findViewById<Button>(R.id.tabOwe)
        val tabDebt = dialogView.findViewById<Button>(R.id.tabDebt)
        val categoryTitle: TextView = dialogView.findViewById<TextView>(R.id.breakdownCategoryTitle)
        val totalAmount: TextView = dialogView.findViewById<TextView>(R.id.breakdownTotalAmount)
        val recyclerView: RecyclerView =
            dialogView.findViewById<RecyclerView>(R.id.breakdownRecyclerView)
        val emptyStateLayout = dialogView.findViewById<View>(R.id.emptyStateLayout)
        val emptyStateText: TextView = dialogView.findViewById<TextView>(R.id.emptyStateText)
        val progressBar = dialogView.findViewById<View>(R.id.breakdownProgressBar)

        // Setup RecyclerView
        val adapter: BreakdownAdapter = BreakdownAdapter(requireContext())
        recyclerView.setLayoutManager(LinearLayoutManager(requireContext()))
        recyclerView.setAdapter(adapter)

        // Create dialog
        val breakdownDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Close button
        btnClose.setOnClickListener(View.OnClickListener { v: View? -> breakdownDialog.dismiss() })

        // Tab click listeners
        val tabClickListener: View.OnClickListener = object : View.OnClickListener {
            override fun onClick(v: View) {
                // Reset all tabs to default style
                tabBalance.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        requireContext(),
                        R.color.grey
                    )
                )
                tabBalance.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
                tabUnpaid.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        requireContext(),
                        R.color.grey
                    )
                )
                tabUnpaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
                tabOwe.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        requireContext(),
                        R.color.grey
                    )
                )
                tabOwe.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
                tabDebt.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        requireContext(),
                        R.color.grey
                    )
                )
                tabDebt.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))

                // Highlight selected tab
                val selectedTab = v as Button
                selectedTab.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        requireContext(),
                        R.color.yellow
                    )
                )
                selectedTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))

                // Load data based on selected tab
                if (v.getId() == R.id.tabBalance) {
                    loadBreakdownData(
                        BreakdownItem.Category.BALANCE,
                        categoryTitle,
                        totalAmount,
                        adapter,
                        recyclerView,
                        emptyStateLayout,
                        emptyStateText,
                        progressBar
                    )
                } else if (v.getId() == R.id.tabUnpaid) {
                    loadBreakdownData(
                        BreakdownItem.Category.UNPAID,
                        categoryTitle,
                        totalAmount,
                        adapter,
                        recyclerView,
                        emptyStateLayout,
                        emptyStateText,
                        progressBar
                    )
                } else if (v.getId() == R.id.tabOwe) {
                    loadBreakdownData(
                        BreakdownItem.Category.OWE,
                        categoryTitle,
                        totalAmount,
                        adapter,
                        recyclerView,
                        emptyStateLayout,
                        emptyStateText,
                        progressBar
                    )
                } else if (v.getId() == R.id.tabDebt) {
                    loadBreakdownData(
                        BreakdownItem.Category.DEBT,
                        categoryTitle,
                        totalAmount,
                        adapter,
                        recyclerView,
                        emptyStateLayout,
                        emptyStateText,
                        progressBar
                    )
                }
            }
        }

        tabBalance.setOnClickListener(tabClickListener)
        tabUnpaid.setOnClickListener(tabClickListener)
        tabOwe.setOnClickListener(tabClickListener)
        tabDebt.setOnClickListener(tabClickListener)

        // Load Balance data by default
        loadBreakdownData(
            BreakdownItem.Category.BALANCE,
            categoryTitle,
            totalAmount,
            adapter,
            recyclerView,
            emptyStateLayout,
            emptyStateText,
            progressBar
        )

        breakdownDialog.show()
    }

    private fun loadBreakdownData(
        category: BreakdownItem.Category, categoryTitle: TextView, totalAmount: TextView,
        adapter: BreakdownAdapter, recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        // Show loading
        progressBar.setVisibility(View.VISIBLE)
        recyclerView.setVisibility(View.GONE)
        emptyStateLayout.setVisibility(View.GONE)

        val items: ArrayList<BreakdownItem?> = ArrayList<BreakdownItem?>()

        when (category) {
            BreakdownItem.Category.BALANCE -> {
                categoryTitle.setText("Total Balance")
                totalAmount.setText(CurrencyUtils.formatAmountWithCurrency(balance.toDouble()))
                loadBalanceBreakdown(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar
                )
            }

            BreakdownItem.Category.UNPAID -> {
                categoryTitle.setText("Total Unpaid")
                totalAmount.setText(CurrencyUtils.formatAmountWithCurrency(unpaid.toDouble()))
                loadUnpaidBreakdown(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar
                )
            }

            BreakdownItem.Category.OWE -> {
                categoryTitle.setText("Total Owed")
                totalAmount.setText(CurrencyUtils.formatAmountWithCurrency(currentOwe.toDouble()))
                loadOweBreakdown(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar
                )
            }

            BreakdownItem.Category.DEBT -> {
                categoryTitle.setText("Total Debt")
                totalAmount.setText(CurrencyUtils.formatAmountWithCurrency(currentDebt.toDouble()))
                loadDebtBreakdown(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar
                )
            }
        }
    }

    private fun loadBalanceBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        val transRef: DatabaseReference = DeclareDatabase.getDBRefTransaction()

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()
                val tempI = 0
                val tempE = 1
                val tempO = 0

                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val monthKey: String? = monthSnapshot.getKey() // e.g., "January-2024"
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        val dayKey: String? = daySnapshot.getKey() // e.g., "15"
                        val dateStr =
                            if (dayKey != null && monthKey != null) monthKey + " " + dayKey else "Unknown Date"

                        for (timeSnapshot in daySnapshot.getChildren()) {
                            val transaction: Transaction? =
                                timeSnapshot.getValue(Transaction::class.java)
                            if (transaction != null) {
                                val individualPayment = transaction.getTotalIndividualPayment()
                                var userPayment = 0.0

                                // Find user's position in payors list
                                val payorsSnapshot: DataSnapshot = timeSnapshot.child("payorsList")
                                var userIndex = -1
                                var index = 0
                                for (payorSnapshot in payorsSnapshot.getChildren()) {
                                    val payorUsername: String? =
                                        payorSnapshot.getValue(String::class.java)
                                    if (payorUsername != null && payorUsername == currentNickname) {
                                        userIndex = index
                                        break
                                    }
                                    index++
                                }

                                // Get user's payment amount
                                if (userIndex >= 0) {
                                    val amountsSnapshot: DataSnapshot =
                                        timeSnapshot.child("amountsPaidList")
                                    index = 0
                                    for (amountSnapshot in amountsSnapshot.getChildren()) {
                                        if (index == userIndex) {
                                            val amount: Int? =
                                                amountSnapshot.getValue(Int::class.java)
                                            if (amount != null) {
                                                userPayment = amount.toDouble()
                                            }
                                            break
                                        }
                                        index++
                                    }

                                    // Calculate balance for this transaction
                                    val transactionBalance = userPayment - individualPayment
                                    if (transactionBalance > 0) {
                                        val description =
                                            if (transaction.getTransactionType() != null) transaction.getTransactionType() else ""

                                        val item: BreakdownItem = BreakdownItem(
                                            BreakdownItem.Category.BALANCE,
                                            dateStr,
                                            "Transaction",
                                            transactionBalance,
                                            "Completed",
                                            description
                                        )
                                        items.add(item)
                                    }
                                }
                            }
                        }
                    }
                }

                updateBreakdownUI(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar,
                    "No balance transactions found"
                )
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                progressBar.setVisibility(View.GONE)
                emptyStateLayout.setVisibility(View.VISIBLE)
                emptyStateText.setText("Error loading data")
            }
        })
    }

    private fun loadUnpaidBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        val transRef: DatabaseReference = DeclareDatabase.getDBRefTransaction()

        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()

                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val monthKey: String? = monthSnapshot.getKey() // e.g., "January-2024"
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        val dayKey: String? = daySnapshot.getKey() // e.g., "15"
                        val dateStr =
                            if (dayKey != null && monthKey != null) monthKey + " " + dayKey else "Unknown Date"

                        for (timeSnapshot in daySnapshot.getChildren()) {
                            val transaction: Transaction? =
                                timeSnapshot.getValue(Transaction::class.java)
                            if (transaction != null) {
                                val individualPayment = transaction.getTotalIndividualPayment()

                                // Find user's position in payors list
                                val payorsSnapshot: DataSnapshot = timeSnapshot.child("payorsList")
                                var userIndex = -1
                                var index = 0
                                for (payorSnapshot in payorsSnapshot.getChildren()) {
                                    val payorUsername: String? =
                                        payorSnapshot.getValue(String::class.java)
                                    if (payorUsername != null && payorUsername == currentNickname) {
                                        userIndex = index
                                        break
                                    }
                                    index++
                                }

                                // Get user's payment amount
                                if (userIndex >= 0) {
                                    val amountsSnapshot: DataSnapshot =
                                        timeSnapshot.child("amountsPaidList")
                                    index = 0
                                    var userPayment = 0
                                    for (amountSnapshot in amountsSnapshot.getChildren()) {
                                        if (index == userIndex) {
                                            val amount: Int? =
                                                amountSnapshot.getValue(Int::class.java)
                                            if (amount != null) {
                                                userPayment = amount
                                            }
                                            break
                                        }
                                        index++
                                    }

                                    // Calculate unpaid for this transaction
                                    val transactionUnpaid = individualPayment - userPayment
                                    if (transactionUnpaid > 0) {
                                        val description =
                                            if (transaction.getTransactionType() != null) transaction.getTransactionType() else ""

                                        val item: BreakdownItem = BreakdownItem(
                                            BreakdownItem.Category.UNPAID,
                                            dateStr,
                                            "Transaction",
                                            transactionUnpaid,
                                            "Pending",
                                            description
                                        )
                                        items.add(item)
                                    }
                                }
                            }
                        }
                    }
                }

                updateBreakdownUI(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar,
                    "No unpaid transactions found"
                )
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                progressBar.setVisibility(View.GONE)
                emptyStateLayout.setVisibility(View.VISIBLE)
                emptyStateText.setText("Error loading data")
            }
        })
    }

    private fun loadOweBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        val databaseReference: DatabaseReference = DeclareDatabase.getDBRefBorrows()

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()

                for (monthSnapshot in dataSnapshot.getChildren()) {
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        for (currentUserRef in daySnapshot.getChildren()) {
                            for (timeSnapshot in currentUserRef.getChildren()) {
                                val borrowTransaction: BorrowTransaction? =
                                    timeSnapshot.getValue(BorrowTransaction::class.java)
                                if (borrowTransaction != null) {
                                    val borrowee: String? = borrowTransaction.getBorrowee()
                                    if (currentNickname == borrowee) {
                                        val borrowedAmount =
                                            borrowTransaction.getBorrowedAmountStr().toDouble()
                                        val date: String? =
                                            if (borrowTransaction.getDate() != null) borrowTransaction.getDate() else "Unknown Date"
                                        val borrower: String? =
                                            if (currentUserRef.getKey() != null) currentUserRef.getKey() else "Unknown"
                                        val status: String? =
                                            if (borrowTransaction.getStatus() != null) borrowTransaction.getStatus() else "Pending"

                                        val item: BreakdownItem = BreakdownItem(
                                            BreakdownItem.Category.OWE,
                                            date,
                                            "From: " + borrower,
                                            borrowedAmount,
                                            status
                                        )
                                        items.add(item)
                                    }
                                }
                            }
                        }
                    }
                }

                updateBreakdownUI(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar,
                    "No owed amounts found"
                )
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                progressBar.setVisibility(View.GONE)
                emptyStateLayout.setVisibility(View.VISIBLE)
                emptyStateText.setText("Error loading data")
            }
        })
    }

    private fun loadDebtBreakdown(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View
    ) {
        val databaseReference: DatabaseReference = DeclareDatabase.getDBRefBorrows()

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear()

                for (monthSnapshot in dataSnapshot.getChildren()) {
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        for (currentUserRef in daySnapshot.getChildren()) {
                            val currentUserStr: String? = currentUserRef.getKey()
                            if (currentUserStr == currentNickname) {
                                for (timeSnapshot in currentUserRef.getChildren()) {
                                    val borrowTransaction: BorrowTransaction? =
                                        timeSnapshot.getValue(BorrowTransaction::class.java)
                                    if (borrowTransaction != null) {
                                        val borrowedAmount =
                                            borrowTransaction.getBorrowedAmountStr().toDouble()
                                        val date: String? =
                                            if (borrowTransaction.getDate() != null) borrowTransaction.getDate() else "Unknown Date"
                                        val borrowee: String? =
                                            if (borrowTransaction.getBorrowee() != null) borrowTransaction.getBorrowee() else "Unknown"
                                        val status: String? =
                                            if (borrowTransaction.getStatus() != null) borrowTransaction.getStatus() else "Pending"

                                        val item: BreakdownItem = BreakdownItem(
                                            BreakdownItem.Category.DEBT,
                                            date,
                                            "To: " + borrowee,
                                            borrowedAmount,
                                            status
                                        )
                                        items.add(item)
                                    }
                                }
                            }
                        }
                    }
                }

                updateBreakdownUI(
                    items,
                    adapter,
                    recyclerView,
                    emptyStateLayout,
                    emptyStateText,
                    progressBar,
                    "No debt found"
                )
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                progressBar.setVisibility(View.GONE)
                emptyStateLayout.setVisibility(View.VISIBLE)
                emptyStateText.setText("Error loading data")
            }
        })
    }

    private fun updateBreakdownUI(
        items: ArrayList<BreakdownItem?>, adapter: BreakdownAdapter,
        recyclerView: RecyclerView, emptyStateLayout: View,
        emptyStateText: TextView, progressBar: View, emptyMessage: String?
    ) {
        progressBar.setVisibility(View.GONE)

        if (items.isEmpty()) {
            recyclerView.setVisibility(View.GONE)
            emptyStateLayout.setVisibility(View.VISIBLE)
            emptyStateText.setText(emptyMessage)
        } else {
            recyclerView.setVisibility(View.VISIBLE)
            emptyStateLayout.setVisibility(View.GONE)
            adapter.updateData(items)
        }
    }

    private fun showLoading() {
        pendingLoads++
        if (loadingOverlay_profile != null) {
            loadingOverlay_profile!!.setVisibility(View.VISIBLE)
        }
    }

    private fun hideLoading() {
        pendingLoads = max(0, pendingLoads - 1)
        if (pendingLoads == 0 && loadingOverlay_profile != null) {
            loadingOverlay_profile!!.setVisibility(View.GONE)
        }
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_IMAGE_PICK = 2
    }
}
