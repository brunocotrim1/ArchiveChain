package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.KeyManager;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMint.configuration.NodeRegister;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.Peer;
import fcul.ArchiveMintUtils.Model.StorageContract;
import fcul.ArchiveMintUtils.Model.StorageType;
import fcul.ArchiveMintUtils.Model.transactions.StorageContractSubmission;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.CryptoUtils;
import fcul.ArchiveMintUtils.Utils.PoS;
import fcul.ArchiveMintUtils.Utils.Utils;
import fcul.ArchiveMintUtils.Utils.wesolowskiVDF.ProofOfTime;
import fcul.ArchiveMintUtils.Utils.wesolowskiVDF.WesolowskiVDF;
import fcul.wrapper.FileEncodeProcess;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    @Autowired
    private NodeRegister nodeRegister;

    private List<Peer> peers = new ArrayList<>();
    private final List<Block> finalizedBlockChain = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();

    private WesolowskiVDF vdf = new WesolowskiVDF();
    @Value("${app.initialVDFIterations}")
    private int VDF_ITERATIONS = 200000;//1000000;//500000;//About 20 seconds, still need difficulty reset implementation
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
    private volatile boolean synchronizing = false;
    private AtomicInteger archiveCounter = new AtomicInteger(0);
    private BigInteger archivedStorage = BigInteger.ZERO;

    public ResponseEntity<String> archiveFile(MultipartFile file, StorageContract contract) {
        try {

            if (archivedStorage.add(BigInteger.valueOf(file.getSize())).compareTo(BigInteger.valueOf(nodeConfig.getDedicatedStorage())) > 0) {
                //System.out.println(Utils.RED + "Not enough space to store file available" + Utils.RESET);
                return ResponseEntity.status(500).body("Not enough space to store file");
            }
            byte[] fileData = file.getInputStream().readAllBytes();
            Transaction transaction = blockchainState.validateContractSubmission(fileData, contract,
                    keyManager);

            archiveCounter.incrementAndGet();
            plotter.submit(() -> {
                try {
                    if (contract.getStorageType().equals(StorageType.AES)) {
                        aesProcess(fileData, contract.getFileUrl(), transaction);
                    } else {
                        List<Transaction> transactions = new ArrayList<>();
                        Transaction vdeTransaction = vdeProccess(fileData, file.getOriginalFilename(), contract);
                        transactions.add(vdeTransaction);
                        addTransaction(transactions);
                    }
                    archivedStorage = archivedStorage.add(BigInteger.valueOf(file.getSize()));
                } catch (Exception e) {
                  log.debug(e.getMessage());
                } finally {
                    log.debug("Archive Counter: " + archiveCounter.get());
                    archiveCounter.decrementAndGet();
                }
            });

            return ResponseEntity.ok("File Archived Successfully with contract: " + contract);
        } catch (Exception e) {
            //e.printStackTrace();
            //Send error
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    public void aesProcess(byte[] fileData, String fileUrl, Transaction t) throws Exception {
        log.debug("AES File submitted to plotter, available space: " + nodeConfig.getDedicatedStorage());
        posService.plotFileData(fileData, fileUrl);
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(t);
        addTransaction(transactions);

        // Assuming t is or contains StorageContractSubmission
        StorageContractSubmission storageContract = (StorageContractSubmission) t;

        // Simple RestTemplate setup
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<StorageContractSubmission> requestEntity = new HttpEntity<>(storageContract, headers);

        // Send to the validateAES endpoint
        String targetUrl = nodeConfig.getFccnNetworkAddress() + "/storage/validateAES";
        ResponseEntity<Boolean> response = restTemplate.exchange(
                targetUrl,
                HttpMethod.POST,
                requestEntity,
                Boolean.class
        );

        // Check response
        if (response.getStatusCode() == HttpStatus.OK && Boolean.TRUE.equals(response.getBody())) {
            log.debug("Storage Contract signed and verified for AES!");
            nodeConfig.setDedicatedStorage(nodeConfig.getDedicatedStorage() - fileData.length);
        } else {
            throw new Exception("AES validation failed! Status Code: " + response.getStatusCode());
        }
    }


    public Transaction vdeProccess(byte[] file, String orinigalFilename, StorageContract contract) throws Exception {
        String normalizedFileName = Normalizer.normalize(orinigalFilename, Normalizer.Form.NFC);
        int iterationsPerChunk = FileEncodeProcess.iterationsPerChunk(file.length);
        String salt = normalizedFileName + CryptoUtils.getWalletAddress(Hex.toHexString(keyManager.getPublicKey().getEncoded()));
        byte[] iv = CryptoUtils.hash256(salt.getBytes());
        System.out.println(Utils.GREEN + "VDE Encoding File: " + orinigalFilename + Utils.RESET);
        byte[] fileData = FileEncodeProcess.encodeFileVDE(file, iv, 1);
        System.out.println(Utils.GREEN + "VDE File submitted to plotter, available space: " +
                nodeConfig.getDedicatedStorage() + Utils.RESET);
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
            nodeConfig.setDedicatedStorage(nodeConfig.getDedicatedStorage() - fileData.length);
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


    @PostConstruct
    public void onInit() {
        synchronizing = true;
        if (nodeConfig.isTimelord()) {
            System.out.println(Utils.GREEN + "Timelord comecou com " + this.VDF_ITERATIONS + " Iteracoes de VDF" + Utils.RESET);
        }
        try {
            posService.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            reloadState();
            syncNewNode(finalizedBlockChain.isEmpty() ? -1 : finalizedBlockChain.getLast().getHeight());
            synchronizing = false;
            if (nodeConfig.isTimelord()) {
                if (!finalizedBlockChain.isEmpty()) {
                    extendFinalizedBlock(finalizedBlockChain.getLast());
                    System.out.println(Utils.YELLOW + "Timelord Comecou" + Utils.RESET);
                } else {
                    startMining();
                }
            }

            if (!nodeRegister.registerFCCN()) {
                System.exit(-1);
            } else {
                System.out.println(Utils.GREEN + "Node Registou-se na FCCN" + Utils.RESET);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void reloadState() {
        try {
            long currentHeight = 0;
            Block block = blockchainState.readBlockFromFile(0, true);
            if (block == null) {
                System.out.println(Utils.RED + "Sem blocos para fazer reload" + Utils.RESET);
                return;
            }

            while (block != null) {

                block.setHash(null);
                block.calculateHash();
                block.setQuality(posService.proofQuality(block.getPosProof(), block.getMinerPublicKey()));
                if (block.getHeight() == 0) {
                    validateGenesisBlock(block);
                } else {
                    if (!timelordAlgorithm(block)) {
                        System.out.println(Utils.RED + "Falha ao fazer reload do bloco com altura: " + block.getHeight() + Utils.RESET);
                    } else {
                        System.out.println(Utils.YELLOW + "Reload do bloco com altura: " + block.getHeight() + Utils.RESET);
                    }
                }
                finalizeBlock();
                currentHeight++;
                block = blockchainState.readBlockFromFile(currentHeight, true);
            }
        } catch (Exception e) {
        }finally {
            synchronizing = false;
        }
    }

    public boolean syncNewNode(long currentHeightP) {
        String seedNodeUrl = net.getOriginalSeedNode();
        synchronizing = true;
        long currentHeight = currentHeightP + 1;
        try {
            System.out.println(Utils.YELLOW + "Sincronizar com a seed node: " + seedNodeUrl + Utils.RESET);
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Start with the genesis block (height 0)
            blockProcessingLock.lock();
            try {
                while (true) {
                    // Request block at current height
                    ResponseEntity<Block> response = restTemplate.exchange(
                            seedNodeUrl + "/blockchain/getBlock?height=" + currentHeight,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            Block.class
                    );

                    if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                            // No more blocks available
                            log.info("Blockchain sincronizada até altura {}", finalizedBlockHeight);
                            break;
                        }
                        return false;
                    }

                    Block block = response.getBody();
                    block.setHash(null); // Clear hash to recalculate
                    block.calculateHash();

                    // Validate block
                    if (block.getHeight() == 0) {
                        if (!validateGenesisBlock(block)) {
                            log.error("Genesis block validation failed");
                            return false;
                        }
                        System.out.println(Utils.YELLOW + "Synchronized genesis block" + Utils.RESET);
                    } else {
                        if (!timelordAlgorithm(block)) {
                            log.error("Block validation failed at height {}", block.getHeight());
                            return false;
                        }
                        System.out.println(Utils.YELLOW + "Synchronized block at height: " + block.getHeight() + Utils.RESET);
                    }
                    finalizeBlock();
                    currentHeight++;
                }

                log.info("Successfully synchronized blockchain with {} blocks", finalizedBlockHeight + 1);
                synchronizing = false;
                return true;
            } finally {
                synchronizing = false;
                blockProcessingLock.unlock();
            }
        } catch (Exception e) {
            synchronizing = false;
            return false;
        }
    }


    public void startMining() {
        try {
            log.info("A comecar Mineracao");
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
        block.setQuality(posService.proofQuality(block.getPosProof(), block.getMinerPublicKey()));
        blockProcessingLock.lock();
        try {
            addTransaction(block.getTransactions());
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
        if (!finalizedBlockChain.isEmpty() && finalizedBlockChain.get(finalizedBlockChain.size() - 1).getHeight() - finalizedBlockHeight > 3) {
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

        blockchainState.addTransaction(blockchainState.getCensuredTransactions(blockBeingMined.getTransactions(),
                block.getTransactions()),true);

        restartThread(block);
        net.broadcastBlock(block);
        //System.out.println("Block swapped non finalized");
    }


    public boolean timelordAlgorithm(Block block) {
        if (finalizedBlockChain.isEmpty()) {
            syncNewNode(-1);
            return false;
        }

        Block lastFinalizedBlock = finalizedBlockChain.getLast();

        if (block.equals(lastFinalizedBlock)) {
            return false;
        }
        if (!validateSignature(block)) {
            return false;
        }
        System.out.println("A processar bloco: " + block.getHeight() + " com hash: " + Hex.toHexString(block.calculateHash()));
        if (block.getHeight() == lastFinalizedBlock.getHeight()) {
            //Caso de ultimo bloco estar a mesma height do bloco recebido, substituir e extender
            if (!blockIsBetter(block, lastFinalizedBlock)) {
                //System.out.println("Block not better " + Hex.toHexString(block.calculateHash()) + " : " + Hex.toHexString(lastFinalizedBlock.calculateHash()));
                return false;
            }
            //Check if the new block extends the block with height - 1 which is the before last finalized block
            if (!(extendsChain(block, finalizedBlockChain.get(finalizedBlockChain.size() - 2))
                    && validatePoS(block, finalizedBlockChain.get(finalizedBlockChain.size() - 2)))) {
                System.out.println("Not Extending chain or POS invalid");
                return false; //Se nao estender a chain
            }

            if (!validatePoT(block)) {
                System.out.println("Pot invalid");
                return false;
            }
            //IF a new Block is received and it is better than the last finalized block, we swap and extend the new one
            if (!swapAndRollBlackBlock(lastFinalizedBlock, block)) {
                System.out.println("Failed Validating and executing1");
                return false;
            }
            if (synchronizing) {
                return true;
            }

            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
            net.broadcastBlock(block);
            return true;
        } else if (block.getHeight() == lastFinalizedBlock.getHeight() + 1) {
            //Caso em que recebemos o finalizado mas ainda estamos a minar o proximo
            if (blockBeingMined != null) {
                if (!blockIsBetter(block, blockBeingMined)) {
                    //System.out.println("Block not better being mined" + Hex.toHexString(block.calculateHash()) + " : " +
                    //  Hex.toHexString(blockBeingMined.calculateHash()));
                    return false;
                }
            }
            if (!extendsChain(block, lastFinalizedBlock)) {
                System.out.println("Not Extending chain2:" + block.getHeight() + " " + lastFinalizedBlock.getHeight() + ":" + Hex.toHexString(block.calculateHash()) + " : " +
                        Hex.toHexString(lastFinalizedBlock.calculateHash()));
                return false;
            }

            if (!validatePoS(block, lastFinalizedBlock)) {
                System.out.println("Pos invalid2");
                return false;
            }

            if (!validatePoT(block)) {
                System.out.println("Pot invalid2");
                return false;
            }
            //if a new block is received we validate and execute it
            if (!processBlockState(block)) {
                System.out.println("Failed Validating and executing2");
                return false;
            }

            if (synchronizing) {
                return true;
            }
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
            net.broadcastBlock(block);
            return true;
        } else if (block.getHeight() > lastFinalizedBlock.getHeight() + 1) {
            syncNewNode(lastFinalizedBlock.getHeight());
        }
        return false;
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
        blockchainState.addTransaction(blockchainState.getCensuredTransactions(blockSwapped.getTransactions(),
                newBlock.getTransactions()),true);

        List<Transaction> transactionsResultantOfExecution = blockchainState.executeBlock(newBlock,synchronizing);
        addTransaction(transactionsResultantOfExecution);
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
            List<Transaction> transactions = blockchainState.executeBlock(block,synchronizing);
            addTransaction(transactions);
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

    public boolean validateGenesisBlock(Block block) {
        if (finalizedBlockChain.size() == 1) {
            //Verify if our genesis being possibly mined is better than the one we have if no ignore, if yes validate
            // and substitute
            Block blockBeingMined = finalizedBlockChain.get(0);
            if (PoS.proofQuality(block.getPosProof(), block.getPosProof().getChallenge(), block.getMinerPublicKey()) <=
                    PoS.proofQuality(blockBeingMined.getPosProof(), blockBeingMined.getPosProof().getChallenge(), blockBeingMined.getMinerPublicKey())) {
                return false;
            }
        }
        if (validateSignature(block) && validatePoT(block) && validatePoS(block, null)) {
            finalizedBlockChain.clear();
            processBlockState(block);
            if (synchronizing) {
                return true;
            }
            if (nodeConfig.isTimelord()) {
                extendFinalizedBlock(block);
            } else {
                extendNonFinalizedBlock(block);
            }
            net.broadcastBlock(block);
            return true;
        }
        return false;
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
        processingExecutor.submit(() -> {
                    processBlock(block);
                }
        );
        return ResponseEntity.ok("Block received");
    }

    public ResponseEntity<String> addTransaction(List<Transaction> transactions) {
        if (!blockchainState.addTransaction(transactions,false)) {
            return ResponseEntity.ok("Transaction not added");
        }
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

                if (!finalizedBlockChain.isEmpty()) {
                    lastFinalizedBlock = finalizedBlockChain.getLast();

                    if (Thread.interrupted()) {
                        return;
                    }
                    if (!extendsChain(block, lastFinalizedBlock)) {
                        System.out.println("Thread should be interrupted");
                        return;
                    }
                }

                if (blockBeingMined == null) {
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
