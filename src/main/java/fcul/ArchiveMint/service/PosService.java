package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMintUtils.Utils.PoS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class PosService {
    public static final String PLOT_FOLDER = "plots";
    @Autowired
    NodeConfig nodeConfig;
    @Autowired
    KeyManager keyManager;

    public void plotFile(String fileName) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        PoS.plot_files(nodeConfig.getFilesToPlotPath() + "/" + fileName,
                nodeConfig.getStoragePath() + "/" + PLOT_FOLDER +
                        "/" + encodedFileName, keyManager.getPublicKey().getEncoded());

    }

    public void plotFileData(byte[] data, String fileName) throws IOException {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        String destinationFolder = nodeConfig.getStoragePath() + "/" + PLOT_FOLDER + "/" + encodedFileName;
        PoS.plot_files(data, destinationFolder, keyManager.getPublicKey().getEncoded());
    }

    public byte[] retrieveOriginalData(String filename){
        String encodedFileName = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return PoS.retrieveOriginal(nodeConfig.getStoragePath() + "/" + PLOT_FOLDER + "/" + encodedFileName);

    }

    public PoS.PosProof generatePoSProof(byte[] challenge) {
        return PoS.proofOfSpace(challenge, nodeConfig.getStoragePath() + "/" + PLOT_FOLDER);
    }

    public double proofQuality(PoS.PosProof proof, byte[] publicKey) {
        return PoS.proofQuality(proof, proof.getChallenge(), publicKey);
    }

    public boolean verifyProof(PoS.PosProof proof, byte[] challenge, byte[] publicKey) {
        return PoS.verifyProof(proof, challenge, publicKey);
    }
}
