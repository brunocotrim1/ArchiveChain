package fcul.ArchiveMint.model;

import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;

@Data
public class WalletDetailsModel {
    private String address;
    private String publicKey;
    private List<Long> wonBlocks;
    private BigInteger balance;
    private List<Transaction> transactions;
    private List<StorageContract> storageContracts;
}
