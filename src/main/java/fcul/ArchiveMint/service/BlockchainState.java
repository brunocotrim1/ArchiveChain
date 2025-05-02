package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMint.model.BackupLastExecuted;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.CurrencyTransaction;
import fcul.ArchiveMintUtils.Model.transactions.FileProofTransaction;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.Utils;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Getter
public class BlockchainState {

    private Mempool mempool;
    private CoinLogic coinLogic = new CoinLogic();
    private StorageContractLogic storageContractLogic = new StorageContractLogic();
    private Block lastExecutedBlock = null;
    //private BackupLastExecuted backup = null;
    private static int maxAmountTransactions = 8000;
    private static int blockReward = 1000;
    @Autowired
    private KeyManager keyManager;
    @Autowired
    private PosService posService;
    @Autowired
    private NetworkService net;
    @Autowired
    private NodeConfig nodeConfig;

    @PostConstruct
    public void init() {
        this.mempool = new Mempool(nodeConfig.getId());
    }

    public List<Transaction> executeBlock(Block toExecute,boolean isSync) {

        try {
            System.out.println("Executing block height: " + toExecute.getHeight()
                    + " hash: " + Hex.encodeHexString(toExecute.calculateHash()) + "with " + toExecute.getTransactions().size() + " transactions");
            //backup = new BackupLastExecuted(toExecute, coinLogic, storageContractLogic);
            //BACKUP
            String minerPk = Hex.encodeHexString(toExecute.getMinerPublicKey());
            for (Transaction transaction : toExecute.getTransactions()) {
                switch (transaction.getType()) {
                    case CURRENCY_TRANSACTION:
                        CurrencyTransaction currencyTransaction = (CurrencyTransaction) transaction;
                        coinLogic.spendCoin(currencyTransaction.getSenderAddress(), currencyTransaction.getReceiverAddress(),
                                currencyTransaction.getCoins(), currencyTransaction.getAmount(), toExecute);
                        break;
                    case STORAGE_CONTRACT_SUBMISSION:
                        StorageContractSubmission storageContractSubmission = (StorageContractSubmission) transaction;
                        storageContractLogic.addStorageContract(storageContractSubmission, toExecute, keyManager);
                        break;
                    case FILE_PROOF:
                        FileProofTransaction fileProofTransaction = (FileProofTransaction) transaction;
                        storageContractLogic.processFileProof(fileProofTransaction, coinLogic, toExecute);
                        break;
                    default:
                        throw new RuntimeException("Invalid transaction type");
                }
            }
            List<Transaction> resultingTransactions = new ArrayList<>();
            storageContractLogic.processFileExpiredAndUpcomingProvingWindows(toExecute, keyManager);
            resultingTransactions.addAll(storageContractLogic.generateFileProofs(posService, keyManager, toExecute,isSync));
            coinLogic.createCoin(CryptoUtils.getWalletAddress(minerPk), BigInteger.valueOf(blockReward), toExecute, true);
            //State Post Block Execution
            storeBlockAndStateInDisk(toExecute, coinLogic, storageContractLogic);
            lastExecutedBlock = toExecute;
            return resultingTransactions;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public boolean addTransaction(List<Transaction> transactions, boolean isCensured) {
        synchronized (mempool) {
            if (mempool.addTransaction(transactions)) {
                net.broadcastTransactions(transactions, isCensured);
                return true;
            }
            net.broadcastTransactions(transactions, isCensured);
            return false;
        }

    }


    public List<Transaction> getValidTransactions() {
        synchronized (mempool) {
            CachedModifications cachedModifications = new CachedModifications();
            List<Transaction> validTransactions = new ArrayList<>();
            int i = 0;
            System.out.println("Mempool size: " + mempool.size());
            while (i < maxAmountTransactions && mempool.size() > 0) {
                Transaction transaction = mempool.poll();
                if (transaction != null) {
                    if (validateTransaction(transaction, coinLogic, storageContractLogic)) {
                        if (cachedModifications.verifyDoubleSpend(transaction)) {
                            validTransactions.add(transaction);
                        }
                    }
                }
                i++;
            }
            System.out.println("Valid transactions: " + validTransactions.size());
            return validTransactions;
        }
    }


    private boolean validateTransaction(Transaction transaction, CoinLogic coinLogic, StorageContractLogic storageContractLogic) {
        try {
            switch (transaction.getType()) {
                case CURRENCY_TRANSACTION:
                    return coinLogic.validTransaction((CurrencyTransaction) transaction);
                case STORAGE_CONTRACT_SUBMISSION:
                    return storageContractLogic.validSubmission((StorageContractSubmission) transaction, keyManager);
                case FILE_PROOF:
                    return storageContractLogic.validFileProof((FileProofTransaction) transaction, posService);
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public Transaction validateContractSubmission(byte[] fileData,
                                                  StorageContract contract,
                                                  KeyManager key) {
        return storageContractLogic.verifyStorageContractBuildTransaction(fileData, contract, key);
    }

    public boolean validateBlockTransactions(Block block) {
        try {
            CachedModifications cachedModifications = new CachedModifications();

            for (Transaction transaction : block.getTransactions()) {
                if (!validateTransaction(transaction, coinLogic, storageContractLogic)) {
                    System.out.println("Invalid transaction in block");
                    return false;
                }
                if (!cachedModifications.verifyDoubleSpend(transaction)) {
                    System.out.println("Double spend detected in block");
                    return false;
                }

            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean validateBlockWithRollback(Block block) {
        try {
            CachedModifications cachedModifications = new CachedModifications();
            BackupLastExecuted backup = getStateFromDisk(block.getHeight() - 1);
            for (Transaction transaction : block.getTransactions()) {
                if (!validateTransaction(transaction, backup.getCoinLogic(), backup.getStorageContractLogic())) {
                    System.out.println("Invalid transaction in block");
                    return false;
                }
                if (!cachedModifications.verifyDoubleSpend(transaction)) {
                    System.out.println("Double spend detected in block");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Coin> getCoins(String address) {
        return coinLogic.getCoins(address);
    }

    public void rollBackBlock(Block blockSwapped) {
        try {
            BackupLastExecuted backup = getStateFromDisk(blockSwapped.getHeight() - 1);
            if (backup == null) {
                throw new RuntimeException("No backup available");
            }
            if (!Arrays.equals(backup.getExecutedBlock().calculateHash(), blockSwapped.getPreviousHash())) {
                throw new RuntimeException("Block height does not match");
            }
            coinLogic = backup.getCoinLogic();
            storageContractLogic = backup.getStorageContractLogic();
            System.out.println(Utils.RED + "Rollback Sucessfull to height: " + blockSwapped.getHeight() + Utils.RESET);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    private BackupLastExecuted getStateFromDisk(long height) throws Exception {
        String baseDir = nodeConfig.getStoragePath() + "/state/";
        String coinPath = baseDir + height + "_coinLogic.state";
        String storagePath = baseDir + height + "_storageContractLogic.state";
        String blockPath = nodeConfig.getStoragePath() + "/blocks/" + height + ".block";
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(coinPath));
        CoinLogic coinLogic = (CoinLogic) ois.readObject();
        ois.close();
        ois = new ObjectInputStream(new FileInputStream(storagePath));
        StorageContractLogic storageContractLogic = (StorageContractLogic) ois.readObject();
        ois.close();
        ois = new ObjectInputStream(new FileInputStream(blockPath));
        Block block = (Block) ois.readObject();
        ois.close();
        return new BackupLastExecuted(block, coinLogic, storageContractLogic);
    }

    public List<Transaction> getCensuredTransactions(List<Transaction> oldBlock, List<Transaction> newBlock) {
        List<Transaction> censuredTransactions = new ArrayList<>();
        for (Transaction oldTransaction : oldBlock) {
            String id = oldTransaction.getTransactionId();
            boolean found = false;
            for (Transaction newTransaction : newBlock) {
                if (newTransaction.getTransactionId().equals(id)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                censuredTransactions.add(oldTransaction);
                //System.out.println("Readded Censured Transaction");
            }
        }
        return censuredTransactions;
    }

    public void storeBlockAndStateInDisk(Block block, CoinLogic coinLogic, StorageContractLogic storageContractLogic)
            throws Exception {
        storeBlockinFile(block);
        storeStateInDisk(coinLogic, storageContractLogic, block.getHeight());
    }

    public void storeBlockinFile(Block block) throws IOException {
        String blockPath = nodeConfig.getStoragePath() + "/blocks/" + block.getHeight() + ".block";
        File file = new File(blockPath);
        file.getParentFile().mkdirs();
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(blockPath));
        oos.writeObject(block);
        oos.flush();
        oos.close();
    }

    public Block readBlockFromFile(long height, boolean reload) throws IOException, ClassNotFoundException {
        try {
            if (!reload) {
                if (lastExecutedBlock == null) {
                    return null;
                }
                if (height > lastExecutedBlock.getHeight()) {
                    return null;
                }
            }

            String blockPath = nodeConfig.getStoragePath() + "/blocks/" + height + ".block";
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(blockPath));
            Block block = (Block) ois.readObject();
            ois.close();
            return block;
        } catch (Exception e) {
            return null;
        }
    }

    public void storeStateInDisk(CoinLogic coinLogic, StorageContractLogic storageContractLogic, long height)
            throws Exception {
        String baseDir = nodeConfig.getStoragePath() + "/state/";
        // Create directories if they do not exist
        new File(baseDir).mkdirs();
        String coinPath = baseDir + height + "_coinLogic.state";
        String storagePath = baseDir + height + "_storageContractLogic.state";
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(coinPath));
        oos.writeObject(coinLogic);
        oos.flush();
        oos.close();
        oos = new ObjectOutputStream(new FileOutputStream(storagePath));
        oos.writeObject(storageContractLogic);
        oos.flush();
        oos.close();
    }


    public CoinLogic getCoinLogic() {
        return coinLogic;
    }

    public StorageContractLogic getStorageContractLogic() {
        return storageContractLogic;
    }

    public void finalize(Block blockAtHeight) {
        long height = blockAtHeight.getHeight();
        String baseDir = nodeConfig.getStoragePath() + "/state/";
        File[] dirs = new File(baseDir).listFiles();
        for (File dir : dirs) {
            if (dir.getName().contains("_")) {
                long stateHeight = Long.parseLong(dir.getName().split("_")[0]);
                if (stateHeight < height - 1) {
                    dir.delete();
                }
            }
        }
    }

}
