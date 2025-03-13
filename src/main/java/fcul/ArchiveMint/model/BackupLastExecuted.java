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
        this.coinLogic = toClone;
        this.storageContractLogic = storageContractLogicToClone;
    }
}
