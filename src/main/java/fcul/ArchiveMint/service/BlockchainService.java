package fcul.ArchiveMint.service;
import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.model.Peer;
import fcul.ArchiveMint.model.transactions.Transaction;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private PosService posService;

    private List<Peer> peers = new ArrayList<>();
    private final List<Block> finalizedBlockChain = new ArrayList<>();

    private WesolowskiVDF vdf = new WesolowskiVDF();
    private int VDF_ITERATIONS = 500000;
    private Future<?> currentVdfTask = null;

    private byte[] genesisChallenge = Hex.decode("ccd5bb71183532bff220ba46c268991a3ff07eb358e8255a65c30a2dce0e5fbb");
    private Block blockBeingMined = null;
    private long finalizedBlockHeight = 0;
    private Block lastFinalizedBlock = null;
    private ExecutorService vdfExecutor = Executors.newSingleThreadExecutor();
    public static ExecutorService processingExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ReentrantLock blockProcessingLock = new ReentrantLock();

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
        currentVdfTask = vdfExecutor.submit(new VdfTask(genesisBlock));
    }
    public void processBlock(Block block) {
        long time = System.currentTimeMillis();
        block.calculateHash();
        blockProcessingLock.lock();
        try {
            if (block.getHeight() == 0 && finalizedBlockChain.size() <= 1) {
                validateGenesisBlock(block);
            } else {
                validateBlock(block);
            }
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

    public void validateBlock(Block block) {
        Block lastFinalizedBlock = finalizedBlockChain.get(finalizedBlockChain.size() - 1);
        double blockQuality = posService.proofQuality(block.getPosProof(), block.getMinerPublicKey());
        double lastFinalizedBlockQuality = posService.proofQuality(lastFinalizedBlock.getPosProof(),
                lastFinalizedBlock.getMinerPublicKey());
        if (!(validateSignature(block) && validatePoT(block))) {
            return;
        }
        //System.out.println(Hex.toHexString(block.calculateHash()) + "Quality: " + blockQuality);
        if (block.getHeight() == lastFinalizedBlock.getHeight()) {
            if (blockQuality <= lastFinalizedBlockQuality && new BigInteger(block.calculateHash())
                    .compareTo(new BigInteger(lastFinalizedBlock.calculateHash())) <= 0) {
                return;
            }
            if (extendsChain(block, finalizedBlockChain.get((int) block.getHeight() - 1))
                    && validatePoS(block, finalizedBlockChain.get((int) block.getHeight() - 1))) {
                finalizedBlockChain.remove(lastFinalizedBlock);
                finalizedBlockChain.add(block);
                extendBlock(block);
            }
        } else if (block.getHeight() == lastFinalizedBlock.getHeight() + 1) {
            if (extendsChain(block, lastFinalizedBlock) && validatePoS(block, lastFinalizedBlock)) {
                if (blockBeingMined != null) {
                    double blockBeingMinedQuality = posService.proofQuality(blockBeingMined.getPosProof(),
                            blockBeingMined.getMinerPublicKey());
                    if (blockQuality <= blockBeingMinedQuality && new BigInteger(block.calculateHash())
                            .compareTo(new BigInteger(blockBeingMined.calculateHash())) <= 0) {
                        return;
                    }

                }
                finalizedBlockChain.add(block);
                extendBlock(block);
            }

        }
    }


    private void extendBlock(Block blockToExtend) {
        if (currentVdfTask != null) {
            currentVdfTask.cancel(true);
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
            long time = System.currentTimeMillis();
            currentVdfTask = vdfExecutor.submit(new VdfTask(block));

        } catch (Exception e) {
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
            extendBlock(block);
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
        return vdf.verify(challenge, pot.getT(), pot.getLPrime(), pot.getProof());
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
                    .add(new BigInteger(block.getPosProof().getSlothResult().getHash().toByteArray())).toByteArray();
            int iterations = (int) Math.round(VDF_ITERATIONS * posService.proofQuality(block.getPosProof(), block.getMinerPublicKey()));
            ProofOfTime pot = vdf.eval(potChallenge, iterations);
            block.addProofOfTime(pot);
            blockProcessingLock.lock();
            try {
                blockBeingMined = null;
                currentVdfTask = null;
            } finally {
                blockProcessingLock.unlock();
            }
            net.broadcastBlock(block);
            receiveBlock(block);

            //BROADCAST BLOCK
        }
    }
}
