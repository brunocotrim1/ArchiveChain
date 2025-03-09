package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Peer;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.StorageType;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.PoS;
import fcul.ArchiveMintUtils.Utils.Utils;
import fcul.ArchiveMintUtils.Utils.wesolowskiVDF.ProofOfTime;
import fcul.ArchiveMintUtils.Utils.wesolowskiVDF.WesolowskiVDF;
import fcul.wrapper.FileEncodeProcess;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigInteger;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Data
@Slf4j
public class BlockchainService {
    @Autowired
    private NetworkService net;
    @Autowired
    private KeyManager keyManager;
    @Autowired
    NodeConfig nodeConfig;
    @Autowired
    private PosService posService;
    @Autowired
    private PosService proofOfSpaceService;

    @Autowired
    private BlockchainState blockchainState;

    private List<Peer> peers = new ArrayList<>();
    private final List<Block> finalizedBlockChain = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();

    private WesolowskiVDF vdf = new WesolowskiVDF();
    private int VDF_ITERATIONS = 250000;
    private Thread currentVdfTask = null;

    private byte[] genesisChallenge = Hex.decode("ccd5bb71183532bff220ba46c268991a3ff07eb358e8255a65c30a2dce0e5fbb");
    private Block blockBeingMined = null;
    private long finalizedBlockHeight = 0;
    private long lastExecutedBlockHeight = 0;
    private Block lastFinalizedBlock = null;

    public static ExecutorService processingExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private ExecutorService plotter = Executors.newSingleThreadExecutor();
    private final ReentrantLock blockProcessingLock = new ReentrantLock();
    private final Map<Integer, ArrayList<Block>> cacheFutureBlocks = new HashMap<>();


