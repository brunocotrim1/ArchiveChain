package fcul.ArchiveMint.model.transactions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.math.BigInteger;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")  // Add "type" field in JSON
@JsonSubTypes({
        @JsonSubTypes.Type(value = CurrencyTransaction.class, name = "currency")  // Map "currency" to CurrencyTransaction
})
public abstract class Transaction {
    private TransactionType type;
    abstract public String getTransactionId();

}
