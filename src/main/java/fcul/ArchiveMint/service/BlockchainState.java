package fcul.ArchiveMint.service;

import fcul.ArchiveMint.model.Block;
import org.springframework.stereotype.Service;

@Service
public class BlockchainState {


    public boolean validateBlockTransactions(Block block){
        return true;
    }
    public boolean executeBlock(Block block){
        return true;
    }
}
