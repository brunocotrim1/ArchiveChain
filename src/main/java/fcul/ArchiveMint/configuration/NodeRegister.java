package fcul.ArchiveMint.configuration;

import fcul.ArchiveMintUtils.Model.PeerRegistration;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NodeRegister {
    @Autowired
    private NodeConfig nodeConfig;
    @Autowired
    private KeyManager keyManager;
    public boolean registerFCCN() {

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = nodeConfig.getFccnNetworkAddress() + "/storage/registerFarmer";
            String farmerAddress = nodeConfig.getFarmerAddress() + ":" + keyManager.getPort();
            System.out.println(farmerAddress);
            PeerRegistration peer = PeerRegistration.builder()
                    .walletAddress(CryptoUtils.getWalletAddress(Hex.encodeHexString(keyManager.getPublicKey().getEncoded())))
                    .dedicatedStorage(nodeConfig.getDedicatedStorage())
                    .publicKey(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                    .networkAddress(farmerAddress)
                    .fillStorageNow(nodeConfig.isInitializeStorage())
                    .build();

            String toSign = peer.getNetworkAddress() + peer.getWalletAddress() + peer.getDedicatedStorage();
            byte[] signature = CryptoUtils.ecdsaSign(toSign.getBytes(), keyManager.getPrivateKey());
            peer.setSignature(Hex.encodeHexString(signature));
            HttpEntity<PeerRegistration> requestEntity = new HttpEntity<>(peer);

            // Send the POST request using RestTemplate
            ResponseEntity<Boolean> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean requestMoreStorage(long amount) {
        try{
            nodeConfig.setDedicatedStorage(nodeConfig.getDedicatedStorage() + amount);
            RestTemplate restTemplate = new RestTemplate();
            String url = nodeConfig.getFccnNetworkAddress() + "/storage/requestMoreFiles";
            System.out.println("Requesting more storage from FCCN: " + amount);
            String farmerAddress = nodeConfig.getFarmerAddress() + ":" + keyManager.getPort();
            PeerRegistration peer = PeerRegistration.builder()
                    .walletAddress(CryptoUtils.getWalletAddress(Hex.encodeHexString(keyManager.getPublicKey().getEncoded())))
                    .dedicatedStorage(amount)
                    .publicKey(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                    .networkAddress(farmerAddress)
                    .fillStorageNow(nodeConfig.isInitializeStorage())
                    .build();

            String toSign = peer.getNetworkAddress() + peer.getWalletAddress() + peer.getDedicatedStorage();
            byte[] signature = CryptoUtils.ecdsaSign(toSign.getBytes(), keyManager.getPrivateKey());
            peer.setSignature(Hex.encodeHexString(signature));
            HttpEntity<PeerRegistration> requestEntity = new HttpEntity<>(peer);

            // Send the POST request using RestTemplate
            ResponseEntity<Boolean> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }
}
