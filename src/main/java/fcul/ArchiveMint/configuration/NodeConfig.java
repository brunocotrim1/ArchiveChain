package fcul.ArchiveMint.configuration;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@NoArgsConstructor
@ConfigurationProperties(prefix = "app")
@Data
@Slf4j
public class NodeConfig {
    @PostConstruct
    public void init() {
        try{
            if(!Files.exists(Path.of(storagePath))){
                Files.createDirectories(Paths.get(storagePath));
            }
            if(!Files.exists(Path.of(filesToPlotPath))){
                Files.createDirectories(Paths.get(filesToPlotPath));
            }
            log.info("Created Node Folder at "+storagePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private String id;
    private String storagePath;
    private String seed;
    private String filesToPlotPath;
}
