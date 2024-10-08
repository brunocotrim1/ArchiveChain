package fcul.ArchiveMint.controller;


import fcul.ArchiveMint.service.BlockchainService;
import fcul.ArchiveMint.service.PosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/blockchain")
public class BlockchainController {

    @Autowired
    private BlockchainService blockchainService;
    @Autowired
    private PosService proofOfSpaceService;
    @GetMapping("/test")
    public int test(){
        blockchainService.startMining();
        return 1;
    }

    @GetMapping("/plotFile")
    public boolean test(@RequestParam String fileName){
        proofOfSpaceService.plotFile(fileName);
        return true;
    }

}
