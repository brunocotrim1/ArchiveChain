package fcul.ArchiveMint.model;

import fcul.ArchiveMint.utils.CryptoUtils;
import fcul.ArchiveMint.utils.PoS;
import fcul.ArchiveMint.utils.wesolowskiVDF.ProofOfTime;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
public class Block {
    private byte[] previousHash;
    private String timeStamp;
    private List<Transaction> transactions;
    private byte[] signature;
    private PoS.PosProof posProof;
    private ProofOfTime potProof;
    private long blockHeight;
    private byte[] minerPublicKey;

    public byte[] getHash() {
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
        return Arrays.equals(getHash(), block.getHash());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getHash());
    }

    @Override
    public String toString() {
        String potProof = null;
        if(potProof == null){
            potProof = "null";
        }
        else {
            potProof = Hex.encodeHexString(CryptoUtils.hash256(getPotProof().getProof().toByteArray()));
        }
        return "Block{" +
                "previousHash=" + Hex.encodeHexString(previousHash) +
                ", timeStamp='" + timeStamp + '\'' +
                ", signature=" + Hex.encodeHexString(signature) +
                ", posProof=" + Hex.encodeHexString(posProof.getSlothResult().getHash().toByteArray()) +
                ", potProof=" + potProof +
                ", blockHeight=" + blockHeight +
                ", minerPublicKey=" + Hex.encodeHexString(minerPublicKey)+
                ", hash=" + Hex.encodeHexString(getHash()) +
                ", quality=" + PoS.proofQuality(posProof,posProof.getChallenge(),minerPublicKey)+
                '}';
    }
}
