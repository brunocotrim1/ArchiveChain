package fcul.ArchiveMint.model;

import fcul.ArchiveMint.service.CoinLogic;
import fcul.ArchiveMintUtils.Model.Block;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class BackupLastExecuted {
    private Block executedBlock;
    private CoinLogic coinLogic;
}
