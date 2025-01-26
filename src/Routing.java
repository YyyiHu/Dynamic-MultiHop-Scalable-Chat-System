import client.Message;
import client.MessageType;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Routing class handles routing operations, including maintaining a local routing table,
 * broadcasting link state information, managing neighbors, and determining the next hop for messages.
 */
public class Routing {
    private Colours colourUtility = new Colours(); // Utility for colored output messages
    private int ownIP; // IP address of the current node
    private HashMap<String, Integer> mapNameToIP; // Mapping of node names to their IP addresses
    private ConcurrentHashMap<Integer, ArrayList<Integer>> localRoutingTable = new ConcurrentHashMap<>(); // Local routing table
    // Key: Neighbor IP, Value: Counter tracking the number of times a neighbor has been heard from
    private ConcurrentHashMap<Integer, Integer> neighbors = new ConcurrentHashMap<>();
    // Routing logic interfaces with MAC for communication
    private final MAC mac;
    private MyProtocol myProtocol; // Protocol for managing communication
    private keepAliveThread alive; // Thread for sending and processing keep-alive messages
    private Chunker chunker; // Responsible for chunking and reassembling messages
    private AtomicInteger count; // Counter for link state broadcast retries
    private Receiver1 receiver; // Receiver for handling incoming messages

    /**
     * Constructs a Routing object with the specified MAC layer and protocol.
     *
     * @param mac        The MAC layer for communication.
     * @param myProtocol The protocol instance for managing network communication.
     */
    public Routing(MAC mac, MyProtocol myProtocol) {
        this.mapNameToIP = new HashMap<>();
        ownIP = 0;
        this.mac = mac;
        this.myProtocol = myProtocol;
    }

