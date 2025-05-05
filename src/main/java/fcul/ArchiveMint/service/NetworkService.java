package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import fcul.ArchiveMintUtils.Utils.Utils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Data
@Slf4j
public class NetworkService {

    @Autowired
    private NodeConfig nodeConfig;

    @Value("${server.port}")
    private String ownPort;

    private ExecutorService networkExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private List<String> peers;
    private RestTemplate restTemplate = new RestTemplate();
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://)((?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}|(?:\\d{1,3}\\.){3}\\d{1,3})(?::\\d{1,5})?(?:/[^\\s]*)?$");

    private final ConcurrentHashMap<String, Long> sentItems = new ConcurrentHashMap<>();
    private static final long EXPIRY_TIME_MS = 1 * 60 * 1000;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        peers = new ArrayList<>(nodeConfig.getSeedNodes());
        if (!getPeers().contains(getPeerAddress())) {
            peers.add(getPeerAddress());
        }
        broadcastPeerAddress(getPeerAddress());
        requestPeersFromSeed(nodeConfig.getSeedNodes().get(1));
        System.out.println(Arrays.toString(peers.toArray()));
        System.out.println(Utils.GREEN + "No comecou em: " + getPeerAddress() + Utils.RESET);

        scheduler.scheduleAtFixedRate(this::housekeepOldMessages, 1, 1, TimeUnit.MINUTES);
    }

    public <T> void broadcast(T data, String endpoint) {

        for (String peer : peers) {
            try {
                int port = Integer.parseInt(peer.split(":")[2]);
                if (port == Integer.parseInt(ownPort)) {
                    continue;
                }
                networkExecutor.execute(() -> sendToPeer(peer, data, endpoint));

            } catch (Exception e) {
                log.error("Error broadcasting to peer {}: {}", peer, e.getMessage());
            }
        }
    }

    private <T> String sendToPeer(String peer, T data, String endpoint) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<T> request = new HttpEntity<>(data, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    peer + endpoint,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            return response.getBody();
        } catch (Exception ignored) {
            return null;
        }
    }

    public void broadcastBlock(Block block) {
        broadcast(block, "/blockchain/sendBlock");
    }

    public void broadcastTransactions(List<Transaction> transactions, boolean isCensured) {
        List<Transaction> itemsToSend = new ArrayList<>(transactions);
        if (!isCensured) {
            itemsToSend = itemsToSend.stream()
                    .filter(tx -> !sentItems.containsKey(generateItemId(tx)))
                    .toList();
            for (Transaction transaction : itemsToSend) {
                sentItems.put(generateItemId(transaction), Instant.now().toEpochMilli());
            }
        } else {
            log.info("Broadcast {} transacoes censuradas", itemsToSend.size());
        }
        if (itemsToSend.isEmpty()) {
            return;
        }
        broadcast(itemsToSend, "/blockchain/sendTransaction?isCensured=" + isCensured);
    }

    public void broadcastPeerAddress(String peerAddress) {
        if (!peers.contains(peerAddress)) {
            peers.add(peerAddress);
        }
        broadcast(peerAddress, "/blockchain/addPeer");
    }

    public void addPeerAddress(String peerAddress) {
        synchronized (peers) {
            // Regex for validating URLs like http://192.168.1.72:8080 or http://google.com:8080
            String urlRegex = "^(https?://)((?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}|(?:\\d{1,3}\\.){3}\\d{1,3})(?::\\d{1,5})?(?:/[^\\s]*)?$";
            Pattern pattern = Pattern.compile(urlRegex);
            Matcher matcher = pattern.matcher(peerAddress);

            // Check if peerAddress is valid, not already in peers, and doesn't end with ownPort
            if (!peers.contains(peerAddress) && matcher.matches()) {
                System.out.println("Parceiro adicionado: " + peerAddress);
                peers.add(peerAddress);
                broadcastPeerAddress(peerAddress);
            } else {
                System.out.println("Endecero de parceiro invalido ou repetido: " + peerAddress);
            }
        }
    }

    public String getPeerAddress() {
        return nodeConfig.getFarmerAddress() + ":" + ownPort;
    }

    public String getOriginalSeedNode() {
        for (String seedNode : nodeConfig.getSeedNodes()) {
            if (!seedNode.equals(getPeerAddress())) {
                return seedNode;
            }
        }
        return null;
    }

    private <T> String generateItemId(T data) {
        if (data instanceof Block block) {
            block.calculateHash();
            return "block_" + Hex.encodeHexString(block.getHash());
        } else if (data instanceof Transaction transaction) {
            return "txn_" + transaction.getTransactionId();
        } else if (data instanceof String peerAddress) {
            return "peer_" + peerAddress;
        }
        return null;
    }

    private void housekeepOldMessages() {
        long currentTime = Instant.now().toEpochMilli();
        sentItems.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > EXPIRY_TIME_MS);
        log.debug("Housekeeping completed, current sent items count: {}", sentItems.size());
    }

    public boolean requestPeersFromSeed(String seedNodeUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String[]> response = restTemplate.exchange(
                    seedNodeUrl + "/blockchain/getPeers",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String[].class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("Failed to fetch peers from seed node: {}", response.getStatusCode());
                return false;
            }

            String[] receivedPeers = response.getBody();
            // Update local peers list, avoiding duplicates and own address
            for (String peer : receivedPeers) {
                if (!peers.contains(peer) && !peer.endsWith(":" + ownPort)) {
                    peers.add(peer);
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Error requesting peers from seed node: {}", e.getMessage());
            return false;
        }
    }
}