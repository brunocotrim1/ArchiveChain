package fcul.ArchiveMint.service;

import fcul.ArchiveMint.model.WalletBalanceModel;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.StorageContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExplorerService {

    @Autowired
    private BlockchainState blockchainState;

    public ResponseEntity<List<Block>> getBlocks(Integer limit) {
        try {
            if (blockchainState.getLastExecutedBlock() == null) {
                return ResponseEntity.ok(new ArrayList<>());
            }
            long lastExecutedHeight = blockchainState.getLastExecutedBlock().getHeight();
            List<Block> blockList = new ArrayList<>();
            for (long i = lastExecutedHeight; i >= Math.max(lastExecutedHeight - limit, 0); i--) {
                Block block = blockchainState.readBlockFromFile(i);
                if (block == null) {
                    return ResponseEntity.status(500).build();
                }
                blockList.add(block);
            }
            return ResponseEntity.ok(blockList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    public ResponseEntity<List<WalletBalanceModel>> getWalletBalances() {
        CoinLogic coinLogic = blockchainState.getCoinLogic();
        ConcurrentHashMap<String, List<Coin>> coinsPerWallet = coinLogic.getCoinMap();
        List<WalletBalanceModel> walletBalances = new ArrayList<>();
        for (Map.Entry<String, List<Coin>> entry : coinsPerWallet.entrySet()) {
            walletBalances.add(new WalletBalanceModel(entry.getKey(), entry.getValue().stream().map(Coin::getValue).reduce(BigInteger.ZERO, BigInteger::add)));
        }
        return ResponseEntity.ok(walletBalances);
    }

    public ResponseEntity<List<String>> getStoredFiles() {
        return ResponseEntity.ok(blockchainState.getStorageContractLogic().getStorageContracts().keySet().stream().toList());
    }

    public ResponseEntity<List<StorageContract>> getStorageContracts(String fileName, int offset, int limit) {
        List<StorageContract> allContracts;
        if (fileName == null || fileName.isEmpty()) {
            allContracts = new ArrayList<>();
            HashMap<String, List<StorageContract>> storageContractsMap = blockchainState.getStorageContractLogic().getStorageContracts();
            for (Map.Entry<String, List<StorageContract>> entry : storageContractsMap.entrySet()) {
                allContracts.addAll(entry.getValue());
            }
        } else {
            allContracts = blockchainState.getStorageContractLogic().getStorageContracts().get(fileName);
            if (allContracts == null) {
                return ResponseEntity.status(404).build();
            }
        }

        // Apply pagination
        int toIndex = Math.min(offset + limit, allContracts.size());
        if (offset >= allContracts.size()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<StorageContract> chunk = allContracts.subList(offset, toIndex);
        return ResponseEntity.ok(chunk);
    }

    public ResponseEntity<Block> getBlock(int index) {
        try {
            if (blockchainState.getLastExecutedBlock() == null) {
                return ResponseEntity.status(404).build();
            }
            long lastExecutedHeight = blockchainState.getLastExecutedBlock().getHeight();
            if (index > lastExecutedHeight) {
                return ResponseEntity.status(404).build();
            }
            Block block = blockchainState.readBlockFromFile(index);
            if (block == null) {
                return ResponseEntity.status(404).build();
            }
            return ResponseEntity.ok(block);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    public ResponseEntity<HashMap<String, BigInteger>> getMinedCoins() {
        return ResponseEntity.ok(blockchainState.getCoinLogic().getMinedCoinsHistory());
    }

    public ResponseEntity<String> getArchivedStorage() {
        return ResponseEntity.ok(blockchainState.getStorageContractLogic().getTotalStorage().toString());
    }


    public ResponseEntity<String> getTotalAmountOfContracts() {
        HashMap<String, List<StorageContract>> storageContracts = blockchainState.getStorageContractLogic().getStorageContracts();
        BigInteger totalAmount = BigInteger.ZERO;
        for (Map.Entry<String, List<StorageContract>> entry : storageContracts.entrySet()) {
            totalAmount = totalAmount.add(BigInteger.ONE);
        }
        return ResponseEntity.ok(totalAmount.toString());
    }

    public ResponseEntity<String> getTotalAmountOfCoins() {
        return ResponseEntity.ok(String.valueOf(blockchainState.getCoinLogic().getTotalCoins().toString()));
    }

}
