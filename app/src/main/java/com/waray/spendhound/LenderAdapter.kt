package com.waray.spendhound

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.target.Target
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.atomic.AtomicInteger

class LenderAdapter(private val lenders: MutableList<User>) :
    RecyclerView.Adapter<LenderAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_lender_profile, parent, false)
        var width: Int = parent.getMeasuredWidth() / 5
        if (width <= 0) {
            width = parent.getResources().getDisplayMetrics().widthPixels / 5
        }
        view.setLayoutParams(
            RecyclerView.LayoutParams(
                width,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lender = lenders.get(position)

        if (lender.getUsername() == null || lender.getUsername().isEmpty()) {
            holder.profileImage.setVisibility(View.INVISIBLE)
            holder.usernameText.setVisibility(View.INVISIBLE)
        } else {
            holder.profileImage.setVisibility(View.VISIBLE)
            holder.usernameText.setVisibility(View.VISIBLE)
            holder.usernameText.setText(lender.getUsername())

            val uid = lender.getUid()
            val cachedUrl: String? =
                PayorAdapter.Companion.sDownloadUrlCache.get(if (uid != null) uid else "")

            if (cachedUrl != null) {
                loadGlideImage(holder, cachedUrl)
            } else if (lender.getProfileImageUrl() != null && !lender.getProfileImageUrl()
                    .isEmpty()
            ) {
                loadGlideImage(holder, lender.getProfileImageUrl())
            } else if (uid != null) {
                FirebaseStorage.getInstance().getReference("profile_images").child(uid)
                    .getDownloadUrl().addOnSuccessListener({ uri ->
                        val url: String? = uri.toString()
                        PayorAdapter.Companion.sDownloadUrlCache.put(uid, url)
                        loadGlideImage(holder, url)
                    }).addOnFailureListener({ e ->
                        holder.profileImage.setImageResource(R.drawable.placeholder_profile_image)
                    })
            } else {
                holder.profileImage.setImageResource(R.drawable.placeholder_profile_image)
            }
        }
    }

    private fun loadGlideImage(holder: ViewHolder, url: String?) {
        Glide.with(holder.itemView.getContext())
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.placeholder_profile_image)
            .error(R.drawable.placeholder_profile_image)
            .circleCrop()
            .into(holder.profileImage)
    }

    val itemCount: Int
        get() = lenders.size

    fun getLenderAt(position: Int): User? {
        if (position >= 0 && position < lenders.size) {
            val user = lenders.get(position)
            if (user.getUsername() != null && !user.getUsername().isEmpty()) {
                return user
            }
        }
        return null
    }

    fun preloadAllImages(context: Context, onComplete: Runnable?) {
        val usersToFetch: MutableList<User> = ArrayList<User>()
        for (lender in lenders) {
            if (lender.getUsername() != null && !lender.getUsername().isEmpty()) {
                usersToFetch.add(lender)
            }
        }

        if (usersToFetch.isEmpty()) {
            if (onComplete != null) onComplete.run()
            return
        }

        val loadedCount = AtomicInteger(0)
        val total = usersToFetch.size

        for (lender in usersToFetch) {
            val uid = lender.getUid()
            val cachedUrl: String? =
                PayorAdapter.Companion.sDownloadUrlCache.get(if (uid != null) uid else "")

            if (cachedUrl != null) {
                preloadUrl(context, cachedUrl, loadedCount, total, onComplete)
            } else if (lender.getProfileImageUrl() != null && !lender.getProfileImageUrl()
                    .isEmpty()
            ) {
                preloadUrl(context, lender.getProfileImageUrl(), loadedCount, total, onComplete)
            } else if (uid != null) {
                FirebaseStorage.getInstance().getReference("profile_images").child(uid)
                    .getDownloadUrl().addOnSuccessListener({ uri ->
                        val url: String? = uri.toString()
                        PayorAdapter.Companion.sDownloadUrlCache.put(uid, url)
                        preloadUrl(context, url, loadedCount, total, onComplete)
                    }).addOnFailureListener({ e ->
                        if (loadedCount.incrementAndGet() >= total) {
                            if (onComplete != null) onComplete.run()
                        }
                    })
            } else {
                if (loadedCount.incrementAndGet() >= total) {
                    if (onComplete != null) onComplete.run()
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
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    checkComplete()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    checkComplete()
                    return false
                }

                fun checkComplete() {
                    if (loadedCount.incrementAndGet() >= total) {
                        if (onComplete != null) onComplete.run()
                    }
                }
            })
            .preload()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var profileImage: ShapeableImageView
        var usernameText: TextView

        init {
            profileImage = itemView.findViewById<ShapeableImageView>(R.id.profileImage)
            usernameText = itemView.findViewById<TextView>(R.id.usernameText)
        }
    }
}
