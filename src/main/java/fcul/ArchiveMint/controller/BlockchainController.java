package fcul.ArchiveMint.controller;


import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.service.BlockchainService;
import fcul.ArchiveMint.service.BlockchainState;
import fcul.ArchiveMint.service.NetworkService;
import fcul.ArchiveMint.service.PosService;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/blockchain")
public class BlockchainController {

    @Autowired
    private BlockchainService blockchainService;
    @Autowired
    private PosService proofOfSpaceService;
    @Autowired
    private KeyManager keyManager;
    @Autowired
    private NetworkService networkService;

    @GetMapping("/test")
    public int test() {
        blockchainService.startMining();
        return 1;
    }

    @GetMapping("/plotFile")
    public boolean test(@RequestParam String fileName) throws Exception {
        proofOfSpaceService.plotFile(fileName);
        return true;
    }

    @PostMapping("/sendBlock")
    public ResponseEntity<String> sendBlock(@RequestBody Block block) {
        return blockchainService.receiveBlock(block);
    }

    @GetMapping("/getBlocks")
    public List<String> getBlocks() {
        return blockchainService.getFinalizedBlockChain().stream().map(Block::toString).collect(Collectors.toList());
    }

    @GetMapping("/publicKey")
    public String publicKey() {
        return Hex.encodeHexString(CryptoUtils.hash256(keyManager.getPublicKey().getEncoded()));
    }

    @PostMapping("/sendTransaction")
    public ResponseEntity<String> sendTransaction(@RequestBody Transaction transaction) {
        return blockchainService.addTransaction(transaction);
    }

    @GetMapping("/getCoins")
    public List<Coin> getCoins(@RequestParam String address) {
        return blockchainService.getCoinLogic().getCoins(address);
    }

    @GetMapping("/getStorageContracts")
    public HashMap<String, List<StorageContract>> getStorageContracts() {
        return blockchainService.getStorageContractLogic().getStorageContracts();
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

    @GetMapping("/register")
    public boolean register() {
        return keyManager.registerFCCN();
    }

    @GetMapping("/getBlock")
    public ResponseEntity<Block> getBlock(@RequestParam long height) {
        try {
            BlockchainState state = blockchainService.getBlockchainState();
            if (state.getLastExecutedBlock() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            long blockHeight = state.getLastExecutedBlock().getHeight();
            if (height > blockHeight) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Block block = state.readBlockFromFile(height);
            return ResponseEntity.ok(block);
        } catch (IOException | ClassNotFoundException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping("/addPeer")
    public ResponseEntity<String> addPeer(@RequestBody String peerAddress) {
        networkService.addPeerAddress(peerAddress);
        return ResponseEntity.ok(networkService.getPeerAddress());
    }
    @GetMapping("/getPeers")
    public ResponseEntity<List<String>> getPeers() {
        return ResponseEntity.ok(networkService.getPeers());
    }
}
