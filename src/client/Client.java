package client;

import java.nio.channels.SocketChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * The Client class manages client-side communication with the server,
 * including establishing connections, sending messages, and receiving responses.
 */
public class Client {

    private SocketChannel sock; // Socket channel for communication with the server
    private BlockingQueue<Message> receivedQueue; // Queue for storing received messages
    private LinkedBlockingDeque<Message> sendingQueue; // Queue for storing messages to be sent
    private String token; // Authentication token for communication

    /**
     * Utility method to print the content of a ByteBuffer for debugging purposes.
     *
     * @param bytes       The ByteBuffer to print.
     * @param bytesLength The number of bytes to print.
     */
    public void printByteBuffer(ByteBuffer bytes, int bytesLength) {
        System.out.print("DATA: ");
        for (int i = 0; i < bytesLength; i++) {
            System.out.print(Byte.toString(bytes.get(i)) + " ");
        }
        System.out.println();
    }

    /**
     * Constructs a Client instance and initializes the connection to the server.
     *
     * @param serverIp      The server's IP address.
     * @param serverPort    The server's port number.
     * @param frequency     The frequency value for communication.
     * @param token         The authentication token.
     * @param receivedQueue The queue for received messages.
     * @param sendingQueue  The queue for messages to be sent.
     */
    public Client(String serverIp, int serverPort, int frequency, String token, BlockingQueue<Message> receivedQueue, LinkedBlockingDeque<Message> sendingQueue) {
        this.receivedQueue = receivedQueue;
        this.sendingQueue = sendingQueue;
        this.token = token;
        SocketChannel sock;
        Sender sender;
        Listener listener;
        try {
            sock = SocketChannel.open();
            sock.connect(new InetSocketAddress(serverIp, serverPort));
            listener = new Listener(sock, receivedQueue);
            sender = new Sender(sock, sendingQueue);

            sender.sendConnect(frequency);
            sender.sendToken(token);

            listener.start();
            sender.start();
        } catch (IOException e) {
            System.err.println("Failed to connect: " + e);
            System.exit(1);
        }
    }

    /**
     * The Sender class is responsible for sending messages from the sending queue to the server.
     */
    private class Sender extends Thread {
        private BlockingQueue<Message> sendingQueue; // Queue for storing messages to be sent
        private SocketChannel sock; // Socket channel for communication with the server

        public Sender(SocketChannel sock, BlockingQueue<Message> sendingQueue) {
            super();
            this.sendingQueue = sendingQueue;
            this.sock = sock;
        }

        /**
         * The main loop for sending messages from the queue to the server.
         */
        private void senderLoop() {
            while (sock.isConnected()) {
                try {
                    Message msg = sendingQueue.take();
                    if (msg.getType() == MessageType.DATA || msg.getType() == MessageType.DATA_SHORT) {
                        ByteBuffer data = msg.getData();
                        data.position(0); // Reset position to ensure correct data sending
                        int length = data.capacity();
                        ByteBuffer toSend = ByteBuffer.allocate(length + 2);
                        if (msg.getType() == MessageType.DATA) {
                            toSend.put((byte) 3);
                        } else { // Must be DATA_SHORT due to the check above
                            toSend.put((byte) 6);
                        }
                        toSend.put((byte) length);
                        toSend.put(data);
                        toSend.position(0);
                        sock.write(toSend);
                    }
                } catch (IOException e) {
                    System.err.println("Error in socket (sender): " + e);
                } catch (InterruptedException e) {
                    System.err.println("Failed to take from sendingQueue: " + e);
                }
            }
        }

        /**
         * Sends a connection message to the server with the specified frequency.
         *
         * @param frequency The frequency value.
         */
        public void sendConnect(int frequency) {
            ByteBuffer buff = ByteBuffer.allocate(4);
            buff.put((byte) 9);
            buff.put((byte) ((frequency >> 16) & 0xff));
            buff.put((byte) ((frequency >> 8) & 0xff));
            buff.put((byte) (frequency & 0xff));
            buff.position(0);
            try {
                sock.write(buff);
            } catch (IOException e) {
                System.err.println("Failed to send HELLO");
            }
        }

