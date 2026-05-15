package com.waray.spendhound

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.ui.profile.CrewMembersAdapter
import com.waray.spendhound.ui.profile.CrewViewModel
import com.waray.spendhound.ui.profile.PendingInvitesAdapter
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CrewMembersActivity : AppCompatActivity() {

    private val viewModel: CrewViewModel by viewModels()
    private var currentUserId: Long = -1L

    private lateinit var rvCrewMembers: RecyclerView
    private lateinit var emptyCrewState: LinearLayout
    private lateinit var crewSkeletonContainer: LinearLayout
    private lateinit var crewProgressBar: ProgressBar
    private lateinit var tvCrewCount: TextView
    private lateinit var crewAdapter: CrewMembersAdapter
    private var lastSeenCrewUpdate: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_members)
        supportActionBar?.hide()
        Log.d("CrewDebug", "CrewMembersActivity onCreate")

        rvCrewMembers = findViewById(R.id.rvCrewMembers)
        emptyCrewState = findViewById(R.id.emptyCrewState)
        crewSkeletonContainer = findViewById(R.id.crewSkeletonContainer)
        crewProgressBar = findViewById(R.id.crewProgressBar)
        tvCrewCount = findViewById(R.id.tvCrewCount)

        crewAdapter = CrewMembersAdapter(
            currentUserId = -1L,
            onMessage = { otherUser, _ -> openDm(otherUser) },
            onRemove = { crew -> confirmRemove(crew.id!!) }
        )
        rvCrewMembers.layoutManager = LinearLayoutManager(this)
        rvCrewMembers.adapter = crewAdapter

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnAddCrewHeader).setOnClickListener {
            startActivity(android.content.Intent(this, AddCrewActivity::class.java).apply {
                putExtra(AddCrewActivity.EXTRA_OWNER_USER_ID, currentUserId)
            })
        }

        resolveUserAndLoad()
        lastSeenCrewUpdate = com.waray.spendhound.CrewState.lastUpdateTimestamp
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        Log.d("CrewDebug", "CrewMembersActivity onResume currentUserId=$currentUserId")
        if (currentUserId != -1L) {
            // Always perform a refresh when visiting this page
            viewModel.reloadCrew(currentUserId)
            lastSeenCrewUpdate = com.waray.spendhound.CrewState.lastUpdateTimestamp
        }
    }

    private fun resolveUserAndLoad() {
        Log.i("CrewDebug", "CrewMembersActivity: resolveUserAndLoad() called")
        lifecycleScope.launch {
            Log.i("CrewDebug", "CrewMembersActivity: Coroutine started")
            try {
                val authId = DeclareDatabase.auth.currentUserOrNull()?.id
                Log.i("CrewDebug", "CrewMembersActivity: authId=$authId")
                if (authId == null) { 
                    Log.e("CrewDebug", "CrewMembersActivity: authId is NULL — aborting")
                    return@launch 
                }
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                Log.i("CrewDebug", "CrewMembersActivity: Resolved user=${user?.id}")
                if (user?.id == null) { 
                    Log.e("CrewDebug", "CrewMembersActivity: user is NULL — aborting")
                    return@launch 
                }
                currentUserId = user.id
                crewAdapter.updateCurrentUserId(currentUserId)
                Log.i("CrewDebug", "CrewMembersActivity: calling reloadCrew userId=$currentUserId")
                viewModel.reloadCrew(currentUserId)
            } catch (e: Exception) {
                Log.e("CrewDebug", "CrewMembersActivity EXCEPTION: ${e.message}", e)
                if (!isFinishing) {
                    Toast.makeText(this@CrewMembersActivity, "Failed to load user.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        Log.i("CrewDebug", "CrewMembersActivity: observeViewModel START")
        
        lifecycleScope.launch {
            // Use combine or zip to handle both states together and avoid race conditions
            kotlinx.coroutines.flow.combine(
                viewModel.isLoading,
                viewModel.crewList
            ) { loading, list ->
                loading to list
            }.collectLatest { (loading, list) ->
                Log.d("CrewDebug", "Observer: loading=$loading, size=${list.size}")
                
                if (loading) {
                    crewSkeletonContainer.visibility = View.VISIBLE
                    rvCrewMembers.visibility = View.GONE
                    emptyCrewState.visibility = View.GONE
                } else {
                    crewSkeletonContainer.visibility = View.GONE
                    
                    if (list.isEmpty()) {
                        emptyCrewState.visibility = View.VISIBLE
                        rvCrewMembers.visibility = View.GONE
                    } else {
                        emptyCrewState.visibility = View.GONE
                        rvCrewMembers.visibility = View.VISIBLE
                        crewAdapter.updateItems(list)
                    }
                    tvCrewCount.text = "${list.size} member${if (list.size != 1) "s" else ""}"
                }
            }
        }

        lifecycleScope.launch {
            viewModel.actionError.collectLatest { err ->
                err ?: return@collectLatest
                Log.e("CrewDebug", "actionError: $err")
                Toast.makeText(this@CrewMembersActivity, err, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
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

    private fun confirmRemove(crewId: Long) {
        AlertDialog.Builder(this)
            .setTitle("Remove crew member")
            .setMessage("Are you sure you want to remove this person from your crew?")
            .setPositiveButton("Remove") { _, _ -> viewModel.removeCrew(crewId, currentUserId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDm(user: User) {
        startActivity(android.content.Intent(this, DirectMessageActivity::class.java).apply {
            putExtra(DirectMessageActivity.EXTRA_RECIPIENT_ID, user.id)
            putExtra(DirectMessageActivity.EXTRA_RECIPIENT_NAME, user.username)
            putExtra(DirectMessageActivity.EXTRA_RECIPIENT_AVATAR, user.profileImageUrl)
            putExtra(DirectMessageActivity.EXTRA_CURRENT_USER_ID, currentUserId)
        })
    }
}
