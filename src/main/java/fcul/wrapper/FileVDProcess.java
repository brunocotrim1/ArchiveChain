package fcul.wrapper;

import fcul.ArchiveMintUtils.Utils.CryptoUtils;

import java.util.Arrays;
import java.util.Random;

public class FileVDProcess {
    public static int CHUNK_SIZE = 4096;
    private static Sloth sloth = new Sloth();
    private static int averageTimePerChunkMS = 405; //Tested in McBookM1 and its an hyperparameter of the system 200 iterations
    private static int defaultChunkIteration= 200;
    private static int goalTimePerChunkMS = 5000;
    public static byte[] encodeFile(byte[] file, byte[] iv, int iterationsPerChunk) {
        if (file.length % CHUNK_SIZE != 0) {
            throw new IllegalArgumentException("File size must be a multiple of " + CHUNK_SIZE);
        }
        long startTime = System.nanoTime();
        byte[] out = new byte[file.length];
        byte[] ivCopy = Arrays.copyOf(iv, iv.length);

        for (int i = 0; i < file.length; i += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, file.length - i);
            byte[] chunk = new byte[length];

            System.arraycopy(file, i, chunk, 0, length);

            // Process the chunk here
            byte[] encodedChunk = sloth.encode(chunk, ivCopy, iterationsPerChunk);
            System.arraycopy(encodedChunk, 0, out, i, encodedChunk.length);
            byte[] hash = CryptoUtils.hash256(encodedChunk);
            ivCopy = Arrays.copyOf(hash, hash.length);
        }
        System.out.println("Time taken encoding: " + (System.nanoTime() - startTime) / 1000000 + "ms");
        return out;
    }

    public static byte[] decodeFile(byte[] file, byte[] iv, int iterationsPerChunk) {
        if (file.length % CHUNK_SIZE != 0) {
            throw new IllegalArgumentException("File size must be a multiple of " + CHUNK_SIZE);
        }
        long startTime = System.nanoTime();
        byte[] out = new byte[file.length];

        byte[] ivCopy = Arrays.copyOf(iv, iv.length);

        for (int i = 0; i < file.length; i += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, file.length - i);
            byte[] chunk = new byte[CHUNK_SIZE];

            System.arraycopy(file, i, chunk, 0, length);
            // Process the chunk here
            byte[] decodedChunk = sloth.decode(chunk, ivCopy, iterationsPerChunk);
            System.arraycopy(decodedChunk, 0, out, i, decodedChunk.length);
            byte[] hash = CryptoUtils.hash256(chunk);
            ivCopy = Arrays.copyOf(hash, hash.length);
        }
        System.out.println("Time taken decoding: " + (System.nanoTime() - startTime) / 1000000 + "ms");
        return out;
    }
public static int iterationsPerChunk(int fileSize){
        if(fileSize % CHUNK_SIZE != 0){
            throw new IllegalArgumentException("File size must be a multiple of " + CHUNK_SIZE);
        }
        int chunkAmount = fileSize/CHUNK_SIZE;
        double totalIterationsGoal = (double) (goalTimePerChunkMS * defaultChunkIteration) /averageTimePerChunkMS;
        return (int) Math.max(Math.round(totalIterationsGoal/chunkAmount), 1);

}
    public static void main(String[] args) throws Exception {
        Random random = new Random(123);
        int fileSize = CHUNK_SIZE * 2;  // Example 3 chunks
        System.out.println("File size: " + fileSize);
        byte[] file = new byte[fileSize];  // Example 3 chunks
        byte[] iv = new byte[32];       // Example 32 byte IV
        int iterationsPerChunk = 200;              // Example number of layers
        System.out.println("Iterations per chunk: " + iterationsPerChunk);
        random.nextBytes(file);
        random.nextBytes(iv);
        byte[] encodedFile = encodeFile(file, iv, iterationsPerChunk);
        byte[] decodedFile = decodeFile(encodedFile, iv, iterationsPerChunk);
        System.out.println(Arrays.equals(file, decodedFile));
        //USAR O HASH DO PRIMEIRO CHUNK COMO INPUT IV PARA O SEGUNDO CHUNK
    }


}
