package fcul.ArchiveMint.service;

import fcul.ArchiveMint.model.BackupLastExecuted;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.transactions.CurrencyTransaction;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class BlockchainState {

    private Mempool mempool = new Mempool();
    private CoinLogic coinLogic = new CoinLogic();
    private BackupLastExecuted backup = null;
    private static int maxAmountTransactions = 8000;
    private static int blockReward = 1000;



    public boolean executeBlock(Block toExecute) {

        try {
            System.out.println("Executing block height: " + toExecute.getHeight() + " hash: " + Hex.encodeHexString(toExecute.calculateHash()));
            backup = new BackupLastExecuted(toExecute, coinLogic.clone());
            //BACKUP
            String minerPk = Hex.encodeHexString(toExecute.getMinerPublicKey());
            for (Transaction transaction : toExecute.getTransactions()) {
                switch (transaction.getType()) {
                    case CURRENCY_TRANSACTION:
                        CurrencyTransaction currencyTransaction = (CurrencyTransaction) transaction;
                        coinLogic.spendCoin(currencyTransaction.getSenderAddress(), currencyTransaction.getReceiverAddress(), currencyTransaction.getCoins(), currencyTransaction.getAmount());
                        break;
                    default:
                        throw new RuntimeException("Invalid transaction type");
                }
            }

            coinLogic.createCoin(CryptoUtils.getWalletAddress(minerPk), BigInteger.valueOf(blockReward));
            return true;
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean addTransaction(Transaction transaction) {
        return mempool.addTransaction(transaction);
    }

    public List<Transaction> getValidTransactions() {
        CachedModifications cachedModifications = new CachedModifications();
        List<Transaction> validTransactions = new ArrayList<>();
        int i = 0;
        System.out.println("Mempool size: " + mempool.size());
        while (i < maxAmountTransactions && mempool.size() > 0) {
            Transaction transaction = mempool.poll();
            if (transaction != null) {
                if (validateTransaction(transaction,coinLogic)) {
                    if(cachedModifications.verifyDoubleSpend(transaction)){
                        validTransactions.add(transaction);
                    }
                }
            }
            i++;
        }
        System.out.println("Valid transactions: " + validTransactions.size());
        return validTransactions;
    }


    private boolean validateTransaction(Transaction transaction,CoinLogic coinLogic) {
        try {
            switch (transaction.getType()) {
                case CURRENCY_TRANSACTION:
                    return coinLogic.validTransaction((CurrencyTransaction) transaction);
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateBlockTransactions(Block block) {
        CachedModifications cachedModifications = new CachedModifications();

        for(Transaction transaction : block.getTransactions()){
            if(!validateTransaction(transaction,coinLogic)){
                throw new RuntimeException("Invalid transaction in block");
            }

            cachedModifications.verifyDoubleSpend(transaction);
        }
        return true;
    }

    public boolean validateBlockWithRollback(Block block) {
        CachedModifications cachedModifications = new CachedModifications();

        for(Transaction transaction : block.getTransactions()){
            if(!validateTransaction(transaction,backup.getCoinLogic())){
                throw new RuntimeException("Invalid transaction in block rollback");
            }
            cachedModifications.verifyDoubleSpend(transaction);
        }
        return true;
    }
    public List<Coin> getCoins(String address) {
        return coinLogic.getCoins(address);
    }

    public void rollBackBlock(Block blockSwapped){
        if(backup == null){
            throw new RuntimeException("No backup available");
        }
        if(!Arrays.equals(backup.getExecutedBlock().calculateHash(), blockSwapped.calculateHash())){
            throw new RuntimeException("Block height does not match");
        }
        coinLogic = backup.getCoinLogic();
        backup = null;
        System.out.println("Rollback Sucessfull to height: " + blockSwapped.getHeight());
    }
}
