package client;

/**
 * The MessageType enum defines various types of messages that can be used in the system.
 * These message types represent specific states, actions, or signals exchanged between nodes in the network.
 */
public enum MessageType {
    /**
     * Represents a state where no message is being transmitted or the channel is free.
     */
    FREE,

    /**
     * Indicates that the channel is currently busy and cannot accept new messages.
     */
    BUSY,

    /**
     * Represents a regular data message being transmitted between nodes.
     */
    DATA,

    /**
     * Signals the start of a message transmission process.
     */
    SENDING,

    /**
     * Indicates that the message transmission has been completed.
     */
    DONE_SENDING,

    /**
     * Represents a short data message, typically for lightweight or control communications.
     */
    DATA_SHORT,

    /**
     * Indicates the end of a communication session or connection.
     */
    END,

    /**
     * A "Hello" message used for initial communication or neighbor discovery.
     */
    HELLO,

    /**
     * Indicates that a token (permission to send) has been accepted by a node.
     */
    TOKEN_ACCEPTED,

    /**
     * Indicates that a token (permission to send) has been rejected by a node.
     */
    TOKEN_REJECTED
}