    public ResponseEntity<String> archiveFile(MultipartFile file, StorageContract contract) {
        try {

            long availableSpace = nodeConfig.getDedicatedStorage();
            if (availableSpace < file.getSize()) {
                return ResponseEntity.status(500).body("Not enough space to store file");
            }
            byte[] fileData = file.getInputStream().readAllBytes();
            Transaction transaction = blockchainState.validateContractSubmission(fileData, contract,
                    keyManager);

            nodeConfig.setDedicatedStorage(availableSpace - file.getSize());

            plotter.submit(() -> {
                try {

                    if (contract.getStorageType().equals(StorageType.AES)) {
                        System.out.println("AES File submitted to plotter, available space: " + nodeConfig.getDedicatedStorage());
                        posService.plotFileData(fileData, contract.getFileUrl());
                        addTransaction(transaction);
                        System.out.println("Storage contract signed and verified!");
                    } else {
                        addTransaction(vdeProccess(fileData, file.getOriginalFilename(), contract));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });

            return ResponseEntity.ok("File Archived Successfully with contract: " + contract);
        } catch (Exception e) {
            e.printStackTrace();
            //Send error
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }


    public Transaction vdeProccess(byte[] file, String orinigalFilename, StorageContract contract) throws Exception {
        String normalizedFileName = Normalizer.normalize(orinigalFilename, Normalizer.Form.NFC);
        int iterationsPerChunk = FileEncodeProcess.iterationsPerChunk(file.length);
        String salt = normalizedFileName + CryptoUtils.getWalletAddress(Hex.toHexString(keyManager.getPublicKey().getEncoded()));
        byte[] iv = CryptoUtils.hash256(salt.getBytes());
        byte[] fileData = FileEncodeProcess.encodeFileVDE(file, iv, 1);
        System.out.println("AES File submitted to plotter, available space: " + nodeConfig.getDedicatedStorage());
        posService.plotFileData(fileData, contract.getFileUrl());
        ByteArrayResource resource = new ByteArrayResource(fileData) {
            @Override
            public String getFilename() {
                return orinigalFilename; // Preserve original filename
            }

            @Override
            public long contentLength() {
                return fileData.length;
            }
        };
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("ArchivalFile", resource);
        body.add("farmerPublicKey", Hex.toHexString(keyManager.getPublicKey().getEncoded()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        // Forward the request to another service

        String targetUrl = nodeConfig.getFccnNetworkAddress() + "/storage/signVDE";
        ResponseEntity<StorageContract> response = restTemplate.exchange(
                targetUrl, HttpMethod.POST, requestEntity, StorageContract.class);
        // Check if request was successful
        if (response.getStatusCode() == HttpStatus.OK) {
            StorageContract storageContract = response.getBody();
            System.out.println("Storage contract signed and verified for VDE!");
            return blockchainState.validateContractSubmission(fileData, storageContract, keyManager);
        } else {
            System.out.println("Request failed! Status Code: " + response.getStatusCode());
        }
        return null;
    }


    public ResponseEntity<byte[]> getMockRetrieve(String fileName) {
        try {
            // Simulating fetching file content as byte[]
            byte[] fileContent = posService.retrieveOriginalData(fileName);
            // Set response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(Utils.getMediaTypeForUrl(fileName));
            headers.setContentLength(fileContent.length);
            if (headers.getContentType() == MediaType.APPLICATION_OCTET_STREAM) {
                headers.setContentDispositionFormData("attachment", fileName);
            }
            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(("Error retrieving file: " + e.getMessage()).getBytes());
        }
    }


    ////////////////////////////  Consensus Related Methods ////////////////////////////


    public void startMining() {
        try {
            log.info("Starting mining");
            if (finalizedBlockHeight > 0 || blockBeingMined != null) {
                return;
            }

            Block genesisBlock = Block.builder()
                    .height(0)
                    .previousHash(new byte[32])
                    .timeStamp(Instant.now().toString())
                    .transactions(new ArrayList<>())
                    .posProof(posService.generatePoSProof(genesisChallenge))
                    .potProof(null)
                    .minerPublicKey(keyManager.getPublicKey().getEncoded())
                    .build();
            genesisBlock.setSignature(CryptoUtils.ecdsaSign(genesisBlock.calculateHash(), keyManager.getPrivateKey()));
            blockBeingMined = genesisBlock;
            restartThread(genesisBlock);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processBlock(Block block) {
        block.setHash(null);//To clear the hashField of the received block
        block.calculateHash();
        blockProcessingLock.lock();
        try {
            for (Transaction transaction : block.getTransactions()) {
                addTransaction(transaction);
            }
            if (block.getHeight() == 0 && finalizedBlockChain.size() <= 1) {
                validateGenesisBlock(block);
            } else {
                if (block.getPotProof() == null) {
                    if (nodeConfig.isTimelord()) {
                        processNonFinalizedBlock(block);
                    }
                } else {
                    timelordAlgorithm(block);
                }
            }
            processCache();
            finalizeBlock();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            blockProcessingLock.unlock();

        }
    }

    private void finalizeBlock() {
        if (finalizedBlockChain.get(finalizedBlockChain.size() - 1).getHeight() - finalizedBlockHeight > 3) {
            Block blockAtHeight = null;

            for (int i = 0; i < finalizedBlockChain.size(); i++) {
                if (finalizedBlockChain.get(i).getHeight() == finalizedBlockHeight) {
                    blockAtHeight = finalizedBlockChain.get(i);
                    break;
                }
            }
            if (blockAtHeight == null) {
                return;
            }
            if (lastFinalizedBlock != null && lastFinalizedBlock.equals(blockAtHeight)) {
                return;
            }

            lastFinalizedBlock = blockAtHeight;
            finalizedBlockChain.remove(blockAtHeight);
            blockchainState.finalize(blockAtHeight);
            //System.out.println("Finalized block: " + Hex.toHexString(blockAtHeight.calculateHash()));
            //System.out.println("New Size: " + finalizedBlockChain.size());
            //Generate Code to finalize here
            finalizedBlockHeight++;
        }

    }

    private void processCache() {
        if (blockBeingMined == null) {
            return;
        }
        if (cacheFutureBlocks.containsKey((int) blockBeingMined.getHeight())) {
            //Reprocess cached blocks at the same height
            for (Block futureBlock : cacheFutureBlocks.get((int) blockBeingMined.getHeight())) {
                compareAndSwapMinedBlock(futureBlock);
            }
            cacheFutureBlocks.remove((int) blockBeingMined.getHeight());
        }
    }

    private void cacheBlock(Block block) {
        if (cacheFutureBlocks.containsKey((int) block.getHeight())) {
            cacheFutureBlocks.get((int) block.getHeight()).add(block);
        } else {
            ArrayList<Block> blocks = new ArrayList<>();
            blocks.add(block);
            cacheFutureBlocks.put((int) block.getHeight(), blocks);
        }
    }

    private void processNonFinalizedBlock(Block block) {
        if (blockBeingMined == null || blockBeingMined.getHeight() != block.getHeight()) {
            cacheBlock(block);
            return;
        }

        compareAndSwapMinedBlock(block);

        if (cacheFutureBlocks.containsKey((int) block.getHeight())) {
            //Reprocess cached blocks at the same height
            for (Block futureBlock : cacheFutureBlocks.get((int) block.getHeight())) {
                compareAndSwapMinedBlock(futureBlock);
            }
            cacheFutureBlocks.remove((int) block.getHeight());
        }

    }

    public void compareAndSwapMinedBlock(Block block) {
        if (!validateSignature(block)) {
            return;
        }
        Block lastFinalizedBlock = finalizedBlockChain.get(finalizedBlockChain.size() - 1);

        if (!blockIsBetter(block, blockBeingMined)) {
            return;
        }
        if (!extendsChain(block, lastFinalizedBlock)) {
            return;
        }
        if (!validatePoS(block, lastFinalizedBlock)) {
            return;
        }
        if (!blockchainState.validateBlockTransactions(block)) {
            return;
        }
        if (currentVdfTask != null) {
            currentVdfTask.interrupt();
            currentVdfTask = null;
        }

        blockchainState.addTransactions(blockchainState.getCensuredTransactions(blockBeingMined.getTransactions(),
                block.getTransactions()));

        restartThread(block);
        //System.out.println("Block swapped non finalized");
    }


    public void timelordAlgorithm(Block block) {
        Block lastFinalizedBlock = finalizedBlockChain.getLast();
        if (block.equals(lastFinalizedBlock)) {
            return;
        }
        if (!validateSignature(block)) {
            return;
        }

        if (block.getHeight() == lastFinalizedBlock.getHeight()) {
            //Caso de ultimo bloco estar a mesma height do bloco recebido, substituir e extender
            if (!blockIsBetter(block, lastFinalizedBlock)) {
                //System.out.println("Block not better " + Hex.toHexString(block.calculateHash()) + " : " + Hex.toHexString(lastFinalizedBlock.calculateHash()));
                return;
            }
            //Check if the new block extends the block with height - 1 which is the before last finalized block
            if (!(extendsChain(block, finalizedBlockChain.get(finalizedBlockChain.size() - 2))
                    && validatePoS(block, finalizedBlockChain.get(finalizedBlockChain.size() - 2)))) {
                System.out.println("Not Extending chain or POS invalid");
                return; //Se nao estender a chain
            }

            if (!validatePoT(block)) {
                System.out.println("Pot invalid");
                return;
            }
            //IF a new Block is received and it is better than the last finalized block, we swap and extend the new one
            //Note that we only validate the state but not execute
            if (!swapAndRollBlackBlock(lastFinalizedBlock, block)) {
                System.out.println("Failed Validating and executing1");
                return;
            }
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
        } else if (block.getHeight() == lastFinalizedBlock.getHeight() + 1) {
            //Caso em que recebemos o finalizado mas ainda estamos a minar o proximo
            if (blockBeingMined != null) {
                if (!blockIsBetter(block, blockBeingMined)) {
                    //System.out.println("Block not better being mined" + Hex.toHexString(block.calculateHash()) + " : " +
                          //  Hex.toHexString(blockBeingMined.calculateHash()));
                    return;
                }
            }
            if (!extendsChain(block, lastFinalizedBlock)) {
                System.out.println("Not Extending chain2:" +block.getHeight() + " " + lastFinalizedBlock.getHeight()+ ":" + Hex.toHexString(block.calculateHash()) + " : " +
                        Hex.toHexString(lastFinalizedBlock.calculateHash()));
                return;
            }

            if (!validatePoS(block, lastFinalizedBlock)) {
                System.out.println("Pos invalid2");
                return;
            }

            if (!validatePoT(block)) {
                System.out.println("Pot invalid2");
                return;
            }
            //if a new block is received we validate and execute it
            if (!processBlockState(block)) {
                System.out.println("Failed Validating and executing2");
                return;
            }
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
        }

    }

    public boolean swapAndRollBlackBlock(Block blockSwapped, Block newBlock) {

        if (lastExecutedBlockHeight == newBlock.getHeight()) {
            //When the last executed block is equal to the new block we need to validate the block with the state
            // previous to this level execution, if this one is valid we can swap the blocks roll back the state
            // and execute again
            if (!blockchainState.validateBlockWithRollback(newBlock)) {
                System.out.println("Failed Validating and executing rollback");
                return false;
            }
            blockchainState.rollBackBlock(blockSwapped);
            finalizedBlockChain.removeLast();
            finalizedBlockChain.add(newBlock);
            String blockHash = Hex.toHexString(newBlock.calculateHash());
            String blockSwappedHash = Hex.toHexString(blockSwapped.calculateHash());
            System.out.println("Swapped block: " + blockSwappedHash + " with block: " + blockHash);
        } else {
            //If the last executed block is not the same as the new block we can just swap the blocks

            if (!blockchainState.validateBlockTransactions(newBlock)) {
                System.out.println("Failed Validating and executing");
                return false;
            }
            finalizedBlockChain.removeLast();
            finalizedBlockChain.add(newBlock);
            String blockHash = Hex.toHexString(newBlock.calculateHash());
            String blockSwappedHash = Hex.toHexString(blockSwapped.calculateHash());
            System.out.println("Swapped block2: " + blockSwappedHash + " with block: " + blockHash);
        }

        //ADD Transactions censured back into the mempool for future processing
        blockchainState.addTransactions(blockchainState.getCensuredTransactions(blockSwapped.getTransactions(),
                newBlock.getTransactions()));

        List<Transaction> transactions = blockchainState.executeBlock(newBlock);
        for (Transaction transaction : transactions) {
            addTransaction(transaction);
        }
        lastExecutedBlockHeight = newBlock.getHeight();
        /*
        if(blockchainState.validateBlockTransactions(newBlock)){
            finalizedBlockChain.removeLast();
            if(lastExecutedBlockHeight == newBlock.getHeight()){
                blockchainState.rollBackBlock(blockSwapped);
                lastExecutedBlockHeight--;
            }

        }*/
        return true;
    }

    public boolean processBlockState(Block block) {
        if (blockchainState.validateBlockTransactions(block)) {
            finalizedBlockChain.add(block);
            List<Transaction> transactions = blockchainState.executeBlock(block);
            for (Transaction transaction : transactions) {
                addTransaction(transaction);
            }
            lastExecutedBlockHeight = block.getHeight();
            return true;
        }
        return false;
    }


    public boolean blockIsBetter(Block block, Block blockToCompare) {
        double blockQuality = posService.proofQuality(block.getPosProof(), block.getMinerPublicKey());
        double blockToCompareQuality = posService.proofQuality(blockToCompare.getPosProof(),
                blockToCompare.getMinerPublicKey());
        block.setQuality(blockQuality);
        blockToCompare.setQuality(blockToCompareQuality);
        if (blockQuality > blockToCompareQuality) {
            return true;
        } else if (blockQuality == blockToCompareQuality) {
            return CryptoUtils.compareTo(block.calculateHash(), blockToCompare.calculateHash()) < 0;
        }
        return false;
    }

    private void extendNonFinalizedBlock(Block blockToExtend) {
        if (currentVdfTask != null) {
            currentVdfTask.interrupt();
            currentVdfTask = null;
        }
        try {
            Block block = Block.builder()
                    .height(blockToExtend.getHeight() + 1)
                    .previousHash(blockToExtend.calculateHash())
                    .timeStamp(Instant.now().toString())
                    .posProof(posService.generatePoSProof(CryptoUtils.hash256(blockToExtend.getPotProof().getProof().toByteArray())))
                    .minerPublicKey(keyManager.getPublicKey().getEncoded())
                    .transactions(blockchainState.getValidTransactions())
                    .build();
            block.setSignature(CryptoUtils.ecdsaSign(block.calculateHash(), keyManager.getPrivateKey()));
            net.broadcastBlock(block);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error extending block: " + e.getMessage());
        }
    }

    private void extendFinalizedBlock(Block blockToExtend) {
        if (currentVdfTask != null) {
            currentVdfTask.interrupt();
            currentVdfTask = null;
        }
        try {
            blockBeingMined = null;
            Block block = Block.builder()
                    .height(blockToExtend.getHeight() + 1)
                    .previousHash(blockToExtend.calculateHash())
                    .timeStamp(Instant.now().toString())
                    .posProof(posService.generatePoSProof(CryptoUtils.hash256(blockToExtend.getPotProof().getProof().toByteArray())))
                    .minerPublicKey(keyManager.getPublicKey().getEncoded())
                    .transactions(blockchainState.getValidTransactions())
                    .build();
            block.setSignature(CryptoUtils.ecdsaSign(block.calculateHash(), keyManager.getPrivateKey()));
            blockBeingMined = block;
            restartThread(block);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error extending block: " + e.getMessage());
        }
    }

    public void restartThread(Block block) {
        if (currentVdfTask != null) {
            currentVdfTask.interrupt();
            currentVdfTask = null;
        }
        currentVdfTask = new Thread(new VdfTask(block));
        currentVdfTask.start();
    }

    public void validateGenesisBlock(Block block) {
        if (finalizedBlockChain.size() == 1) {
            //Verify if our genesis being possibly mined is better than the one we have if no ignore, if yes validate
            // and substitute
            Block blockBeingMined = finalizedBlockChain.get(0);
            if (PoS.proofQuality(block.getPosProof(), block.getPosProof().getChallenge(), block.getMinerPublicKey()) <=
                    PoS.proofQuality(blockBeingMined.getPosProof(), blockBeingMined.getPosProof().getChallenge(), blockBeingMined.getMinerPublicKey())) {
                return;
            }
        }
        if (validateSignature(block) && validatePoT(block) && validatePoS(block, null)) {
            finalizedBlockChain.clear();
            processBlockState(block);
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
        }
    }

    private boolean extendsChain(Block block, Block lastFinalizedBlock) {
        if (lastFinalizedBlock == null) {
            return Arrays.equals(block.getPreviousHash(), new byte[32]);
        }
        return Arrays.equals(lastFinalizedBlock.calculateHash(), block.getPreviousHash());
    }

    private boolean validateSignature(Block block) {
        return CryptoUtils.ecdsaVerify(block.getSignature(), block.calculateHash(), block.getMinerPublicKey());
    }

    private boolean validatePoT(Block block) {
        ProofOfTime pot = block.getPotProof();
        byte[] challenge = new BigInteger(block.calculateHash())
                .add(new BigInteger(block.getPosProof().getSlothResult().getHash().toByteArray())).toByteArray();
        return CryptoUtils.ecdsaVerify(pot.getSignature(), pot.hash(), pot.getPublicKeyTimelord())
                && vdf.verify(challenge, pot.getT(), pot.getLPrime(), pot.getProof());
    }

    private boolean validatePoS(Block block, Block lastFinalizedBlock) {
        byte[] challenge = null;
        if (lastFinalizedBlock == null) {
            challenge = genesisChallenge;
        } else {
            //Challenge of PoS is the hash of the PoT of the previous block height
            challenge = CryptoUtils.hash256(lastFinalizedBlock.getPotProof().getProof().toByteArray());
        }
        return posService.verifyProof(block.getPosProof(), challenge, block.getMinerPublicKey());
    }

    public ResponseEntity<String> receiveBlock(Block block) {
       /* Thread t = new Thread(() -> deliverBlock(block));
        t.start();*/
        processingExecutor.submit(() -> processBlock(block));
        return ResponseEntity.ok("Block received");
    }

    public ResponseEntity<String> addTransaction(Transaction transaction) {
        if (!blockchainState.addTransaction(transaction)) {
            return ResponseEntity.ok("Transaction not added");
        }
        net.broadcastTransaction(transaction);
        return ResponseEntity.ok("Transaction added and broadcasted");
    }

    public CoinLogic getCoinLogic() {
        return blockchainState.getCoinLogic();
    }

    public StorageContractLogic getStorageContractLogic() {
        return blockchainState.getStorageContractLogic();
    }


    public class VdfTask implements Runnable {

        private final Block block;

        public VdfTask(Block block) {
            this.block = block;
        }

        @Override
        public void run() {
            //log.info("Starting VDF computation for block: " + block);
            blockBeingMined = block;
            byte[] potChallenge = new BigInteger(block.calculateHash())
                    .add(new BigInteger(block.getPosProof().getSlothResult().getHash().toByteArray())).toByteArray();
            int iterations = (int) Math.round(VDF_ITERATIONS / posService.proofQuality(block.getPosProof(), block.getMinerPublicKey()));
            ProofOfTime pot = vdf.eval(potChallenge, iterations);
            if (pot == null || Thread.interrupted()) {
                return;
            }
            pot.setPublicKeyTimelord(keyManager.getPublicKey().getEncoded());
            pot.setSignature(CryptoUtils.ecdsaSign(pot.hash(), keyManager.getPrivateKey()));
            block.addProofOfTime(pot);
            try {
                blockProcessingLock.lock();

                if(!finalizedBlockChain.isEmpty()) {
                    lastFinalizedBlock = finalizedBlockChain.getLast();

                    if(Thread.interrupted()){
                        return;
                    }
                    if (!extendsChain(block, lastFinalizedBlock)) {
                        System.out.println("Thread should be interrupted");
                        return;
                    }
                }

                if(blockBeingMined == null){
                    return;
                }
                blockBeingMined = null;
                currentVdfTask = null;
            } finally {
                blockProcessingLock.unlock();
            }
            //System.out.println("Pot computed");
            net.broadcastBlock(block);
            receiveBlock(block);
            //BROADCAST BLOCK
        }
    }
}
