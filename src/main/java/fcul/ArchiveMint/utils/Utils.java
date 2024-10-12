package fcul.ArchiveMint.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import fcul.ArchiveMint.model.Block;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class Utils {
    private static final Gson gson = new Gson();
    public static void removeFolderAndRecreate(String folder){
        File file = new File(folder);
        if(file.exists()){
            File[] files = file.listFiles();
            if(files != null){
                for(File f: files){
                    if(f.isDirectory()){
                        removeFolderAndRecreate(f.getAbsolutePath());
                    }else{
                        f.delete();
                    }
                }
            }
            file.delete();
        }
        if(!new File(folder).exists()){
            new File(folder).mkdirs();
        }
    }
    public static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    // Method to subtract one byte array from another
    public static byte[] subtract(byte[] minuend, byte[] subtrahend) {
        if (minuend == null || subtrahend == null) {
            throw new IllegalArgumentException("Byte arrays cannot be null");
        }
        if (minuend.length != subtrahend.length) {
            throw new IllegalArgumentException("Byte arrays must be of the same length");
        }

        byte[] result = new byte[minuend.length];
        int borrow = 0;

        for (int i = minuend.length - 1; i >= 0; i--) {
            // Convert bytes to unsigned values for subtraction
            int minuendByte = minuend[i] & 0xFF;
            int subtrahendByte = subtrahend[i] & 0xFF;

            // Subtract the bytes and adjust for any previous borrow
            int diff = minuendByte - subtrahendByte - borrow;

            // If diff is negative, we need to borrow from the next byte
            if (diff < 0) {
                diff += 256; // 256 is 2^8, the range of a byte
                borrow = 1;
            } else {
                borrow = 0;
            }

            // Store the result byte
            result[i] = (byte) diff;
        }

        return result;
    }

    public static BigInteger difference(byte[] a, byte[] b) {
        return new BigInteger(a).subtract(new BigInteger(b)).abs();
    }
    // Method to calculate bitwise difference
    // Method to calculate bitwise difference between two byte arrays
    public static int bitwiseDifference(byte[] arr1, byte[] arr2) {
        if (arr1.length != arr2.length) {
            throw new IllegalArgumentException("Arrays must be of the same length");
        }

        int bitDifference = 0;

        for (int i = 0; i < arr1.length; i++) {
            byte byte1 = arr1[i];
            byte byte2 = arr2[i];
            byte xorResult = (byte) (byte1 ^ byte2);
            bitDifference += Integer.bitCount(Byte.toUnsignedInt(xorResult));
        }
        return bitDifference;
    }

    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2) {
        // Create a new byte array with the combined length of both arrays
        byte[] result = new byte[array1.length + array2.length];

        // Copy the first array into the result array
        System.arraycopy(array1, 0, result, 0, array1.length);

        // Copy the second array into the result array, starting after the first array
        System.arraycopy(array2, 0, result, array1.length, array2.length);

        // Return the concatenated array
        return result;
    }

    public static byte[] intToByteArray(int number, ByteOrder byteOrder) {
        // Allocate a ByteBuffer and set the order
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(byteOrder);
        buffer.putInt(number);
        return buffer.array();
    }
    public static String serializeBlock(Block block) {
        try {
            return gson.toJson(block);
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception
            return null;
        }
    }
    public static Block deserializeBlock(String jsonString) {
        try {
            return gson.fromJson(jsonString, Block.class);
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception
            return null;
        }
    }

    public static int byteArrayToInt(byte[] byteArray, ByteOrder byteOrder) {
        // Wrap the byte array into a ByteBuffer and set the order
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        buffer.order(byteOrder);
        return buffer.getInt();
    }
    public static byte[] getLastNBytes(byte[] array, int n) {
        // Copy the last 4 bytes from the array
        return Arrays.copyOfRange(array, array.length - n, array.length);
    }
}
