import java.nio.ByteBuffer;

/**
 * The Printer class is responsible for handling and processing data messages
 * represented as ByteBuffers. It constructs messages from fragmented data and
 * ensures they are printed only when the full message is received.
 */
public class Printer {

    /** Buffer used for constructing messages for printing. */
    private ByteBuffer forPrinting = null;

    /** Tracks the last random values received in the header to avoid duplicate messages. */
    private int lastRan1 = -128;
    private int lastRan2 = -128;

    /** Helper class for printing colored output. */
    private final Colours colours = new Colours();

    /** Sequence number of the last processed fragment. */
    private int lastSequenceProcessed = 0;

    /** IP address of the sender. */
    private int senderIP = 0;

    /**
     * Processes and prints data messages from a ByteBuffer.
     *
     * @param buf The ByteBuffer containing the message fragments.
     */
    public void printDataMessage(ByteBuffer buf) {
        // Extract the sender's IP address
        senderIP = buf.get(3);

        // Extract the message metadata
        byte firstByte = buf.get(0);
        byte amountOfFragments = (byte) (firstByte & 0x7F); // Number of fragments in the message
        int messageLength = buf.get(1); // Length of the payload in the current fragment
        int sequenceNumber = (buf.get(5) & 0b01111111); // Sequence number of the fragment
        int currentRandom1 = buf.get(7);
        int currentRandom2 = buf.get(8);

        // Process only if the sequence number matches the expected sequence
        if (sequenceNumber == lastSequenceProcessed + 1) {
            if (sequenceNumber == 1) {
                // Initialize the buffer for the new message
                forPrinting = ByteBuffer.allocate(23 * amountOfFragments);
                forPrinting.position(0);

                // Copy the payload from the first fragment
                for (int i = 9; i < messageLength; i++) {
                    forPrinting.put(buf.get(i));
                }
            } else if (sequenceNumber > 1 && sequenceNumber <= amountOfFragments) {
                // Append the payload of subsequent fragments
                for (int i = 9; i < messageLength; i++) {
                    forPrinting.put(buf.get(i));
                }
            }

            // Update the last processed sequence number
            lastSequenceProcessed++;

            // Check if the last fragment of the message is received
            if (sequenceNumber == amountOfFragments) {
                // Ensure the random values have changed to avoid duplicates
                if (lastRan1 != currentRandom1 || lastRan2 != currentRandom2) {
                    int finalMessageLength = ((amountOfFragments - 1) * 23) + (messageLength - 9);

                    // Prepare the final buffer for printing
                    ByteBuffer finalBuffer = ByteBuffer.allocate(finalMessageLength);
                    forPrinting.position(0);
                    for (int i = 0; i < finalMessageLength; i++) {
                        finalBuffer.put(forPrinting.get(i));
                    }

                    // Print the reconstructed message
                    printByteBuffer(finalBuffer);

                    // Reset state for the next message
                    lastSequenceProcessed = 0;
                    lastRan1 = currentRandom1;
                    lastRan2 = currentRandom2;
                }
            }
        }
    }

    /**
     * Converts the ByteBuffer content into a readable string and prints it.
     *
     * @param bytes The ByteBuffer containing the reconstructed message.
     */
    private void printByteBuffer(ByteBuffer bytes) {
        StringBuilder byteStringBuilder = new StringBuilder();
        StringBuilder stringStringBuilder = new StringBuilder();

        bytes.position(0); // Reset the position to the start
        int length = bytes.remaining();

        // Convert each byte to its integer and character representation
        for (int i = 0; i < length; i++) {
            byte currentByte = bytes.get(i);
            int intValue = currentByte & 0xFF;

            byteStringBuilder.append(intValue);
            stringStringBuilder.append((char) intValue);
        }

        // Print the reconstructed message
        System.out.println("Message from " + senderIP + ": " + "\u001B[36m" + stringStringBuilder);
        System.out.print("\u001B[0m"); // Reset the console color
    }
}
