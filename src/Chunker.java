import client.Message;
import client.MessageType;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * The Chunker class is responsible for splitting large data into smaller chunks
 * and preparing messages for transmission through the reliability layer.
 * It supports both broadcast and unicast (whispering) messaging modes.
 */
public class Chunker {

    private boolean broadcast; // Flag to indicate whether the message is broadcast or unicast
    private final Reliability reliability; // Reference to the reliability layer for queueing messages
    private Routing routing; // Reference to the routing component for next-hop determination
    private Colours colours = new Colours(); // Utility for colored debugging output
    private int destination; // Destination node for unicast messages

    private ArrayList<Integer> reachableNodes; // List of reachable nodes in the network
    private int ownIP; // The IP address of the current node (assigned after network convergence)

    /**
     * Constructor to initialize the Chunker with reliability and routing components.
     *
     * @param rel     Reference to the reliability component
     * @param routing Reference to the routing component
     */
    public Chunker(Reliability rel, Routing routing) {
        this.reliability = rel;
        this.routing = routing;
        routing.setChunker(this); // Inform routing about this Chunker instance
        this.reachableNodes = new ArrayList<>();
        reachableNodes.add(1); // Adding a default node (adjust as needed)
    }

    /**
     * Splits the given data into chunks and forwards them to the reliability layer.
     *
     * @param toSend The ByteBuffer containing the data to be sent
     * @throws InterruptedException If interrupted while adding messages to the reliability queue
     */
    public void chunkAndParseToReliability(ByteBuffer toSend) throws InterruptedException {
        if (!reachableNodes.isEmpty()) {
            ArrayList<Message> messageList = new ArrayList<>();
            int remaining = toSend.remaining();

            while (remaining > 0) {
                int length = Math.min(remaining, 23); // Maximum chunk size is 23 bytes

                // Create a chunk of data from the toSend buffer
                ByteBuffer chunk = toSend.slice();
                chunk.limit(length);
                toSend.position(toSend.position() + length);

                // Copy chunk data into a new ByteBuffer for message creation
                ByteBuffer packageData = ByteBuffer.allocate(length);
                packageData.put(chunk);
                packageData.flip();

                // Create a new message with the chunk data
                Message msg = new Message(MessageType.DATA, packageData, length);
                messageList.add(msg); // Add the message to the list

                remaining -= length;
            }

            // Construct and send the messages using the prepared chunks
            constructMsg(messageList);
        }
    }

    /**
     * Constructs and queues messages for broadcast or unicast transmission.
     *
     * @param messageList List of message chunks to be transmitted
     */
    private void constructMsg(ArrayList<Message> messageList) {
        int length = messageList.size();
        byte firstByte = (byte) (0b10000000 | (length & 0x7F)); // Flag and number of chunks
        byte[] header = new byte[9];
        header[0] = firstByte; // Message header starts with the flag and length
        header[1] = (byte) -1; // Placeholder for message length
        header[2] = (byte) -1; // Placeholder for next hop
        header[3] = (byte) ownIP; // Source IP
        header[4] = (byte) -1; // Destination IP
        header[5] = (byte) -1; // Sequence number
        header[6] = (byte) ownIP; // Source IP again (used in certain protocols)

        Random random = new Random();

        if (broadcast) {
            // Handle broadcast messages
            for (int node : reachableNodes) {
                int seq = 1;
                for (Message chunk : messageList) {
                    header[1] = (byte) (chunk.getLength() + header.length);
                    header[2] = (byte) routing.getNextHop(node); // Determine next hop
                    if (header[2] == 0) {
                        break; // Skip if no valid next hop
                    }
                    header[4] = (byte) node; // Set destination node
                    header[5] = (byte) seq; // Sequence number
                    header[7] = (byte) random.nextInt(-127, 127); // Random identifier (byte 1)
                    header[8] = (byte) random.nextInt(-127, 127); // Random identifier (byte 2)

                    ByteBuffer message = ByteBuffer.allocate(header.length + chunk.getLength());
                    message.put(header);
                    message.put(chunk.getData());

                    Message msg = new Message(MessageType.DATA, message);
                    synchronized (reliability) {
                        reliability.addReliabilityQueue(msg);
                    }
                    chunk.getData().position(0); // Reset position for reuse
                    seq++;
                }
            }
        } else {
            // Handle unicast messages
            int seq = 1;
            for (Message chunk : messageList) {
                header[1] = (byte) (chunk.getLength() + header.length);
                header[2] = (byte) routing.getNextHop(destination); // Next hop for destination
                if (header[2] == 0) {
                    break; // Skip if no valid next hop
                }
                header[4] = (byte) destination; // Set destination node
                header[5] = (byte) seq; // Sequence number
                header[7] = (byte) random.nextInt(-127, 127); // Random identifier (byte 1)
                header[8] = (byte) random.nextInt(-127, 127); // Random identifier (byte 2)

                ByteBuffer message = ByteBuffer.allocate(32); // Allocate buffer for message
                message.put(header);
                message.put(chunk.getData());

                Message msg = new Message(MessageType.DATA, message);
                synchronized (reliability) {
                    reliability.addReliabilityQueue(msg);
                }
                seq++;
            }
        }
    }

    /**
     * Sets the destination for unicast messages.
     *
     * @param dest The destination node's IP address
     */
    public void setDestination(int dest) {
        this.destination = dest;
    }

    /**
     * Configures whether the messages are broadcast or unicast.
     *
     * @param broadcast True for broadcast, false for unicast
     */
    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    /**
     * Updates the list of reachable nodes for broadcasting.
     *
     * @param reachnodes Set of reachable node IP addresses
     */
    public void setNeighbours(Set<Integer> reachnodes) {
        this.reachableNodes.clear();
        this.reachableNodes.addAll(reachnodes);
    }

    /**
     * Sets the current node's IP address and updates the reliability component.
     *
     * @param ip The IP address of the current node
     */
    public void setIp(int ip) {
        this.ownIP = ip;
        reliability.setIP(ip);
    }
}
