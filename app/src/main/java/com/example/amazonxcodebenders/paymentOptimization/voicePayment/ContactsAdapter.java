package com.example.amazonxcodebenders.paymentOptimization.voicePayment;

import static com.example.amazonxcodebenders.HomeActivity.generateUpiId;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.amazonxcodebenders.R;

import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {
    private List<Contact> contacts;

    public ContactsAdapter(List<Contact> contacts) {
        this.contacts = contacts;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.contact_list_item, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        Contact contact = contacts.get(position);
        holder.nameTextView.setText(contact.name);
        holder.phoneTextView.setText(contact.phone);
        // Generate initials for contactCircle
        String initials = getInitials(contact.name);
        holder.circleTextView.setText(initials);

        holder.itemView.setOnClickListener(v -> {
            // Generate UPI ID
            String upiId = generateUpiId(contact.name, contact.phone);

            // Start the new Activity
            Intent intent = new Intent(v.getContext(), SendMoneyOnlineActivity.class);
            intent.putExtra("recipient", contact.name);
            intent.putExtra("phone", contact.phone);
            intent.putExtra("upi", upiId);
            intent.putExtra("bankingName",contact.name);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    // Helper method to get initials
    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            // Single word, take first two letters
            return parts[0].length() >= 2 ? parts[0].substring(0, 2).toUpperCase() : parts[0].toUpperCase();
        } else {
            // Take first letter of first two words
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, phoneTextView;
         TextView circleTextView;

        public ContactViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.contactName);
            phoneTextView = itemView.findViewById(R.id.contactPhone);
            circleTextView = itemView.findViewById(R.id.contactCircle);



        }
    }
}
