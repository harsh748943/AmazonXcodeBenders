package com.example.amazonxcodebenders.billReminder;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.amazonxcodebenders.R;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class BillAdapter extends RecyclerView.Adapter<BillAdapter.BillViewHolder> {

    private final List<Bill> bills;

    public BillAdapter(List<Bill> bills) {
        this.bills = bills;
    }

    @NonNull
    @Override
    public BillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.bill_item, parent, false);
        return new BillViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BillViewHolder holder, int position) {
        Bill bill = bills.get(position);

        // Split description into billType and fullMessage
        String billTypeExtracted = "Other Bill";
        String fullMessage = bill.getDescription();
        if (fullMessage.contains(" - ")) {
            String[] parts = fullMessage.split(" - ", 2);
            billTypeExtracted = parts[0];
            fullMessage = parts[1];
        }

        final String billType = billTypeExtracted; // Make it effectively final

        holder.billType.setText(billType);
        holder.amount.setText(bill.getAmount());
        holder.dueDate.setText(bill.getDueDate());
        holder.description.setText(fullMessage);

        // Delete button
        holder.deleteButton.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                FirebaseDatabase.getInstance()
                        .getReference("bills")
                        .child(bill.getKey())
                        .removeValue()
                        .addOnSuccessListener(aVoid -> {
                            bills.remove(pos);
                            notifyItemRemoved(pos);
                        })
                        .addOnFailureListener(Throwable::printStackTrace);
            }
        });

        // Pay button
        holder.payButton.setOnClickListener(v -> {
            Context context = holder.itemView.getContext();
            Intent intent = null;

            switch (billType.toLowerCase()) {
                case "electricity bill":
                    intent = new Intent(context, ElectricityBillActivity.class);
                    break;
                case "gas bill":
                    intent = new Intent(context, GasBillActivity.class);
                    break;
                default:
                    // Optional: handle unknown types
                    break;
            }

            if (intent != null) {
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return bills.size();
    }

    public static class BillViewHolder extends RecyclerView.ViewHolder {
        TextView billType, amount, dueDate, description;
        Button deleteButton, payButton;

        public BillViewHolder(@NonNull View itemView) {
            super(itemView);
            billType = itemView.findViewById(R.id.billTypeText);
            amount = itemView.findViewById(R.id.billAmountText);
            dueDate = itemView.findViewById(R.id.billDueDateText);
            description = itemView.findViewById(R.id.billDetailsText);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            payButton = itemView.findViewById(R.id.payBillButton);
        }
    }
}

