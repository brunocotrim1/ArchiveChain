package fcul.ArchiveMint.model.transactions;

import com.fasterxml.jackson.annotation.JsonTypeName;
import fcul.ArchiveMint.model.StorageContract;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.codec.binary.Hex;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@JsonTypeName("currency")
public class StorageContractSubmission extends Transaction {
    private StorageContract contract;
    private String storerPublicKey;
    @Override
    public String getTransactionId() {
        return Hex.encodeHexString(contract.getHash());
    }
}
