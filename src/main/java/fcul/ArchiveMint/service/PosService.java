package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMint.utils.PoS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import fcul.ArchiveMint.utils.PoS.PosProof;

@Service
public class PosService {
    public static final String PLOT_FOLDER = "plots";
    @Autowired
    NodeConfig nodeConfig;
    @Autowired
    KeyManager keyManager;

    public boolean plotFile(String fileName) {
        try {
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8");
            PoS.plot_files(nodeConfig.getFilesToPlotPath()+"/"+fileName,
                    nodeConfig.getStoragePath()+"/"+ PLOT_FOLDER+
                            "/"+encodedFileName, keyManager.getPublicKey().getEncoded());

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public PosProof generatePoSProof(byte[] challenge){
        return PoS.proofOfSpace(challenge,nodeConfig.getStoragePath() + "/" + PLOT_FOLDER);
    }

    public double proofQuality(PosProof proof,byte[] publicKey){
        return PoS.proofQuality(proof, proof.getChallenge(), publicKey);
    }

    public boolean verifyProof(PosProof proof,byte[] challenge,byte[] publicKey){
        return PoS.verifyProof(proof, challenge,publicKey);
    }
}
