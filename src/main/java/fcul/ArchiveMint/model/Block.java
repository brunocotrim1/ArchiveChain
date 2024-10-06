package fcul.ArchiveMint.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Block {
    private byte[] previousHash;
    private String timeStamp;
    private List<Transaction> transactions;
    private byte[] hash;
    private byte[] signature;
    private long blockHeight;
}
