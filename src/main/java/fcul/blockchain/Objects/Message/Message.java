package fcul.blockchain.Objects.Message;

import lombok.Data;

import java.io.Serializable;
@Data

public abstract class Message implements Serializable {
    private String message;
    private boolean broadcast;
    private MessageType type;
    private String sender;
}
