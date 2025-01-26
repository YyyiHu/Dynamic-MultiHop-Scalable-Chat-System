import client.Message;
import client.MessageType;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * The Addressing class is responsible for managing unique addresses for nodes in a distributed network.
 * It facilitates address assignment, communication of known addresses, and processing of incoming addressing packets.
 * Additionally, it allows nodes to associate usernames with addresses and maintains a mapping of these associations.
 */
public class Addressing extends Thread {
    private Colours coloursUtility; // Utility for colored debugging output
    private Routing routing; // Routing component for managing network routes
    private MAC mac; // Medium Access Control for transmitting and receiving messages
    private int address; // Address assigned to this node
    private String username; // Username for the node, must be 6 characters
    private boolean receivedKnownAddress; // Indicates if known addresses have been received
    public ArrayList<Integer> knownAddresses; // List of known addresses in the network
    private HashMap<String, Integer> mapNameToIP; // Map of usernames to their IP addresses
    private Printer printer; // Utility for printing formatted output

    /**
     * Constructs an Addressing instance and initializes required components.
     *
     * @param routing The routing component for managing network routes.
     * @param mac     The MAC component for managing message transmission.
     */
    public Addressing(Routing routing, MAC mac) {
        this.routing = routing;
        this.mac = mac;
        this.receivedKnownAddress = false;
        this.address = 0;
        this.mapNameToIP = new HashMap<>();
        this.knownAddresses = new ArrayList<>();
        this.printer = new Printer();
        this.coloursUtility = new Colours();
    }

    /**
     * Prompts the user to enter a username, initiates the addressing process,
     * and assigns an address to the node.
     */
    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome! Please enter a name of 6 characters: ");
        username = scanner.next();

        while (username.length() != 6) {
            System.out.println("Please enter a name of 6 characters.");
            username = scanner.next();
        }

        coloursUtility.printC("Your username is " + username, coloursUtility.routing);

        address = 0;
        mapNameToIP = new HashMap<>();

        try {
            address();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        while (address == 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            routing.getMyIP(address, mapNameToIP);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Assigns an address to the node by communicating with the network.
     * If no known addresses are received within a specified time, assigns a random address.
     *
     * @throws InterruptedException Thrown if the thread is interrupted.
     */
    public void address() throws InterruptedException {
        int tempAddress = 0;
        float timer = 0;

        sendExplorationPacket();

        while (true) {
            Thread.sleep(1600);
            timer += 1;
            if (timer > 5) {
                if (!receivedKnownAddress) {
                    Random random = new Random();
                    tempAddress = random.nextInt(31) + 1; // Random address in range [1, 31]
                    address = tempAddress;
                    knownAddresses.add(address);
                    break;
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Sends an exploration packet to discover known addresses in the network.
     *
     * @throws InterruptedException Thrown if the thread is interrupted while sending the packet.
     */
    public void sendExplorationPacket() throws InterruptedException {
        byte[] packetArray = new byte[32];
        packetArray[0] = 0; // First byte is padding
        packetArray[1] = 0; // Source address (0 since the node has no address yet)
        packetArray[2] = 0; // Padding
        packetArray[3] = 0b00001010; // Time-to-live (10 seconds)
        packetArray[4] = 10; // Data

        ByteBuffer packetData = ByteBuffer.allocate(32);
        packetData.put(packetArray);

        Message packet = new Message(MessageType.DATA, packetData);
        synchronized (mac) {
            mac.addBack(packet);
        }
    }

    /**
     * Sends a packet containing known addresses to inform other nodes in the network.
     *
     * @param address The address of the sender node.
     * @param last    A flag indicating whether this is the final packet of known addresses.
     * @throws InterruptedException Thrown if the thread is interrupted while sending the packet.
     */
    public void sendKnownAddressPacket(int address, boolean last) throws InterruptedException {
        byte[] packetArray = new byte[32];
        int dataLength = knownAddresses.size();

        packetArray[0] = (byte) dataLength; // Data length
        packetArray[1] = (byte) (last ? 0 : 1); // Final packet flag
        packetArray[2] = (byte) address; // Source address
        packetArray[3] = 0b00001010; // Time-to-live (10 seconds)

        int index = 4;
        for (int node : knownAddresses) {
            packetArray[index] = (byte) node;
            index++;
        }

        ByteBuffer packetData = ByteBuffer.allocate(32);
        packetData.put(packetArray);

        Message packet = new Message(MessageType.DATA, packetData);
        synchronized (mac) {
            mac.addBack(packet);
        }
    }

    /**
     * Processes addressing packets received from the network, updating known addresses
     * and responding to requests for known address lists.
     *
     * @param bytes The byte array containing the addressing packet data.
     */
    public void processAddressingPackets(byte[] bytes) {
        if (address != 0) {
            if ((bytes[2] & 0b11111111) == 0) {
                try {
                    sendKnownAddressPacket(address, false);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (bytes[2] != 0) {
            if (bytes[1] == 1) {
                receivedKnownAddress = true;
                int lengthOfData = bytes[0];
                ArrayList<Integer> tempList = new ArrayList<>();

                for (int i = 4; i < 4 + lengthOfData; i++) {
                    tempList.add((int) bytes[i]);
                }

                for (Integer entry : tempList) {
                    if (!knownAddresses.contains(entry)) {
                        knownAddresses.add(entry);
                    }
                }

                Random random = new Random();
                int ownAddress = random.nextInt(31) + 1;
                while (knownAddresses.contains(ownAddress)) {
                    ownAddress = random.nextInt(31) + 1;
                }
                knownAddresses.add(ownAddress);
                address = ownAddress;

                try {
                    sendKnownAddressPacket(address, true);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else if (bytes[1] == 0) {
                int lengthOfData = bytes[0];
                ArrayList<Integer> tempList = new ArrayList<>();

                for (int i = 4; i < 4 + lengthOfData; i++) {
                    tempList.add((int) bytes[i]);
                }

                for (Integer entry : tempList) {
                    if (!knownAddresses.contains(entry)) {
                        knownAddresses.add(entry);
                    }
                }
            }
        }
    }
}
