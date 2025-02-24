package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;

import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Model.transactions.TransactionType;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.PoDp;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

public class StorageContractLogic {
    public static Transaction verifyStorageContractBuildTransaction(byte[] fileData, StorageContract contract,
                                                                    KeyManager keyManager) {
        try {
            byte[] computedRoot = PoDp.merkleRootFromData(fileData);
            if (!Arrays.equals(Hex.decodeHex(contract.getMerkleRoot()), computedRoot)) {
                throw new RuntimeException("Invalid merkle root");
            }
            if (!CryptoUtils.ecdsaVerify(Hex.decodeHex(contract.getFccnSignature()), contract.getHash(),
                    keyManager.getFccnPublicKey())) {
                throw new RuntimeException("Invalid fccn signature");
            }
            String storerAddress = CryptoUtils.getWalletAddress(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()));
            if (!storerAddress.equals(contract.getStorerAddress())) {
                throw new RuntimeException("Invalid storer address" + storerAddress + " " + contract.getStorerAddress());
            }
            System.out.println("Storage contract verified" + contract);
            byte[] signature = CryptoUtils.ecdsaSign(contract.getHash(),keyManager.getPrivateKey());
            contract.setStorerSignature(Hex.encodeHexString(signature));
            System.out.println("Storage contract signed" + contract);
            StorageContractSubmission storageContract = StorageContractSubmission.builder()
                    .contract(contract)
                    .storerPublicKey(Hex.encodeHexString(keyManager.getPublicKey().getEncoded()))
                    .build();
            storageContract.setType(TransactionType.STORAGE_CONTRACT_SUBMISSION);
            return storageContract;
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }
}
