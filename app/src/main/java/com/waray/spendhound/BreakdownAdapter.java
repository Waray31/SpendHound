package com.waray.spendhound;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for displaying breakdown items in the financial breakdown dialog.
 */
public class BreakdownAdapter extends RecyclerView.Adapter<BreakdownAdapter.BreakdownViewHolder> {

    private List<BreakdownItem> breakdownItems;
    private Context context;

    public BreakdownAdapter(Context context) {
        this.context = context;
        this.breakdownItems = new ArrayList<>();
    }

    public BreakdownAdapter(Context context, List<BreakdownItem> breakdownItems) {
        this.context = context;
        this.breakdownItems = breakdownItems != null ? breakdownItems : new ArrayList<>();
    }

    @NonNull
    @Override
    public BreakdownViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_breakdown, parent, false);
        return new BreakdownViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BreakdownViewHolder holder, int position) {
        BreakdownItem item = breakdownItems.get(position);

        // Set person name
        holder.personName.setText(item.getPersonName());

        // Set date
        holder.date.setText(item.getDate());

        // Set amount with peso sign
        holder.amount.setText("₱ " + item.getAmount() + ".00");

        // Set status
        String status = item.getStatus();
        holder.status.setText(status != null ? status : "");

        // Set description if available
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            holder.description.setText(item.getDescription());
            holder.description.setVisibility(View.VISIBLE);
        } else {
            holder.description.setVisibility(View.GONE);
        }

        // Set status indicator color based on status
        int indicatorColor;
        int statusTextColor;

        if (status != null) {
            switch (status.toLowerCase()) {
                case "paid":
                case "completed":
                case "settled":
                    indicatorColor = ContextCompat.getColor(context, R.color.green);
                    statusTextColor = ContextCompat.getColor(context, R.color.green);
                    break;
                case "pending":
                case "awaiting":
                    indicatorColor = ContextCompat.getColor(context, R.color.yellow);
                    statusTextColor = ContextCompat.getColor(context, R.color.yellow);
                    break;
                case "overdue":
                case "rejected":
                    indicatorColor = ContextCompat.getColor(context, R.color.red);
                    statusTextColor = ContextCompat.getColor(context, R.color.red);
                    break;
                default:
                    indicatorColor = ContextCompat.getColor(context, R.color.grey);
                    statusTextColor = ContextCompat.getColor(context, R.color.grey);
                    break;
            }
        } else {
            indicatorColor = ContextCompat.getColor(context, R.color.grey);
            statusTextColor = ContextCompat.getColor(context, R.color.grey);
        }

        holder.statusIndicator.setBackgroundColor(indicatorColor);
        holder.status.setTextColor(statusTextColor);

        // Set amount color based on category
        if (item.getCategory() != null) {
            switch (item.getCategory()) {
                case BALANCE:
                    holder.amount.setTextColor(ContextCompat.getColor(context, R.color.green));
                    break;
                case UNPAID:
                case DEBT:
                    holder.amount.setTextColor(ContextCompat.getColor(context, R.color.red));
                    break;
                case OWE:
                    holder.amount.setTextColor(ContextCompat.getColor(context, R.color.yellow));
                    break;
                default:
                    holder.amount.setTextColor(ContextCompat.getColor(context, R.color.darkBlue));
                    break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return breakdownItems.size();
    }

    public void updateData(List<BreakdownItem> newItems) {
        this.breakdownItems.clear();
        if (newItems != null) {
            this.breakdownItems.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void clearData() {
        this.breakdownItems.clear();
        notifyDataSetChanged();
    }

    static class BreakdownViewHolder extends RecyclerView.ViewHolder {
        View statusIndicator;
        TextView personName;
        TextView date;
        TextView description;
        TextView amount;
        TextView status;

        BreakdownViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            personName = itemView.findViewById(R.id.breakdownPersonName);
            date = itemView.findViewById(R.id.breakdownDate);
            description = itemView.findViewById(R.id.breakdownDescription);
            amount = itemView.findViewById(R.id.breakdownAmount);
            status = itemView.findViewById(R.id.breakdownStatus);
        }
    }
}

