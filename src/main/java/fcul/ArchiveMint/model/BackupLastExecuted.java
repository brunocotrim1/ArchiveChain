package fcul.ArchiveMint.model;

import fcul.ArchiveMint.service.CoinLogic;
import fcul.ArchiveMint.service.StorageContractLogic;
import fcul.ArchiveMintUtils.Model.Block;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@Data
public class BackupLastExecuted {
    private Block executedBlock;
    private CoinLogic coinLogic;
    private StorageContractLogic storageContractLogic;

    public BackupLastExecuted(Block executedBlock, CoinLogic toClone, StorageContractLogic storageContractLogicToClone) {
        this.executedBlock = executedBlock;
        try {
            // Serialize the object into a byte stream
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(toClone);
            oos.writeObject(storageContractLogicToClone);
            oos.flush();

            // Deserialize from byte stream back to object
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            this.coinLogic = (CoinLogic) ois.readObject();
            this.storageContractLogic = (StorageContractLogic) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
