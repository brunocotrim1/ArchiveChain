package fcul.ArchiveMint;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.crypto.MnemonicCode;

import java.security.*;
import java.security.cert.Certificate;
import java.security.KeyStore;
import java.io.*;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import javax.crypto.*;

public class ArchiveMintClient{

    public static void main(String[] args) throws Exception {
        String mnemonic = generateMnemonic();
        byte[] seed = mnemonic.getBytes();
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(seed);  // Provide the custom seed
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256, random);  // Use 256 bits of security (e.g., P-256 curve)
        // Generate the EC key pair
        KeyPair keyPair = keyGen.generateKeyPair();

        // Extract private and public keys
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Output the keys
        System.out.println("Mnemonic: " + mnemonic);
        System.out.println("Private Key: " + Hex.encodeHexString(privateKey.getEncoded()));
        System.out.println("Public Key: " + Hex.encodeHexString(publicKey.getEncoded()));
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
