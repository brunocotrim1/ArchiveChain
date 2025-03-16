package fcul.ArchiveMint.controller;

import fcul.ArchiveMint.model.WalletBalanceModel;
import fcul.ArchiveMint.service.ExplorerService;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.FileProvingWindow;
import fcul.ArchiveMintUtils.Model.StorageContract;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    @GetMapping("/getStorageContract")
    public ResponseEntity<StorageContract> getStorageContract(@RequestParam String contractHash,
                                                              @RequestParam String fileUrl) {

        return explorerService.getStorageContract(contractHash, fileUrl);
    }

    @GetMapping("/getContractFileProvingWindows")
    public ResponseEntity<List<FileProvingWindow>> getContractFileProvingWindows(@RequestParam String contractHash) {
        return explorerService.getContractFileProvingWindows(contractHash);
    }

    private boolean isBase64(String str) {
        // Check if the string contains only valid Base64 characters
        if (!str.matches("^[A-Za-z0-9+/=]+$")) {
            return false;
        }

        // Ensure length is a multiple of 4
        if (str.length() % 4 != 0) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(str);

            // Optional: Additional check to ensure decoded data is valid
            return decoded.length > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }


    private boolean isHex(String str) {
        return str.matches("^[0-9A-Fa-f]+$") && str.length() % 2 == 0;
    }

    private String bytesToHex(byte[] bytes) {
        return Hex.encodeHexString(bytes);
    }
}
