package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.model.Peer;
import fcul.ArchiveMint.model.Transaction;
import fcul.ArchiveMint.utils.CryptoUtils;
import fcul.ArchiveMint.utils.PoS;
import fcul.ArchiveMint.utils.wesolowskiVDF.ProofOfTime;
import fcul.ArchiveMint.utils.wesolowskiVDF.WesolowskiVDF;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Data
@Slf4j
public class BlockchainService2 {
    @Autowired
    private NetworkService net;
    @Autowired
    private KeyManager keyManager;
    @Autowired
    NodeConfig nodeConfig;
    @Autowired
    private PosService posService;

    private List<Peer> peers = new ArrayList<>();
    private final List<Block> finalizedBlockChain = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();

    private WesolowskiVDF vdf = new WesolowskiVDF();
    private int VDF_ITERATIONS = 500000;
    private Thread currentVdfTask = null;

    private byte[] genesisChallenge = Hex.decode("ccd5bb71183532bff220ba46c268991a3ff07eb358e8255a65c30a2dce0e5fbb");
    private Block blockBeingMined = null;
    private long finalizedBlockHeight = 0;
    private Block lastFinalizedBlock = null;
    public static ExecutorService processingExecutor = Executors.newFixedThreadPool(8);
    private final ReentrantLock blockProcessingLock = new ReentrantLock();
    private final Map<Integer, ArrayList<Block>> cacheFutureBlocks = new HashMap<>();

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
        currentVdfTask = new Thread(new VdfTask(genesisBlock));
        currentVdfTask.start();
    }

    public void processBlock(Block block) {
        block.setHash(null);
        block.calculateHash();
        blockProcessingLock.lock();
        try {
            if (block.getHeight() == 0 && finalizedBlockChain.size() <= 1) {
                validateGenesisBlock(block);
            } else {
                timelordAlgorithm(block);
            }
            finalizeBlock();
        } finally {
            blockProcessingLock.unlock();

        }
    }

    private void processCacheBlocks() {
        Block block = blockBeingMined;
        if(!nodeConfig.isTimelord()){
            return;
        }
        if (cacheFutureBlocks.containsKey((int) block.getHeight())) {
            ArrayList<Block> blocks = cacheFutureBlocks.get((int) block.getHeight());
            Block bestBlock = null;
            for (Block blockCached : blocks) {
                if(bestBlock == null){
                    bestBlock = blockCached;
                    continue;
                }
                if(!validatePoS(blockCached, finalizedBlockChain.get((int) block.getHeight() - 1))){
                    continue;
                }
                if(blockIsBetter(blockCached, bestBlock)){
                    bestBlock = blockCached;
                }
            }
            if(bestBlock!=null){
                if(blockIsBetter(bestBlock,blockBeingMined)){
                    currentVdfTask.interrupt();
                    currentVdfTask = new Thread(new VdfTask(bestBlock));
                    currentVdfTask.start();
                    System.out.println("Replaced by Cache: " + bestBlock);
                }
            }

            cacheFutureBlocks.remove((int) block.getHeight());
        }
    }

    private void cacheBlock(Block block) {
        int height = (int) block.getHeight();
        if (cacheFutureBlocks.containsKey(height)) {
            cacheFutureBlocks.get(height).add(block);
        }
        ArrayList<Block> blocks = new ArrayList<>();
        blocks.add(block);
        cacheFutureBlocks.put(height, blocks);
    }

    private void finalizeBlock() {
        if (finalizedBlockChain.size() - finalizedBlockHeight > 3) {
            if (lastFinalizedBlock != null && lastFinalizedBlock.equals(finalizedBlockChain.get((int) finalizedBlockHeight))) {
                return;
            }
            lastFinalizedBlock = finalizedBlockChain.get((int) finalizedBlockHeight);
            System.out.println("Finalized block: " + lastFinalizedBlock);
            //Generate Code to finalize here
            finalizedBlockHeight++;

        }

    }


    public ResponseEntity<String> receiveBlock(Block block) {
       /* Thread t = new Thread(() -> deliverBlock(block));
        t.start();*/
        processingExecutor.submit(() -> processBlock(block));
        return ResponseEntity.ok("Block received");
    }


    public void timelordAlgorithm(Block block) {
        Block lastFinalizedBlock = finalizedBlockChain.get(finalizedBlockChain.size() - 1);
        if (!validateSignature(block)) {
            System.out.println("Invalid signature");
            return;
        }
        if (!isFinalized(block)) {
            cacheBlock(block);
            processCacheBlocks();
        }
        if (block.getHeight() == lastFinalizedBlock.getHeight()) {
            //Caso de ultimo bloco estar a mesma height do bloco recebido, substituir e extender
            if (!blockIsBetter(block, lastFinalizedBlock)) {
                return;
            }
            if (!(extendsChain(block, finalizedBlockChain.get((int) block.getHeight() - 1))
                    && validatePoS(block, finalizedBlockChain.get((int) block.getHeight() - 1)))) {
                return; //Se nao estender a chain
            }

            if (!validatePoT(block)) {
                return;
            }
            finalizedBlockChain.remove(lastFinalizedBlock);
            finalizedBlockChain.add(block);
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                farmBlockNonFinalized(block);
            }
        } else if (block.getHeight() == lastFinalizedBlock.getHeight() + 1) {
            //Caso em que recebemos o finalizado mas ainda estamos a minar o proximo
            if (!(extendsChain(block, lastFinalizedBlock) && validatePoS(block, lastFinalizedBlock))) {
                return;
            }
            if (blockBeingMined != null) {
                if (!blockIsBetter(block, blockBeingMined)) {
                    return;
                }
            }
            if (!validatePoT(block)) {
                return;
            }
            finalizedBlockChain.add(block);
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                farmBlockNonFinalized(block);
            }
        } else if (block.getHeight() > lastFinalizedBlock.getHeight() + 1) {
            cacheBlock(block);
            processCacheBlocks();
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
    private void farmBlockNonFinalized(Block blockToExtend) {
        try {
            if (currentVdfTask != null) {
                currentVdfTask.interrupt();
                currentVdfTask = null;
            }
            Block block = Block.builder()
                    .height(blockToExtend.getHeight() + 1)
                    .previousHash(blockToExtend.calculateHash())
                    .timeStamp(Instant.now().toString())
                    .posProof(posService.generatePoSProof(CryptoUtils.hash256(blockToExtend.getPotProof().getProof().toByteArray())))
                    .minerPublicKey(keyManager.getPublicKey().getEncoded())
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
        }
        try {
            blockBeingMined = null;
            Block block = Block.builder()
                    .height(blockToExtend.getHeight() + 1)
                    .previousHash(blockToExtend.calculateHash())
                    .timeStamp(Instant.now().toString())
                    .posProof(posService.generatePoSProof(CryptoUtils.hash256(blockToExtend.getPotProof().getProof().toByteArray())))
                    .minerPublicKey(keyManager.getPublicKey().getEncoded())
                    .build();
            block.setSignature(CryptoUtils.ecdsaSign(block.calculateHash(), keyManager.getPrivateKey()));
            currentVdfTask = new Thread(new VdfTask(block));
            currentVdfTask.start();
            blockBeingMined = block;
            processCacheBlocks();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error extending block: " + e.getMessage());
        }
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
            finalizedBlockChain.add(block);
            extendFinalizedBlock(block);
        }
    }

    private boolean isFinalized(Block block) {
        return block.getPotProof() != null;
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
                .add(new BigInteger(pot.getPublicKeyTimelord()))
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
                    .add(new BigInteger(keyManager.getPublicKey().getEncoded()))
                    .add(new BigInteger(block.getPosProof().getSlothResult().getHash().toByteArray())).toByteArray();
            int iterations = (int) Math.round(VDF_ITERATIONS * posService.proofQuality(block.getPosProof(), block.getMinerPublicKey()));
            ProofOfTime pot = vdf.eval(potChallenge, iterations);
            pot.setPublicKeyTimelord(keyManager.getPublicKey().getEncoded());
            pot.setSignature(CryptoUtils.ecdsaSign(pot.hash(), keyManager.getPrivateKey()));
            block.addProofOfTime(pot);
            blockProcessingLock.lock();
            try {
                blockBeingMined = null;
                currentVdfTask = null;
            } finally {
                blockProcessingLock.unlock();
            }
            if(Thread.interrupted()){
                return;
            }
            net.broadcastBlock(block);
            receiveBlock(block);
            //BROADCAST BLOCK
        }
    }
}
