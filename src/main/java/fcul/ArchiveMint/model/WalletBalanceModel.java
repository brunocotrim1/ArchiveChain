package fcul.ArchiveMint.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigInteger;

@Data
@AllArgsConstructor
public class WalletBalanceModel {
    private String walletAddress;
    private BigInteger balance;
}
