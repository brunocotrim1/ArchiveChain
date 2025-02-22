package fcul.wrapper;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.Mac;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

public class AESEncode {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_MODE = "AES/CBC/PKCS5Padding"; // CBC mode with PKCS5 padding
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int AES_KEY_SIZE = 256; // AES key size (128, 192, or 256 bits)
    private static final int HMAC_KEY_SIZE = 256; // HMAC key size
    private static final String PROVIDER = "SunJCE";
    private static final int HMAC_SIZE = 32; // HMAC-SHA256 produces a 32-byte hash

    // Generate AES key
    public static SecretKey generateAESKey() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM, PROVIDER);
        keyGenerator.init(AES_KEY_SIZE);
        return keyGenerator.generateKey();
    }

    // Generate HMAC key
    public static SecretKey generateHMACKey() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(HMAC_ALGORITHM, PROVIDER);
        keyGenerator.init(HMAC_KEY_SIZE);
        return keyGenerator.generateKey();
    }

    // Encrypt data and compute HMAC, then combine into a single byte array
    public static byte[] encrypt(byte[] data, SecretKey aesKey, SecretKey hmacKey, byte[] iv) throws Exception {
        // Encrypt the data
        Cipher cipher = Cipher.getInstance(AES_MODE, PROVIDER);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);
        byte[] encryptedData = cipher.doFinal(data);

        // Compute HMAC of the encrypted data
        Mac mac = Mac.getInstance(HMAC_ALGORITHM, PROVIDER);
        mac.init(hmacKey);
        byte[] hmac = mac.doFinal(encryptedData);

        // Combine HMAC and encrypted data into a single byte array
        byte[] combined = new byte[HMAC_SIZE + encryptedData.length];
        System.arraycopy(hmac, 0, combined, 0, HMAC_SIZE); // Prepend HMAC
        System.arraycopy(encryptedData, 0, combined, HMAC_SIZE, encryptedData.length); // Append encrypted data

        return combined;
    }

    // Decrypt data and validate HMAC
    public static byte[] decrypt(byte[] combinedData, SecretKey aesKey, SecretKey hmacKey, byte[] iv) throws Exception {
        // Split the combined data into HMAC and encrypted data
        byte[] hmac = new byte[HMAC_SIZE];
        byte[] encryptedData = new byte[combinedData.length - HMAC_SIZE];
        System.arraycopy(combinedData, 0, hmac, 0, HMAC_SIZE); // Extract HMAC
        System.arraycopy(combinedData, HMAC_SIZE, encryptedData, 0, encryptedData.length); // Extract encrypted data

        // Verify HMAC
        Mac mac = Mac.getInstance(HMAC_ALGORITHM, PROVIDER);
        mac.init(hmacKey);
        byte[] computedHmac = mac.doFinal(encryptedData);
        if (!java.security.MessageDigest.isEqual(hmac, computedHmac)) {
            throw new SecurityException("HMAC verification failed: Data may have been tampered with.");
        }

        // Decrypt the data
        Cipher cipher = Cipher.getInstance(AES_MODE, PROVIDER);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
        return cipher.doFinal(encryptedData);
    }

    public static void main(String[] args) {
        try {
            // Generate random data and IV
            byte[] originalData = new byte[1024 * 1024]; // 1 MB of data
            byte[] iv = new byte[16]; // IV size for AES is 16 bytes (128 bits)
            new SecureRandom().nextBytes(originalData);
            new SecureRandom().nextBytes(iv);

            // Generate AES and HMAC keys
            SecretKey aesKey = generateAESKey();
            SecretKey hmacKey = generateHMACKey();

            // Encrypt the data
            long startTime = System.nanoTime();
            byte[] combinedData = encrypt(originalData, aesKey, hmacKey, iv);
            System.out.println("Time taken to encrypt and compute HMAC: " + (System.nanoTime() - startTime) / 1000000 + "ms");

            // Decrypt the data
            long startTime2 = System.nanoTime();
            byte[] decryptedData = decrypt(combinedData, aesKey, hmacKey, iv);
            System.out.println("Time taken to decrypt and verify HMAC: " + (System.nanoTime() - startTime2) / 1000000 + "ms");

            // Verify that the decrypted data matches the original data
            if (java.util.Arrays.equals(originalData, decryptedData)) {
                System.out.println("Decryption successful: Data matches the original.");
            } else {
                System.out.println("Decryption failed: Data does not match the original.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}