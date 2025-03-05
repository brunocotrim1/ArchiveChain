package fcul.ArchiveMint.service;


import fcul.ArchiveMintUtils.Model.FileProof;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.CurrencyTransaction;
import fcul.ArchiveMintUtils.Model.transactions.FileProofTransaction;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CachedModifications {
    private Map<String, List<BigInteger>> coins = new HashMap<>();
    private Map<StorageContract, Boolean> storageContracts = new HashMap<>();
    private Map<FileProof, Boolean> fileProofs = new HashMap<>();

    public void addCoin(String address, BigInteger coin) {
        if (coins == null) {
            return;
        }
        if (coins.containsKey(address)) {
            coins.get(address).add(coin);
        } else {
            List<BigInteger> coinList = new ArrayList<>();
            coinList.add(coin);
            coins.put(address, coinList);
        }
    }

    public boolean coindDoubleSpend(String address, BigInteger coinId) {
        if (coins.containsKey(address)) {
            return coins.get(address).contains(coinId);
        }
        return false;
    }

    public void addStorageContract(StorageContract contract) {
        if (storageContracts == null) {
            return;
        }
        storageContracts.put(contract, true);
    }

    public void addFileProof(FileProof fileProof) {
        if (fileProofs == null) {
            return;
        }
        fileProofs.put(fileProof, true);
    }


    public boolean verifyStorageSubmissionDouble(StorageContractSubmission submission) {
        return !storageContracts.containsKey(submission.getContract());
    }
    public boolean verifyFileProofDouble(FileProofTransaction fileProofTransaction) {
        return !storageContracts.containsKey(fileProofTransaction.getFileProof());
    }

    public boolean verifyDoubleSpend(Transaction transaction) {
        switch (transaction.getType()) {
            case CURRENCY_TRANSACTION:
                CurrencyTransaction currencyTransaction = (CurrencyTransaction) transaction;
                for (BigInteger coin : currencyTransaction.getCoins()) {
                    if (coin == null || coindDoubleSpend(currencyTransaction.getSenderAddress(), coin)) {
                        System.out.println("Coin Double spend detected");
                        return false;
                    }
                    addCoin(currencyTransaction.getSenderAddress(), coin);
                }
                break;
            case STORAGE_CONTRACT_SUBMISSION:
                StorageContract contract = ((StorageContractSubmission) transaction).getContract();
                if (!verifyStorageSubmissionDouble((StorageContractSubmission) transaction)) {
                    System.out.println("Storage Double spend detected");
                    return false;
                }
                addStorageContract(contract);
                break;
            case FILE_PROOF:
                FileProofTransaction fileProofTransaction = (FileProofTransaction) transaction;
                if (!verifyFileProofDouble((FileProofTransaction) transaction)) {
                    System.out.println("File Proof Double spend detected");
                    return false;
                }
                addFileProof(fileProofTransaction.getFileProof());
                break;
            default:
                System.out.println("Invalid transaction type");
                return false;
        }
        return true;
    }
}
