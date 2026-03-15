package com.waray.spendhound;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.storage.FirebaseStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LenderAdapter extends RecyclerView.Adapter<LenderAdapter.ViewHolder> {

    private final List<User> lenders;

    public LenderAdapter(List<User> lenders) {
        this.lenders = lenders;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lender_profile, parent, false);
        int width = parent.getMeasuredWidth() / 5;
        if (width <= 0) {
            width = parent.getResources().getDisplayMetrics().widthPixels / 5;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(width, RecyclerView.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User lender = lenders.get(position);
        
        if (lender.getUsername() == null || lender.getUsername().isEmpty()) {
            holder.profileImage.setVisibility(View.INVISIBLE);
            holder.usernameText.setVisibility(View.INVISIBLE);
        } else {
            holder.profileImage.setVisibility(View.VISIBLE);
            holder.usernameText.setVisibility(View.VISIBLE);
            holder.usernameText.setText(lender.getUsername());
            
            String uid = lender.getUid();
            String cachedUrl = PayorAdapter.sDownloadUrlCache.get(uid != null ? uid : "");
            
            if (cachedUrl != null) {
                loadGlideImage(holder, cachedUrl);
            } else if (lender.getProfileImageUrl() != null && !lender.getProfileImageUrl().isEmpty()) {
                loadGlideImage(holder, lender.getProfileImageUrl());
            } else if (uid != null) {
                FirebaseStorage.getInstance().getReference("profile_images").child(uid)
                        .getDownloadUrl().addOnSuccessListener(uri -> {
                            String url = uri.toString();
                            PayorAdapter.sDownloadUrlCache.put(uid, url);
                            loadGlideImage(holder, url);
                        }).addOnFailureListener(e -> {
                            holder.profileImage.setImageResource(R.drawable.placeholder_profile_image);
                        });
            } else {
                holder.profileImage.setImageResource(R.drawable.placeholder_profile_image);
            }
        }
    }

    private void loadGlideImage(ViewHolder holder, String url) {
        Glide.with(holder.itemView.getContext())
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.placeholder_profile_image)
                .error(R.drawable.placeholder_profile_image)
                .circleCrop()
                .into(holder.profileImage);
    }

    @Override
    public int getItemCount() {
        return lenders.size();
    }

    public User getLenderAt(int position) {
        if (position >= 0 && position < lenders.size()) {
            User user = lenders.get(position);
            if (user.getUsername() != null && !user.getUsername().isEmpty()) {
                return user;
            }
        }
        return null;
    }

    public void preloadAllImages(Context context, Runnable onComplete) {
        List<User> usersToFetch = new ArrayList<>();
        for (User lender : lenders) {
            if (lender.getUsername() != null && !lender.getUsername().isEmpty()) {
                usersToFetch.add(lender);
            }
        }

        if (usersToFetch.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        AtomicInteger loadedCount = new AtomicInteger(0);
        int total = usersToFetch.size();

        for (User lender : usersToFetch) {
            String uid = lender.getUid();
            String cachedUrl = PayorAdapter.sDownloadUrlCache.get(uid != null ? uid : "");
            
            if (cachedUrl != null) {
                preloadUrl(context, cachedUrl, loadedCount, total, onComplete);
            } else if (lender.getProfileImageUrl() != null && !lender.getProfileImageUrl().isEmpty()) {
                preloadUrl(context, lender.getProfileImageUrl(), loadedCount, total, onComplete);
            } else if (uid != null) {
                FirebaseStorage.getInstance().getReference("profile_images").child(uid)
                    .getDownloadUrl().addOnSuccessListener(uri -> {
                        String url = uri.toString();
                        PayorAdapter.sDownloadUrlCache.put(uid, url);
                        preloadUrl(context, url, loadedCount, total, onComplete);
                    }).addOnFailureListener(e -> {
                        if (loadedCount.incrementAndGet() >= total) {
                            if (onComplete != null) onComplete.run();
                        }
                    });
            } else {
                if (loadedCount.incrementAndGet() >= total) {
                    if (onComplete != null) onComplete.run();
                }
            }
        }
    }

    private void preloadUrl(Context context, String url, AtomicInteger loadedCount, int total, Runnable onComplete) {
        Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        checkComplete();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        checkComplete();
                        return false;
                    }

                    private void checkComplete() {
                        if (loadedCount.incrementAndGet() >= total) {
                            if (onComplete != null) onComplete.run();
                        }
                    }
                })
                .preload();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView profileImage;
        TextView usernameText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profileImage);
            usernameText = itemView.findViewById(R.id.usernameText);
        }
    }
}
