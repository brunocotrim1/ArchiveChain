package fcul.ArchiveMint.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.codec.binary.Hex;

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.*;

//Notes:
//At the end of construction we encode the tree using SLOTH
//The leaf nodes contain the hashes of the data and their right child contains the data node itself
@Data
@NoArgsConstructor
public class MerkleTree implements Serializable {

    public static int FANOUT = 2;
    public int LEAVES;
    private static int SLOTH_ITERATIONS = 20; //45000Approx 1.3ms per Block which matches Chia generation time
    private MerkleNode root;
    private MySloth.SlothResult slothResult;


    public MerkleTree(byte[] data, int LEAVES,byte[] publicKey) {
        this.LEAVES = LEAVES;
        List<byte[]> dataLeaves = splintDataInLeaves(data);
        if (dataLeaves.size() != LEAVES ) {
            throw new IllegalArgumentException("Invalid number of leaves");
        }
        List<MerkleNode> leaves = buildTreeLeavesFromData(dataLeaves);

        this.root = buildTreeRoot(leaves);
        slothResult = MySloth.sloth(new BigInteger(root.data).add(new BigInteger(publicKey)).toByteArray(),
                SLOTH_ITERATIONS);
    }

    public MerkleTree(List<byte[]> data, int LEAVES,byte[] publicKey) {
        this.LEAVES = LEAVES;
        if (data.size() != LEAVES) {
            throw new IllegalArgumentException("Invalid number of leaves");
        }
        List<MerkleNode> leaves = buildTreeLeavesFromData(data);

        this.root = buildTreeRoot(leaves);
        long time = System.currentTimeMillis();
        slothResult = MySloth.sloth(new BigInteger(root.data).add(new BigInteger(publicKey)).toByteArray(),
                SLOTH_ITERATIONS);
    }
    @JsonIgnore
    public byte[] getSlothNonce(byte[] publicKey) {
        BigInteger sloth = slothResult.getHash();
        BigInteger root = new BigInteger(this.root.data);
        BigInteger nonce = root.add(sloth);
        //nonce.add(new BigInteger(publicKey));
        return CryptoUtils.hash256(nonce.toByteArray());
    }
    @JsonIgnore
    public static byte[] getSlothNonce(byte[] root, BigInteger sloth,byte[] publicKey) {
        BigInteger rootInt = new BigInteger(root);
        BigInteger nonce = rootInt.add(sloth);
        //nonce.add(new BigInteger(publicKey));
        return CryptoUtils.hash256(nonce.toByteArray());
    }

    private List<byte[]> splintDataInLeaves(byte[] data) {
        List<byte[]> dataLeaves = new ArrayList<>();
        int chunkSize = data.length / LEAVES;
        for (int i = 0; i < LEAVES; i++) {
            byte[] chunk = Arrays.copyOfRange(data, i * chunkSize, (i + 1) * chunkSize);
            dataLeaves.add(chunk);
        }
        return dataLeaves;
    }

    public static byte[] rootFromProof(List<byte[]> proof) {
        return proof.get(proof.size() - 1);
    }

    public static byte[] dataFromProof(List<byte[]> proof) {
        return proof.get(0);
    }

    public static boolean verifyProof(List<byte[]> proof, byte[] challenge) {
        int index = new BigInteger(1, challenge).mod(BigInteger.valueOf(64)).intValue();
        int leafHashIndex1 = Utils.byteArrayToInt(Utils.getLastNBytes(proof.get(1), 4), ByteOrder.BIG_ENDIAN);
        int leafHashIndex2 = Utils.byteArrayToInt(Utils.getLastNBytes(proof.get(2), 4), ByteOrder.BIG_ENDIAN);
        if(leafHashIndex1 != index && leafHashIndex2 != index){
            //The leafHashes contain a suffix of the leaf index, this helps verifying that the targeted inted was respected
            //and also help with second preimage resistance, we just need to verify the suffix of both of the hashes
            return false;
        }
        byte[] root = proof.get(proof.size() - 1);
        byte[] data = CryptoUtils.hash256(proof.get(0));//Possibly exclude data from the proof to save space

        byte[] temp = CryptoUtils.hash256(add(proof.get(1), proof.get(2)));
        for (int i = 3; i < proof.size() - 1; i++) {
            temp = CryptoUtils.hash256(add(temp, proof.get(i)));
        }
        if (Arrays.equals(temp, root)) {
            return true;
        }
        return false;
    }

