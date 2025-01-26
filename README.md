# Dynamic MultiHop Scalable Chat System

This project implements a **Dynamic MultiHop Scalable Chat System**, designed to simulate a scalable and fault-tolerant network protocol. The system allows communication between multiple nodes in an ad-hoc topology, featuring multi-hop message routing, neighbor discovery, and fault detection.

---

## **Features**

- **Multi-Hop Routing**: Efficiently routes messages across multiple nodes in a dynamic network.
- **Neighbor Discovery**: Tracks active neighbors using periodic keep-alive messages.
- **Message Chunking and Reassembly**: Handles large messages by splitting and reassembling them for reliable transmission.
- **Resilience**: Automatically removes failed nodes from routing tables.
- **Dynamic Topology Updates**: Continuously updates routing tables to adapt to changes in the network.

---

## **Project Structure**

```plaintext
Project Root
├── src/
│   ├── client/
│   │   ├── Client.java          # Manages client-side communication, including connecting to the server
│   │   ├── Message.java         # Defines the structure of communication messages
│   │   └── MessageType.java     # Enumerates the types of messages
│   ├── Addressing.java          # Handles address assignment and parsing
│   ├── Chunker.java             # Manages message chunking and reassembly
│   ├── Colours.java             # Utility for colorful console output
│   ├── MAC.java                 # Simulates the MAC layer for communication
│   ├── MyProtocol.java          # Main class to run the system
│   ├── Printer.java             # Helper class for console output
│   ├── Receiver1.java           # Handles receiving and processing messages
│   ├── Reliability.java         # Ensures reliability in message transmission
│   └── Routing.java             # Maintains routing tables and manages routing logic
```

---

## **Getting Started**

### **Prerequisites**

- **Java Development Kit (JDK)**: Version 11 or higher.
- **IntelliJ IDEA** or any Java-compatible IDE.

### **Setup Instructions**

1. Clone the repository:
   ```bash
   git clone https://github.com/YyyiHu/Dynamic-MultiHop-Chat-System.git
   ```
2. Open the project in your preferred IDE.
3. Ensure the dependencies are properly configured (if applicable).
4. Navigate to the `MyProtocol` class and run the main method to start the application.

---

## **Usage**

1. Start the application on four nodes using the `MyProtocol` class.
2. Nodes will automatically discover neighbors and exchange routing information.
3. Use the console interface to send messages to other nodes in the network.

---

## **Contributing**

Contributions are welcome! To contribute:

1. Fork the repository.
2. Create a new feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Commit your changes with clear messages.
4. Push the branch and create a pull request.

---

## **License**

This project is licensed under the MIT License. See the `LICENSE` file for details.

---

## **Acknowledgments**

- This project draws inspiration from the Network Systems module at the University of Twente and has been supported by its resources and guidance.
