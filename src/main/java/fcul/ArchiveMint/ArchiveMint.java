package fcul.ArchiveMint;

import fcul.ArchiveMint.configuration.NodeRegister;
import fcul.ArchiveMintUtils.Utils.Utils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ArchiveMint {

    public static void main(String[] args){
        ConfigurableApplicationContext context = SpringApplication.run(ArchiveMint.class, args);

    }

}
