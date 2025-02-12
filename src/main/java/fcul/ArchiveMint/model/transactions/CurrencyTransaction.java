package fcul.ArchiveMint.model.transactions;

import com.fasterxml.jackson.annotation.JsonTypeName;
import fcul.ArchiveMint.model.Coin;
import fcul.ArchiveMint.utils.CryptoUtils;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@JsonTypeName("currency")
public class CurrencyTransaction extends Transaction {

    private List<BigInteger> coins;
    private BigInteger amount;
    private String senderAddress;
    private String receiverAddress;
    private String signature;
    private String senderPk;

    @Override
    public String getTransactionId() {
        StringBuilder data = new StringBuilder(senderAddress + receiverAddress + senderPk);
        for (BigInteger coin : coins) {
            data.append(coin.toString());
        }
        byte [] hash = CryptoUtils.hash256(data.toString().getBytes());
        byte[] id = CryptoUtils.hash256(hash);
        return Hex.encodeHexString(id);
    }

}
