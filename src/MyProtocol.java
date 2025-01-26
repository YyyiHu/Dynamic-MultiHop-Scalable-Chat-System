import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyProtocol class implements the communication protocol for interacting with the server.
 * This class handles message sending and receiving, along with maintaining the necessary
 * components for reliable communication.
 */
public class MyProtocol {

    // The host to connect to. Use localhost for the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl";
    // The port to connect to. Use 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency for message sending.
    private static int frequency = 2301;
    // Token received for the frequency range.
    String token = "java-18-LEXH8BFYSAQ0TMGCUR";

    // Queues for sending and receiving messages.
    private BlockingQueue<Message> receivedQueue;
    private LinkedBlockingDeque<Message> sendingQueue;

    // Core components for the protocol.
    private MAC mac;
    private Receiver1 receiver;
    private Reliability reliability;
    private Routing routing;
    private Addressing addressing;
    private Chunker chunk;
    public Colours colours;

    /**
     * Initializes the MyProtocol instance with the specified parameters.
     *
     * @param server_ip   The IP address of the server.
     * @param server_port The port number of the server.
     * @param frequency   The frequency for message sending.
     */
    public MyProtocol(String server_ip, int server_port, int frequency) {
        receivedQueue = new LinkedBlockingQueue<>();
        sendingQueue = new LinkedBlockingDeque<>();

        // Initialize the client with the queues to use.
        new Client(SERVER_IP, SERVER_PORT, frequency, token, receivedQueue, sendingQueue);

        // Initialize and start the MAC thread for managing message sending conditions.
        mac = new MAC(sendingQueue);
        mac.start();

        // Initialize the coloured debugging tool for output.
        colours = new Colours();

        // Initialize core protocol components.
        routing = new Routing(mac, this); // Manages routing of messages between nodes.
        addressing = new Addressing(routing, mac); // Handles addressing mechanisms for nodes.
        addressing.start();
        reliability = new Reliability(mac, routing); // Ensures reliable communication by managing retransmissions.
        reliability.start();
        chunk = new Chunker(reliability, routing); // Breaks large messages into smaller chunks.
        receiver = new Receiver1(receivedQueue, reliability, mac, routing, addressing); // Handles incoming messages and processes them.

        // Start the receiver thread to process received messages.
        receiver.start();
    }

    /**
     * Starts the chat functionality, enabling users to send and receive messages.
     * Users can whisper to specific nodes, broadcast to all nodes, or view online nodes.
     */
    public void startChat() {
        routing.regularLinkState.start(); // Starts the regular link-state routing updates.
        try {
            ByteBuffer temp = ByteBuffer.allocate(1024); // Temporary buffer to hold user input.
            int read;
            int new_line_offset = 0;

            // Display chat usage instructions.
            colours.printC("Please write 'W <receiver's number>:' + 'Your message' if you want to whisper, 'B:' + 'Your message' if you want to broadcast, or 'ONLINE' to see online users!", colours.myProtocol);

            while (true) {
                read = System.in.read(temp.array());
                if (read > 0) {
                    // Check for newline characters and adjust offset accordingly.
                    if (temp.get(read - 1) == '\n' || temp.get(read - 1) == '\r')
                        new_line_offset = 1;
                    if (read > 1 && (temp.get(read - 2) == '\n' || temp.get(read - 2) == '\r'))
                        new_line_offset = 2;

                    // Convert input to a string and process commands.
                    String input = new String(temp.array(), 0, read - new_line_offset, StandardCharsets.UTF_8);
                    String message = null;

                    // Handle whisper command (W <receiver>: <message>).
                    if (input.startsWith("W ")) {
                        Pattern pattern = Pattern.compile("W (\\d+):\\s*(.*)");
                        Matcher matcher = pattern.matcher(input);
                        if (matcher.find()) {
                            Integer.parseInt(matcher.group(1)); // Extract the receiver's ID.
                            message = matcher.group(2); // Extract the message.
                            chunk.setBroadcast(false); // Set to unicast mode.
                            chunk.setDestination(Integer.parseInt(matcher.group(1))); // Set destination node.
                        }

                        assert message != null;
                        ByteBuffer toSend = ByteBuffer.allocate(message.length());
                        toSend.put(message.getBytes(StandardCharsets.UTF_8));
                        toSend.flip();
                        if ((read - new_line_offset) > 0) {
                            chunk.chunkAndParseToReliability(toSend); // Process the message for reliability.
                        }
                    }
                    // Handle broadcast command (B: <message>).
                    else if (input.startsWith("B:")) {
                        message = input.substring("B:".length());
                        chunk.setBroadcast(true); // Set to broadcast mode.
                        ByteBuffer toSend = ByteBuffer.allocate(message.length());
                        toSend.put(message.getBytes(StandardCharsets.UTF_8));
                        toSend.flip();
                        if ((read - new_line_offset) > 0) {
                            chunk.chunkAndParseToReliability(toSend); // Process the broadcast message.
                        }
                    }
                    // Handle ONLINE command to display online nodes.
                    else if (input.startsWith("ONLINE")) {
                        System.out.println("Nodes in the network: ");
                        for (int neighbour : routing.getNeighbours()) {
                            colours.printC(String.valueOf(neighbour), colours.myProtocol); // Display each online node.
                        }
                    }
                    // Invalid command handling.
                    else {
                        System.out.println("Invalid command. Please start your message with 'W:' or 'B:'.");
                    }
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Main method to initialize the protocol and start the client.
     *
     * @param args Command-line arguments to specify frequency (optional).
     */
    public static void main(String args[]) {
        if (args.length > 0) {
            frequency = Integer.parseInt(args[0]); // Override default frequency if specified.
        }
        new MyProtocol(SERVER_IP, SERVER_PORT, frequency); // Start the protocol instance.
    }
}
