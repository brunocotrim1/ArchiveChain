package fcul.wrapper;

import fcul.ArchiveMintUtils.Utils.CryptoUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

public class FileVDProcess {
    public static int CHUNK_SIZE = 4096;
    private static Sloth sloth = new Sloth();
    private static int averageTimePerChunkMS = 405; //Tested in McBookM1 and its an hyperparameter of the system 200 iterations
    private static int defaultChunkIteration = 200;
    private static int goalTimePerChunkMS = 60000;

    public static byte[] encodeFile(byte[] file, byte[] iv, int iterationsPerChunk) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            byte[] ivCopy = Arrays.copyOf(iv, iv.length);
            int padding = 0;
            for (int i = 0; i < file.length; i += CHUNK_SIZE) {
                int remainingBytes = Math.min(file.length - i, CHUNK_SIZE);
                byte[] chunk = Arrays.copyOfRange(file, i, i + remainingBytes);
                if (remainingBytes < CHUNK_SIZE) {
                    padding = CHUNK_SIZE - remainingBytes;
                    chunk = pad(chunk);
                }
                byte[] encodedChunk = sloth.encode(chunk, ivCopy, iterationsPerChunk);
                byteStream.write(encodedChunk);
                ivCopy = CryptoUtils.hash256(encodedChunk);
            }
            byteStream.write(intToByteArray(padding));
            return byteStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decodeFile(byte[] file, byte[] iv, int iterationsPerChunk) {
        try{
        int paddingAdded = ByteBuffer.wrap(file, file.length - 4, 4).getInt();
        byte[] ivCopy = Arrays.copyOf(iv, iv.length);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        for (int i = 0; i < file.length-Integer.BYTES; i += CHUNK_SIZE) {
            int remainingBytes = Math.min(file.length - i, CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(file, i, i + remainingBytes);
            byte[] decodedChunk = sloth.decode(chunk, ivCopy, iterationsPerChunk);
            byteStream.write(decodedChunk);
            ivCopy = CryptoUtils.hash256(chunk);
        }
        byte[] decodedFile = byteStream.toByteArray();
        decodedFile = unpad(decodedFile, paddingAdded);
        return decodedFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] pad(byte[] original) {
        byte[] padded = new byte[CHUNK_SIZE];
        System.arraycopy(original, 0, padded, 0, original.length);
        return padded;
    }
    private static byte[] unpad(byte[] original, int padding) {
        byte[] unpadded = new byte[original.length-padding];
        System.arraycopy(original, 0, unpadded, 0, unpadded.length);
        return unpadded;
    }

    public static byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public static int iterationsPerChunk(int fileSize) {
        int chunkAmount = (fileSize / CHUNK_SIZE)+1;
        double totalIterationsGoal = (double) (goalTimePerChunkMS * defaultChunkIteration) / averageTimePerChunkMS;
        return (int) Math.max(Math.ceil(totalIterationsGoal / chunkAmount), 1);

    }

    public static void main(String[] args) throws Exception {
        Random random = new Random(123);
        byte[] file = Files.readAllBytes(Path.of("TestFiles/relatorio_preliminar.pdf"));
        int fileSize = file.length;
        System.out.println("File size: " + fileSize);
        byte[] iv = new byte[32];       // Example 32 byte IV
        int iterationsPerChunk = iterationsPerChunk(fileSize);              // Example number of layers
        System.out.println("Iterations per chunk: " + iterationsPerChunk);
        random.nextBytes(iv);
        long startTime = System.nanoTime();
        byte[] encodedFile = encodeFile(file, iv, iterationsPerChunk);
        System.out.println("Time taken encode: " + (System.nanoTime() - startTime) / 1000000 + "ms");
        startTime = System.nanoTime();
        byte[] decodedFile = decodeFile(encodedFile, iv, iterationsPerChunk);
        System.out.println("Time taken decode: " + (System.nanoTime() - startTime) / 1000000 + "ms");
        System.out.println(Arrays.equals(file, decodedFile));
        //USAR O HASH DO PRIMEIRO CHUNK COMO INPUT IV PARA O SEGUNDO CHUNK
    }


}
