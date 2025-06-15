package com.example.amazonxcodebenders;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;

public class KeyStoreHelper {

    private static final String KEY_ALIAS = "wallet_key";

    // Generates RSA keypair if not already present
    public static void generateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            keyPairGenerator.initialize(
                    new KeyGenParameterSpec.Builder(KEY_ALIAS,
                            KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                            .build());
            keyPairGenerator.generateKeyPair();
        }
    }

    // Signs data with private key
    public static byte[] signData(String data) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, null);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes());
        return signature.sign();
    }

    // Gets public key in Base64 for sharing/verification
    public static String getPublicKeyBase64() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        PublicKey publicKey = keyStore.getCertificate(KEY_ALIAS).getPublicKey();
        byte[] publicKeyBytes = publicKey.getEncoded();
        return Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP);
    }

    // Verifies data with provided public key
    public static boolean verifyDataWithPublicKey(String data, String sigBase64, String publicKeyBase64) throws Exception {
        byte[] keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey senderPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(senderPublicKey);
        signature.update(data.getBytes());
        byte[] sigBytes = Base64.decode(sigBase64, Base64.NO_WRAP);
        return signature.verify(sigBytes);
    }
}
