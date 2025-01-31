package fcul.ArchiveMint.utils;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Protos;

import java.math.BigInteger;
import java.security.*;
import java.util.List;

public class CryptoUtils {
    public static byte[] hash256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA3-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] ecdsaSign(byte[] data, PrivateKey privateKey) {
        try {
            java.security.Signature ecdsa = java.security.Signature.getInstance("SHA256withECDSA");
            ecdsa.initSign(privateKey);
            ecdsa.update(data);
            return ecdsa.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int compareTo(byte [] a, byte [] b){
        return new BigInteger(a).compareTo(new BigInteger(b));
    }

    public static boolean ecdsaVerify(byte[] signature, byte[] data, byte[] publicKey) {
        try {
            java.security.Signature ecdsa = java.security.Signature.getInstance("SHA256withECDSA");
            ecdsa.initVerify(java.security.KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(publicKey)));
            ecdsa.update(data);
            return ecdsa.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateMnemonic() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        byte[] entropy = new byte[16];  // 128-bit entropy for 12-word mnemonic
        secureRandom.nextBytes(entropy);

        MnemonicCode mnemonicCode = new MnemonicCode();
        List<String> mnemonicWords = mnemonicCode.toMnemonic(entropy);
        return String.join(" ", mnemonicWords);
    }

    public static KeyPair generateClientKeys(){
        try {
            String mnemonic = generateMnemonic();
            byte[] seed = mnemonic.getBytes();
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(seed);  // Provide the custom seed
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256, random);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }




    public static String base64Encode(byte[] input) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }
    public static byte[] base64Decode(String input) {
        return java.util.Base64.getDecoder().decode(input);
    }
}
