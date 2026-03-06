package com.waray.spendhound;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.List;
import java.util.Locale;

public class PayorAdapter extends RecyclerView.Adapter<PayorAdapter.PayorViewHolder> {

    private List<String> payorsUids;
    private List<String> payorsNames;
    private List<Double> amountsPaid;
    private double individualPayment;
    private OnPayorClickListener onPayorClickListener;

    public interface OnPayorClickListener {
        void onPayorClick(int index, double paid);
    }

    public PayorAdapter(List<String> payorsUids, List<String> payorsNames, List<Double> amountsPaid, double individualPayment, OnPayorClickListener onPayorClickListener) {
        this.payorsUids = payorsUids;
        this.payorsNames = payorsNames;
        this.amountsPaid = amountsPaid;
        this.individualPayment = individualPayment;
        this.onPayorClickListener = onPayorClickListener;
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
        holder.payorPayment.setText(String.format(Locale.getDefault(), "%.2f/%.2f", paid, individualPayment));

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

        StorageReference pStorageRef = FirebaseStorage.getInstance().getReference("profile_images").child(uid);
        pStorageRef.getDownloadUrl().addOnSuccessListener(uri -> {
            Glide.with(holder.itemView.getContext())
                    .load(uri)
                    .placeholder(R.drawable.placeholder_profile_image)
                    .circleCrop()
                    .into(holder.payorImage);
        }).addOnFailureListener(e -> holder.payorImage.setImageResource(R.drawable.placeholder_profile_image));

        if (onPayorClickListener != null) {
            holder.itemView.setOnClickListener(v -> onPayorClickListener.onPayorClick(position, paid));
        }
    }

    @Override
    public int getItemCount() {
        return payorsUids != null ? payorsUids.size() : 0;
    }

    static class PayorViewHolder extends RecyclerView.ViewHolder {
        ImageView payorImage;
        TextView payorName, payorPayment, payorStatus;

        public PayorViewHolder(@NonNull View itemView) {
            super(itemView);
            payorImage = itemView.findViewById(R.id.payorProfileImage);
            payorName = itemView.findViewById(R.id.payorNameTextView);
            payorPayment = itemView.findViewById(R.id.payorPaymentTextView);
            payorStatus = itemView.findViewById(R.id.payorStatusTextView);
        }
    }
}
