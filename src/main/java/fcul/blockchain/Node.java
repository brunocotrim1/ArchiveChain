package fcul.blockchain;

import fcul.blockchain.Objects.Message.Message;
import fcul.blockchain.Objects.Message.MessageType;
import fcul.blockchain.Objects.Message.Network.NetworkMessage;
import fcul.blockchain.Objects.Message.Network.NetworkType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class Node {
    private String address;

    private int id;
    private Set<Peer> peers;
    private static final int maxAmountOfPeers = 4;
    private ExecutorService threadPool;
    private ServerSocket serverSocket;

    private Blockchain blockchain;

    public Node(String address, int id, Blockchain blockchain) {
        this.address = address;
        this.id = id;
        peers = new HashSet<>();
        this.blockchain = blockchain;
    }

    public void addPeer(String peer) throws IOException, ClassNotFoundException {
        NetworkMessage message = createNetworkMessage("Hello from node " + id);
        Message reply = sendWithReply(peer, message);
        processNetworkMessage((NetworkMessage) reply);
    }

    public Optional<Message> processNetworkMessage(NetworkMessage message) {

        if (message.getNetworkType() == NetworkType.CONNECT) {
            Peer newPeer = new Peer();
            newPeer.setAddress(message.getSender());
            newPeer.setId(message.getPeer().getId());
            peers.add(newPeer);

            System.out.println("Peer added: " + newPeer);
            return Optional.of(createNetworkMessage("Hello from node " + id));
        }

        return Optional.empty();
    }



    public Message sendWithReply(String address, Message message) throws IOException, ClassNotFoundException {
        Socket socket = connectToPeer(address);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.writeObject(message);
        out.flush();

        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        return (Message) in.readObject();
    }





    private void sendMessage(String address, Message message) throws IOException {
        Socket socket = connectToPeer(address);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.writeObject(message);
        out.flush();
    }

    public void broadcastMessage(Message message) {
        for (Peer peer : peers) {
            try {
                sendMessage(peer.getAddress(), message);
                System.out.println("Message sent to peer " + peer);
            } catch (IOException e) {
                System.err.println("Error: Unable to send message to peer " + peer);
                e.printStackTrace();
            }
        }
    }











    private Socket connectToPeer(String address) throws IOException {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        return new Socket(host, port);
    }
    public void start() {
        int port = Integer.parseInt(address.split(":")[1]);
        try {
            serverSocket = new ServerSocket(port);
            threadPool = Executors.newFixedThreadPool(maxAmountOfPeers);
            System.out.println("Node " + id + " is running and waiting for connections on port " + port);

            threadPool.submit(() -> {
                try {
                    while (true) {
                        Socket clientSocket = serverSocket.accept(); // Wait for a client to connect
                        System.out.println("Client connected: " + clientSocket.getInetAddress());

                        // Handle client connection in a separate thread
                        threadPool.submit(new ClientHandler(clientSocket, this.blockchain));
                    }
                } catch (IOException e) {
                    System.err.println("Error: Unable to accept client connection on port " + port);
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            System.err.println("Error: Unable to start server on port " + port);
            e.printStackTrace();
        }
    }
    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final Blockchain blockchain;
        public ClientHandler(Socket socket,Blockchain blockchain) {
            this.clientSocket = socket;
            this.blockchain = blockchain;
        }

        @Override
        public void run() {
            try {
                System.out.println("Handling client connection: " + clientSocket.getInetAddress());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                Object obj = in.readObject();
                Message message = (Message) obj;
                if (message.getType() == MessageType.NETWORK) {
                    NetworkMessage networkMessage = (NetworkMessage) message;
                    Optional<Message> reply = processNetworkMessage(networkMessage);
                    if (reply.isPresent()) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        System.out.println("Sending reply: " + reply.get());
                        out.writeObject(reply.get());
                        out.flush();
                        out.close();
                    }
                } else {
                    Optional<Message> reply = blockchain.deliver();
                    if (reply.isPresent()) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        System.out.println("Sending reply: " + reply.get());
                        out.writeObject(reply.get());
                        out.flush();
                        out.close();
                    }
                }
                System.out.println("Message received: " + obj);
                in.close();
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("IOException while handling client connection: " + e.getMessage());
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                System.err.println("ClassNotFoundException while reading message: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    private NetworkMessage createNetworkMessage(String Message){
        NetworkMessage message = new NetworkMessage();
        message.setMessage("Hello from node " + id);
        message.setSender(address);
        message.setPeer(new Peer(this.address, this.id));
        message.setType(MessageType.NETWORK);
        message.setBroadcast(false);
        message.setNetworkType(NetworkType.CONNECT);
        return message;
    }
}
