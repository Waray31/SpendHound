package com.waray.spendhound

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class LenderAdapter(private val lenders: MutableList<User?>) :
    RecyclerView.Adapter<LenderAdapter.ViewHolder>() {
    
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lender_profile, parent, false)
        var width: Int = parent.measuredWidth / 5
        if (width <= 0) {
            width = parent.resources.displayMetrics.widthPixels / 5
        }
        view.layoutParams = RecyclerView.LayoutParams(width, RecyclerView.LayoutParams.WRAP_CONTENT)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lender = lenders[position] ?: return

        if (lender.username.isNullOrEmpty()) {
            holder.profileImage.visibility = View.INVISIBLE
            holder.usernameText.visibility = View.INVISIBLE
        } else {
            holder.profileImage.visibility = View.VISIBLE
            holder.usernameText.visibility = View.VISIBLE
            holder.usernameText.text = lender.username

            val authId = lender.authId
            val userId = lender.id?.toString()
            val cachedUrl: String? = if (authId != null) PayorAdapter.sDownloadUrlCache[authId] else if (userId != null) PayorAdapter.sDownloadUrlCache[userId] else null

            if (cachedUrl != null) {
                loadCoilImage(holder, cachedUrl)
            } else if (!lender.profileImageUrl.isNullOrEmpty() && lender.profileImageUrl!!.startsWith("http")) {
                loadCoilImage(holder, lender.profileImageUrl)
            } else if (userId != null) {
                // Use user_id/user_id.jpg format
                val url = DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                if (authId != null) PayorAdapter.sDownloadUrlCache[authId] = url
                PayorAdapter.sDownloadUrlCache[userId] = url
                loadCoilImage(holder, url)
            } else {
                holder.profileImage.setImageResource(R.drawable.ic_profile_silhouette)
            }
        }
    }

    private fun loadCoilImage(holder: ViewHolder, url: String?) {
        holder.profileImage.load(url) {
            placeholder(R.drawable.ic_profile_silhouette)
            error(R.drawable.ic_profile_silhouette)
            transformations(CircleCropTransformation())
        }
    }

    override fun getItemCount(): Int = lenders.size

    fun getLenderAt(position: Int): User? {
        if (position >= 0 && position < lenders.size) {
            val user = lenders[position]
            if (!user?.username.isNullOrEmpty()) {
                return user
            }
        }
        return null
    }

    fun preloadAllImages(context: Context?, onComplete: Runnable?) {
        val usersToFetch = lenders.filter { !it?.username.isNullOrEmpty() }

        if (usersToFetch.isEmpty()) {
            onComplete?.run()
            return
        }

        val loadedCount = AtomicInteger(0)
        val total = usersToFetch.size

        for (lender in usersToFetch) {
            val authId = lender?.authId
            val userId = lender?.id?.toString()
            val cachedUrl: String? = if (authId != null) PayorAdapter.sDownloadUrlCache[authId] else if (userId != null) PayorAdapter.sDownloadUrlCache[userId] else null

            if (cachedUrl != null) {
                preloadUrl(context!!, cachedUrl, loadedCount, total, onComplete)
            } else if (!lender?.profileImageUrl.isNullOrEmpty() && lender!!.profileImageUrl!!.startsWith("http")) {
                preloadUrl(context!!, lender.profileImageUrl, loadedCount, total, onComplete)
            } else if (userId != null) {
                val url = DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                if (authId != null) PayorAdapter.sDownloadUrlCache[authId] = url
                PayorAdapter.sDownloadUrlCache[userId] = url
                preloadUrl(context!!, url, loadedCount, total, onComplete)
            } else {
                if (loadedCount.incrementAndGet() >= total) {
                    onComplete?.run()
                }
            }
        }
    }

    private fun preloadUrl(
        context: Context,
        url: String?,
        loadedCount: AtomicInteger,
        total: Int,
        onComplete: Runnable?
    ) {
        val request = coil.request.ImageRequest.Builder(context)
            .data(url)
            .transformations(CircleCropTransformation())
            .listener(
                onSuccess = { _, _ -> 
                    if (loadedCount.incrementAndGet() >= total) {
                        onComplete?.run()
                    }
                },
                onError = { _, _ ->
                    if (loadedCount.incrementAndGet() >= total) {
                        onComplete?.run()
                    }
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ShapeableImageView = itemView.findViewById(R.id.profileImage)
        val usernameText: TextView = itemView.findViewById(R.id.usernameText)
    }
}
