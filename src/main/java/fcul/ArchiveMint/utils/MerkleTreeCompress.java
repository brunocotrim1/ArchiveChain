package fcul.ArchiveMint.utils;

import lombok.Data;

import java.util.Arrays;
import java.util.List;

@Data
public class MerkleTreeCompress implements java.io.Serializable{
    private List<byte[]> leaves;
    private MySloth.SlothResult slothResult;

    public MerkleTreeCompress(MerkleTree merkleTree) {
        this.leaves = merkleTree.getAllLeaves(merkleTree.getRoot());
        this.slothResult = merkleTree.getSlothResult();
    }

    public MerkleTree decompressMerkleTree(){
        MerkleTree merkleTree = MerkleTree.decompress(this.leaves, MerkleTree.LEAVES,
                this.slothResult);
        return merkleTree;
    }

}
