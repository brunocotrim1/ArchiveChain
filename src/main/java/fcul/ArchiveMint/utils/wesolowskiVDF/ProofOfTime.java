package fcul.ArchiveMint.utils.wesolowskiVDF;

import fcul.ArchiveMint.utils.CryptoUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigInteger;
@Data
@ToString
public class ProofOfTime implements java.io.Serializable{
    private BigInteger proof;
    private BigInteger lPrime;
    private int T;
    private byte[] publicKeyTimelord;
    private byte[] signature;
    public ProofOfTime() {
    }
    public ProofOfTime(BigInteger proof, BigInteger lPrime, int T) {
        this.proof = proof;
        this.lPrime = lPrime;
        this.T = T;
    }

    public byte[] hash(){
        BigInteger hash = proof.add(lPrime).mod(BigInteger.valueOf(T)).add(new BigInteger(publicKeyTimelord));
        return CryptoUtils.hash256(hash.toByteArray());
    }
}