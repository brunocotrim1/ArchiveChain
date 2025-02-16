package fcul.ArchiveMint.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.codec.binary.Hex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static fcul.ArchiveMint.utils.PoS.CHUNK_SIZE;

public class PoDp {


    public static PDProof generateProofFromPlots(String plotPath, byte[] challenge) throws IOException {
        File folder = new File(plotPath);
        File[] directories = folder.listFiles();
        Utils.orderFileByName(directories);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        for (File file : directories) {
            MerkleTree merkleTree = MerkleTree.readMerkleTreeFromFile(file);
            List<byte[]> getLeaves = merkleTree.getAllLeaves(merkleTree.getRoot());
            for (byte[] leaf : getLeaves) {
                byteStream.write(leaf);
            }
        }
        MerkleTree merkleTree = new MerkleTree(byteStream.toByteArray());
        return new PDProof(merkleTree.getProofChallenge(challenge));
    }

    public static byte[] merkleRootFromOriginalFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(Files.readAllBytes(path));
        while (data.size() % CHUNK_SIZE != 0) {
            data.write('\n');
        }
        MerkleTree merkleTree = new MerkleTree(data.toByteArray());
        return merkleTree.getRoot().getData();
    }

    public static byte[] merkleRootFromData(byte[] fileData) {
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            data.write(fileData);
            while (data.size() % CHUNK_SIZE != 0) {
                data.write('\n');
            }
            MerkleTree merkleTree = new MerkleTree(data.toByteArray());
            return merkleTree.getRoot().getData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws IOException {
        Random random = new Random();
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        System.out.println("MerkleRoot from original " + Hex.encodeHexString(merkleRootFromOriginalFile("PoSTest/relatorio_preliminar.pdf")));
        PDProof pdp = generateProofFromPlots("PoSTest/plots/relatorio_preliminar.pdf", challenge);
        byte[] root = pdp.getProof().get(pdp.getProof().size() - 1);
        System.out.println("MerkleRoot from PoDp " + Hex.encodeHexString(root));
        assert Arrays.equals(root, merkleRootFromOriginalFile("PoSTest/relatorio_preliminar.pdf"));
    }

    @Getter
    @ToString
    @AllArgsConstructor
    public static class PDProof implements Serializable {
        private List<byte[]> proof;

        public PDProof(List<byte[]> proof, byte[] challenge) {
            this.proof = proof;
        }
    }
}
