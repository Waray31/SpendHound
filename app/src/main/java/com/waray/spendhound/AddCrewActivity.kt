package com.waray.spendhound

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.waray.spendhound.ui.profile.CrewViewModel
import com.waray.spendhound.ui.profile.PendingInvitesAdapter
import com.waray.spendhound.ui.profile.UserSearchAdapter
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddCrewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OWNER_USER_ID = "owner_user_id"
    }

    private val viewModel: CrewViewModel by viewModels()
    private var currentUserId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_crew)
        supportActionBar?.hide()

        currentUserId = intent.getLongExtra(EXTRA_OWNER_USER_ID, -1L)
        if (currentUserId == -1L) {
            // Fallback: resolve from auth
            resolveUserThenInit()
        } else {
            init()
        }
    }

    private fun resolveUserThenInit() {
        lifecycleScope.launch {
            try {
                val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return@launch finish()
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                currentUserId = user?.id ?: return@launch finish()
                init()
            } catch (e: Exception) {
                Toast.makeText(this@AddCrewActivity, "Failed to load user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentUserId != -1L) {
            viewModel.loadAllUsers(currentUserId)
            viewModel.loadCrew(currentUserId)
        }
    }

    private fun init() {
        viewModel.loadAllUsers(currentUserId)
        viewModel.loadCrew(currentUserId)
        setupViews()
        observePendingBadge()
    }

    private fun setupViews() {
        val tabInvite = findViewById<TextView>(R.id.tabInviteRegistered)
        val tabGuest = findViewById<TextView>(R.id.tabAddGuest)
        val layoutInvite = findViewById<LinearLayout>(R.id.layoutInviteRegistered)
        val layoutGuest = findViewById<LinearLayout>(R.id.layoutAddGuest)
        val etSearch = findViewById<EditText>(R.id.etSearchUser)
        val rvSearch = findViewById<RecyclerView>(R.id.rvSearchResults)
        val tvNoResults = findViewById<TextView>(R.id.tvNoResults)
        val etGuestName = findViewById<EditText>(R.id.etGuestName)
        val etGuestEmail = findViewById<EditText>(R.id.etGuestEmail)
        val etGuestPhone = findViewById<EditText>(R.id.etGuestPhone)
        val btnAddGuest = findViewById<MaterialButton>(R.id.btnAddGuest)
        val btnPending = findViewById<ImageButton>(R.id.btnPendingInvites)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnPending.setOnClickListener { showPendingInvitesDialog() }

        // Tab switching
        fun selectTab(isInvite: Boolean) {
            tabInvite.setBackgroundResource(if (isInvite) R.drawable.toggle_selected_background else 0)
            tabInvite.setTextColor(getColor(if (isInvite) R.color.whitest else R.color.grey))
            tabGuest.setBackgroundResource(if (!isInvite) R.drawable.toggle_selected_background else 0)
            tabGuest.setTextColor(getColor(if (!isInvite) R.color.whitest else R.color.grey))
            layoutInvite.visibility = if (isInvite) View.VISIBLE else View.GONE
            layoutGuest.visibility = if (!isInvite) View.VISIBLE else View.GONE
        }
        tabInvite.setOnClickListener { selectTab(true) }
        tabGuest.setOnClickListener { selectTab(false) }

        // Search adapter
        val searchAdapter = UserSearchAdapter(onInvite = { user ->
            viewModel.sendInvite(currentUserId, user.id!!) { error ->
                runOnUiThread {
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invite sent to ${user.username}!", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        })
        rvSearch.layoutManager = LinearLayoutManager(this)
        rvSearch.adapter = searchAdapter

        // Observe search results
        lifecycleScope.launch {
            viewModel.searchResults.collectLatest { results ->
                if (results.isEmpty()) {
                    rvSearch.visibility = View.GONE
                    tvNoResults.visibility = if (etSearch.text.isNotBlank()) View.VISIBLE else View.GONE
                } else {
                    tvNoResults.visibility = View.GONE
                    rvSearch.visibility = View.VISIBLE
                    searchAdapter.updateItems(results)
                }
            }
        }

        // Filter on keystroke
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.searchUsers(s?.toString()?.trim() ?: "", currentUserId)
            }
        })

        // Add guest
        btnAddGuest.setOnClickListener {
            val name = etGuestName.text.toString().trim()
            if (name.isBlank()) { etGuestName.error = "Name is required"; return@setOnClickListener }
            val email = etGuestEmail.text.toString().trim().ifBlank { null }
            val phone = etGuestPhone.text.toString().trim().ifBlank { null }
            btnAddGuest.isEnabled = false
            viewModel.createGuestAndInvite(name, email, phone, currentUserId) { error ->
                runOnUiThread {
                    btnAddGuest.isEnabled = true
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "$name added to your crew!", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }

    private fun observePendingBadge() {
        lifecycleScope.launch {
            viewModel.pendingInvites.collectLatest { pending ->
                val badge = findViewById<TextView>(R.id.tvPendingBadge)
                if (pending.isNotEmpty()) {
                    badge.visibility = View.VISIBLE
                    badge.text = pending.size.toString()
                } else {
                    badge.visibility = View.GONE
                }
            }
        }
    }

    private fun showPendingInvitesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pending_invites, null)
        val rvPending = dialogView.findViewById<RecyclerView>(R.id.rvPendingInvites)
        val tvNoPending = dialogView.findViewById<TextView>(R.id.tvNoPending)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnClosePending)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        btnClose.setOnClickListener { dialog.dismiss() }

        val pendingAdapter = PendingInvitesAdapter(
            onAccept = { crew -> viewModel.respondToInvite(crew.id!!, true, currentUserId); dialog.dismiss() },
            onDecline = { crew -> viewModel.respondToInvite(crew.id!!, false, currentUserId); dialog.dismiss() }
        )
        rvPending.layoutManager = LinearLayoutManager(this)
        rvPending.adapter = pendingAdapter

        val pending = viewModel.pendingInvites.value
        if (pending.isEmpty()) {
            tvNoPending.visibility = View.VISIBLE
            rvPending.visibility = View.GONE
        } else {
            tvNoPending.visibility = View.GONE
            rvPending.visibility = View.VISIBLE
            pendingAdapter.updateItems(pending)
        }

        dialog.show()
    }
}
