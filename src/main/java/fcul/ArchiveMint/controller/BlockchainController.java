package fcul.ArchiveMint.controller;


import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.model.Coin;
import fcul.ArchiveMint.model.StorageContract;
import fcul.ArchiveMint.model.transactions.CurrencyTransaction;
import fcul.ArchiveMint.model.transactions.Transaction;
import fcul.ArchiveMint.service.BlockchainService;
import fcul.ArchiveMint.service.BlockchainService2;
import fcul.ArchiveMint.service.PosService;
import fcul.ArchiveMint.utils.CryptoUtils;
import fcul.ArchiveMint.utils.Utils;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
    public ResponseEntity<String> sendBlock(@RequestBody Block block){
        return blockchainService.receiveBlock(block);
    }
    @GetMapping("/getBlocks")
    public List<String> getBlocks(){
        return blockchainService.getFinalizedBlockChain().stream().map(Block::toString).collect(Collectors.toList());
    }
    @GetMapping("/publicKey")
    public String publicKey(){
        return  Hex.encodeHexString(CryptoUtils.hash256(keyManager.getPublicKey().getEncoded()));
    }
    @PostMapping("/sendCurrencyTransaction")
    public ResponseEntity<String> sendTransaction(@RequestBody CurrencyTransaction transaction){
        return blockchainService.addTransaction(transaction);
    }

    @GetMapping("/getCoins")
    public List<Coin> getCoins(@RequestParam String address) {
        return blockchainService.getCoins(address);
    }

    @PostMapping(consumes = "multipart/form-data", value = "/archiveFile")
    public ResponseEntity<String> handleFileUpload(
            @RequestPart("ArchivalFile") MultipartFile file,
            @RequestPart("data") StorageContract storageContract) {
        return blockchainService.archiveFile(file, storageContract);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String fileUrl) {
        return blockchainService.getMockRetrieve(fileUrl);
    }

}
