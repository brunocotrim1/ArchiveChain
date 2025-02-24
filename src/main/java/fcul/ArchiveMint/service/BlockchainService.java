package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;


import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Coin;
import fcul.ArchiveMintUtils.Model.Peer;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.PoS;
import fcul.ArchiveMintUtils.Utils.Utils;
import fcul.ArchiveMintUtils.Utils.wesolowskiVDF.ProofOfTime;
import fcul.ArchiveMintUtils.Utils.wesolowskiVDF.WesolowskiVDF;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigInteger;
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
    @Autowired
    private PosService proofOfSpaceService;


    public ResponseEntity<String> archiveFile(MultipartFile file, StorageContract contract) {
        try {

            long availableSpace = nodeConfig.getDedicatedStorage();
            if(availableSpace < file.getSize()){
                return ResponseEntity.status(500).body("Not enough space to store file");
            }
            byte[] fileData = file.getInputStream().readAllBytes();
            Transaction transaction = StorageContractLogic.verifyStorageContractBuildTransaction(fileData, contract,
                    keyManager);
            nodeConfig.setDedicatedStorage(availableSpace - file.getSize());
            plotter.submit(() -> {
                try {
                    System.out.println("File submitted to plotter, available space: " + nodeConfig.getDedicatedStorage());
                    posService.plotFileData(fileData,contract.getFileUrl());
                    addTransaction(transaction);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });


            return ResponseEntity.ok("FileArchived Successfully");
        } catch (Exception e) {
            e.printStackTrace();
            //Send error
            return ResponseEntity.status(500).body("Error processing file: "+e.getMessage());
        }
    }

    public ResponseEntity<byte[]> getMockRetrieve(String fileName) {
        // Simulating fetching file content as byte[]
        byte[] fileContent = posService.retrieveOriginalData(fileName);
        // Set response headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(Utils.getMediaTypeForUrl(fileName));
        headers.setContentLength(fileContent.length);
        if(headers.getContentType() == MediaType.APPLICATION_OCTET_STREAM){
            headers.setContentDispositionFormData("attachment", fileName);
        }
        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }


    ////////////////////////////  Consensus Related Methods ////////////////////////////


    public void startMining() {
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
    }

    public void processBlock(Block block) {
        block.setHash(null);//To clear the hashField of the received block
        block.calculateHash();
        blockProcessingLock.lock();
        try {
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
        } finally {
            blockProcessingLock.unlock();

        }
    }

    private void finalizeBlock() {
        if (finalizedBlockChain.size() - finalizedBlockHeight > 3) {
            if (lastFinalizedBlock != null && lastFinalizedBlock.equals(finalizedBlockChain.get((int) finalizedBlockHeight))) {
                return;
            }
            lastFinalizedBlock = finalizedBlockChain.get((int) finalizedBlockHeight);
            //System.out.println("Finalized block: " + lastFinalizedBlock);
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
        restartThread(block);
        //System.out.println("Block swapped non finalized");
    }


    public void timelordAlgorithm(Block block) {
        Block lastFinalizedBlock = finalizedBlockChain.get(finalizedBlockChain.size() - 1);
        if (!validateSignature(block)) {
            return;
        }

        if (block.getHeight() == lastFinalizedBlock.getHeight()) {
            //Caso de ultimo bloco estar a mesma height do bloco recebido, substituir e extender
            if (!blockIsBetter(block, lastFinalizedBlock)) {
                System.out.println("Block not better");
                return;
            }
            if (!(extendsChain(block, finalizedBlockChain.get((int) block.getHeight() - 1))
                    && validatePoS(block, finalizedBlockChain.get((int) block.getHeight() - 1)))) {
                System.out.println("Not Extending chain or POS invalid");
                return; //Se nao estender a chain
            }

            if (!validatePoT(block)) {
                System.out.println("Pot invalid");
                return;
            }
            //IF a new Block is received and it is better than the last finalized block, we swap and extend the new one
            //Note that we only validate the state but not execute
            swapAndRollBlackBlock(lastFinalizedBlock, block);
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
        } else if (block.getHeight() == lastFinalizedBlock.getHeight() + 1) {
            //Caso em que recebemos o finalizado mas ainda estamos a minar o proximo
            if (blockBeingMined != null) {
                if (!blockIsBetter(block, blockBeingMined)) {
                    //System.out.println("Block not better2"+ block.getHeight() + " " + blockBeingMined.getHeight());
                    return;
                }
            }
            if (!extendsChain(block, lastFinalizedBlock)) {
                System.out.println("Not Extending chain2");
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
            //If a new better block is received we will execute lastFinalizedBlock.getHeight()
            // and validate the new block without executing it
            processBlockState(block, lastFinalizedBlock);
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
        }

    }

    public void swapAndRollBlackBlock(Block blockSwapped, Block newBlock) {

        if (lastExecutedBlockHeight == newBlock.getHeight()) {
            //When the last executed block is equal to the new block we need to validate the block with the state
            // previous to this level execution, if this one is valid we can swap the blocks roll back the state
            // and execute again
            if (!blockchainState.validateBlockWithRollback(newBlock)) {
                return;
            }
            blockchainState.rollBackBlock(blockSwapped);
            finalizedBlockChain.removeLast();
            finalizedBlockChain.add(newBlock);


            String blockHash = Hex.toHexString(newBlock.calculateHash());
            String blockSwappedHash = Hex.toHexString(blockSwapped.calculateHash());
            System.out.println("Swapped block: " + blockSwappedHash + " with block: " + blockHash);
        } else {
            //If the last executed block is not the same as the new block we can just swap the blocks
            finalizedBlockChain.removeLast();
            finalizedBlockChain.add(newBlock);
            String blockHash = Hex.toHexString(newBlock.calculateHash());
            String blockSwappedHash = Hex.toHexString(blockSwapped.calculateHash());
            System.out.println("Swapped block2: " + blockSwappedHash + " with block: " + blockHash);
        }
        blockchainState.executeBlock(newBlock);
        lastExecutedBlockHeight = newBlock.getHeight();
        /*
        if(blockchainState.validateBlockTransactions(newBlock)){
            finalizedBlockChain.removeLast();
            if(lastExecutedBlockHeight == newBlock.getHeight()){
                blockchainState.rollBackBlock(blockSwapped);
                lastExecutedBlockHeight--;
            }

        }*/
    }

    public void processBlockState(Block block, Block toExecute) {
        if (toExecute != null && toExecute.getHeight() > lastExecutedBlockHeight) {
            //If we receive a block that is higher than the lastExecutedBlockHigh we execute the block
            //blockchainState.executeBlock(toExecute);
            //lastExecutedBlockHeight = toExecute.getHeight();
        }
        if (blockchainState.validateBlockTransactions(block)) {
            finalizedBlockChain.add(block);
            blockchainState.executeBlock(block);
            lastExecutedBlockHeight = block.getHeight();
        }
    }


    public boolean blockIsBetter(Block block, Block blockToCompare) {
        double blockQuality = posService.proofQuality(block.getPosProof(), block.getMinerPublicKey());
        double blockToCompareQuality = posService.proofQuality(blockToCompare.getPosProof(),
                blockToCompare.getMinerPublicKey());

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
            processBlockState(block, null);
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

    public List<Coin> getCoins(String address) {
        return blockchainState.getCoins(address);
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
