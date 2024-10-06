package fcul.ArchiveMint.controller;


import fcul.ArchiveMint.configuration.NodeConfig;
import fcul.ArchiveMint.service.ProofOfSpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/blockchain")
public class BlockchainController {

    @Autowired
    private ProofOfSpaceService proofOfSpaceService;
    @GetMapping("/test")
    public boolean test(@RequestParam String fileName){
        return proofOfSpaceService.plotFile(fileName);
    }

}
