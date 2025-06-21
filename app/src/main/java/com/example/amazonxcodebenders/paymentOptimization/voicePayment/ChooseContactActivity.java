package com.example.amazonxcodebenders.paymentOptimization.voicePayment;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.amazonxcodebenders.R;

import java.util.ArrayList;

public class ChooseContactActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_choose_contact);

        ArrayList<Contact> contactList = (ArrayList<Contact>) getIntent().getSerializableExtra("contacts");

        Log.d("ChooseContact", "Contacts received: " + (contactList == null ? "null" : contactList.size()));


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#FBDF4D")); // Apna yellow color code yahan daalein
        }

        RecyclerView rvContacts = findViewById(R.id.rvContacts);
        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        ContactsAdapter adapter = new ContactsAdapter(contactList);
        rvContacts.setAdapter(adapter);


    }
}