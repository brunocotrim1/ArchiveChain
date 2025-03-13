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

import java.util.List;

@RestController
@RequestMapping("/explorer")
public class ExplorerController {

    @Autowired
    private ExplorerService explorerService;


    @GetMapping("/getBlocks")
    public ResponseEntity<List<Block>> getBlocks(@RequestParam(required = false) Integer limit) {
        return explorerService.getBlocks(limit != null ? limit : 10);
    }

    @GetMapping("/walletBalances")
    public ResponseEntity<List<WalletBalanceModel>> getWalletBalances() {
        return explorerService.getWalletBalances();
    }

    @GetMapping("/storedFiles")
    public ResponseEntity<List<String>> getStoredFiles() {
        return explorerService.getStoredFiles();
    }

    @GetMapping("/storageContracts")
    public ResponseEntity<List<StorageContract>> getStorageContracts(@RequestParam String fileName) {
        return explorerService.getStorageContracts(fileName);
    }
}
