package fcul.wrapper;


import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.Arrays;

import java.io.File;
import java.util.Random;

public class Sloth {
    static {
        String libPath = new File("src/main/native/sloth.so").getAbsolutePath();
        System.load(libPath);
    }

    // Native method declarations
    public native byte[] encode(byte[] piece, byte[] iv, int layers);
    public native byte[] decode(byte[] piece, byte[] iv, int layers);

    public static void main(String[] args) {
        Sloth sloth = new Sloth();

        // Example usage
        byte[] piece = new byte[4096];  // Example 4096 byte array
        byte[] iv = new byte[32];       // Example 32 byte IV
        int layers = 200;                // Example number of layers
        Random random = new Random(123);
        random.nextBytes(piece);
        random.nextBytes(iv);
        long startTime = System.nanoTime();
        byte[] temp = Arrays.clone(piece);

        byte[] encodeSuccess = sloth.encode(piece, iv, layers);
        System.out.println("Time taken: " + (System.nanoTime() - startTime) / 1000000 + "ms");
        startTime = System.nanoTime();
        byte[] decodeOutput = sloth.decode(encodeSuccess, iv, layers);
        System.out.println("Time taken: " + (System.nanoTime() - startTime) / 1000000 + "ms");
        System.out.println(java.util.Arrays.equals(temp, decodeOutput));
        System.out.println("Decode complete");
    }
}