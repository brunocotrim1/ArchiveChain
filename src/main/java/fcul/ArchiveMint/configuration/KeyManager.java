package fcul.ArchiveMint.configuration;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
@Data
@Slf4j
public class KeyManager {
    public static int RSA_BIT_SIZE = 2048;
    @Autowired
    NodeConfig nodeConfig;

    private PublicKey publicKey;
    private PrivateKey privateKey;
    @PostConstruct
    public void init() {
        if(Files.exists(Paths.get(nodeConfig.getStoragePath()+ "/private.key"))){
            loadKeys();
            return;
        }
        KeyPairGenerator generator = null;
        //I will use ECDSA algoritmh since it provides similar security to RSA with smaller key size
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            keyGen.initialize(256, random);
            KeyPair pair = keyGen.generateKeyPair();
            try (
                    FileOutputStream fosPub = new FileOutputStream(nodeConfig.getStoragePath()+ "/public.key");
                    FileOutputStream fosPriv = new FileOutputStream(nodeConfig.getStoragePath()+ "/private.key")

            ) {
                fosPub.write(pair.getPublic().getEncoded());
                fosPriv.write(pair.getPrivate().getEncoded());
                publicKey = pair.getPublic();
                privateKey = pair.getPrivate();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadKeys() {
        try {
            // Load public key
            byte[] publicKeyBytes = Files.readAllBytes(Paths.get(nodeConfig.getStoragePath() + "/public.key"));
            X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            publicKey = keyFactory.generatePublic(publicSpec);

            // Load private key
            byte[] privateKeyBytes = Files.readAllBytes(Paths.get(nodeConfig.getStoragePath() + "/private.key"));
            PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            privateKey = keyFactory.generatePrivate(privateSpec);
        } catch (IOException e) {
            throw new RuntimeException("Error loading keys from files", e);
        } catch (Exception e) {
            throw new RuntimeException("Error processing keys", e);
        }
    }
}
