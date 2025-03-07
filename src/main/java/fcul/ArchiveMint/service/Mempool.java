package fcul.ArchiveMint.service;


import fcul.ArchiveMintUtils.Model.transactions.Transaction;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Mempool {
    private final ConcurrentLinkedQueue<Transaction> transactionQueue = new ConcurrentLinkedQueue<>();
    private final HashMap<String, Transaction> transactionMap = new HashMap<>();

    public boolean addTransaction(Transaction transaction) {
        if(transactionMap.containsKey(transaction.getTransactionId())) {
            return false;
        }
        transactionQueue.add(transaction);
        transactionMap.put(transaction.getTransactionId(), transaction);
        return true;
    }

    public int size() {
        return transactionQueue.size();
    }

    public Transaction poll(){
        Transaction transaction = transactionQueue.poll();
        if(transaction != null) {
            transactionMap.remove(transaction.getTransactionId());
        }
        return transaction;
    }
}
