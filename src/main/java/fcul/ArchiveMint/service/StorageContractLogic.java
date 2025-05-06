package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMintUtils.Model.*;
import fcul.ArchiveMintUtils.Model.transactions.FileProofTransaction;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Model.transactions.TransactionType;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.PoDp;
import lombok.Data;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import org.mapdb.*;

@Data
public class StorageContractLogic implements Serializable {
    private static final int BEGINNING_NEXT_WINDOW_DELAY = 1; //4 blocks to start the next window
    private DB db;
    //MAP URL->LIST OF CONTRACTS
    private HTreeMap<String, List<StorageContract>> storageContracts;
    //Map storage_contract_hash->proving_window
    private HTreeMap<String, FileProvingWindow> provingWindows;
    private PriorityQueue<FileProvingWindow> expiringContracts = new PriorityQueue<>(new FileProvingWindowComparatorExpiring());
    private PriorityQueue<FileProvingWindow> upcomingContracts = new PriorityQueue<>(new FileProvingWindowComparatorUpcoming());
    private IndexTreeList<FileProvingWindow> currentMinerWindows;
    private HTreeMap<String, List<FileProvingWindow>> fileProvingWindows;

    //This fields can be removed since they are only used for the frontend and no logic needs them
    private BigInteger totalStorage = BigInteger.ZERO;
    public HTreeMap<String, BigInteger> storageUsedHistory;
    public HTreeMap<String, BigInteger> archivedFileHistory;


    public StorageContractLogic(String dbPath) {
        db = DBMaker.fileDB(dbPath)
                .fileMmapEnableIfSupported()
                .closeOnJvmShutdown()
                .make();
        fileProvingWindows = db.hashMap("fileProvingWindows")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.JAVA)
                .createOrOpen();

