package fcul.ArchiveMint.service;

import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.HashMap;
import java.util.concurrent.ConcurrentNavigableMap;

public class Mempool {
    private final ConcurrentLinkedQueue<Transaction> transactionQueue;
    private final ConcurrentNavigableMap<String, Transaction> transactionMap;
    private final DB db;

    public Mempool(String id) {
        // Create database file path
        String dbPath = "./nodes/" + id + "/databases/";
        String filename = "mempool.db";
        new File(dbPath).mkdirs(); // Create directories if they don't exist
        // Initialize MapDB database
        this.db = DBMaker
                .fileDB(dbPath+filename)
                .fileMmapEnableIfSupported() // Use memory-mapped files if supported
                .closeOnJvmShutdown()
                .make();

        // Initialize persistent queue (using MapDB's atomic circular queue)
        this.transactionQueue = new ConcurrentLinkedQueue<>();
        // Load existing transactions from MapDB to queue
        this.transactionMap = db
                .treeMap("transactionMap", Serializer.STRING, Serializer.JAVA)
                .createOrOpen();

        // Rebuild queue from persistent map to maintain order
        transactionQueue.addAll(transactionMap.values());
    }

    public boolean addTransaction(List<Transaction> transaction) {

        for (Transaction tx : transaction) {
            if (transactionMap.containsKey(tx.getTransactionId())) {
                return false;
            }
            transactionQueue.add(tx);
            transactionMap.put(tx.getTransactionId(), tx);
        }
        db.commit(); // Persist changes
        return true;
    }

    public int size() {
        return transactionQueue.size();
    }

    public Transaction poll() {
        Transaction transaction = transactionQueue.poll();
        if (transaction != null) {
            transactionMap.remove(transaction.getTransactionId());
            db.commit(); // Persist changes
        }
        return transaction;
    }

    // Clean up resources
    public void close() {
        if (!db.isClosed()) {
            db.commit();
            db.close();
        }
    }
}