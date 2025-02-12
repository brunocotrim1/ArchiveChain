package fcul.ArchiveMint;

import fcul.ArchiveMint.model.Coin;
import fcul.ArchiveMint.model.transactions.CurrencyTransaction;
import fcul.ArchiveMint.model.transactions.Transaction;
import fcul.ArchiveMint.model.transactions.TransactionType;
import fcul.ArchiveMint.utils.CryptoUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.crypto.MnemonicCode;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.*;
import java.util.Arrays;
import java.util.List;

public class ArchiveMintClient{

    public static void main(String[] args) throws Exception {
        String mnemonic = "ahead polar subject park cable prevent talk cover sick strike sound spirit";
        KeyPair keyPair = CryptoUtils.generateKeys(mnemonic);
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        // Output the keys
        System.out.println("Mnemonic: " + mnemonic);
        System.out.println("Private Key: " + Hex.encodeHexString(privateKey.getEncoded()));
        System.out.println("Public Key: " + Hex.encodeHexString(publicKey.getEncoded()));
        System.out.println("Wallet Address: 0x" +  Hex.encodeHexString(CryptoUtils.hash256(publicKey.getEncoded())));
        getCoins("e1c94c37ac886f5aeb7433c3bc4a5d088c7fc4ed1b69fe0d092c7a67e3d99897");
        sendTransaction("551826f4fef79fe1d6bd0cf9ce4ecd1cfd3db4b171391d44a64b9a6678c0aa12",
                Hex.encodeHexString(publicKey.getEncoded()), privateKey, getCoins("e1c94c37ac886f5aeb7433c3bc4a5d088c7fc4ed1b69fe0d092c7a67e3d99897"), 1000);
    }
    public static List<Coin> getCoins(String address){
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

        for(Coin coin : coins){
            // Setting headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            CurrencyTransaction transaction = CurrencyTransaction.builder()
                    .receiverAddress(receiver)
                    .senderAddress(CryptoUtils.getWalletAddress(senderPk))
                    .senderPk(senderPk)
                    .amount(BigInteger.valueOf((long) amount))
                    .coins(List.of(coin.getId()))
                    .build();
            transaction.setType(TransactionType.CURRENCY_TRANSACTION);
            System.out.println("Sending coin: " + coin.getId());
            byte[] signature = CryptoUtils.ecdsaSign(Hex.decodeHex(transaction.getTransactionId()), privateKey);
            transaction.setSignature(Hex.encodeHexString(signature));


            HttpEntity<Transaction> request = new HttpEntity<>(transaction, headers);

            // Making the POST request
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            System.out.println("Response: " + response.getBody());
        }
        if (true){
            return;
        }

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

    // Generate a BIP39 mnemonic (12 words)
    public static String generateMnemonic() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        byte[] entropy = new byte[16];  // 128-bit entropy for 12-word mnemonic
        secureRandom.nextBytes(entropy);
        MnemonicCode mnemonicCode = new MnemonicCode();
        List<String> mnemonicWords = mnemonicCode.toMnemonic(entropy);
        return String.join(" ", mnemonicWords);
    }
}
