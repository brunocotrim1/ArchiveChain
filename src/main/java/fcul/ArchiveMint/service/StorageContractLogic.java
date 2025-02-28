package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;

import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.FileProvingWindow;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Model.transactions.TransactionType;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.PoDp;
import org.apache.commons.codec.binary.Hex;

import java.util.*;

public class StorageContractLogic {
    private static final int BEGINNING_NEXT_WINDOW_DELAY = 4; //4 blocks to start the next window

    //MAP URL->LIST OF CONTRACTS
    private HashMap<String, List<StorageContract>> storageContracts = new HashMap<>();
    PriorityQueue<FileProvingWindow> expiringContracts = new PriorityQueue<>
            (Comparator.comparingLong(FileProvingWindow::getEndBlockIndex));


    public boolean validSubmission(StorageContractSubmission submission, KeyManager keyManager) {
        StorageContract contract = submission.getContract();
        try {
            if (storageContracts.containsKey(contract.getFileUrl())) {
                List<StorageContract> storageContractList = storageContracts.get(contract.getFileUrl());
                for (StorageContract existingContract : storageContractList) {
                    if (existingContract.equals(contract)) {
                        System.out.println("Contract Already exists in the blockchain");
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

    public void addStorageContract(StorageContractSubmission contract, Block block) {
        if (storageContracts.containsKey(contract.getContract().getFileUrl())) {
            storageContracts.get(contract.getContract().getFileUrl()).add(contract.getContract());
        } else {
            List<StorageContract> storageContractList = new ArrayList<>();
            storageContractList.add(contract.getContract());
            storageContracts.put(contract.getContract().getFileUrl(), storageContractList);
        }

        //Creation of a proving window for the contract
        FileProvingWindow window = new FileProvingWindow(contract.getContract(),
                Hex.encodeHexString(block.getPreviousHash()),
                block.getHeight() + BEGINNING_NEXT_WINDOW_DELAY,
                block.getHeight() + BEGINNING_NEXT_WINDOW_DELAY +
                        contract.getContract().getProofFrequency());
        System.out.println("Adding window: " + window);
        expiringContracts.offer(window);
    }


    public void processFileProvingWindows(Block toExecute) {
        List<FileProvingWindow> expiringNow = new ArrayList<>();
        while (!expiringContracts.isEmpty() && expiringContracts.peek().getEndBlockIndex() <= toExecute.getHeight()) {
            expiringNow.add(expiringContracts.poll());
        }
        System.out.println("Expired contracts without proofs: " + expiringNow);
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


        StorageContractLogic clone = new StorageContractLogic();
        clone.storageContracts = storageContractMapClone;
        clone.expiringContracts = expiringContractsClone;

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
            System.out.println("Storage contract signed and verified!");
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

    public HashMap<String, List<StorageContract>> getStorageContracts() {
        return storageContracts;
    }
}
