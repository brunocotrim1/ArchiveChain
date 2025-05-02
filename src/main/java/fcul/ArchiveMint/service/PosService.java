package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMintUtils.Model.FileProof;
import fcul.ArchiveMintUtils.Model.FileProvingWindow;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Utils.PoDp;
import fcul.ArchiveMintUtils.Utils.PoS;
import fcul.ArchiveMintUtils.Utils.Utils;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

@Service
public class PosService {
    public static final String PLOT_FOLDER = "plots";
    @Autowired
    NodeConfig nodeConfig;
    @Autowired
    KeyManager keyManager;

    public void init() throws Exception {
        plotFileData("BOOTSTRAP".getBytes(), "BOOTSTRAP.txt");
    }

    public void plotFile(String fileName) throws Exception {
        String normalizedFileName = Normalizer.normalize(fileName, Normalizer.Form.NFC);
        fileName = encodeURIComponent(normalizedFileName);
        PoS.plot_files(nodeConfig.getFilesToPlotPath() + "/" + fileName,
                nodeConfig.getStoragePath() + "/" + PLOT_FOLDER +
                        "/" + fileName, keyManager.getPublicKey().getEncoded());

    }

    public void plotFileData(byte[] data, String fileName) throws Exception {
        String normalizedFileName = Normalizer.normalize(fileName, Normalizer.Form.NFC);
        fileName = encodeURIComponent(normalizedFileName);
        //System.out.println(Utils.GREEN + "Plotting file " + fileName + Utils.RESET);
        String destinationFolder = nodeConfig.getStoragePath() + "/" + PLOT_FOLDER + "/" + fileName;
        PoS.plot_FilesParallel(data, destinationFolder, keyManager.getPublicKey().getEncoded());
    }

    public byte[] retrieveOriginalData(String filename) throws Exception {
        filename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
        filename = Normalizer.normalize(filename, Normalizer.Form.NFC);
        filename = encodeURIComponent(filename);
        System.out.println("Retrieving original data from " + nodeConfig.getStoragePath() + "/" + PLOT_FOLDER + "/" + filename);
        return PoS.retrieveOriginalParallel(nodeConfig.getStoragePath() + "/" + PLOT_FOLDER + "/" + filename);

    }

    public PoS.PosProof generatePoSProof(byte[] challenge) throws Exception {
        return PoS.proofOfSpace(challenge, nodeConfig.getStoragePath() + "/" + PLOT_FOLDER);
    }

    public double proofQuality(PoS.PosProof proof, byte[] publicKey) {
        return PoS.proofQuality(proof, proof.getChallenge(), publicKey);
    }

    public boolean verifyProof(PoS.PosProof proof, byte[] challenge, byte[] publicKey) {
        return PoS.verifyProof(proof, challenge, publicKey);
    }

    public FileProof generateFileProof(FileProvingWindow fileProvingWindow) throws Exception {
        StorageContract storageContract = fileProvingWindow.getContract();
        String normalizedFileName = Normalizer.normalize(storageContract.getFileUrl(), Normalizer.Form.NFC);
        normalizedFileName = encodeURIComponent(normalizedFileName);
        String plotPath = nodeConfig.getStoragePath() + "/" + PLOT_FOLDER + "/" + normalizedFileName;
        byte[] challenge = Hex.decode(fileProvingWindow.getPoDpChallenge());
        List<byte[]> pdpProof = PoDp.generateProofFromPlots(plotPath, challenge);
        return FileProof.builder()
                .merkleProof(pdpProof)
                .storageContractHash(Hex.toHexString(storageContract.getHash()))
                .fileUrl(storageContract.getFileUrl())
                .PoDpChallenge(fileProvingWindow.getPoDpChallenge())
                .startBlockIndex(fileProvingWindow.getStartBlockIndex())
                .endBlockIndex(fileProvingWindow.getEndBlockIndex())
                .build();
    }

    public boolean verifyFileProof(FileProof fileProof, byte[] challenge, byte[] root, int fileLength) {
        try {
            byte[] challengeProof = Hex.decode(fileProof.getPoDpChallenge());
            if (!Arrays.equals(challenge, challengeProof)) {
                System.out.println("Challenge does not match");
                return false;
            }
            return PoDp.verifyPdp(fileProof.getMerkleProof(), challenge, root, fileLength);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private static String encodeURIComponent(String s)
    {
        String result = null;

        result = URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~");
        return result;
    }
}
