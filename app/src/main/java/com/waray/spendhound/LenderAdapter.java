package com.waray.spendhound;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.List;

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
            
            if (lender.getProfileImageUrl() != null && !lender.getProfileImageUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(lender.getProfileImageUrl())
                        .placeholder(R.drawable.placeholder_profile_image)
                        .circleCrop()
                        .into(holder.profileImage);
            } else {
                holder.profileImage.setImageResource(R.drawable.placeholder_profile_image);
            }
        }
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
