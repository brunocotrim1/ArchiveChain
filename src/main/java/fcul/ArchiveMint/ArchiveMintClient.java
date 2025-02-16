package fcul.ArchiveMint;


import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.CurrencyTransaction;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Model.transactions.TransactionType;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.PoDp;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

public class ArchiveMintClient {

    public static String FCCNMnemonic = "phone poem two excuse lift surge power member ghost faith media ethics";
    public static String hostUrl = "http://localhost:8080/blockchain/archiveFile";

    public static void main(String[] args) throws Exception {
        String mnemonic = "ahead polar subject park cable prevent talk cover sick strike sound spirit";
        //mnemonic = FCCNMnemonic;
        System.out.println("Mnemonic: " + mnemonic);
        KeyPair keyPair = CryptoUtils.generateKeys(mnemonic);
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        // Output the keys
        System.out.println("Private Key: " + Hex.encodeHexString(privateKey.getEncoded()));
        System.out.println("Public Key: " + Hex.encodeHexString(publicKey.getEncoded()));
        System.out.println("Wallet Address: 0x" + Hex.encodeHexString(CryptoUtils.hash256(publicKey.getEncoded())));
        getCoins("e1c94c37ac886f5aeb7433c3bc4a5d088c7fc4ed1b69fe0d092c7a67e3d99897");
        //uploadFile("PoSTest/relatorio_preliminar.pdf","www.fcul.pt/relatorio_preliminar.pdf", hostUrl,
        //"e1c94c37ac886f5aeb7433c3bc4a5d088c7fc4ed1b69fe0d092c7a67e3d99897", privateKey);
        sendTransaction("551826f4fef79fe1d6bd0cf9ce4ecd1cfd3db4b171391d44a64b9a6678c0aa12",
                Hex.encodeHexString(publicKey.getEncoded()), privateKey, getCoins("e1c94c37ac886f5aeb7433c3bc4a5d088c7fc4ed1b69fe0d092c7a67e3d99897"), 1050);
    }

    public static List<Coin> getCoins(String address) {
        RestTemplate restTemplate = new RestTemplate();

        // Define the request URL with the parameter
        String url = "http://localhost:8080/blockchain/getCoins?address=" + address;

        // Make the GET request and retrieve the response
        List<Coin> coins = Arrays.asList(restTemplate.getForObject(url, Coin[].class));

        // Print result
        //coins.forEach(System.out::println);
        return coins;
    }

    public static void sendTransaction(String receiver, String senderPk, PrivateKey privateKey, List<Coin> coins, float amount) throws DecoderException {
        RestTemplate restTemplate = new RestTemplate();

        // Define the request URL
        String url = "http://localhost:8080/blockchain/sendCurrencyTransaction";
        // Setting headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        CurrencyTransaction transaction = CurrencyTransaction.builder()
                .receiverAddress(receiver)
                .senderAddress(CryptoUtils.getWalletAddress(senderPk))
                .senderPk(senderPk)
                .amount(BigInteger.valueOf((long) amount))
                .coins(coins.stream().map(Coin::getId).toList())
                .build();
        transaction.setType(TransactionType.CURRENCY_TRANSACTION);
        System.out.println(transaction.getCoins().size());
        byte[] signature = CryptoUtils.ecdsaSign(Hex.decodeHex(transaction.getTransactionId()), privateKey);
        transaction.setSignature(Hex.encodeHexString(signature));
        HttpEntity<Transaction> request = new HttpEntity<>(transaction, headers);
        // Making the POST request
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        System.out.println("Response: " + response.getBody());
    }

    public static void uploadFile(String filePath, String fileUrl, String hostUrl, String storerAddress, PrivateKey privateKey) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        File file = new File(filePath);
        FileSystemResource resource = new FileSystemResource(new File(filePath));
        StorageContract storageContract = getStorageContract(fileUrl, resource.getContentAsByteArray(), privateKey, storerAddress);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("ArchivalFile", resource);
        body.add("data", storageContract);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(hostUrl, HttpMethod.POST, requestEntity, String.class);
        System.out.println("Response: " + response.getBody());
    }

    public static StorageContract getStorageContract(String url, byte[] data, PrivateKey privateKey, String storerAddress) {
        byte[] merkleRoot = PoDp.merkleRootFromData(data);
        String merkleRootHex = Hex.encodeHexString(merkleRoot);
        StorageContract contract = StorageContract.builder()
                .merkleRoot(merkleRootHex)
                .value(BigInteger.valueOf(25))
                .fileUrl(url)
                .timestamp(BigInteger.valueOf(System.currentTimeMillis()))
                .storerAddress(storerAddress)
                .build();
        contract.setFccnSignature(Hex.encodeHexString(CryptoUtils.ecdsaSign(contract.getHash(), privateKey)));
        return contract;
    }
}
