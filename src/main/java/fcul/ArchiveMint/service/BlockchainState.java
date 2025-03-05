package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.model.BackupLastExecuted;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.CurrencyTransaction;
import fcul.ArchiveMintUtils.Model.transactions.FileProofTransaction;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service

public class BlockchainState {

    private Mempool mempool = new Mempool();
    private CoinLogic coinLogic = new CoinLogic();
    private StorageContractLogic storageContractLogic = new StorageContractLogic();
    private BackupLastExecuted backup = null;
    private static int maxAmountTransactions = 8000;
    private static int blockReward = 1000;
    @Autowired
    private KeyManager keyManager;
    @Autowired
    private PosService posService;
    @Autowired
    private NetworkService net;

    @PostConstruct
    public void init() {

    }

    public List<Transaction> executeBlock(Block toExecute) {

        try {
            System.out.println("Executing block height: " + toExecute.getHeight()
                    + " hash: " + Hex.encodeHexString(toExecute.calculateHash()) + "with " + toExecute.getTransactions().size() + " transactions");
            backup = new BackupLastExecuted(toExecute, coinLogic.clone(), storageContractLogic.clone());
            //BACKUP
            String minerPk = Hex.encodeHexString(toExecute.getMinerPublicKey());
            for (Transaction transaction : toExecute.getTransactions()) {
                switch (transaction.getType()) {
                    case CURRENCY_TRANSACTION:
                        CurrencyTransaction currencyTransaction = (CurrencyTransaction) transaction;
                        coinLogic.spendCoin(currencyTransaction.getSenderAddress(), currencyTransaction.getReceiverAddress(), currencyTransaction.getCoins(), currencyTransaction.getAmount());
                        break;
                    case STORAGE_CONTRACT_SUBMISSION:
                        StorageContractSubmission storageContractSubmission = (StorageContractSubmission) transaction;
                        storageContractLogic.addStorageContract(storageContractSubmission, toExecute, keyManager);
                        break;
                    case FILE_PROOF:
                        FileProofTransaction fileProofTransaction = (FileProofTransaction) transaction;
                        storageContractLogic.processFileProof(fileProofTransaction);
                        break;
                    default:
                        throw new RuntimeException("Invalid transaction type");
                }
            }
            List<Transaction> resultingTransactions = new ArrayList<>();
            resultingTransactions.addAll(storageContractLogic.generateFileProofs(posService, keyManager, toExecute));
            storageContractLogic.processFileExpiredProvingWindows(toExecute);
            coinLogic.createCoin(CryptoUtils.getWalletAddress(minerPk), BigInteger.valueOf(blockReward));
            return resultingTransactions;
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean addTransaction(Transaction transaction) {
        if(mempool.addTransaction(transaction)) {
            net.broadcastTransaction(transaction);
            return true;
        }
        return false;
    }

    public void addTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            mempool.addTransaction(transaction);
        }
    }

    public List<Transaction> getValidTransactions() {
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
        CachedModifications cachedModifications = new CachedModifications();

        for (Transaction transaction : block.getTransactions()) {
            if (!validateTransaction(transaction, coinLogic, storageContractLogic)) {
                System.out.println("Invalid transaction in block");
                return false;
            }

            cachedModifications.verifyDoubleSpend(transaction);
        }
        return true;
    }

    public boolean validateBlockWithRollback(Block block) {
        CachedModifications cachedModifications = new CachedModifications();

        for (Transaction transaction : block.getTransactions()) {
            if (!validateTransaction(transaction, backup.getCoinLogic(), backup.getStorageContractLogic())) {
                System.out.println("Invalid transaction in block");
                return false;
            }
            cachedModifications.verifyDoubleSpend(transaction);
        }
        return true;
    }

    public List<Coin> getCoins(String address) {
        return coinLogic.getCoins(address);
    }

    public void rollBackBlock(Block blockSwapped) {
        if (backup == null) {
            throw new RuntimeException("No backup available");
        }
        if (!Arrays.equals(backup.getExecutedBlock().calculateHash(), blockSwapped.calculateHash())) {
            throw new RuntimeException("Block height does not match");
        }
        coinLogic = backup.getCoinLogic();
        storageContractLogic = backup.getStorageContractLogic();
        backup = null;
        System.out.println("Rollback Sucessfull to height: " + blockSwapped.getHeight());
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
                System.out.println("Readded Censured Transaction");
            }
        }
        return censuredTransactions;
    }


    public CoinLogic getCoinLogic() {
        return coinLogic;
    }

    public StorageContractLogic getStorageContractLogic() {
        return storageContractLogic;
    }
}