    public List<byte[]> getProof(byte[] data) {
        List<Map.Entry> proofStore = new ArrayList<>();
        buildMerkleProof(data, root, proofStore, 0);
        quickSortReverse(proofStore, 0, proofStore.size() - 1);
        //System.out.println(proofStore); Helps debugging since it prints the levels and the hash for each level
        //each level only contains 1 hash except for the last level the hashed data and the neighbor to recompute
        return proofStore.stream().map(entry -> ((MerkleNode) entry.getKey()).getData()).toList();
    }

    public List<byte[]> getProof(int index) {
        List<Map.Entry> proofStore = new ArrayList<>();
        buildMerkleProof(index, root, proofStore, 0);
        quickSortReverse(proofStore, 0, proofStore.size() - 1);
        //System.out.println(proofStore); //Helps debugging since it prints the levels and the hash for each level
        //each level only contains 1 hash except for the last level the hashed data and the neighbor to recompute
        return proofStore.stream().map(entry -> ((MerkleNode) entry.getKey()).getData()).toList();
    }

    public List<byte[]> getProofChallenge(byte[] challenge) {
        BigInteger index = new BigInteger(1, challenge).mod(BigInteger.valueOf(LEAVES));
        return getProof(index.intValue());
    }


    public void buildMerkleProof(byte[] data, MerkleNode node, List<Map.Entry> proofStore, int level) {
        if (node == null) {
            return;
        }
        if (Arrays.equals(node.getData(), data)) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node, level));
            return;
        }
        if (node.getLeft() != null && Arrays.equals(node.getLeft().getData(), CryptoUtils.hash256(data))) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node.getLeft(), level + 1));
        }
        if (node.getRight() != null && Arrays.equals(node.getRight().getData(), CryptoUtils.hash256(data))) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node.getRight(), level + 1));
        }


        if (!subTreeContainsData(data, node)) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node, level));
            return;
        }
        buildMerkleProof(data, node.getLeft(), proofStore, level + 1);
        buildMerkleProof(data, node.getRight(), proofStore, level + 1);
        if (node == root) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node, level));
        }
    }

    public void buildMerkleProof(int index, MerkleNode node, List<Map.Entry> proofStore, int level) {
        if (node == null) {
            return;
        }

        if (node.getLeafIndex() == index) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node, level));
            proofStore.add(new AbstractMap.SimpleEntry<>(node.getRight(), level + 1));
            return;
        }
        if (!subTreeContainsIndex(index, node)) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node, level));
            return;
        }
        buildMerkleProof(index, node.getLeft(), proofStore, level + 1);
        buildMerkleProof(index, node.getRight(), proofStore, level + 1);
        if (node == root) {
            proofStore.add(new AbstractMap.SimpleEntry<>(node, level));
        }
    }

    private boolean alreadyContainsData(int level, List<Map.Entry> proofStore) {
        for (Map.Entry entry : proofStore) {
            if ((int) entry.getValue() == level) {
                return true;
            }
        }
        return false;
    }

    private boolean subTreeContainsIndex(int index, MerkleNode node) {
        if (node == null) {
            return false;
        }
        if (node.getLeafIndex() == index) {
            return true;
        }
        return subTreeContainsIndex(index, node.getLeft()) || subTreeContainsIndex(index, node.getRight());
    }

    private boolean subTreeContainsData(byte[] data, MerkleNode node) {
        if (node == null) {
            return false;
        }
        if (Arrays.equals(node.getData(), data)) {
            return true;
        }
        return subTreeContainsData(data, node.getLeft()) || subTreeContainsData(data, node.getRight());
    }


    private static MerkleNode buildTreeRoot(List<MerkleNode> leaves) {
        List<MerkleNode> parents = new ArrayList<>();
        while (leaves.size() > 1) {
            parents = new ArrayList<>();
            for (int i = 0; i < leaves.size(); i += FANOUT) {
                MerkleNode left = leaves.get(i);
                MerkleNode right = null;
                if (i + 1 < leaves.size()) {
                    right = leaves.get(i + 1);
                }
                byte[] hash = null;
                if(right == null){
                    hash = CryptoUtils.hash256(left.getData());
                    MerkleNode parent = new MerkleNode(hash, left, null);
                }else {
                    hash = CryptoUtils.hash256(add(left.getData(), right.getData()));
                }
                MerkleNode parent = new MerkleNode(hash, left, right);
                parents.add(parent);
            }
            leaves = parents;
        }
        return parents.get(0);
    }

    private static byte[] add(byte[] a, byte[] b) {
        BigInteger aInt = new BigInteger(1, a);
        BigInteger bInt = new BigInteger(1, b);

        return aInt.add(bInt).toByteArray();
    }


    private static List<MerkleNode> buildTreeLeavesFromData(List<byte[]> data) {
        List<MerkleNode> leaves = new ArrayList<>();
        short leaf = 0;
        for (byte[] d : data) {
            MerkleNode dataNode = new MerkleNode(d);
            byte[] hash = CryptoUtils.hash256(d);
            hash = Utils.concatenateByteArrays(hash,Utils.intToByteArray(leaf, ByteOrder.BIG_ENDIAN));
            //Store data Nodes on the right child node of leaf nodes
            MerkleNode hashNode = new MerkleNode(hash, null, dataNode);
            hashNode.setLeafIndex(leaf);
            leaves.add(hashNode);
            leaf++;
        }
        return leaves;
    }

    public List<byte[]> getAllLeaves(MerkleNode root) {
        List<byte[]> leaves = new ArrayList<>();
        if (root == null) {
            return leaves;
        }
        if (root.getLeft() == null && root.getRight() == null) {
            leaves.add(root.data);
        }
        leaves.addAll(getAllLeaves(root.getLeft()));
        leaves.addAll(getAllLeaves(root.getRight()));
        return leaves;
    }

    @Data
    @NoArgsConstructor
    public static class MerkleNode implements Serializable {
        private byte[] data;
        private MerkleNode left;
        private MerkleNode right;
        private short leafIndex = -1;

        public MerkleNode(byte[] data) {
            this.data = data;
        }

        public MerkleNode(byte[] data, MerkleNode left, MerkleNode right) {
            this.data = data;
            this.left = left;
            this.right = right;
        }

        public MerkleNode(byte[] data, MerkleNode left, MerkleNode right, short leafIndex) {
            this.data = data;
            this.left = left;
            this.right = right;
            this.leafIndex = leafIndex;
        }

        public String toString() {
            return Hex.encodeHexString(data);
        }
    }


    // QuickSort method
    public static void quickSortReverse(List<Map.Entry> list, int low, int high) {
        if (low < high) {
            int pivotIndex = partition(list, low, high);
            quickSortReverse(list, low, pivotIndex - 1);
            quickSortReverse(list, pivotIndex + 1, high);
        }
    }

    // Partition method
    private static int partition(List<Map.Entry> list, int low, int high) {
        // Pivot is the value at the high index
        Map.Entry<String, Integer> pivot = list.get(high);
        int i = low - 1;

        for (int j = low; j < high; j++) {
            int value = (int) list.get(j).getValue();
            if (value > pivot.getValue()) {
                i++;
                // Swap list[i] and list[j]
                swap(list, i, j);
            }
        }

        // Swap list[i + 1] and list[high] (pivot)
        swap(list, i + 1, high);
        return i + 1;
    }

    // Swap method
    private static void swap(List<Map.Entry> list, int i, int j) {
        Map.Entry temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }

    public void printTreeByLevels() {
        if (root == null) {
            System.out.println("Tree is empty");
            return;
        }

        Queue<MerkleNode> nodeQueue = new LinkedList<>();
        Queue<Integer> levelQueue = new LinkedList<>();

        nodeQueue.add(root);
        levelQueue.add(0);

        int currentLevel = 0;
        StringBuilder levelOutput = new StringBuilder();

        while (!nodeQueue.isEmpty()) {
            MerkleNode node = nodeQueue.poll();
            int level = levelQueue.poll();

            if (level > currentLevel) {
                System.out.println("Level " + currentLevel + ": " + levelOutput.toString());
                levelOutput.setLength(0);
                currentLevel = level;
            }

            levelOutput.append(node.toString()).append(" ");

            if (node.getLeft() != null) {
                nodeQueue.add(node.getLeft());
                levelQueue.add(level + 1);
            }

            if (node.getRight() != null) {
                nodeQueue.add(node.getRight());
                levelQueue.add(level + 1);
            }
        }

        // Print the last level
        if (levelOutput.length() > 0) {
            System.out.println("Level " + currentLevel + ": " + levelOutput.toString());
        }
    }

/*    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        List<byte[]> data = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            String s = "Test " + i;
            data.add(s.getBytes());
        }
        MerkleTree tree = new MerkleTree(data,64);
        System.out.println("Proof: " + tree.getProof(5));
        System.out.println("Verify: " + verifyProof(tree.getProof(5)));
        System.out.println("Verify: " + verifyProof(tree.getProof("Test 5".getBytes())));
        System.out.println("Time to build tree: " + (System.currentTimeMillis() - start) + "ms");
    }*/
}
