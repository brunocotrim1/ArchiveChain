package fcul.ArchiveMint.utils.wesolowskiVDF;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
@Data
public class ProofOfTime{
    private BigInteger proof;
    private BigInteger lPrime;
    private int T;

    public ProofOfTime() {
    }
    public ProofOfTime(BigInteger proof, BigInteger lPrime, int T) {
        this.proof = proof;
        this.lPrime = lPrime;
        this.T = T;
    }
}