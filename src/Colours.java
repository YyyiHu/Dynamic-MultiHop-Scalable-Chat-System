/**
 * The Colours class provides colors for different classes for TUI.
 */
public class Colours {
    String mac = "\u001B[31m"; //red
    String reliability = "\u001B[32m"; //green
    String routing = "\u001B[33m"; //yellow
    String receiver = "\u001B[34m"; //blue
    String myProtocol = "\u001B[35m"; //purple
    public void printC(String s, String c) {
        System.out.println(c + s + "\u001B[0m");
    }

}
