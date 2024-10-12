package fcul.ArchiveMint.model;

import fcul.ArchiveMint.utils.CryptoUtils;
import fcul.ArchiveMint.utils.PoS;
import fcul.ArchiveMint.utils.wesolowskiVDF.ProofOfTime;
import lombok.*;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Block {
    private byte[] previousHash;
    private String timeStamp;
    private List<Transaction> transactions;
    private byte[] signature;
    private PoS.PosProof posProof;
    private ProofOfTime potProof;
    private long blockHeight;
    private byte[] minerPublicKey;

    public void addProofOfTime(ProofOfTime potProof){
        this.potProof = potProof;
    }

    public byte[] calculateHash() {
        BigInteger hash = BigInteger.ZERO;
        hash = hash.add(new BigInteger(this.previousHash));
        hash = hash.add(new BigInteger(this.timeStamp.getBytes()));
        //INCLUIR TRANSACTIONS
        hash = hash.add(new BigInteger(this.timeStamp.getBytes()));
        hash = hash.add(BigInteger.valueOf(this.blockHeight));
        hash = hash.add(new BigInteger(minerPublicKey));
        hash = hash.add(posProof.getSlothResult().getHash());
        return CryptoUtils.hash256(hash.toByteArray());
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return Arrays.equals(calculateHash(), block.calculateHash());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(calculateHash());
    }

    @Override
    public String toString(){
        return "Block{" +
                "previousHash=" + Hex.encodeHexString(previousHash) +
                ", blockHeight=" + blockHeight +
                ", hash=" + Hex.encodeHexString(calculateHash()) +
                ", quality=" + PoS.proofQuality(posProof,posProof.getChallenge(),minerPublicKey)+
                '}';
    }

/*    @Override
    public String toString() {
        String potProofString = "NOT_EXIST";
        if(potProof != null){
            potProofString = Hex.encodeHexString(CryptoUtils.hash256(getPotProof().getProof().toByteArray()));
        }
        return "Block{" +
                "previousHash=" + Hex.encodeHexString(previousHash) +
                ", timeStamp='" + timeStamp + '\'' +
                ", signature=" + Hex.encodeHexString(signature) +
                ", posProof=" + Hex.encodeHexString(posProof.getSlothResult().getHash().toByteArray()) +
                ", potProof=" + potProof +
                ", blockHeight=" + blockHeight +
                ", minerPublicKey=" + Hex.encodeHexString(minerPublicKey)+
                ", hash=" + Hex.encodeHexString(calculateHash()) +
                ", quality=" + PoS.proofQuality(posProof,posProof.getChallenge(),minerPublicKey)+
                '}';
    }*/
}
