package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMintUtils.Model.FileProof;
import fcul.ArchiveMintUtils.Model.FileProvingWindow;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Utils.PoDp;
import fcul.ArchiveMintUtils.Utils.PoS;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    @PostConstruct
    public void init() throws Exception {
        //Initial Plotting to boostrap the network
        plotFile("test_3.txt");
    }

    public void plotFile(String fileName) throws Exception {
        String normalizedFileName = Normalizer.normalize(fileName, Normalizer.Form.NFC);
        fileName = URLEncoder.encode(normalizedFileName, StandardCharsets.UTF_8);
        PoS.plot_files(nodeConfig.getFilesToPlotPath() + "/" + fileName,
                nodeConfig.getStoragePath() + "/" + PLOT_FOLDER +
                        "/" + fileName, keyManager.getPublicKey().getEncoded());

    }

    public void plotFileData(byte[] data, String fileName) throws Exception {
        String normalizedFileName = Normalizer.normalize(fileName, Normalizer.Form.NFC);
        fileName = URLEncoder.encode(normalizedFileName, StandardCharsets.UTF_8);
        String destinationFolder = nodeConfig.getStoragePath() + "/" + PLOT_FOLDER + "/" + fileName;
        PoS.plot_FilesParallel(data, destinationFolder, keyManager.getPublicKey().getEncoded());
    }

    public byte[] retrieveOriginalData(String filename) throws Exception {
        //String encodedFileName = URLEncoder.encode(filename, StandardCharsets.UTF_8);
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
        normalizedFileName = URLEncoder.encode(normalizedFileName, StandardCharsets.UTF_8);
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

    public boolean verifyFileProof(FileProof fileProof, byte[] challenge, byte[] root) {
        try {
            byte[] challengeProof = Hex.decode(fileProof.getPoDpChallenge());
            if(!Arrays.equals(challenge, challengeProof)){
                System.out.println("Challenge does not match");
                return false;
            }
            return PoDp.verifyPdp(fileProof.getMerkleProof(), challenge, root);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
