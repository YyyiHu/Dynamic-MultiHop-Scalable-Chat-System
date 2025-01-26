import client.Message;

import java.util.Random;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * The MAC class manages Medium Access Control and handles the transmission of messages.
 * It utilizes separate threads for normal messages and acknowledgment (ACK) messages.
 */
public class MAC extends Thread {

    /** Indicates if the communication channel is free. */
    private boolean channelFree = true;

    /** Queue for normal messages to send. */
    private final LinkedBlockingDeque<Message> normalToSend = new LinkedBlockingDeque<>();

    /** Queue for acknowledgment (ACK) messages to send. */
    private final LinkedBlockingDeque<Message> ackToSend = new LinkedBlockingDeque<>();

    /** Queue for messages being sent. */
    private final LinkedBlockingDeque<Message> sendingQueue;

    /** Queue for other messages waiting to send. */
    private final LinkedBlockingDeque<Message> toSendQueue = new LinkedBlockingDeque<>();

    /** Thread for sending normal messages. */
    private final SendNormal sendNormal;

    /** Thread for sending acknowledgment messages. */
    private final SendACK sendACK;

    /** Indicates if normal messages can be sent. */
    private boolean canSend = false;

    /** Stores the last normal message attempted to send. */
    private Message normalMsgTried = null;

    /** Stores other types of messages being sent. */
    private Message otherMsg = null;

    /** Indicates if the MAC is in the backoff stage. */
    private boolean backoff = false;

    /** Random number generator for backoff timing. */
    private final Random random = new Random();

    /** Minimum time for backoff in milliseconds. */
    private static final int MIN_TIME = 0;

    /** Maximum time for backoff in milliseconds. */
    private int maxTime = 3000;

    /**
     * Constructor to initialize the MAC class with a sending queue.
     *
     * @param sendingQueue The shared queue for messages being transmitted.
     */
    public MAC(LinkedBlockingDeque<Message> sendingQueue) {
        super("MAC");
        this.sendingQueue = sendingQueue;
        this.sendNormal = new SendNormal(normalToSend);
        this.sendACK = new SendACK(ackToSend);
        this.sendACK.start();
        this.sendNormal.start();
    }

    /**
     * Marks the MAC as entering the backoff stage and enables sending of the first message.
     */
    public synchronized void sendFirstMsg() {
        backoff = true;
        canSend = true;
    }

    /**
     * Adds a normal message to the normalToSend queue.
     *
     * @param message The normal message to add.
     */
    public void addToNormalQueue(Message message) {
        try {
            synchronized (normalToSend) {
                normalToSend.put(message);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the channel's state.
     *
     * @param isFree True if the channel is free, false otherwise.
     */
    public synchronized void setChannel(boolean isFree) {
        channelFree = isFree;
    }

    /**
     * Marks the last message as acknowledged and resets backoff settings.
     */
    public synchronized void acked() {
        canSend = true;
        maxTime = 3000; // Reset max waiting time for backoff
        backoff = false;
        normalMsgTried = null;
    }

    /**
     * Adds a message back into the toSendQueue.
     *
     * @param message The message to add back.
     * @throws InterruptedException If interrupted while adding the message.
     */
    public void addBack(Message message) throws InterruptedException {
        synchronized (toSendQueue) {
            toSendQueue.put(message);
        }
    }

    /**
     * Adds an acknowledgment message to the ackToSend queue.
     *
     * @param message The acknowledgment message to add.
     */
    public void ackQueuePut(Message message) {
        try {
            synchronized (ackToSend) {
                ackToSend.put(message);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds a message to the front of the toSendQueue.
     *
     * @param message The message to add to the front.
     */
    public synchronized void addFront(Message message) {
        synchronized (toSendQueue) {
            toSendQueue.offerFirst(message);
        }
    }

    /**
     * The main thread execution logic for handling message transmission.
     */
    @Override
    public void run() {
        while (true) {
            boolean toSendEmpty;

            if (channelFree) {
                synchronized (toSendQueue) {
                    toSendEmpty = toSendQueue.isEmpty();
                }

                if (!toSendEmpty) {
                    try {
                        synchronized (toSendQueue) {
                            otherMsg = toSendQueue.take();
                        }

                        int waitTime = random.nextInt(150, 300);
                        Thread.sleep(waitTime);

                        synchronized (sendingQueue) {
                            sendingQueue.put(otherMsg);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Thread for sending normal messages.
     */
    public class SendNormal extends Thread {
        private final LinkedBlockingDeque<Message> normalToSend;

        /**
         * Constructor for the SendNormal thread.
         *
         * @param normalToSend The queue for normal messages to send.
         */
        SendNormal(LinkedBlockingDeque<Message> normalToSend) {
            this.normalToSend = normalToSend;
        }

        /**
         * The main thread execution logic for sending normal messages.
         */
        @Override
        public void run() {
            while (true) {
                boolean canTake = false;

                if (channelFree) {
                    synchronized (normalToSend) {
                        if (canSend && (normalToSend.peek() != null)) {
                            canTake = true;
                        }
                    }

                    if (canTake) {
                        synchronized (normalToSend) {
                            try {
                                normalMsgTried = normalToSend.take();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    if (normalMsgTried != null) {
                        backoff = ((normalMsgTried.getData().get(5) & 0b01111111) == 1);
                    }

                    if (backoff) {
                        int waitTime = random.nextInt(MIN_TIME, maxTime);
                        try {
                            Thread.sleep(waitTime);

                            if (maxTime < 15000) {
                                maxTime += 1000;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    if (canSend) {
                        try {
                            synchronized (sendingQueue) {
                                if (normalMsgTried != null) {
                                    sendingQueue.put(normalMsgTried);
                                    canSend = false;

                                    int waitForAckTime = random.nextInt(4000, 12000);
                                    Thread.sleep(waitForAckTime);
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        try {
                            synchronized (sendingQueue) {
                                if (normalMsgTried != null) {
                                    sendingQueue.put(normalMsgTried);
                                    int waitForAckTime = random.nextInt(6000, 15000);
                                    Thread.sleep(waitForAckTime);
                                }
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Thread for sending acknowledgment (ACK) messages.
     */
    private class SendACK extends Thread {
        private final LinkedBlockingDeque<Message> ackToSend;

        /**
         * Constructor for the SendACK thread.
         *
         * @param ackToSend The queue for ACK messages to send.
         */
        SendACK(LinkedBlockingDeque<Message> ackToSend) {
            this.ackToSend = ackToSend;
        }

        /**
         * The main thread execution logic for sending ACK messages.
         */
        @Override
        public void run() {
            while (true) {
                if (!ackToSend.isEmpty()) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    synchronized (sendingQueue) {
                        try {
                            sendingQueue.put(ackToSend.take());
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }
}