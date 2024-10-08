package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.model.Peer;
import fcul.ArchiveMint.model.Transaction;
import fcul.ArchiveMint.utils.CryptoUtils;
import fcul.ArchiveMint.utils.PoS;
import fcul.ArchiveMint.utils.PoS.PosProof;
import fcul.ArchiveMint.utils.wesolowskiVDF.ProofOfTime;
import fcul.ArchiveMint.utils.wesolowskiVDF.WesolowskiVDF;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Data
@Slf4j
public class BlockchainService {
    private List<Peer> peers = new ArrayList<>();
    private final List<Block> finalizedBlockChain = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();
    private byte[] genesisChallenge = Hex.decode("ccd5bb71183532bff220ba46c268991a3ff07eb358e8255a65c30a2dce0e5fbb");
    private long currentHeight = 0;
    @Autowired
    private KeyManager keyManager;
    @Autowired
    private PosService posService;
    private WesolowskiVDF vdf = new WesolowskiVDF();
    private int VDF_ITERATIONS = 20000;
    private Thread currentVdfTask = null;
    private Block blockBeingMined = null;

    public void startMining() {
        if (currentHeight > 0) {
            return;
        }
        PosProof genesisChallengeProof = posService.generatePoSProof(genesisChallenge);
        double quality = posService.proofQuality(genesisChallengeProof, keyManager.getPublicKey().getEncoded());
        Block genesisBlock = Block.builder()
                .blockHeight(0)
                .previousHash(new byte[32])
                .timeStamp(Instant.now().toString())
                .transactions(new ArrayList<>())
                .posProof(posService.generatePoSProof(genesisChallenge))
                .potProof(null)
                .minerPublicKey(keyManager.getPublicKey().getEncoded())
                .build();
        genesisBlock.setSignature(CryptoUtils.ecdsaSign(genesisBlock.getHash(), keyManager.getPrivateKey()));
        VdfTask genesisVdfTask = new VdfTask(genesisBlock);
        Thread genesisThread = new Thread(genesisVdfTask);
        genesisThread.start();
        currentVdfTask = genesisThread;
        finalizedBlockChain.add(genesisBlock);
    }

    public synchronized void deliverBlock(Block block) {
        synchronized (finalizedBlockChain){
            Block lastFinalizedBlock = lastFinalizedBlock();
            if(lastFinalizedBlock == null){
                validateGenesisBlock(block);
            }else{
                validateBlock(block,lastFinalizedBlock);
            }
        }
    }

    private Block lastFinalizedBlock() {
        //Block with complete PoT
        if(finalizedBlockChain.isEmpty()){
            return null;
        }
        for(int i = finalizedBlockChain.size() - 1; i >= 0; i--){
            if(finalizedBlockChain.get(i).getPotProof() != null){
                return finalizedBlockChain.get(i);
            }
        }
        return null;
    }

    public void validateBlock(Block block,Block lastFinalizedBlock){
        if(block.getBlockHeight() < lastFinalizedBlock.getBlockHeight()){
            return;
        }
        if(block.equals(lastFinalizedBlock) && finalizedBlockChain.size() == block.getBlockHeight()+1){
            currentVdfTask.interrupt();
            block = extendBlock(block);
            currentVdfTask = new Thread(new VdfTask(block));
            currentVdfTask.start();
            return;
        }
        if(lastFinalizedBlock.getBlockHeight() == block.getBlockHeight()){
            if(posService.proofQuality(block.getPosProof(),block.getMinerPublicKey()) <=
                    posService.proofQuality(lastFinalizedBlock.getPosProof(),lastFinalizedBlock.getMinerPublicKey())){
                return;
            }

            if (validateSignature(block) && validatePoT(block) &&
                    validatePoS(block,finalizedBlockChain.get((int) lastFinalizedBlock.getBlockHeight() - 1))) {
                //Case where finalized-blocks are equal height and new one is better replace block
                finalizedBlockChain.remove(lastFinalizedBlock);
                finalizedBlockChain.add(block);
            }
        } else if (block.getBlockHeight() == lastFinalizedBlock.getBlockHeight() + 1) {

            if (extendsChain(block, lastFinalizedBlock) && validateSignature(block) && validatePoT(block) &&
                    validatePoS(block, lastFinalizedBlock)) {

                if(blockBeingMined !=null){
                    Block blockBeingMined = finalizedBlockChain.get((int) lastFinalizedBlock.getBlockHeight()+1);
                    if(posService.proofQuality(block.getPosProof(),block.getMinerPublicKey()) <=
                            posService.proofQuality(blockBeingMined.getPosProof(),blockBeingMined.getMinerPublicKey())){
                        //Case where VDF being calculated is better then the one received we ignore new block
                        return;
                    }
                }
                finalizedBlockChain.remove(blockBeingMined);
                finalizedBlockChain.add(block);
                currentVdfTask.interrupt();
                block = extendBlock(block);
                currentVdfTask = new Thread(new VdfTask(block));
                currentVdfTask.start();
            }
        } else if (block.getBlockHeight() > lastFinalizedBlock.getBlockHeight() + 1) {
            //CHAIN SYNCHRONIZATION

        }
    }

