package fcul.ArchiveMint.configuration;

import fcul.ArchiveMint.utils.CryptoUtils;
import fcul.ArchiveMint.utils.Utils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
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
        if(Files.exists(Paths.get(nodeConfig.getStoragePath()+ "/mnemonic.txt"))){
            loadKeys();
            return;
        }
        //I will use ECDSA algoritmh since it provides similar security to RSA with smaller key size
        try {
            String mnemonic = CryptoUtils.generateMnemonic();
            KeyPair pair = CryptoUtils.generateKeys(mnemonic);
            try (
                    FileOutputStream fosPub = new FileOutputStream(nodeConfig.getStoragePath()+ "/mnemonic.txt");

            ) {
                fosPub.write(mnemonic.getBytes());
                publicKey = pair.getPublic();
                privateKey = pair.getPrivate();
                String address =  Hex.encodeHexString(CryptoUtils.hash256(publicKey.getEncoded()));
                System.out.println(Utils.GREEN + "Saved Mnemonic: " + mnemonic + Utils.RESET);
                System.out.println(Utils.GREEN + "address: " + address + Utils.RESET);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void loadKeys() {
        try {
            String mnemonic = Files.readString(Paths.get(nodeConfig.getStoragePath() + "/mnemonic.txt"));
            KeyPair pair = CryptoUtils.generateKeys(mnemonic);
            publicKey = pair.getPublic();
            privateKey = pair.getPrivate();
            String address =  Hex.encodeHexString(CryptoUtils.hash256(publicKey.getEncoded()));
            System.out.println(Utils.GREEN + "Loaded Mnemonic: " + mnemonic + Utils.RESET);
            System.out.println(Utils.GREEN + "address: " + address + Utils.RESET);
        } catch (IOException e) {
            throw new RuntimeException("Error loading keys from files", e);
        } catch (Exception e) {
            throw new RuntimeException("Error processing keys", e);
        }
    }

    public byte[] getFccnPublicKey(){
        try {
            return Hex.decodeHex(nodeConfig.getFccnPublicKey());
        } catch (DecoderException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
