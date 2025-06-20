package com.example.amazonxcodebenders.billReminder;

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
        holder.billType.setText(bill.getBillType());
        holder.amount.setText(bill.getAmount());
        holder.dueDate.setText(bill.getDueDate());
        holder.description.setText(bill.getDescription());

        holder.deleteButton.setOnClickListener(v -> {
            FirebaseDatabase.getInstance()
                    .getReference("bills")
                    .child(bill.getKey()) // Uses key stored in Bill object
                    .removeValue()
                    .addOnSuccessListener(aVoid -> {
                        bills.remove(position);
                        notifyItemRemoved(position);
                    })
                    .addOnFailureListener(e -> e.printStackTrace());
        });
    }

    @Override
    public int getItemCount() {
        return bills.size();
    }

    public static class BillViewHolder extends RecyclerView.ViewHolder {
        TextView billType, amount, dueDate, description;
        Button deleteButton;

        public BillViewHolder(@NonNull View itemView) {
            super(itemView);
            billType = itemView.findViewById(R.id.billTypeText);
            amount = itemView.findViewById(R.id.billAmountText);
            dueDate = itemView.findViewById(R.id.billDueDateText);
            description = itemView.findViewById(R.id.billDescriptionText);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}

