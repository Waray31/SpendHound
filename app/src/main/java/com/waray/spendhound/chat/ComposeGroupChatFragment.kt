package com.waray.spendhound.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.User

/**
 * Fragment wrapper that hosts the Compose ChatScreen.
 * Resolves the current user's numeric ID from Supabase Auth before rendering.
 */
class ComposeGroupChatFragment : Fragment() {

    companion object {
        private const val TAG = "ComposeGroupChatFrag"
        fun newInstance(groupId: Long) = ComposeGroupChatFragment().apply {
            arguments = Bundle().also { it.putLong("group_id", groupId) }
        }
    }

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val groupId = arguments?.getLong("group_id") ?: -1L
        Log.d(TAG, "onCreateView groupId=$groupId")

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentUserIdState = remember { mutableStateOf<Long?>(null) }
                val errorState = remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val authId = DeclareDatabase.auth.currentUserOrNull()?.id
                    Log.d(TAG, "authId=$authId")
                    if (authId == null) {
                        errorState.value = "Not logged in"
                        return@LaunchedEffect
                    }
                    try {
                        val user = DeclareDatabase.usersTable.select {
                            filter { eq("auth_id", authId) }
                        }.decodeSingleOrNull<User>()
                        Log.d(TAG, "resolved user=${user?.id} username=${user?.username}")
                        if (user?.id != null) {
                            currentUserIdState.value = user.id
                        } else {
                            errorState.value = "User profile not found"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resolve user", e)
                        errorState.value = e.message ?: "Unknown error"
                    }
                }

                val userId = currentUserIdState.value
                val error = errorState.value
                when {
                    userId != null -> ChatScreen(
                        groupId = groupId,
                        currentUserId = userId,
                        chatViewModel = chatViewModel
                    )
                    error != null -> Text(
                        text = "Chat unavailable: $error",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                    else -> CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
