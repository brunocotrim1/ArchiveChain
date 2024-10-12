package fcul.ArchiveMint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ArchiveMint {

    public static void main(String[] args) {
        SpringApplication.run(ArchiveMint.class, args);
    }

}
