package fcul.ArchiveMint.controller;


import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.service.BlockchainService;
import fcul.ArchiveMint.service.BlockchainService2;
import fcul.ArchiveMint.service.PosService;
import fcul.ArchiveMint.utils.CryptoUtils;
import fcul.ArchiveMint.utils.Utils;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/blockchain")
public class BlockchainController {

    @Autowired
    private BlockchainService2 blockchainService;
    @Autowired
    private PosService proofOfSpaceService;
    @Autowired
    private KeyManager keyManager;
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
    @GetMapping("/publicKey")
    public String publicKey(){
        return  Hex.encodeHexString(CryptoUtils.hash256(keyManager.getPublicKey().getEncoded()));
    }
}
