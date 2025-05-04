package fcul.ArchiveMint.configuration;

import fcul.ArchiveMintUtils.Model.PeerRegistration;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.Utils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

@Component
@Data
@Slf4j
public class KeyManager {
    public static int RSA_BIT_SIZE = 2048;
    @Autowired
    NodeConfig nodeConfig;

    private PublicKey publicKey;
    private PrivateKey privateKey;

    @Value("${server.port}")
    private String port;

    @PostConstruct
    public void init() {
        if (Files.exists(Paths.get(nodeConfig.getStoragePath() + "/mnemonic.txt"))) {
            loadKeys();
            //registerFCCN();
            return;
        }
        //I will use ECDSA algoritmh since it provides similar security to RSA with smaller key size
        try {
            String mnemonic = CryptoUtils.generateMnemonic();
            KeyPair pair = CryptoUtils.generateKeys(mnemonic);
            try (
                    FileOutputStream fosPub = new FileOutputStream(nodeConfig.getStoragePath() + "/mnemonic.txt");

            ) {
                fosPub.write(mnemonic.getBytes());
                publicKey = pair.getPublic();
                privateKey = pair.getPrivate();
                String address = CryptoUtils.getWalletAddress(Hex.encodeHexString(publicKey.getEncoded()));
                System.out.println(Utils.GREEN + "Mnemonica Guardada: " + mnemonic + Utils.RESET);
                System.out.println(Utils.GREEN + "Endereço da carteira: https://archivechain.pt/wallet-details/" + address + Utils.RESET);
                //Save address into an empty file
                try (FileOutputStream fos = new FileOutputStream(nodeConfig.getStoragePath() + "/"+address+".txt")) {
                    String addressLine = "Adress: " + address + "\n";
                    fos.write(addressLine.getBytes());

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
            String address = CryptoUtils.getWalletAddress(Hex.encodeHexString(publicKey.getEncoded()));
            System.out.println(Utils.GREEN + "Loaded Mnemonic: " + mnemonic + Utils.RESET);
            System.out.println(Utils.GREEN + "address: " + address + Utils.RESET);
        } catch (IOException e) {
            throw new RuntimeException("Error loading keys from files", e);
        } catch (Exception e) {
            throw new RuntimeException("Error processing keys", e);
        }
    }

    public byte[] getFccnPublicKey() {
        try {
            return Hex.decodeHex(nodeConfig.getFccnPublicKey());
        } catch (DecoderException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    //Method should not be in this class, there should be a controller that calls this but its just for
    //simplicity of tests
    public boolean registerFCCN() {

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = nodeConfig.getFccnNetworkAddress() + "/storage/registerFarmer";
            String farmerAddress = nodeConfig.getFarmerAddress() + ":" + port;
            System.out.println(farmerAddress);
            PeerRegistration peer = PeerRegistration.builder()
                    .walletAddress(CryptoUtils.getWalletAddress(Hex.encodeHexString(publicKey.getEncoded())))
                    .dedicatedStorage(nodeConfig.getDedicatedStorage())
                    .publicKey(Hex.encodeHexString(publicKey.getEncoded()))
                    .networkAddress(farmerAddress)
                    .build();

            String toSign = peer.getNetworkAddress() + peer.getWalletAddress() + peer.getDedicatedStorage();
            byte[] signature = CryptoUtils.ecdsaSign(toSign.getBytes(), privateKey);
            peer.setSignature(Hex.encodeHexString(signature));
            HttpEntity<PeerRegistration> requestEntity = new HttpEntity<>(peer);

            // Send the POST request using RestTemplate
            ResponseEntity<Boolean> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }
}
