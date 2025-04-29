package fcul.ArchiveMint.service;

import fcul.ArchiveMint.model.WalletBalanceModel;
import fcul.ArchiveMint.model.WalletDetailsModel;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.FileProvingWindow;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.CurrencyTransaction;
import fcul.ArchiveMintUtils.Model.transactions.FileProofTransaction;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import jnr.ffi.Struct.socklen_t;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExplorerService {

    @Autowired
    private BlockchainState blockchainState;

    private static final LevenshteinDistance levenshtein = new LevenshteinDistance();

    public ResponseEntity<WalletDetailsModel> getWalletDetails(String address) {
        WalletDetailsModel wd = new WalletDetailsModel();
        wd.setAddress(address);
        HashMap<String, List<StorageContract>> storageContractsMap = blockchainState.getStorageContractLogic()
                .getStorageContracts();
        List<StorageContract> contracts = new ArrayList<>();
        for (List<StorageContract> sc : storageContractsMap.values()) {
            for (StorageContract contract : sc) {
                if (contract.getStorerAddress().equals(address)) {
                    contracts.add(contract);
                }
            }
        }
        wd.setStorageContracts(contracts);
        fillWalletDetailsBlock(wd, address);
        wd.setTransactions(wd.getTransactions().reversed());
        ConcurrentHashMap<String, List<Coin>> coinsPerWallet = blockchainState.getCoinLogic().getCoinMap();
        if (coinsPerWallet.containsKey(address)) {
            wd.setBalance(coinsPerWallet.get(address).stream().map(Coin::getValue).reduce(BigInteger.ZERO, BigInteger::add));
        } else {
            wd.setBalance(BigInteger.ZERO);
        }
        return ResponseEntity.ok(wd);
    }

    private void fillWalletDetailsBlock(WalletDetailsModel wd, String address) {
        try {
            wd.setTransactions(new ArrayList<>());
            wd.setWonBlocks(new ArrayList<>());
            wd.setPublicKey(null);
            if (blockchainState.getLastExecutedBlock() == null) {
                return;
            }
            for (long i = blockchainState.getLastExecutedBlock().getHeight(); i>= Math.max(blockchainState.getLastExecutedBlock().getHeight()-1000, 0L); i--) {
                Block block = blockchainState.readBlockFromFile(i,false);
                String minerPk = Hex.encodeHexString(block.getMinerPublicKey());
                String addressMiner = CryptoUtils.getWalletAddress(minerPk);
                if (addressMiner.equals(address)) {
                    wd.getWonBlocks().add(block.getHeight());
                    if (wd.getPublicKey() == null) {
                        wd.setPublicKey(minerPk);
                    }
                }
                List<Transaction> transactions = block.getTransactions();
                for (int j = 0; j < transactions.size(); j++) {
                    Transaction transaction = transactions.get(j);
                    switch (transaction.getType()) {
                        case CURRENCY_TRANSACTION:
                            CurrencyTransaction currencyTransaction = (CurrencyTransaction) transaction;
                            if (currencyTransaction.getSenderAddress().equals(address) ||
                                    currencyTransaction.getReceiverAddress().equals(address)) {
                                wd.getTransactions().add(currencyTransaction);
                            }
                            break;
                        case STORAGE_CONTRACT_SUBMISSION:
                            StorageContractSubmission storageContractSubmission = (StorageContractSubmission) transaction;
                            if (storageContractSubmission.getContract().getStorerAddress().equals(address)) {
                                wd.getTransactions().add(storageContractSubmission);
                                if (wd.getPublicKey() == null) {
                                    wd.setPublicKey(storageContractSubmission.getStorerPublicKey());
                                }
                            }
                            break;
                        case FILE_PROOF:
                            FileProofTransaction fileProofTransaction = (FileProofTransaction) transaction;
                            String addressFp = CryptoUtils.getWalletAddress(fileProofTransaction.getStorerPublicKey());
                            if (addressFp.equals(address)) {
                                wd.getTransactions().add(fileProofTransaction);
                                if (wd.getPublicKey() == null) {
                                    wd.setPublicKey(fileProofTransaction.getStorerPublicKey());
                                }
                            }
                            break;
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ResponseEntity<List<Block>> getBlocks(Integer limit) {
        try {
            if (blockchainState.getLastExecutedBlock() == null) {
                return ResponseEntity.ok(new ArrayList<>());
            }
            long lastExecutedHeight = blockchainState.getLastExecutedBlock().getHeight();
            List<Block> blockList = new ArrayList<>();
            for (long i = lastExecutedHeight; i >= Math.max(lastExecutedHeight - limit, 0); i--) {
                Block block = blockchainState.readBlockFromFile(i,false);
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


    public ResponseEntity<List<String>> getStoredFiles(int offset, int limit, String fileName) {
        List<String> allFiles;
        if (fileName != null && !fileName.isEmpty()) {
            allFiles = searchedFiles(fileName);
        }else{
            allFiles = blockchainState.getStorageContractLogic().getStorageContracts().keySet().stream().toList();
        }
        int toIndex = Math.min(offset + limit, allFiles.size());
        if (offset >= allFiles.size()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<String> chunk = allFiles.subList(offset, toIndex);
        return ResponseEntity.ok(chunk);
    }
    
    public List<String> searchedFiles(String fileName) {
        fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
        List<String> allFiles = blockchainState.getStorageContractLogic().getStorageContracts().keySet().stream().toList();
        List<Map.Entry<String, Integer>> fileDistances = new ArrayList<>();
        System.out.println("Searching for file: " + fileName);
        for (String file : allFiles) {
            String cleaned = file.replaceAll("^\\d{14}/https?://", "");
            String[] parts = cleaned.split("/", 2);
            String domain = parts[0];
            String path = parts.length > 1 ? parts[1] : "";
        
            String normalizedFileName = normalize(fileName);
            String normalizedDomain = normalize(domain);
            String normalizedPath = normalize(path);
        
            double domainSim = jaccardSimilarity(normalizedDomain, normalizedFileName);
            double pathSim = jaccardSimilarity(normalizedPath, normalizedFileName);
        
            double combinedScore = 0.3 * domainSim + 0.7 * pathSim;
        
            // Flip similarity to distance for sorting (higher similarity = lower "distance")
            double finalScore = 1.0 - combinedScore;
        
            fileDistances.add(new AbstractMap.SimpleEntry<>(file, (int)(finalScore * 100)));
        }
        
                return fileDistances.stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                         .replaceAll("\\p{M}", "")
                         .toLowerCase();
    }
    
    private static double jaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("[-_/ .]")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("[-_/ .]")));
    
        setA.removeIf(String::isEmpty);
        setB.removeIf(String::isEmpty);
    
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
    
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
    
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
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
            Block block = blockchainState.readBlockFromFile(index,false);
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

    public ResponseEntity<StorageContract> getStorageContract(String contractHash, String fileUrl) {
        List<StorageContract> storageContract = blockchainState.getStorageContractLogic().getStorageContracts().get(fileUrl);

        if (storageContract == null) {
            return ResponseEntity.status(404).build();
        }
        for (StorageContract sc : storageContract) {
            if (Hex.encodeHexString(sc.getHash()).equals(contractHash)) {
                return ResponseEntity.ok(sc);
            }
        }
        return ResponseEntity.status(404).build();
    }


    public ResponseEntity<List<FileProvingWindow>> getContractFileProvingWindows(String contractHash) {
        List<FileProvingWindow> fileProvingWindows = blockchainState.getStorageContractLogic()
                .getFileProvingWindows().get(contractHash);
        if (fileProvingWindows == null) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(fileProvingWindows);
    }

    public ResponseEntity<String> getTotalAmountOfFiles() {
        return ResponseEntity.ok(new BigInteger(String.valueOf(blockchainState.getStorageContractLogic().getStorageContracts().keySet().size())).toString());
    }

    public ResponseEntity<List<String>> getStorersOfFile(String fileUrl) {
        List<StorageContract> storageContracts = blockchainState.getStorageContractLogic().getStorageContracts().get(fileUrl);
        if (storageContracts == null) {
            return ResponseEntity.status(404).build();
        }
        List<String> storers = new ArrayList<>();
        for (StorageContract sc : storageContracts) {
            storers.add(sc.getStorerAddress());
        }
        return ResponseEntity.ok(storers);
    }

    public ResponseEntity<String> getStorageHashFileAndAddress(String fileUrl, String address) {
        List<StorageContract> storageContracts = blockchainState.getStorageContractLogic().getStorageContracts().get(fileUrl);
        if (storageContracts == null) {
            return ResponseEntity.status(404).build();
        }
        for (StorageContract sc : storageContracts) {
            if (sc.getStorerAddress().equals(address)) {
                return ResponseEntity.ok(Hex.encodeHexString(sc.getHash()));
            }
        }
        return ResponseEntity.status(404).build();
    }

    public ResponseEntity<HashMap<String, BigInteger>> getStorageHistory() {
        return ResponseEntity.ok(blockchainState.getStorageContractLogic().storageUsedHistory);
    }

    public ResponseEntity<HashMap<String, BigInteger>> getFileHistory() {
        return ResponseEntity.ok(blockchainState.getStorageContractLogic().archivedFileHistory);
    }
}
