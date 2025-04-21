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
        System.out.println(Utils.YELLOW + "Starting ArchiveMint Node" + Utils.RESET);
        if(!context.getBean(NodeRegister.class).registerFCCN()){
            System.out.println(Utils.RED + "Error registering node in FCCN" + Utils.RESET);
            SpringApplication.exit(context, () -> 1);
            System.exit(1);
        }else {
            System.out.println(Utils.GREEN + "Node registered in FCCN" + Utils.RESET);
        }

    }

}
