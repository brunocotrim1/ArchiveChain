package fcul.ArchiveMint.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CryptoUtils {
    public static byte[] hash256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
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
