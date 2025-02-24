package fcul.ArchiveMint.service;

import com.google.gson.Gson;
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


    @PostConstruct
    public void init() {
        peers = nodeConfig.getSeedNodes();
    }

    public ExecutorService networkExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private List<String> peers;
    private RestTemplate restTemplate = new RestTemplate();

    public void broadcastBlock(Block block) {
        for (String peer : peers) {
            int port = Integer.parseInt(peer.split(":")[2]);
            if (port == Integer.parseInt(ownPort)) {
                continue;
            }
            networkExecutor.execute(() -> sendBlockToPeer(peer, block));
        }
    }

    public void broadcastTransaction(Transaction transaction){
        for (String peer : peers) {
            int port = Integer.parseInt(peer.split(":")[2]);
            if (port == Integer.parseInt(ownPort)) {
                continue;
            }
            networkExecutor.execute(() -> sendTransactionToPeer(peer, transaction));
        }
    }


    public void sendBlockToPeer(String peer, Block block) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Block> request = new HttpEntity<>(block, headers);
            ResponseEntity<?> response = restTemplate.exchange(peer + "/blockchain/sendBlock", HttpMethod.POST,
                    request, String.class);
            //restTemplate.postForObject(peer + "/blockchain/sendBlock", block, Block.class);
        } catch (Exception ignored) {

        }
    }

    public void sendTransactionToPeer(String peer, Transaction transaction){
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Transaction> request = new HttpEntity<>(transaction, headers);

            ResponseEntity<String> response = restTemplate.exchange(peer + "/blockchain/sendTransaction", HttpMethod.POST, request , String.class);
        } catch (Exception ignored) {
        }
    }
}
