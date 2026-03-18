package com.waray.spendhound

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.imageview.ShapeableImageView
import io.github.jan.supabase.storage.resumable.Resumable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

            val uid = lender.id
            val cachedUrl: String? = PayorAdapter.sDownloadUrlCache[uid ?: ""]

            if (cachedUrl != null) {
                loadGlideImage(holder, cachedUrl)
            } else if (!lender.profileImageUrl.isNullOrEmpty() && lender.profileImageUrl!!.startsWith("http")) {
                loadGlideImage(holder, lender.profileImageUrl)
            } else if (uid != null) {
                scope.launch {
                    try {
                        val url = DeclareDatabase.profileImagesBucket.publicUrl("$uid.jpg")
                        PayorAdapter.sDownloadUrlCache[uid] = url
                        loadGlideImage(holder, url)
                    } catch (e: Exception) {
                        holder.profileImage.setImageResource(R.drawable.placeholder_profile_image)
                    }
                }
            } else {
                holder.profileImage.setImageResource(R.drawable.placeholder_profile_image)
            }
        }
    }

    private fun loadGlideImage(holder: ViewHolder, url: String?) {
        Glide.with(holder.itemView.context)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.placeholder_profile_image)
            .error(R.drawable.placeholder_profile_image)
            .circleCrop()
            .into(holder.profileImage)
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
            val uid = lender?.id
            val cachedUrl: String? = PayorAdapter.sDownloadUrlCache[uid ?: ""]

            if (cachedUrl != null) {
                preloadUrl(context!!, cachedUrl, loadedCount, total, onComplete)
            } else if (!lender?.profileImageUrl.isNullOrEmpty() && lender!!.profileImageUrl!!.startsWith("http")) {
                preloadUrl(context!!, lender.profileImageUrl, loadedCount, total, onComplete)
            } else if (uid != null) {
                scope.launch {
                    try {
                        val url = DeclareDatabase.profileImagesBucket.publicUrl("$uid.jpg")
                        PayorAdapter.sDownloadUrlCache[uid] = url
                        preloadUrl(context!!, url, loadedCount, total, onComplete)
                    } catch (e: Exception) {
                        if (loadedCount.incrementAndGet() >= total) {
                            onComplete?.run()
                        }
                    }
                }
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
        Glide.with(context)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable?>?, isFirstResource: Boolean): Boolean {
                    checkComplete()
                    return false
                }
                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable?>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    checkComplete()
                    return false
                }
                fun checkComplete() {
                    if (loadedCount.incrementAndGet() >= total) {
                        onComplete?.run()
                    }
                }
            })
            .preload()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ShapeableImageView = itemView.findViewById(R.id.profileImage)
        val usernameText: TextView = itemView.findViewById(R.id.usernameText)
    }
}
