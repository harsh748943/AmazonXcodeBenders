package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

public class KeyStoreHelper {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    public static String getKeyAlias(String userId) {
        return "my_rsa_key_" + userId;
    }

    // Generate per-user key
    public static void generateKey(String userId) throws Exception {


// Then generate new key with all purposes including DECRYPT

        String alias = "my_rsa_key_" + userId; // Unique per user
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null); // Initialization attempt
//        if (keyStore.containsAlias(alias)) {
//            keyStore.deleteEntry(alias); // Delete old key without DECRYPT purpose
//        }

        if (!keyStore.containsAlias(alias)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA,
                    ANDROID_KEYSTORE
            );
            // Updated KeyGenParameterSpec in generateKey()
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN |
                            KeyProperties.PURPOSE_VERIFY |
                            KeyProperties.PURPOSE_ENCRYPT |  // Add encryption purpose
                            KeyProperties.PURPOSE_DECRYPT     // Add decryption purpose
            )
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1) // Add encryption padding
                    .build();

            kpg.initialize(spec);
            kpg.generateKeyPair();
        }
    }

    // Sign with user-specific key
    public static byte[] signData(String userId, String data) throws Exception {
        String alias = "my_rsa_key_" + userId;
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);

        if (privateKey == null) {
            throw new IllegalStateException("Private key not found for user: " + userId);
        }

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes());
        return signature.sign();
    }

    public static boolean verifyDataWithPublicKey(String data, String signatureBase64, String publicKeyBase64) throws Exception {
        byte[] signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP);
        byte[] publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data.getBytes());
        return signature.verify(signatureBytes);
    }

    public static String getPublicKeyBase64(String userId) throws Exception {
        String alias = getKeyAlias(userId);
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        PublicKey publicKey = keyStore.getCertificate(alias).getPublicKey();
        return Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
    }

    public static PrivateKey getPrivateKey(String userId) throws Exception {
        String alias = getKeyAlias(userId);
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return (PrivateKey) keyStore.getKey(alias, null);
    }

    public static PublicKey getPublicKeyFromBase64(String publicKeyBase64) throws Exception {
        byte[] publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    }
}
