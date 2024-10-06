package fcul.blockchain;

import fcul.blockchain.Objects.Message.Network.NetworkMessage;

import java.io.IOException;

public class ArchiveMain {
    public static void main(String[] args) throws InterruptedException, IOException, ClassNotFoundException {
        int id = Integer.parseInt(args[0]);
        int port = 8080+id;
        Node node = new Node("0.0.0.0:"+port, id, new Blockchain());
        node.start();
        if(id != 0) {
            String peerBootStrap = "0.0.0.0:8080";
            node.addPeer(peerBootStrap);
        }else {
        }




        while (true) {
            try {
                Thread.sleep(100000); // Optionally sleep to avoid busy waiting
            } catch (InterruptedException e) {
                e.printStackTrace(); // Handle interruption if needed
            }
        }
    }
}
