// WalletViewModel.java
package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class WalletViewModel extends AndroidViewModel {
    private final MutableLiveData<Double> balance = new MutableLiveData<>(0.0);

    public WalletViewModel(@NonNull Application application) { super(application); }
    public LiveData<Double> getBalance() { return balance; }
    public void setBalance(double newBalance) { balance.setValue(newBalance); }
}
