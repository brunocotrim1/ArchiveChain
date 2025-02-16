package fcul.ArchiveMint.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@Builder
@ToString
public class StorageContract {
    String merkleRoot;
    String fileUrl;
    String fccnSignature;
    String storerSignature;
    String storerAddress;
    BigInteger timestamp;
    BigInteger value;


    public StorageContract() {
    }
    public byte[] getHash(){
        return (merkleRoot+fileUrl+timestamp+value+storerAddress).getBytes();
    }
}
