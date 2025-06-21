package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoHelper {

    private static final String KEY_ALIAS = "my_aes_key";
    private static final int IV_LENGTH = 12; // 12 bytes is standard for GCM
    private static final int TAG_LENGTH = 128; // bits

    private static void generateKeyIfNeeded() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build();
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(keySpec);
            keyGenerator.generateKey();
        }
    }

    public static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    public static String encryptWithKey(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Combine IV + ciphertext
        byte[] ivAndCiphertext = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
        System.arraycopy(encrypted, 0, ivAndCiphertext, iv.length, encrypted.length);

        return Base64.encodeToString(ivAndCiphertext, Base64.NO_WRAP);
    }



    // Encrypts the plain text and returns IV + ciphertext as Base64
    public static String encrypt(String data) throws Exception {
        generateKeyIfNeeded();
        SecretKey key = getSecretKey();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key); // DO NOT provide IV here

        byte[] iv = cipher.getIV(); // Get the system-generated IV
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Prepend IV to ciphertext
        byte[] ivAndCiphertext = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
        System.arraycopy(encrypted, 0, ivAndCiphertext, iv.length, encrypted.length);

        return Base64.encodeToString(ivAndCiphertext, Base64.NO_WRAP);
    }

    // Decrypts the Base64 string (IV + ciphertext) back to plain text
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        byte[] ivAndCiphertext = Base64.decode(encryptedData, Base64.NO_WRAP);
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[ivAndCiphertext.length - IV_LENGTH];
        System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_LENGTH);
        System.arraycopy(ivAndCiphertext, IV_LENGTH, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] decrypted = cipher.doFinal(ciphertext);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

}
