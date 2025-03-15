package fcul.ArchiveMint.controller;

import fcul.ArchiveMint.model.WalletBalanceModel;
import fcul.ArchiveMint.service.ExplorerService;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.StorageContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/explorer")
public class ExplorerController {

    @Autowired
    private ExplorerService explorerService;


    @GetMapping("/getBlocks")
    public ResponseEntity<List<Block>> getBlocks(@RequestParam(required = false) Integer limit) {
        return explorerService.getBlocks(limit != null ? limit : 100);
    }

    @GetMapping("/getBlock")
    public ResponseEntity<Block> getBlock(@RequestParam  int index) {
        return explorerService.getBlock(index);
    }

    @GetMapping("/walletBalances")
    public ResponseEntity<List<WalletBalanceModel>> getWalletBalances() {
        return explorerService.getWalletBalances();
    }

    @GetMapping("/storedFiles")
    public ResponseEntity<List<String>> getStoredFiles() {
        return explorerService.getStoredFiles();
    }

    @GetMapping("/storageContractsChunk")
    public ResponseEntity<List<StorageContract>> getStorageContractsChunk(
            @RequestParam String fileName,
            @RequestParam int offset,
            @RequestParam int limit) {

        return explorerService.getStorageContracts(fileName, offset, limit);
    }
    @GetMapping("/minedCoins")
    public ResponseEntity<HashMap<String, BigInteger>> getMinedCoins() {
        return explorerService.getMinedCoins();
    }

    @GetMapping("/archivedStorage")
    public ResponseEntity<String> getArchivedStorage() {
        return explorerService.getArchivedStorage();
    }

    @GetMapping("/totalAmountOfContracts")
    public ResponseEntity<String> getTotalAmountOfContracts() {
        return explorerService.getTotalAmountOfContracts();
    }
    @GetMapping("/totalAmountOfCoins")
    public ResponseEntity<String> getTotalAmountOfCoins() {
        return explorerService.getTotalAmountOfCoins();
    }


}
