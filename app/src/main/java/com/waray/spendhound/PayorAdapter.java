package com.waray.spendhound;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PayorAdapter extends RecyclerView.Adapter<PayorAdapter.PayorViewHolder> {

    // Static cache for download URLs to reduce Firebase Storage calls
    private static final Map<String, String> sDownloadUrlCache = new ConcurrentHashMap<>();

    private List<String> payorsUids;
    private List<String> payorsNames;
    private List<Double> amountsPaid;
    private List<Double> originalAmountsPaid;
    private double individualPayment;
    private OnPayorClickListener onPayorClickListener;
    private OnLoadingCompleteListener loadingCompleteListener;
    private OnDataChangedListener dataChangedListener;
    private Set<Integer> loadedPositions = new HashSet<>();
    private Set<Integer> failedPositions = new HashSet<>();
    private boolean isEditMode = false;

    public interface OnPayorClickListener {
        void onPayorClick(int index, double paid);
        void onPartialClick(int index, double currentPaid);
    }

    public interface OnLoadingCompleteListener {
        void onLoadingComplete();
    }

    public interface OnDataChangedListener {
        void onDataChanged(boolean hasChanges);
    }

    public void setOnLoadingCompleteListener(OnLoadingCompleteListener listener) {
        this.loadingCompleteListener = listener;
        if ((payorsUids == null || payorsUids.isEmpty()) && loadingCompleteListener != null) {
            loadingCompleteListener.onLoadingComplete();
        }
    }

    public void setOnDataChangedListener(OnDataChangedListener listener) {
        this.dataChangedListener = listener;
    }

    public PayorAdapter(List<String> payorsUids, List<String> payorsNames, List<Double> amountsPaid, double individualPayment, OnPayorClickListener onPayorClickListener) {
        this.payorsUids = payorsUids;
        this.payorsNames = payorsNames;
        this.amountsPaid = new ArrayList<>(amountsPaid);
        this.originalAmountsPaid = new ArrayList<>(amountsPaid);
        this.individualPayment = individualPayment;
        this.onPayorClickListener = onPayorClickListener;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        if (!editMode) {
            // Revert changes if cancel or finishing edit
            this.amountsPaid = new ArrayList<>(originalAmountsPaid);
        }
        loadedPositions.clear();
        failedPositions.clear();
        notifyDataSetChanged();
        notifyDataChanged();
    }

    public void saveChanges() {
        this.originalAmountsPaid = new ArrayList<>(amountsPaid);
        this.isEditMode = false;
        loadedPositions.clear();
        failedPositions.clear();
        notifyDataSetChanged();
        notifyDataChanged();
    }

    public boolean hasChanges() {
        if (amountsPaid.size() != originalAmountsPaid.size()) return true;
        for (int i = 0; i < amountsPaid.size(); i++) {
            if (!amountsPaid.get(i).equals(originalAmountsPaid.get(i))) {
                return true;
            }
        }
        return false;
    }

    private void notifyDataChanged() {
        if (dataChangedListener != null) {
            dataChangedListener.onDataChanged(hasChanges());
        }
    }

    public List<Double> getAmountsPaid() {
        return amountsPaid;
    }

    @NonNull
    @Override
    public PayorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payor_horizontal, parent, false);
        return new PayorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PayorViewHolder holder, int position) {
        String uid = payorsUids.get(position);
        String name = (payorsNames != null && position < payorsNames.size()) ? payorsNames.get(position) : "User";
        double paid = (amountsPaid != null && position < amountsPaid.size()) ? amountsPaid.get(position) : 0.0;

        holder.payorName.setText(name);
        holder.payorPayment.setText(CurrencyUtils.formatAmount(paid) + "/" + CurrencyUtils.formatAmount(individualPayment));

        updateStatusUI(holder, paid);

        if (isEditMode) {
            holder.editButtonsLayout.setVisibility(View.VISIBLE);
            
            // Logic to hide buttons based on status
            if (paid <= 0) {
                holder.unpaidBtn.setVisibility(View.GONE);
                holder.paidBtn.setVisibility(View.VISIBLE);
                holder.partialBtn.setVisibility(View.VISIBLE);
            } else if (paid >= individualPayment) {
                holder.unpaidBtn.setVisibility(View.VISIBLE);
                holder.paidBtn.setVisibility(View.GONE);
                holder.partialBtn.setVisibility(View.VISIBLE);
            } else {
                holder.unpaidBtn.setVisibility(View.VISIBLE);
                holder.paidBtn.setVisibility(View.VISIBLE);
                holder.partialBtn.setVisibility(View.VISIBLE);
            }

            holder.unpaidBtn.setOnClickListener(v -> {
                amountsPaid.set(position, 0.0);
                notifyItemChanged(position);
                notifyDataChanged();
            });

            holder.paidBtn.setOnClickListener(v -> {
                amountsPaid.set(position, individualPayment);
                notifyItemChanged(position);
                notifyDataChanged();
            });

            holder.partialBtn.setOnClickListener(v -> {
                if (onPayorClickListener != null) {
                    onPayorClickListener.onPartialClick(position, amountsPaid.get(position));
                }
            });
        } else {
            holder.editButtonsLayout.setVisibility(View.GONE);
        }

        // Use cached download URL if available
        String cachedUrl = sDownloadUrlCache.get(uid);
        if (cachedUrl != null) {
            loadGlideImage(holder, cachedUrl, position);
        } else {
            StorageReference pStorageRef = FirebaseStorage.getInstance().getReference("profile_images").child(uid);
            pStorageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String url = uri.toString();
                sDownloadUrlCache.put(uid, url);
                loadGlideImage(holder, url, position);
            }).addOnFailureListener(e -> {
                holder.payorImage.setImageResource(R.drawable.placeholder_profile_image);
                checkLoadingComplete(position);
            });
        }

        if (!isEditMode && onPayorClickListener != null) {
            holder.itemView.setOnClickListener(v -> onPayorClickListener.onPayorClick(position, paid));
        } else {
            holder.itemView.setOnClickListener(null);
        }
    }

    private void loadGlideImage(PayorViewHolder holder, String url, int position) {
        Glide.with(holder.itemView.getContext())
                .load(url)
                .placeholder(R.drawable.placeholder_profile_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Enable full disk caching
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        checkLoadingComplete(position);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        checkLoadingComplete(position);
                        return false;
                    }
                })
                .into(holder.payorImage);
    }

    private void updateStatusUI(PayorViewHolder holder, double paid) {
        if (paid <= 0) {
            holder.payorStatus.setText("Unpaid");
            holder.payorStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        } else if (paid < individualPayment) {
            holder.payorStatus.setText("Paid Partially");
            holder.payorStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            holder.payorStatus.setText("Paid");
            holder.payorStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        }
    }

    public void updatePartialAmount(int index, double amount) {
        if (index < amountsPaid.size()) {
            amountsPaid.set(index, amount);
            notifyItemChanged(index);
            notifyDataChanged();
        }
    }

    private synchronized void checkLoadingComplete(int position) {
        loadedPositions.add(position);
        // We consider it complete if all items have at least attempted to load
        if (loadedPositions.size() >= getItemCount() && loadingCompleteListener != null) {
            loadingCompleteListener.onLoadingComplete();
        }
    }

    @Override
    public int getItemCount() {
        return payorsUids != null ? payorsUids.size() : 0;
    }

    static class PayorViewHolder extends RecyclerView.ViewHolder {
        ImageView payorImage;
        TextView payorName, payorPayment, payorStatus;
        View editButtonsLayout;
        Button unpaidBtn, paidBtn, partialBtn;

        public PayorViewHolder(@NonNull View itemView) {
            super(itemView);
            payorImage = itemView.findViewById(R.id.payorProfileImage);
            payorName = itemView.findViewById(R.id.payorNameTextView);
            payorPayment = itemView.findViewById(R.id.payorPaymentTextView);
            payorStatus = itemView.findViewById(R.id.payorStatusTextView);
            editButtonsLayout = itemView.findViewById(R.id.editButtonsLayout);
            unpaidBtn = itemView.findViewById(R.id.unpaid_btn);
            paidBtn = itemView.findViewById(R.id.paid_btn);
            partialBtn = itemView.findViewById(R.id.partial_btn);
        }
    }
}
