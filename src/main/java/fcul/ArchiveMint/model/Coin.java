package fcul.ArchiveMint.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Coin {
    private String owner;
    private BigInteger value;
    private BigInteger id;
}