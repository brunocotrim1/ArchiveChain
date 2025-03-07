package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.FileProof;
import fcul.ArchiveMintUtils.Model.FileProvingWindow;
import fcul.ArchiveMintUtils.Model.StorageContract;
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
import java.util.*;

@Data
public class StorageContractLogic implements Serializable {
    private static final int BEGINNING_NEXT_WINDOW_DELAY = 1; //4 blocks to start the next window

    //MAP URL->LIST OF CONTRACTS
    private HashMap<String, List<StorageContract>> storageContracts = new HashMap<>();
    //Map storage_contract_hash->proving_window
    private HashMap<String, FileProvingWindow> provingWindows = new HashMap<>();
    PriorityQueue<FileProvingWindow> expiringContracts = new PriorityQueue<>(new FileProvingWindowComparator());
    private List<FileProvingWindow> currentMinerWindows = new ArrayList<>();


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
                System.out.println("Invalid storage contract");
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
                System.out.println("No contract for this file");
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
                System.out.println("No contract for this file");
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
                System.out.println("No window for this contract");
                return false;
            }
            if (window.getStartBlockIndex() != fileProofTransaction.getFileProof().getStartBlockIndex() ||
                    window.getEndBlockIndex() != fileProofTransaction.getFileProof().getEndBlockIndex()) {
                System.out.println("Invalid window");
                return false;
            }

            byte[] challenge = Hex.decodeHex(window.getPoDpChallenge());
            byte[] root = Hex.decodeHex(contract.getMerkleRoot());
            if (!posService.verifyFileProof(fileProofTransaction.getFileProof(), challenge, root)) {
                System.out.println("Invalid proof");
                return false;
            }
            //System.out.println("Valid file proof");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public List<Transaction> generateFileProofs(PosService posService, KeyManager keyManager, Block executedBlock) {
        List<Transaction> fileProofs = new ArrayList<>();
        if (currentMinerWindows.isEmpty()) {
            return fileProofs;
        }
        List<FileProvingWindow> temp = new ArrayList<>();
        for (FileProvingWindow window : currentMinerWindows) {
            if (executedBlock.getHeight() < window.getStartBlockIndex()) {
                //Window not ready
                temp.add(window);
                continue;
            }
            try {
                FileProof fileProof = posService.generateFileProof(window);
                FileProofTransaction fileProofTransaction = FileProofTransaction.builder()
                        .fileProof(fileProof)
                        .storerPublicKey(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                        .build();
                fileProofTransaction.setType(TransactionType.FILE_PROOF);
                fileProofTransaction.setStorerSignature(Hex.encodeHexString(CryptoUtils.ecdsaSign(Hex.decodeHex(fileProofTransaction.getTransactionId()),
                        keyManager.getPrivateKey())));
                fileProofs.add(fileProofTransaction);
                //System.out.println("File proof generated: " + fileProof);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        //System.out.println("Produced file proofs: " + fileProofs.size());
        currentMinerWindows = temp;
        return fileProofs;
    }

    public void processFileProof(FileProofTransaction fileProofTransaction) {
        //System.out.println("Processing file proof: " + fileProofTransaction);
    }


    public void addStorageContract(StorageContractSubmission contract, Block block, KeyManager keyManager) throws DecoderException {
        if (storageContracts.containsKey(contract.getContract().getFileUrl())) {
            storageContracts.get(contract.getContract().getFileUrl()).add(contract.getContract());
        } else {
            List<StorageContract> storageContractList = new ArrayList<>();
            storageContractList.add(contract.getContract());
            storageContracts.put(contract.getContract().getFileUrl(), storageContractList);
        }

        //Creation of a proving window for the contract
        FileProvingWindow window = new FileProvingWindow(contract.getContract(),
                Hex.encodeHexString(block.calculateHash()),
                block.getHeight() + BEGINNING_NEXT_WINDOW_DELAY,
                block.getHeight() + BEGINNING_NEXT_WINDOW_DELAY +
                        contract.getContract().getProofFrequency());
        //System.out.println("Adding window: " + window);
        expiringContracts.offer(window);
        String contractHash = Hex.encodeHexString(contract.getContract().getHash());
        provingWindows.put(contractHash, window);
        if (CryptoUtils.getWalletAddress(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                .equals(contract.getContract().getStorerAddress())) {
            //System.out.println("Adding window to miner");
            currentMinerWindows.add(window);
        }
    }


    public void processFileExpiredProvingWindows(Block toExecute) {
        List<FileProvingWindow> expiringNow = new ArrayList<>();
        while (!expiringContracts.isEmpty() && expiringContracts.peek().getEndBlockIndex() <= toExecute.getHeight()) {
            expiringNow.add(expiringContracts.poll());
        }
        //System.out.println("Expired contracts without proofs: " + expiringNow);
    }


    public StorageContractLogic clone() {
        HashMap<String, List<StorageContract>> storageContractMapClone = new HashMap<>();


        for (Map.Entry<String, List<StorageContract>> entry : this.storageContracts.entrySet()) {
            String key = entry.getKey();
            List<StorageContract> originalList = entry.getValue();

            // Ensure originalList is not null
            List<StorageContract> clonedList = new ArrayList<>();
            if (originalList != null) {
                for (StorageContract contract : originalList) {
                    clonedList.add(new StorageContract(contract)); // Ensure deep copy
                }
            }

            storageContractMapClone.put(key, clonedList);
        }


        // Deep copy the PriorityQueue
        PriorityQueue<FileProvingWindow> expiringContractsClone = new PriorityQueue<>(this.expiringContracts.comparator());

        // Add cloned StorageContracts
        for (FileProvingWindow fileProvingWindow : this.expiringContracts) {
            expiringContractsClone.offer(fileProvingWindow.clone());
        }

        //Deep copy
        HashMap<String, FileProvingWindow> provingWindowsClone = new HashMap<>();
        for (Map.Entry<String, FileProvingWindow> entry : this.provingWindows.entrySet()) {
            String key = entry.getKey();
            FileProvingWindow value = entry.getValue();
            provingWindowsClone.put(key, value.clone());
        }
        List<FileProvingWindow> currentMinerWindowsClone = new ArrayList<>();
        for (FileProvingWindow window : this.currentMinerWindows) {
            currentMinerWindowsClone.add(window.clone());
        }

        StorageContractLogic clone = new StorageContractLogic();
        clone.storageContracts = storageContractMapClone;
        clone.expiringContracts = expiringContractsClone;
        clone.provingWindows = provingWindowsClone;
        clone.currentMinerWindows = currentMinerWindowsClone;
        return clone;
    }


    public Transaction verifyStorageContractBuildTransaction(byte[] fileData, StorageContract contract,
                                                             KeyManager keyManager) {
        try {
            byte[] computedRoot = PoDp.merkleRootFromData(fileData);
            if (!Arrays.equals(Hex.decodeHex(contract.getMerkleRoot()), computedRoot)) {
                throw new RuntimeException("Invalid merkle root");
            }
            if (!verifyStorageContract(contract, keyManager.getPublicKey().getEncoded(), keyManager.getFccnPublicKey())) {
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

    private static class FileProvingWindowComparator implements Comparator<FileProvingWindow>, Serializable {
        @Override
        public int compare(FileProvingWindow o1, FileProvingWindow o2) {
            return Long.compare(o1.getEndBlockIndex(), o2.getEndBlockIndex());
        }
    }

}
