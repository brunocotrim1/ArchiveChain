package fcul.ArchiveMint.service;

import com.google.gson.Gson;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.utils.Utils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static fcul.ArchiveMint.service.BlockchainService.myExecutor;

@Service
@Data
public class NetworkService {

    @Autowired
    private NodeConfig nodeConfig;
    private List<String> peers;
    private RestTemplate restTemplate = new RestTemplate();
    @Value("${server.port}")
    private String ownPort;
    private final Gson gson = new Gson();

    @PostConstruct
    public void init() {
        peers = nodeConfig.getSeedNodes();
    }

    public void broadcastBlock(Block block) {
        String serializedBlock = Utils.serializeBlock(block);
        for (String peer : peers) {
            int port = Integer.parseInt(peer.split(":")[2]);
            if (port == Integer.parseInt(ownPort)) {
                continue;
            }
            myExecutor.execute(() -> sendSynctoPeer(peer, serializedBlock));
        }
    }


    public void sendSynctoPeer(String peer, String block) {
        try {
            ResponseEntity<?> response = restTemplate.exchange(peer + "/blockchain/sendBlock", HttpMethod.POST,
                    new HttpEntity<>(block), String.class);
            //restTemplate.postForObject(peer + "/blockchain/sendBlock", block, Block.class);
        } catch (Exception e) {
        }
    }
}
