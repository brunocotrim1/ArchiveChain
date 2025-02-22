package fcul.wrapper;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;

public class AESEncode {

    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    public static byte[] generateRandomIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_SIZE);
        return keyGenerator.generateKey();
    }

    public static String encrypt(byte[] plaintext, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    public static String decrypt(byte[] ciphertext, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
        byte[] decodedCiphertext = Base64.getDecoder().decode(ciphertext);
        byte[] plaintext = cipher.doFinal(decodedCiphertext);
        return new String(plaintext);
    }

    public static void main(String[] args) throws Exception {
        String text = "Hello, AES-GCM!";

        // Generate key and IV
        SecretKey key = generateKey();
        byte[] iv = generateRandomIV();

        // Encrypt
        String encryptedText = encrypt(text.getBytes(), key, iv);
        System.out.println("Encrypted: " + encryptedText);

        // Decrypt
        byte[] padded = encryptedText.getBytes();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(padded);
        while(outputStream.size() % 2048 != 0) {
            outputStream.write(0);
        }

        String decryptedText = decrypt(outputStream.toByteArray(), key, iv);
        System.out.println("Decrypted: " + decryptedText);
    }
}
