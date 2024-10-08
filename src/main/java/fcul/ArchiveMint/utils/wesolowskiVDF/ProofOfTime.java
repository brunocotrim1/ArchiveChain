package fcul.ArchiveMint.utils.wesolowskiVDF;

import lombok.Data;

import java.math.BigInteger;
@Data
public class ProofOfTime {
    private final BigInteger proof;
    private final BigInteger lPrime;
    private int T;

    public ProofOfTime(BigInteger proof, BigInteger lPrime, int T) {
        this.proof = proof;
        this.lPrime = lPrime;
        this.T = T;
    }
}