        /**
         * Sends the authentication token to the server.
         *
         * @param token The authentication token.
         */
        public void sendToken(String token) {
            byte[] tokenBytes = token.getBytes();
            ByteBuffer buff = ByteBuffer.allocate(tokenBytes.length + 2);
            buff.put((byte) 10);
            buff.put((byte) tokenBytes.length);
            buff.put(tokenBytes);
            buff.position(0);
            try {
                sock.write(buff);
            } catch (IOException e) {
                System.err.println("Failed to send HELLO");
            }
        }

        @Override
        public void run() {
            senderLoop();
        }
    }

    /**
     * The Listener class is responsible for receiving messages from the server and parsing them.
     */
    private class Listener extends Thread {
        private BlockingQueue<Message> receivedQueue; // Queue for storing received messages
        private SocketChannel sock; // Socket channel for communication with the server

        private ByteBuffer messageBuffer = ByteBuffer.allocate(1024); // Buffer for incoming message data
        private int messageLength = -1; // Length of the current message being received
        private boolean messageReceiving = false; // Flag indicating if a message is being received
        private boolean shortData = false; // Flag for identifying short data messages

        public Listener(SocketChannel sock, BlockingQueue<Message> receivedQueue) {
            super();
            this.receivedQueue = receivedQueue;
            this.sock = sock;
        }

        /**
         * Parses received data and constructs messages based on the protocol.
         *
         * @param received     The ByteBuffer containing received data.
         * @param bytesReceived The number of bytes received.
         */
        private void parseMessage(ByteBuffer received, int bytesReceived) {
            try {
                for (int offset = 0; offset < bytesReceived; offset++) {
                    byte d = received.get(offset);

                    if (messageReceiving) {
                        if (messageLength == -1) {
                            messageLength = (int) d;
                            messageBuffer = ByteBuffer.allocate(messageLength);
                        } else {
                            messageBuffer.put(d);
                        }
                        if (messageBuffer.position() == messageLength) {
                            messageBuffer.position(0);
                            ByteBuffer temp = ByteBuffer.allocate(messageLength);
                            temp.put(messageBuffer);
                            temp.rewind();
                            if (shortData) {
                                receivedQueue.put(new Message(MessageType.DATA_SHORT, temp));
                            } else {
                                receivedQueue.put(new Message(MessageType.DATA, temp));
                            }
                            messageReceiving = false;
                        }
                    } else {
                        switch (d) {
                            case 0x09:
                                receivedQueue.put(new Message(MessageType.HELLO));
                                break;
                            case 0x01:
                                receivedQueue.put(new Message(MessageType.FREE));
                                break;
                            case 0x02:
                                receivedQueue.put(new Message(MessageType.BUSY));
                                break;
                            case 0x03:
                                messageLength = -1;
                                messageReceiving = true;
                                shortData = false;
                                break;
                            case 0x04:
                                receivedQueue.put(new Message(MessageType.SENDING));
                                break;
                            case 0x05:
                                receivedQueue.put(new Message(MessageType.DONE_SENDING));
                                break;
                            case 0x06:
                                messageLength = -1;
                                messageReceiving = true;
                                shortData = true;
                                break;
                            case 0x08:
                                receivedQueue.put(new Message(MessageType.END));
                                break;
                            case 0x0A:
                                receivedQueue.put(new Message(MessageType.TOKEN_ACCEPTED));
                                break;
                            case 0x0B:
                                receivedQueue.put(new Message(MessageType.TOKEN_REJECTED));
                                break;
                        }
                    }
                }

            } catch (InterruptedException e) {
                System.err.println("Failed to put data in receivedQueue: " + e);
            }
        }

        /**
         * Main loop for receiving and parsing messages from the server.
         */
        public void receivingLoop() {
            int bytesRead;
            ByteBuffer recv = ByteBuffer.allocate(1024);
            try {
                while (sock.isConnected()) {
                    bytesRead = sock.read(recv);
                    if (bytesRead > 0) {
                        parseMessage(recv, bytesRead);
                    } else {
                        break;
                    }
                    recv.clear();
                }
            } catch (IOException e) {
                System.out.println("Error in socket (receiver): " + e);
            }
        }

        @Override
        public void run() {
            receivingLoop();
        }
    }
}
