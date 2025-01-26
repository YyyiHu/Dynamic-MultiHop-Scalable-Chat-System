import client.Message;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

/**
 * The Receiver1 class is responsible for receiving messages from the outer world via the Client.
 * It processes various message types and handles routing, reliability, and addressing operations.
 */
public class Receiver1 extends Thread {

    private BlockingQueue<Message> receivedQueue; // Queue to store received messages
    private Colours colours = new Colours();

    private MAC mac; // Medium Access Control (MAC) for managing channel access
    private final ByteBuffer forPrinting = null; // Reserved for printing/debugging purposes (unused in current logic)

    private int ownIP; // IP address of this node

    private Reliability reliability; // Reliable data transfer handler
    private Routing routing; // Routing handler for managing network routes
    private Addressing addressing; // Addressing handler for processing addressing packets

    /**
     * Constructor to initialize the Receiver1 instance with required components.
     *
     * @param receivedQueue Queue to receive incoming messages
     * @param reliability   Reference to the Reliability component
     * @param mac           Reference to the MAC component
     * @param routing       Reference to the Routing component
     * @param addressing    Reference to the Addressing component
     */
    public Receiver1(BlockingQueue<Message> receivedQueue, Reliability reliability, MAC mac, Routing routing, Addressing addressing) {
        super();
        this.receivedQueue = receivedQueue;
        this.mac = mac;
        this.reliability = reliability;
        this.routing = routing;
        this.addressing = addressing;
    }

    /**
     * Sets the IP address for this Receiver1 instance.
     *
     * @param ip The IP address to assign to this receiver
     */
    public void setIPForReceiver(int ip) {
        this.ownIP = ip;
    }

    /**
     * Main logic for the Receiver1 thread, which continuously processes incoming messages.
     */
    @Override
    public void run() {
        this.routing.setReceiver(this); // Link this receiver to the routing component
        while (true) {
            try {
                // Take the next message from the queue
                Message m = receivedQueue.take();

                // Handle the message based on its type
                switch (m.getType()) {
                    case BUSY:
                        mac.setChannel(false); // Mark the channel as busy
                        break;

                    case FREE:
                        mac.setChannel(true); // Mark the channel as free
                        break;

                    case DATA:
                        handleDataMessage(m);
                        break;

                    case DATA_SHORT:
                        handleShortDataMessage(m);
                        break;

                    case DONE_SENDING:
                        // Placeholder for handling DONE_SENDING messages
                        break;

                    case HELLO:
                        // Placeholder for handling HELLO messages
                        break;

                    case SENDING:
                        // Placeholder for handling SENDING messages
                        break;

                    case END:
                        System.exit(0); // Terminate the program
                        break;

                    case TOKEN_ACCEPTED:
                        break;

                    case TOKEN_REJECTED:
                        break;

                    default:
                        // Handle unexpected message types if necessary
                        break;
                }
            } catch (InterruptedException e) {
                // Handle interruption gracefully (e.g., log the exception)
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Handles a DATA message, processes it further based on its type and content.
     *
     * @param m The message to process
     */
    private void handleDataMessage(Message m) {
        ByteBuffer data = m.getData();
        byte[] bytes = data.array();

        if (((data.get(0)) >> 7) == -1) { // Check if the packet is a normal message
            int msgFor = data.get(2); // Get the next hop for the sender's message

            if (msgFor == ownIP) { // Process the message if it's for this node
                int sender = data.get(6); // Get the sender's IP
                reliability.sendAck(sender); // Send acknowledgment to the sender
                reliability.processNormalMsg(m); // Process the normal message
            }
        } else if ((bytes[0] & 0b01000000) >> 6 == 1) { // Check if it's a link state table
            try {
                routing.updateTheTable(bytes); // Update the routing table
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                colours.printC("Error when updating the routing table", colours.receiver);
            }
        } else if ((bytes[0] & 0b01000000) == 0) { // Check if it's an addressing packet
            addressing.processAddressingPackets(bytes); // Process the addressing packet
        }
    }

    /**
     * Handles a DATA_SHORT message, determining whether it's an acknowledgment or keep-alive.
     *
     * @param m The short data message to process
     */
    private void handleShortDataMessage(Message m) {
        ByteBuffer data = m.getData();
        byte[] bytes = data.array();

        if (data.get(0) == 0) { // If the short data is an acknowledgment
            reliability.checkAck(data.get(1)); // Verify and process the acknowledgment
        } else { // If it's a keep-alive message
            routing.processKeepAlive(bytes); // Process the keep-alive message
        }
    }
}
