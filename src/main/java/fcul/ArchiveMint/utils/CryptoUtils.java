package fcul.ArchiveMint.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

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

    public static String base64Encode(byte[] input) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }
    public static byte[] base64Decode(String input) {
        return java.util.Base64.getDecoder().decode(input);
    }
}
