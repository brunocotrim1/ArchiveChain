package fcul.ArchiveMint.controller;


import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.service.BlockchainService;
import fcul.ArchiveMint.service.PosService;
import fcul.ArchiveMint.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/blockchain")
public class BlockchainController {

    @Autowired
    private BlockchainService blockchainService;
    @Autowired
    private PosService proofOfSpaceService;
    @GetMapping("/test")
    public int test(){
        blockchainService.startMining();
        return 1;
    }

    @GetMapping("/plotFile")
    public boolean test(@RequestParam String fileName){
        proofOfSpaceService.plotFile(fileName);
        return true;
    }
    @PostMapping("/sendBlock")
    public ResponseEntity<String> sendBlock(@RequestBody String block){
        Block receivedBlock = Utils.deserializeBlock(block);
        return blockchainService.receiveBlock(receivedBlock);
    }
    @GetMapping("/getBlocks")
    public List<String> getBlocks(){
        return blockchainService.getFinalizedBlockChain().stream().map(Block::toString).collect(Collectors.toList());
    }

}
