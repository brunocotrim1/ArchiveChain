package fcul.blockchain.Objects.Message.Network;

import fcul.blockchain.Objects.Message.Message;
import fcul.blockchain.Objects.Message.MessageType;
import fcul.blockchain.Peer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NetworkMessage extends Message {
    private Peer peer;
    private NetworkType networkType;
    public NetworkMessage(){
        super();
        this.setType(MessageType.NETWORK);
    }
}
