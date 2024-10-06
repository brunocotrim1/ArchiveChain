package fcul.ArchiveMint.utils;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

public class MySloth {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static BigInteger generatePrime(int bitLength) {
        Random random = new Random(123);
        BigInteger p;

        while (true) {
            // Step 1: Generate an n-bit prime number
            p = BigInteger.probablePrime(bitLength, random);
            // Step 2: Check if p % 4 == 3
            if (p.mod(BigInteger.valueOf(4)).equals(BigInteger.valueOf(3))) {
                return p; // Found a prime p such that p ≡ 3 mod 4
            }
            // Step 3: If not, add 2 and check again
            p = p.add(BigInteger.valueOf(2));
            // Step 4: Recheck if p is prime and satisfies p ≡ 3 mod 4
            if (p.isProbablePrime(100) && p.mod(BigInteger.valueOf(4)).equals(BigInteger.valueOf(3))) {
                return p;
            }
        }
    }

    public static byte[] hash(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    private static BigInteger sqrt_permutation(BigInteger input, BigInteger p, BigInteger e) {
        BigInteger tmp = BigInteger.ZERO;

        if (is_quadratic_residue(input, p)) {
            tmp = modular_exponentiation(input, e, p);
            if (tmp.mod(BigInteger.TWO).equals(BigInteger.ZERO)) {
                // y^2 = y^2 mod p
                return tmp;
            } else {
                // p - y^2 = y^2 mod p
                return p.subtract(tmp);
            }
        } else {
            tmp = p.subtract(input);
            tmp = modular_exponentiation(tmp, e, p);
            // tmp = (p-input)^e mod p
            if (tmp.mod(BigInteger.TWO).equals(BigInteger.ONE)) {
                return tmp;
            } else {
                return p.subtract(tmp);
            }
        }
    }
    public static SlothResult sloth(byte[]data, int iterations) {
        BigInteger p = generatePrime(256);
        byte[] hashedInput = hash(data);
        BigInteger x = new BigInteger(1,hashedInput).mod(p);
        BigInteger y = x;
        BigInteger e = p.add(BigInteger.ONE).shiftRight(2);
        for (int i = 1; i < iterations; i++) {
            y = sqrt_permutation(y, p, e);
        }

        return new SlothResult(y,iterations,p);
    }

    //Correctly Implemented and extracted from C original Code
    public static boolean verify(SlothResult result, byte[] data) {
        BigInteger p = result.p;
        BigInteger y = result.hash;
        BigInteger x = new BigInteger(1, hash(data)).mod(p);

        // Reverse the SLoth process
        for (int i = 1; i < result.iterations; i++) {
            // Determine the next value of y based on the previous value
            BigInteger ySquaredModP = y.multiply(y).mod(p);
            if (y.testBit(0)) {
                // p - y^2 = y^2 mod p
                y = p.subtract(ySquaredModP); // Odd case
            } else {
                // y^2 = y^2 mod p
                y = ySquaredModP; // Even case
            }
        }
        return y.equals(x);
    }
    //Correctly Implemented
    private static boolean is_quadratic_residue(BigInteger x, BigInteger p) {
        //Legendre symbol - Check if x is a quadratic residue modulo p y^2 = x mod p,check if exists a y
        BigInteger exponent = p.subtract(BigInteger.ONE).shiftRight(1);
        return x.modPow(exponent, p).equals(BigInteger.ONE);
    }


    private static BigInteger modular_exponentiation(BigInteger base, BigInteger exponent, BigInteger modulus) {
        //Modular Exponentiation - Calculate base^exponent mod modulus
        return base.modPow(exponent, modulus);
    }

    @Data
    @NoArgsConstructor
    public static class SlothResult implements Serializable {
        private BigInteger hash;
        private int iterations;
        private BigInteger p;
        public SlothResult(BigInteger h,int iterations,BigInteger p) {
            this.hash = h;
            this.iterations = iterations;
            this.p = p;
        }

    }

    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();
        for(int i = 0; i < 1; i++){
            byte[] data = new byte[128];
            System.out.println("Data: 0x" + new BigInteger(data).toString(16));
            int iterations = 900000;
            long time = System.currentTimeMillis();
            SlothResult result = sloth(data,iterations);
            System.out.println("Time too generate: " + (System.currentTimeMillis() - time) + "ms");
            time = System.currentTimeMillis();
            System.out.println(verify(result,data));
            if (!verify(result,data)){
                break;
            }
            System.out.println("Time too verify: " + (System.currentTimeMillis() - time) + "ms");
            System.out.println("Amount of bytes final result: "+result.hash.toByteArray().length);
            System.out.println("Final Hex hash: 0x" + result.hash.toString(16));
        }



    }
}
