package fcul.wrapper;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AESEncode {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_MODE = "AES/CTR/NoPadding"; // CBC mode with PKCS5 padding
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int AES_KEY_SIZE = 256; // AES key size (128, 192, or 256 bits)
    private static final int HMAC_KEY_SIZE = 256; // HMAC key size
    private static final String PROVIDER = "SunJCE";
    private static final int HMAC_SIZE = 32; // HMAC-SHA256 produces a 32-byte hash
    private static final long CHUNK_SIZE = 1024 * 1024*10; // 1 MB
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    public static SecretKey generateAESKey() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM, PROVIDER);
        keyGenerator.init(AES_KEY_SIZE);
        return keyGenerator.generateKey();
    }

    public static SecretKey generateHMACKey() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(HMAC_ALGORITHM, PROVIDER);
        keyGenerator.init(HMAC_KEY_SIZE);
        return keyGenerator.generateKey();
    }

    public static byte[] encrypt(byte[] data, SecretKey aesKey, SecretKey hmacKey, byte[] iv) throws Exception {
        long time = System.nanoTime();
        byte[] encryptedData = parallelAES(data, aesKey, iv, Cipher.ENCRYPT_MODE);
        System.out.println("Time taken to encrypt: " + (System.nanoTime() - time) / 1000000 + "ms");
        Mac mac = Mac.getInstance(HMAC_ALGORITHM, PROVIDER);
        mac.init(hmacKey);
        byte[] hmac = mac.doFinal(encryptedData);
        byte[] combined = new byte[HMAC_SIZE + encryptedData.length];
        System.arraycopy(hmac, 0, combined, 0, HMAC_SIZE);
        System.arraycopy(encryptedData, 0, combined, HMAC_SIZE, encryptedData.length);
        return combined;
    }

    private static byte[] parallelAES(byte[] data, SecretKey aesKey, byte[] iv, int mode) throws Exception {
        int numChunks = (int) Math.ceil((double) data.length / CHUNK_SIZE);
        try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS)) {
            List<Future<byte[]>> futures = new ArrayList<>();
            for (int i = 0; i < numChunks; i++) {
                long start = i * CHUNK_SIZE;
                long end = Math.min(start + CHUNK_SIZE, data.length);
                byte[] chunk = Arrays.copyOfRange(data, (int) start, (int) end);
                final int chunkIndex = i;
                futures.add(executor.submit(() -> processChunk(chunk, aesKey, iv, chunkIndex, mode)));
            }
            executor.shutdown();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (Future<byte[]> future : futures) {
                outputStream.write(future.get());
            }
            return outputStream.toByteArray();
        }
    }

    private static byte[] processChunk(byte[] chunk, SecretKey aesKey, byte[] iv, int counterOffset, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_MODE, PROVIDER);
        byte[] ivCopy = iv.clone();
        ByteBuffer.wrap(ivCopy).putLong(8, ByteBuffer.wrap(iv).getLong(8) + counterOffset);
        IvParameterSpec ivSpec = new IvParameterSpec(ivCopy);
        cipher.init(mode, aesKey, ivSpec);
        return cipher.doFinal(chunk);
    }

    public static byte[] decrypt(byte[] combinedData, SecretKey aesKey, SecretKey hmacKey, byte[] iv) throws Exception {
        byte[] hmac = new byte[HMAC_SIZE];
        byte[] encryptedData = new byte[combinedData.length - HMAC_SIZE];
        System.arraycopy(combinedData, 0, hmac, 0, HMAC_SIZE);
        System.arraycopy(combinedData, HMAC_SIZE, encryptedData, 0, encryptedData.length);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM, PROVIDER);
        mac.init(hmacKey);
        byte[] computedHmac = mac.doFinal(encryptedData);
        if (!java.security.MessageDigest.isEqual(hmac, computedHmac)) {
            throw new SecurityException("HMAC verification failed: Data may have been tampered with.");
        }
        return parallelAES(encryptedData, aesKey, iv, Cipher.DECRYPT_MODE);
    }

    public static void main(String[] args) {
        try {
            byte[] originalData = new byte[1024 * 1024 * 1024];
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(originalData);
            new SecureRandom().nextBytes(iv);
            SecretKey aesKey = generateAESKey();
            SecretKey hmacKey = generateHMACKey();
            long startTime = System.nanoTime();
            byte[] combinedData = encrypt(originalData, aesKey, hmacKey, iv);
            originalData = null;
            System.out.println("Time taken to encrypt and compute HMAC: " + (System.nanoTime() - startTime) / 1000000 + "ms");
            long startTime2 = System.nanoTime();
            byte[] decryptedData = decrypt(combinedData, aesKey, hmacKey, iv);
            System.out.println("Time taken to decrypt and verify HMAC: " + (System.nanoTime() - startTime2) / 1000000 + "ms");
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
