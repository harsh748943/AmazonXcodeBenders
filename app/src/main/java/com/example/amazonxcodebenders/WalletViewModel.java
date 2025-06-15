package com.example.amazonxcodebenders;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class WalletViewModel extends ViewModel {
    private final MutableLiveData<Double> balance = new MutableLiveData<>(0.0);

    public LiveData<Double> getBalance() { return balance; }

    public void setBalance(double newBalance) { balance.setValue(newBalance); }
}

