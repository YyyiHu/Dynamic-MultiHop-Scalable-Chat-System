package client;

import java.nio.ByteBuffer;

/**
 * The Message class represents a unit of communication in the client-server system.
 * Each Message has a type, optional data, and a length, allowing it to encapsulate
 * various message types and payloads.
 */
public class Message {

    private final MessageType type; // The type of the message (e.g., DATA, FREE, etc.)
    private ByteBuffer data; // The payload of the message (optional)
    private int length; // The length of the data payload

    /**
     * Constructs a Message with only a type.
     *
     * @param type The type of the message.
     */
    public Message(MessageType type) {
        this.type = type;
    }

    /**
     * Constructs a Message with a type and data.
     *
     * @param type The type of the message.
     * @param data The ByteBuffer containing the message's payload.
     */
    public Message(MessageType type, ByteBuffer data) {
        this.type = type;
        this.data = data;
    }

    /**
     * Constructs a Message with a type, data, and specified length.
     *
     * @param type   The type of the message.
     * @param data   The ByteBuffer containing the message's payload.
     * @param length The length of the payload.
     */
    public Message(MessageType type, ByteBuffer data, int length) {
        this.type = type;
        this.data = data;
        this.length = length;
    }

    /**
     * Retrieves the type of the message.
     *
     * @return The MessageType of the message.
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Retrieves the data payload of the message.
     *
     * @return The ByteBuffer containing the message's data.
     */
    public ByteBuffer getData() {
        return data;
    }

    /**
     * Retrieves the length of the message's payload.
     *
     * @return The length of the data payload.
     */
    public int getLength() {
        return length;
    }
}
