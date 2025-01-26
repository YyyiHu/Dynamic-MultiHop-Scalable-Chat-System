import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import client.Message;
import client.MessageType;

/**
 * The Reliability class ensures reliable communication by managing message acknowledgments,
 * retransmissions, and message sequencing. It interacts with the MAC and Routing classes to handle
 * message delivery and routing effectively.
 */
public class Reliability extends Thread {

    /** Utility class for printing colored output. */
    private final Colours colours = new Colours();

    /** The IP address of this node. */
    private int ownIP;

    /** Reference to the MAC layer for message transmission. */
    private final MAC mac;

    /** Stores the previous hash values to prevent message duplication. */
    private int previousHash1 = -128;
    private int previousHash2 = -128;

    /** Reference to the Routing module for determining the next hop. */
    private final Routing routing;

    /** Printer for displaying messages received by this node. */
    private final Printer printer;

    /** Queue for managing messages in the reliability layer. */
    private final BlockingDeque<Message> reliabilityQueue;

    /**
     * Constructor for the Reliability class.
     *
     * @param mac     Reference to the MAC layer.
     * @param routing Reference to the Routing module.
     */
    public Reliability(MAC mac, Routing routing) {
        this.mac = mac;
        this.routing = routing;
        this.reliabilityQueue = new LinkedBlockingDeque<>();
        this.printer = new Printer();
    }

    /**
     * Sets the IP address of this node.
     *
     * @param ip The IP address to set.
     */
    public synchronized void setIP(int ip) {
        this.ownIP = ip;
    }

    /**
     * Checks if an acknowledgment (ACK) message is intended for this node.
     *
     * @param ip The IP address of the node receiving the ACK.
     */
    public void checkAck(Byte ip) {
        if (ip == ownIP) {
            synchronized (mac) {
                mac.acked();
            }
            try {
                synchronized (reliabilityQueue) {
                    if (reliabilityQueue.peek() != null) {
                        Message nextMessage = reliabilityQueue.take();
                        synchronized (mac) {
                            mac.addBack(nextMessage);
                        }
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Adds a message to the reliability queue for processing.
     *
     * @param message The message to add.
     */
    public void addReliabilityQueue(Message message) {
        try {
            synchronized (reliabilityQueue) {
                this.reliabilityQueue.put(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Processes a normal data message. If the message is intended for this node, it is printed;
     * otherwise, it is forwarded to the next hop.
     *
     * @param message The message to process.
     */
    public void processNormalMsg(Message message) {
        int finalDestination = message.getData().get(4);
        int nextHop;
        int hash1 = message.getData().get(7);
        int hash2 = message.getData().get(8);

        if (finalDestination == ownIP) {
            printer.printDataMessage(message.getData());
        } else {
            if (!(hash1 == previousHash1 && hash2 == previousHash2)) {
                nextHop = routing.getNextHop(finalDestination);

                if (nextHop == 0) {
                    colours.printC("Route not found for the destination, dropping the message.", colours.reliability);
                    return;
                }

                previousHash1 = hash1;
                previousHash2 = hash2;

                ByteBuffer nextData = message.getData();
                nextData.put(2, (byte) nextHop);
                nextData.put(6, (byte) ownIP);

                Message nextMessage = new Message(MessageType.DATA, nextData);
                synchronized (reliabilityQueue) {
                    try {
                        reliabilityQueue.put(nextMessage);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Constructs and sends an acknowledgment (ACK) message.
     *
     * @param ip The IP address of the node to acknowledge.
     */
    public void sendAck(int ip) {
        ByteBuffer data = ByteBuffer.allocate(2);
        data.put((byte) 0);
        data.put((byte) ip);
        Message ack = new Message(MessageType.DATA_SHORT, data);

        synchronized (mac) {
            mac.ackQueuePut(ack);
        }
    }

    /**
     * The main thread logic for managing message transmission and ensuring reliable communication.
     */
    @Override
    public void run() {
        while (true) {
            Message firstMessage;

            synchronized (reliabilityQueue) {
                firstMessage = reliabilityQueue.peek();
            }

            if (firstMessage != null) {
                int sequence = firstMessage.getData().get(5);

                if (sequence == 1) {
                    synchronized (mac) {
                        mac.sendFirstMsg();
                    }
                }

                synchronized (reliabilityQueue) {
                    try {
                        Message messageToSend = reliabilityQueue.take();
                        synchronized (mac) {
                            mac.addToNormalQueue(messageToSend);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}