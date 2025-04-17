package fcul.ArchiveMint.service;

import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMintUtils.Model.Block;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @PostConstruct
    public void init() {
        peers = new ArrayList<>(nodeConfig.getSeedNodes());
        if (!getPeers().contains(getPeerAddress())) {
            peers.add(getPeerAddress());
        }
        broadcastPeerAddress(getPeerAddress());
        requestPeersFromSeed(nodeConfig.getSeedNodes().get(1));
        System.out.println(Arrays.toString(peers.toArray()));
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

    public void broadcastTransaction(Transaction transaction) {
        broadcast(transaction, "/blockchain/sendTransaction");
    }

    public void broadcastPeerAddress(String peerAddress) {
        if (!peers.contains(peerAddress)) {
            peers.add(peerAddress);
        }
        broadcast(peerAddress, "/blockchain/addPeer");
    }

    public void addPeerAddress(String peerAddress) {
        synchronized (peers) {
            if (!peers.contains(peerAddress) && !peerAddress.endsWith(":" + ownPort)) {
                peers.add(peerAddress);
                broadcastPeerAddress(peerAddress);
            }
        }
    }

    public String getPeerAddress() {
        return nodeConfig.getFarmerAddress() + ":" + ownPort;
    }
    public String getOriginalSeedNode(){
        for(String seedNode : nodeConfig.getSeedNodes()){
            if(!seedNode.equals(getPeerAddress())){
                return seedNode;
            }
        }
        return null;
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