    private Block extendBlock(Block blockToExtend){

        Block block =   Block.builder()
                .blockHeight(blockToExtend.getBlockHeight()+1)
                .previousHash(blockToExtend.getHash())
                .timeStamp(Instant.now().toString())
                .posProof(posService.generatePoSProof(CryptoUtils.hash256(blockToExtend.getPotProof().getProof().toByteArray())))
                .minerPublicKey(keyManager.getPublicKey().getEncoded())
                .build();
        block.setSignature(CryptoUtils.ecdsaSign(block.getHash(),keyManager.getPrivateKey()));
        return block;
    }


    public void validateGenesisBlock(Block block){
        if(finalizedBlockChain.size() == 1){
            //Verify if our genesis being possibly mined is better than the one we have if no ignore, if yes validate
            // and substitute
            Block blockBeingMined = finalizedBlockChain.get(0);
            if(PoS.proofQuality(block.getPosProof(),block.getPosProof().getChallenge(),block.getMinerPublicKey()) <=
                    PoS.proofQuality(blockBeingMined.getPosProof(),blockBeingMined.getPosProof().getChallenge(),blockBeingMined.getMinerPublicKey())){
                return;
            }
        }
        if(validateSignature(block) && validatePoT(block) && validatePoS(block,null)){
            finalizedBlockChain.clear();
            finalizedBlockChain.add(block);
        }
    }


    private boolean extendsChain(Block block,Block lastFinalizedBlock) {
        if(lastFinalizedBlock == null){
            return Arrays.equals(block.getPreviousHash(),new byte[32]);
        }
        return Arrays.equals(lastFinalizedBlock.getHash(), block.getPreviousHash());
    }
    private boolean validateSignature(Block block){
        return CryptoUtils.ecdsaVerify(block.getSignature(),block.getHash(),block.getMinerPublicKey());
    }
    private boolean validatePoT(Block block){
        ProofOfTime pot = block.getPotProof();
        byte[] challenge = new BigInteger(block.getHash())
                .add(new BigInteger(block.getPosProof().getSlothResult().getHash().toByteArray())).toByteArray();
        return vdf.verify(challenge,pot.getT(),pot.getLPrime(),pot.getProof());
    }

    private boolean validatePoS(Block block,Block lastFinalizedBlock){
        byte[] challenge = null;
        if (lastFinalizedBlock== null) {
            challenge = genesisChallenge;
        } else {
            //Challenge of PoS is the hash of the PoT of the previous block height
            challenge = CryptoUtils.hash256(lastFinalizedBlock.getPotProof().getProof().toByteArray());
        }
        return posService.verifyProof(block.getPosProof(),challenge,block.getMinerPublicKey());
    }



    public class VdfTask implements Runnable {

        private Block block;

        public VdfTask(Block block) {
            this.block = block;
        }

        @Override
        public void run() {
            log.info("Starting VDF computation for block: " + block);
            blockBeingMined = block;
            byte[] potChallenge = new BigInteger(block.getHash())
                    .add(new BigInteger(block.getPosProof().getSlothResult().getHash().toByteArray())).toByteArray();

            synchronized (finalizedBlockChain){
                int iterations = (int)Math.round(VDF_ITERATIONS * posService.proofQuality(block.getPosProof(),block.getMinerPublicKey()));
                block.setPotProof(vdf.eval(potChallenge, iterations));
            }
            blockBeingMined = null;
            deliverBlock(block);
            //BROADCAST BLOCK
        }
    }
}