    /**
     * Retrieves the IP address of the current node and initializes routing components.
     *
     * @param IP      The IP address of the current node.
     * @param nameMap A HashMap mapping node names to their IP addresses.
     * @throws InterruptedException If the thread is interrupted during initialization.
     */
    public void getMyIP(int IP, HashMap<String, Integer> nameMap) throws InterruptedException {
        colourUtility.printC("You got our identification number " + IP + " in the chat!", colourUtility.routing);
        colourUtility.printC("Please wait for the network to stabilize!", colourUtility.routing);
        this.count = new AtomicInteger();
        this.count.set(3);
        this.mapNameToIP = nameMap;
        ownIP = IP;
        chunker.setIp(IP);
        receiver.setIPForReceiver(IP);
        alive = new keepAliveThread();
        alive.start();

        Thread monitorThread = new Thread(() -> {
            while (localRoutingTable.size() < 3) {
                try {
                    broadcastLinkState();
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            chunker.setNeighbours(localRoutingTable.keySet());
            colourUtility.printC("You can chat now!  ", colourUtility.routing);
            colourUtility.printC("You can send message to:  ", colourUtility.routing);
            for (int key : localRoutingTable.keySet()) {
                colourUtility.printC(String.valueOf(key), colourUtility.routing);
            }
            myProtocol.startChat();

        });
        monitorThread.start(); // Start thread to track if we have any neighbors to send messages
    }

    /**
     * Thread responsible for periodically broadcasting link state information.
     */
    Thread regularLinkState = new Thread(() -> {
        while (true) {
            try {
                Random random = new Random();
                int randomNumber = random.nextInt(80000, 100000);
                broadcastLinkState();
                Thread.sleep(randomNumber);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    });

    /**
     * Processes the keep-alive message received from a neighbor node.
     *
     * @param bytes The byte array containing the keep-alive message.
     */
    public synchronized void processKeepAlive(byte[] bytes) {
        if (ownIP != 0) {
            boolean newNeighbour = true;
            int sender = bytes[1];
            for (var neighbor : neighbors.keySet()) {
                if (neighbor.equals(sender)) {
                    neighbors.put(sender, 0); // Reset counter for the neighbor
                    newNeighbour = false;
                }
            }
            if (newNeighbour) {
                neighbors.put(sender, 0); // Add a new neighbor
                if (!localRoutingTable.containsKey(sender)) {
                    ArrayList<Integer> values = new ArrayList<>();
                    values.add(1);
                    values.add(sender);
                    localRoutingTable.put(sender, values);
                    try {
                        broadcastLinkState();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Retrieves the set of neighbors from the local routing table.
     *
     * @return A set of neighbor IP addresses.
     */
    public Set<Integer> getNeighbours() {
        return localRoutingTable.keySet();
    }

    /**
     * Sets the Chunker instance for managing message chunking.
     *
     * @param chunker The Chunker instance.
     */
    public void setChunker(Chunker chunker) {
        this.chunker = chunker;
    }

    /**
     * Sets the Receiver1 instance for handling incoming messages.
     *
     * @param receiver The Receiver1 instance.
     */
    public void setReceiver(Receiver1 receiver) {
        this.receiver = receiver;
    }

    /**
     * Broadcasts the link state information to all neighbors.
     *
     * @throws InterruptedException If the thread is interrupted during broadcasting.
     */
    private void broadcastLinkState() throws InterruptedException {
        byte[] linkstatebytes = new byte[32];
        int messageLength = 2 + 2 * localRoutingTable.size();
        linkstatebytes[0] = (byte) 0b01000000;
        linkstatebytes[1] = (byte) ownIP;
        linkstatebytes[2] = (byte) (messageLength + 4);
        linkstatebytes[3] = (byte) -1;
        linkstatebytes[4] = (byte) ownIP;
        linkstatebytes[5] = 0;

        int laterIndex = 6;
        for (var othernode : localRoutingTable.entrySet()) {
            linkstatebytes[laterIndex] = othernode.getKey().byteValue();
            linkstatebytes[laterIndex + 1] = othernode.getValue().get(0).byteValue();
            laterIndex += 2;
        }

        Message table = new Message(MessageType.DATA, ByteBuffer.wrap(linkstatebytes));
        synchronized (mac) {
            mac.addBack(table);
        }
    }

    /**
     * Updates the local routing table based on the received byte array.
     *
     * @param bytes The byte array containing the routing information.
     * @throws InterruptedException If the thread is interrupted during updating.
     */
    public synchronized void updateTheTable(byte[] bytes) throws InterruptedException {
        if (ownIP != 0) {
            int sender = bytes[1];
            int mesLen = bytes[2];
            boolean updated = false;

            for (int finalIP : localRoutingTable.keySet()) {
                if (localRoutingTable.get(finalIP).get(1) == sender) {
                    int notExistingDest = -1;
                    for (int indexx = 4; indexx < mesLen + 4; indexx += 2) {
                        if (bytes[indexx] == finalIP) {
                            notExistingDest = -1;
                            break;
                        } else {
                            notExistingDest = bytes[indexx];
                        }
                    }
                    if (notExistingDest != -1 && localRoutingTable.containsKey(notExistingDest)) {
                        localRoutingTable.remove(notExistingDest);
                        updated = true;
                    }
                }
            }

            for (var neighbor : neighbors.entrySet()) {
                if (neighbor.getKey().equals(sender)) {
                    neighbors.put(sender, 0);
                }
            }

            if (!localRoutingTable.containsKey(sender)) {
                ArrayList<Integer> values = new ArrayList<>();
                values.add(1);
                values.add(sender);
                localRoutingTable.put(sender, values);
                updated = true;
            }

            for (int index = 4; index < mesLen; index += 2) {
                int destIP = bytes[index];
                int cost = bytes[index + 1];
                if (!localRoutingTable.containsKey(destIP) && destIP != ownIP) {
                    ArrayList<Integer> values = new ArrayList<>();
                    values.add(cost + 1);
                    values.add(sender);
                    localRoutingTable.put(destIP, values);
                    updated = true;
                } else if (destIP != ownIP && cost + 1 < localRoutingTable.get(destIP).get(0)) {
                    localRoutingTable.get(destIP).set(0, cost + 1);
                    localRoutingTable.get(destIP).set(1, sender);
                    updated = true;
                }
            }

            if (updated) {
                chunker.setNeighbours(localRoutingTable.keySet());
                broadcastLinkState();
                count.set(3);
            } else if (count.get() != 0) {
                broadcastLinkState();
                count.decrementAndGet();
            }
        }
    }

    /**
     * Retrieves the next hop for a given destination IP address.
     *
     * @param IP The destination IP address.
     * @return The next hop IP address or 0 if no route is found.
     */
    public int getNextHop(int IP) {
        if (localRoutingTable.containsKey(IP)) {
            return localRoutingTable.get(IP).get(1);
        }
        colourUtility.printC("We don't have this final destination in our database, please try again!", colourUtility.routing);
        return 0;
    }

    /**
     * A thread that sends periodic keep-alive messages to neighbors and removes inactive neighbors.
     */
    private class keepAliveThread extends Thread {
        ByteBuffer keepAlive;
        Message keepAliveMsg;

        public keepAliveThread() {
            super();
            keepAlive = ByteBuffer.allocate(2);
            keepAlive.put((byte) 0b11000000);
            keepAlive.put((byte) ownIP);
            keepAliveMsg = new Message(MessageType.DATA_SHORT, keepAlive);
        }

        /**
         * Periodically sends keep-alive messages and removes inactive neighbors.
         */
        public void run() {
            Random random = new Random();
            int randomNumber = random.nextInt(2000, 4000);
            try {
                Thread.sleep(randomNumber);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            synchronized (mac) {
                try {
                    mac.addBack(keepAliveMsg);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Random ran = new Random();
            int randomNumber1 = ran.nextInt(40000, 60000);
            while (true) {
                try {
                    Thread.sleep(randomNumber1);
                    synchronized (mac) {
                        mac.addBack(keepAliveMsg);
                    }
                    for (int node : neighbors.keySet()) {
                        if (neighbors.get(node) > 4) {
                            neighbors.remove(node);
                            for (int node1 : localRoutingTable.keySet()) {
                                if (node1 == node || localRoutingTable.get(node1).get(1) == node) {
                                    localRoutingTable.remove(node1);
                                }
                            }
                            chunker.setNeighbours(localRoutingTable.keySet());
                            broadcastLinkState();
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
