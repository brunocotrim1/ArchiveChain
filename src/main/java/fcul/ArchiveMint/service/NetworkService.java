package fcul.ArchiveMint.service;

import com.google.gson.Gson;
import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMint.model.Block;
import fcul.ArchiveMint.utils.Utils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
        String serializedBlock = Utils.serializeBlock(block);
        for (String peer : peers) {
            int port = Integer.parseInt(peer.split(":")[2]);
            if (port == Integer.parseInt(ownPort)) {
                continue;
            }
            networkExecutor.execute(() -> sendBlockToPeer(peer, serializedBlock));
        }
    }

    public void sendBlockToPeer(String peer, String block) {
        try {
            ResponseEntity<?> response = restTemplate.exchange(peer + "/blockchain/sendBlock", HttpMethod.POST,
                    new HttpEntity<>(block), String.class);
            //restTemplate.postForObject(peer + "/blockchain/sendBlock", block, Block.class);
        } catch (Exception ignored) {
        }
    }
}
