package nextpresso.tools;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoTools {
    public static KeyPair generateRSAKeyPair(){
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SecretKey generateAESKey() {
        try{
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    /**
     * Encrypts a string using an RSA Public Key
     * @param b64PublicKey The public key in standard Base64
     * @param targetString The string to be encrypted in plain text
     * @return The encrypted string as standard Base64
     */
    public static String encryptRSAString(String b64PublicKey, String targetString){
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(b64PublicKey));
            PublicKey pubK = keyFactory.generatePublic(publicKeySpec);
            Cipher encrypt = Cipher.getInstance("RSA");
            encrypt.init(Cipher.ENCRYPT_MODE,pubK);
            return Base64.getEncoder().encodeToString(encrypt.doFinal(targetString.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Decrypt a string using an RSA Private key
     * @param b64PrivateKey The private key in standard Base64
     * @param b64TargetString The encrypted string in standard Base64
     * @return The decrypted string as plain text
     */
    public static String decryptRSAString(String b64PrivateKey, String b64TargetString){
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64PrivateKey));
            PrivateKey prvK = keyFactory.generatePrivate(publicKeySpec);
            Cipher decrypt = Cipher.getInstance("RSA");
            decrypt.init(Cipher.DECRYPT_MODE,prvK);
            return new String(decrypt.doFinal(Base64.getDecoder().decode(b64TargetString)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Encrypt a string using an AES key
     * @param b64AESKey The AES key in standard Base64
     * @param b64AESiv The AES IV in standard Base64
     * @param targetString The string to be encrypted in plain text
     * @return The encrypted string as standard Base64
     */
    public static String encryptAESString(String b64AESKey, String b64AESiv, String targetString){
        try {
            SecretKey aesKey = new SecretKeySpec(Base64.getDecoder().decode(b64AESKey), 0, Base64.getDecoder().decode(b64AESKey).length, "AES");
            IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(b64AESiv));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
            byte[] cipherText = cipher.doFinal(targetString.getBytes());
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Decrypt a string using an AES key
     * @param b64AESKey The AES key in standard Base64
     * @param b64AESiv The AES IV in standard Base64
     * @param b64TargetString The string to be decrypted in standard Base64
     * @return The decrypted string as plain text
     */
    public static String decryptAESString(String b64AESKey, String b64AESiv, String b64TargetString){
        try {
            SecretKey aesKey = new SecretKeySpec(Base64.getDecoder().decode(b64AESKey), 0, Base64.getDecoder().decode(b64AESKey).length, "AES");
            IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(b64AESiv));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
            return new String(cipher.doFinal(Base64.getDecoder().decode(b64TargetString)));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }
}
