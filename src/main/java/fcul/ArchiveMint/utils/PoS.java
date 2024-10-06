package fcul.ArchiveMint.utils;

import lombok.Getter;
import lombok.ToString;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

//Notes:
// We use the sloth+root as the nonce so it enforces an attacker to run the sloth function and encode the merkle tree
//at the end of each generation.


public class PoS {

    private static final int CHUNK_SIZE = 2048;
    private static final int LEAVES_POS = 64;
    private static final int byteHashSize = 32;

    private static String IN_PROGRESS = ".INPROGRESS.boolean";

    public static void plot_files(String filename, String destinationFolder,byte[] publicKey) {
        System.out.println("Starting Plotting");
        try {
            Utils.removeFolderAndRecreate(destinationFolder);
            File file = new File(destinationFolder + "/" + IN_PROGRESS);
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        byte[] buffer = new byte[CHUNK_SIZE];
        int chunkCount = 0;
        try (FileInputStream fis = new FileInputStream(filename)) {
            int bytesRead = 0;
            long time = System.currentTimeMillis();

            while ((bytesRead = fis.read(buffer)) != -1) {
                if (bytesRead < CHUNK_SIZE) {
                    padding(buffer, bytesRead);
                }
                MerkleTree tree = new MerkleTree(buffer, LEAVES_POS,publicKey);
                //Use the Hash(sloth+root) as the Nonce
                String fileName = destinationFolder + "/" + chunkCount + "_" + Hex.encodeHexString(tree.getSlothNonce(publicKey));
                writeMerkleTreeToFile(tree, fileName);
                chunkCount++;
            }
            System.out.println("Time to plot: " + (System.currentTimeMillis() - time) + "ms for file " + filename);
            time = System.currentTimeMillis();
            //generatePoRepMerkleTree(destinationFolder);
            new File(destinationFolder + "/" + IN_PROGRESS).delete();
            //cSystem.out.println("Time to plot PoRep: " + (System.currentTimeMillis() - time) + "ms for file " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //This method generates a second MerkleTree composed by the root hashes of the MerkleTrees from file blocks
    //This way we can not only verify that a specific block belongs to a file, but also challenge specific nodes
    //that probabilistically allow for PoRep
    /**
    public static void generatePoRepMerkleTree(String plotFolder){
        File folder = new File(plotFolder);
        File[] directories = folder.listFiles();
        directories = removeFile(directories, IN_PROGRESS);
        orderFileByName(directories);
        List<byte[]> leaveHashes = new ArrayList<>();
        for (File file : directories) {
            String indexAndhexHashBlock = file.getName();
            leaveHashes.add(indexAndhexHashBlock.getBytes());
        }
        if(leaveHashes.size() % 2 != 0){
            leaveHashes.add(new byte[byteHashSize]);
        }
        MerkleTree tree = new MerkleTree(leaveHashes, leaveHashes.size());
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(plotFolder + "/.PoRepTree"));
            oos.writeObject(tree);
            oos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }**/

    private static File[] removeFile(File[] listOfFiles,String filename) {
        List<File> files = new ArrayList<>();
        for(int i = 0; i < listOfFiles.length; i++){
            if(listOfFiles[i].getName().equals(filename)){
                continue;
            }
            files.add(listOfFiles[i]);
        }
        return files.toArray(new File[0]);
    }

    public static void retrieveOriginal(String plotsFolder, String desinationFolder, String filename) {
        if(!new File(plotsFolder).exists()){
            throw new RuntimeException("Plot Not Found");
        }
        if (!new File(desinationFolder).exists()) {
            new File(desinationFolder).mkdirs();
        }
        File folder = new File(plotsFolder);
        File[] listOfFiles = folder.listFiles();
        listOfFiles = removeFile(listOfFiles, ".PoRepTree");
        orderFileByName(listOfFiles);

        try(OutputStream os = new FileOutputStream(desinationFolder + filename)) {

            for (File file : listOfFiles) {

                MerkleTree tree = readMerkleTreeFromFile(file);
                //boolean validSLoth = MySloth.verify(tree.getSlothResult(), tree.getRoot().getData());
                List<byte[]> dataLeaves = tree.getAllLeaves(tree.getRoot());

                for (byte[] data : dataLeaves) {
                    os.write(data);
                }
            }
            //unpadding(desinationFolder + filename);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void padding(byte[] buffer, int bytesRead) {
        for (int i = bytesRead; i < CHUNK_SIZE; i++) {
            buffer[i] = '\n';
        }
    }


    private static void orderFileByName(File[] listOfFiles) {
        Arrays.sort(listOfFiles, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                int n1 = Integer.parseInt(o1.getName().split("_")[0]);
                int n2 = Integer.parseInt(o2.getName().split("_")[0]);
                if (n1 == n2)
                    return 0;
                return n1 < n2 ? -1 : 1;
            }
        });
    }

    public static PosProof proofOfSpace(byte[] challenge, String plotFolder) {
        try {

            File folder = new File(plotFolder);
            File[] directories = folder.listFiles();
            String closestPlotPath = "";
            int closestDiff = Integer.MAX_VALUE;
            for (File file : directories) {
                if (!file.isDirectory()) continue;
                if(containsINProgress(file.listFiles())) continue;
                for (File plot : file.listFiles()) {
                    if(plot.getName().equals(".PoRepTree")) continue; // Caso em que nao e um plot file
                    //For each plot file we read the nonce and calculate the difference with the challenge
                    //if the difference is smaller than the previous smallest difference we
                    // update the closestPlotPath
                    String nonce = plot.getName().split("_")[1];
                    int diff = Utils.bitwiseDifference(Hex.decodeHex(nonce), challenge);
                    if (diff < closestDiff) {
                        closestDiff = diff;
                        closestPlotPath = plot.getPath();
                    }
                }
            }
            if(closestPlotPath.equals("")){
                throw new RuntimeException("No plots available");
            }
            MerkleTree tree = readMerkleTreeFromFile(new File(closestPlotPath));
            List<byte[]> proof = tree.getProofChallenge(challenge);
            //We build the proof with the sloth result and the merkle proof
            return new PosProof(tree.getSlothResult(), proof, challenge);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    //When a node receives a PoRep Challenge, read file from the Encoded Merkle Trees and build a proof of space
    //and Rep on the whole file, this way probablistically we can verify that the file was encoded and stored since
    // only valid miners can generate this tree sufficiently fast
    public static PosProof proofOfReplication(String fileFolder,byte[] challenge,byte[] publicKey){
        if(!new File(fileFolder).exists()){
            throw new RuntimeException("Plot Not Found");
        }
        File folder = new File(fileFolder);
        File[] listOfFiles = folder.listFiles();
        listOfFiles = removeFile(listOfFiles, ".PoRepTree");
        orderFileByName(listOfFiles);
        List<byte[]> byteData = new ArrayList<>();
        for (File file : listOfFiles) {
            MerkleTree tree = readMerkleTreeFromFile(file);
            List<byte[]> dataLeaves = tree.getAllLeaves(tree.getRoot());
            byteData.addAll(dataLeaves);
        }
        byte[] combined = combineByteArrays(byteData);
        MerkleTree tree = new MerkleTree(combined, LEAVES_POS,publicKey);
        List<byte[]> proof = tree.getProofChallenge(challenge);
        return new PosProof(tree.getSlothResult(), proof, challenge);
        //unpadding(desinationFolder + filename);
    }

    public static byte[] combineByteArrays(List<byte[]> byteData) {
        // Step 1: Calculate the total length of the resulting byte array
        int totalLength = 0;
        for (byte[] byteArray : byteData) {
            totalLength += byteArray.length;
        }

        // Step 2: Create a new byte array with the calculated length
        byte[] combined = new byte[totalLength];

        // Step 3: Copy each byte array into the combined byte array
        int currentPosition = 0;
        for (byte[] byteArray : byteData) {
            System.arraycopy(byteArray, 0, combined, currentPosition, byteArray.length);
            currentPosition += byteArray.length;
        }

        return combined;
    }
    private static boolean containsINProgress(File[] listOfFiles) {
        for (File file : listOfFiles) {
            if (file.getName().equals(IN_PROGRESS)) {
                return true;
            }
        }
        return false;
    }

    public static double proofQuality(PosProof proof, byte[] challenge,byte[]publicKey) {
        byte[] root = MerkleTree.rootFromProof(proof.getProof());
        double difficulty = 1.0;//Think about this later
        //Difficulty should be between 1 and 0 and the closest it is to zero the more difficult it is
        double maxDifference = challenge.length * 8;
        double difference = Utils.bitwiseDifference(MerkleTree.getSlothNonce(root, proof.getSlothResult().getHash(),publicKey),
                challenge);
        double normalizedDifference = 1 - (difference / maxDifference);
        // Adjust for difficulty
        double adjustedDifference = normalizedDifference * difficulty;
        return Math.min(1.0, Math.max(0.0, adjustedDifference));
    }

    public static boolean verifyProof(PosProof proof,byte[] publicKey) {
        byte[] root = MerkleTree.rootFromProof(proof.getProof());
        boolean validSloth = MySloth.verify(proof.getSlothResult(),
                new BigInteger(root).add(new BigInteger(publicKey)).toByteArray());
        boolean validMerklePath = MerkleTree.verifyProof(proof.getProof(), proof.getChallenge());
        return validSloth && validMerklePath;
    }


    public static void main(String[] args) throws DecoderException {
        plot_files("node4/originalFiles/hello.txt", "node4/originalFiles/plots/hello_txt","key".getBytes());
        plot_files("node4/originalFiles/test2.txt", "node4/originalFiles/plots/test2_txt","key".getBytes());
        //plot_files("node4/originalFiles/test_3.txt", "node4/originalFiles/plots/test3_txt");
        retrieveOriginal("node4/originalFiles/plots/hello_txt", "node4/originalFiles/",
            "test_retrieveddd.txt");
        byte[] randomChallenge = new byte[32];
        new SecureRandom().nextBytes(randomChallenge);
        //randomChallenge = Hex.decodeHex("13b0625e5cdd421f141752aaa0f1dc23e1154335d10e5ce7b5ee1d4eb9716450");
        System.out.println("Challenge: " + Hex.encodeHexString(randomChallenge));
        PosProof proof = proofOfSpace(randomChallenge, "node4/originalFiles/plots");
        System.out.println("Proof quality: " + proofQuality(proof, randomChallenge,"key".getBytes()));
        System.out.println("Proof valid: " + verifyProof(proof,"key".getBytes()));

        PosProof poRep = proofOfReplication("node4/originalFiles/plots/hello_txt",randomChallenge,"key".getBytes());
        System.out.println(MerkleTree.verifyProof(poRep.getProof(), poRep.getChallenge()));
    }

    @Getter
    @ToString
    public static class PosProof implements Serializable {
        private MySloth.SlothResult slothResult;
        private List<byte[]> proof;
        private byte[] slothNonce;
        private byte[] challenge;

        public PosProof(MySloth.SlothResult slothResult, List<byte[]> proof, byte[] challenge) {
            this.slothResult = slothResult;
            this.proof = proof;
            this.challenge = challenge;
        }
    }


    private static MerkleTree readMerkleTreeFromFile(File file){
        try {
            GZIPInputStream gzipin = new GZIPInputStream(new FileInputStream(file));
            ObjectInputStream ois = new ObjectInputStream(gzipin);
            MerkleTree tree = (MerkleTree) ois.readObject();
            ois.close();
            gzipin.close();
            return tree;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeMerkleTreeToFile(MerkleTree tree, String fileName){
        try {
            GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(fileName));
            ObjectOutputStream oos = new ObjectOutputStream(gos);
            oos.writeObject(tree);
            oos.close();
            gos.finish();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