        storageContracts = db.hashMap("storageContracts")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.JAVA)
                .createOrOpen();
        currentMinerWindows = (IndexTreeList<FileProvingWindow>) db.indexTreeList("some_list",Serializer.JAVA)
                .createOrOpen();

        storageUsedHistory= db.hashMap("storageUsedHistory")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BIG_INTEGER)
                .createOrOpen();

        archivedFileHistory= db.hashMap("archivedFileHistory")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BIG_INTEGER)
                .createOrOpen();
        provingWindows = db.hashMap("provingWindows")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.JAVA)
                .createOrOpen();
    }


    public boolean validSubmission(StorageContractSubmission submission, KeyManager keyManager) {
        StorageContract contract = submission.getContract();
        try {
            if (storageContracts.containsKey(contract.getFileUrl())) {
                List<StorageContract> storageContractList = storageContracts.get(contract.getFileUrl());
                for (StorageContract existingContract : storageContractList) {
                    if (existingContract.equals(contract)) {
                        //System.out.println("Contract Already exists in the blockchain");
                        return false;
                    }
                }
            }
            if (!verifyStorageContract(contract, Hex.decodeHex(submission.getStorerPublicKey()),
                    keyManager.getFccnPublicKey())) {
                //System.out.println("Invalid storage contract");
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean validFileProof(FileProofTransaction fileProofTransaction, PosService posService) {
        try {
            StorageContract contract = null;
            List<StorageContract> storageContractList = storageContracts.get(fileProofTransaction.getFileProof().getFileUrl());
            if (storageContractList == null) {
                //System.out.println("No contract for this file");
                return false;
            }
            for (StorageContract storageContract : storageContractList) {
                if (fileProofTransaction.getFileProof().getStorageContractHash()
                        .equals(Hex.encodeHexString(storageContract.getHash()))) {
                    contract = storageContract;
                    break;
                }
            }
            if (contract == null) {
                //System.out.println("No contract for this file");
                return false;
            }
            if (!CryptoUtils.ecdsaVerify(Hex.decodeHex(fileProofTransaction.getStorerSignature()),
                    Hex.decodeHex(fileProofTransaction.getTransactionId()),
                    Hex.decodeHex(fileProofTransaction.getStorerPublicKey()))) {
                System.out.println("Invalid signature");
                return false;
            }
            String storerAddress = CryptoUtils.getWalletAddress(fileProofTransaction.getStorerPublicKey());
            if (!storerAddress.equals(contract.getStorerAddress())) {
                System.out.println("Invalid storer address");
                return false;
            }

            FileProvingWindow window = provingWindows.get(Hex.encodeHexString(contract.getHash()));
            if (window == null) {
                //System.out.println("No window for this contract");
                return false;
            }
            if (window.getStartBlockIndex() != fileProofTransaction.getFileProof().getStartBlockIndex() ||
                    window.getEndBlockIndex() != fileProofTransaction.getFileProof().getEndBlockIndex()) {
                //System.out.println("Invalid window");
                return false;
            }
            if (!window.getPoDpChallenge().equals(fileProofTransaction.getFileProof().getPoDpChallenge())) {
                //System.out.println("Invalid challenge");
                return false;
            }

            byte[] challenge = Hex.decodeHex(window.getPoDpChallenge());
            byte[] root = Hex.decodeHex(contract.getMerkleRoot());
            if (!posService.verifyFileProof(fileProofTransaction.getFileProof(), challenge,
                    root, contract.getFileLength())) {
                //System.out.println("Invalid proof");
                return false;
            }
            //System.out.println("Valid file proof");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public List<Transaction> generateFileProofs(PosService posService, KeyManager keyManager, Block executedBlock, boolean isSync) {
        List<Transaction> fileProofs = new ArrayList<>();
        if (currentMinerWindows.isEmpty()) {
            return fileProofs;
        }

        List<FileProvingWindow> temp = Collections.synchronizedList(new ArrayList<>());
        fileProofs = currentMinerWindows.parallelStream()
                .filter(window -> {
                    if (window.getPoDpChallenge() == null) {
                        temp.add(window);
                        return false;
                    }
                    return !isSync;
                })
                .map(window -> {
                    try {
                        FileProof fileProof = posService.generateFileProof(window);
                        FileProofTransaction fileProofTransaction = FileProofTransaction.builder()
                                .fileProof(fileProof)
                                .storerPublicKey(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                                .build();
                        fileProofTransaction.setType(TransactionType.FILE_PROOF);
                        fileProofTransaction.setStorerSignature(Hex.encodeHexString(
                                CryptoUtils.ecdsaSign(
                                        Hex.decodeHex(fileProofTransaction.getTransactionId()),
                                        keyManager.getPrivateKey()
                                )
                        ));
                        return (Transaction) fileProofTransaction;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        currentMinerWindows.clear();
        currentMinerWindows.addAll(temp);

        System.out.println("Provas ficheiros geradas: " + fileProofs.size());
        return fileProofs;
    }
    public void processFileProof(FileProofTransaction fileProofTransaction, CoinLogic coinLogic, Block block) {
        try {
            //Process is only ran after validation so revalidation is not needed here
            String url = fileProofTransaction.getFileProof().getFileUrl();
            StorageContract contract = storageContracts.get(url).stream()
                    .filter(storageContract -> fileProofTransaction.getFileProof().getStorageContractHash()
                            .equals(Hex.encodeHexString(storageContract.getHash())))
                    .findFirst().orElse(null);
            if (contract == null) {
                System.out.println("Contract not found File Proof");
                throw new RuntimeException("Contract not found");
            }
            FileProvingWindow window = provingWindows.get(fileProofTransaction.getFileProof().getStorageContractHash());
            if (window == null) {
                System.out.println("Window not found file proof");
                throw new RuntimeException("Window not found");
            }
            if (expiringContracts.remove(window)) {
                //System.out.println("Window removed from expiring contracts");
            }
            if (provingWindows.remove(fileProofTransaction.getFileProof().getStorageContractHash()) == null) {
                System.out.println("Window not found in proving windows");
                throw new RuntimeException("Window not found in proving windows");
            }
            window.setState(FileProvingWindowState.PROVED);
            //provingWindows.put(fileProofTransaction.getFileProof().getStorageContractHash(), null);
            String address = CryptoUtils.getWalletAddress(fileProofTransaction.getStorerPublicKey());
            coinLogic.createCoin(address, new BigInteger(String.valueOf(contract.getValue())), block, true);
            FileProvingWindow newWindow = new FileProvingWindow(contract, null,
                    block.getHeight() + contract.getProofFrequency(),
                    block.getHeight() + contract.getProofFrequency() + contract.getWindowSize());
            upcomingContracts.offer(newWindow);
            String contractHash = Hex.encodeHexString(contract.getHash());

            List<FileProvingWindow> windows = fileProvingWindows.get(contractHash);
            updateFileProvingWindowsEntry(window, windows);
            windows.add(newWindow);
            fileProvingWindows.put(contractHash, windows);

            //System.out.println("Processed file proof: " + fileProofTransaction);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public void addStorageContract(StorageContractSubmission contract, Block block, KeyManager keyManager) throws DecoderException {
        if (storageContracts.containsKey(contract.getContract().getFileUrl())) {
            storageContracts.get(contract.getContract().getFileUrl()).add(contract.getContract());
        } else {
            List<StorageContract> storageContractList = new ArrayList<>();
            storageContractList.add(contract.getContract());
            storageContracts.put(contract.getContract().getFileUrl(), storageContractList);
        }
        totalStorage = totalStorage.add(BigInteger.valueOf(contract.getContract().getFileLength()));
        //Creation of a proving window for the contract
        FileProvingWindow window = new FileProvingWindow(contract.getContract(),
                Hex.encodeHexString(block.calculateHash()),
                block.getHeight() + BEGINNING_NEXT_WINDOW_DELAY,
                block.getHeight() + BEGINNING_NEXT_WINDOW_DELAY +
                        contract.getContract().getWindowSize());
        window.setState(FileProvingWindowState.PROVING);


        //System.out.println("Adding window: " + window);
        expiringContracts.offer(window);

        String contractHash = Hex.encodeHexString(contract.getContract().getHash());
        provingWindows.put(contractHash, window);


        List<FileProvingWindow>windows = new ArrayList<>();
        windows.add(window);
        fileProvingWindows.put(contractHash, windows);

        if (CryptoUtils.getWalletAddress(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                .equals(contract.getContract().getStorerAddress())) {
            //System.out.println("Adding window to miner");
            currentMinerWindows.add(window);
        }
        processSubmissionForFrontend(contract, block);
    }

    private void processSubmissionForFrontend(StorageContractSubmission submission, Block block) {
        //This method is only used for the frontend and can be removed
        if (storageUsedHistory.containsKey(block.getTimeStamp())) {
            storageUsedHistory.put(block.getTimeStamp(), storageUsedHistory.get(block.getTimeStamp()).add(BigInteger.valueOf(submission.getContract().getFileLength())));
        } else {
            storageUsedHistory.put(block.getTimeStamp(), BigInteger.valueOf(submission.getContract().getFileLength()));
        }

        if (archivedFileHistory.containsKey(block.getTimeStamp())) {
            archivedFileHistory.put(block.getTimeStamp(), archivedFileHistory.get(block.getTimeStamp()).add(BigInteger.ONE));
        } else {
            archivedFileHistory.put(block.getTimeStamp(), BigInteger.ONE);
        }
    }


    public void processFileExpiredAndUpcomingProvingWindows(Block toExecute, KeyManager keyManager) {
        processExpiredContracts(toExecute);
        processUpcomingContracts(toExecute, keyManager);
        //System.out.println("Expired contracts without proofs: " + expiringNow);
    }

    private void processUpcomingContracts(Block toExecute, KeyManager keyManager) {
        try {
            List<FileProvingWindow> upcomingNow = new ArrayList<>();
            while (!upcomingContracts.isEmpty() && upcomingContracts.peek().getStartBlockIndex() <= toExecute.getHeight()) {
                upcomingNow.add(upcomingContracts.poll());
            }
            for (FileProvingWindow window : upcomingNow) {
                window.setPoDpChallenge(Hex.encodeHexString(toExecute.calculateHash()));
                expiringContracts.offer(window);
                provingWindows.put(Hex.encodeHexString(window.getContract().getHash()), window);
                if (CryptoUtils.getWalletAddress(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                        .equals(window.getContract().getStorerAddress())) {
                    //System.out.println("Adding window to miner");
                    currentMinerWindows.add(window);
                }
                window.setState(FileProvingWindowState.PROVING);
                List<FileProvingWindow> windows = fileProvingWindows.get(Hex.encodeHexString(window.getContract().getHash()));
                updateFileProvingWindowsEntry(window, windows);
                fileProvingWindows.put(Hex.encodeHexString(window.getContract().getHash()), windows);


                //System.out.println("Upcoming window: " + window);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void processExpiredContracts(Block toExecute) {
        //System.out.println("Expired contracts without proofs: " + expiringNow);
        List<FileProvingWindow> expiringNow = new ArrayList<>();
        while (!expiringContracts.isEmpty() && expiringContracts.peek().getEndBlockIndex() <= toExecute.getHeight()) {
            expiringNow.add(expiringContracts.poll());
        }
        for (FileProvingWindow window : expiringNow) {
            window.setState(FileProvingWindowState.FAILED);
            StorageContract contract = window.getContract();
            FileProvingWindow newWindow = new FileProvingWindow(contract, null,
                    toExecute.getHeight() + contract.getProofFrequency(),
                    toExecute.getHeight() + contract.getProofFrequency() + contract.getWindowSize());
            upcomingContracts.offer(newWindow);
            String contractHash = Hex.encodeHexString(contract.getHash());

            List<FileProvingWindow> windows = fileProvingWindows.get(contractHash);
            updateFileProvingWindowsEntry(window,windows);
            windows.add(newWindow);
            fileProvingWindows.put(contractHash, windows);
        }
    }


    public Transaction verifyStorageContractBuildTransaction(byte[] fileData, StorageContract contract,
                                                             KeyManager keyManager) {
        try {
            byte[] computedRoot = PoDp.merkleRootFromData(fileData);
            if (!Arrays.equals(Hex.decodeHex(contract.getMerkleRoot()), computedRoot)) {
                System.out.println("Invalid merkle root");
                throw new RuntimeException("Invalid merkle root");
            }
            if (!verifyStorageContract(contract, keyManager.getPublicKey().getEncoded(), keyManager.getFccnPublicKey())) {
                System.out.println("Invalid storage contract");
                throw new RuntimeException("Invalid storage contract");
            }

            byte[] signature = CryptoUtils.ecdsaSign(contract.getHash(), keyManager.getPrivateKey());
            contract.setStorerSignature(Hex.encodeHexString(signature));
            StorageContractSubmission storageContract = StorageContractSubmission.builder()
                    .contract(contract)
                    .storerPublicKey(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                    .build();
            storageContract.setType(TransactionType.STORAGE_CONTRACT_SUBMISSION);
            return storageContract;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verifyStorageContract(StorageContract contract, byte[] storerPk, byte[] fccnPk) {
        try {
            if (!CryptoUtils.ecdsaVerify(Hex.decodeHex(contract.getFccnSignature()), contract.getHash(),
                    fccnPk)) {
                throw new RuntimeException("Invalid fccn signature");
            }
            String storerAddress = CryptoUtils.getWalletAddress(Hex.encodeHexString(storerPk));
            if (!storerAddress.equals(contract.getStorerAddress())) {
                throw new RuntimeException("Invalid storer address" + storerAddress + " " + contract.getStorerAddress());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static class FileProvingWindowComparatorExpiring implements Comparator<FileProvingWindow>, Serializable {
        @Override
        public int compare(FileProvingWindow o1, FileProvingWindow o2) {
            return Long.compare(o1.getEndBlockIndex(), o2.getEndBlockIndex());
        }
    }

    private static class FileProvingWindowComparatorUpcoming implements Comparator<FileProvingWindow>, Serializable {
        @Override
        public int compare(FileProvingWindow o1, FileProvingWindow o2) {
            return Long.compare(o1.getStartBlockIndex(), o2.getStartBlockIndex());
        }
    }

    private void updateFileProvingWindowsEntry(FileProvingWindow window, List<FileProvingWindow> windows) {
        boolean found = false;
        for (FileProvingWindow w : windows) {
            if (w.getStartBlockIndex() == window.getStartBlockIndex() &&
                    w.getEndBlockIndex() == window.getEndBlockIndex() &&
                    w.getContract().equals(window.getContract())) {
                w.setState(window.getState());
                found = true;
                break;
            }
        }
        if (!found) {
            System.out.println("Warning: No matching window found for update: " + window);
        }
    }

}