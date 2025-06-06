
/**
 * ChatClient.java
 * Represents the client in a chat server.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    private Socket socket;
    private BufferedReader inputReader;
    private PrintWriter outputWriter;
    private Scanner userInputScanner;

    /**
     * Chat client constructor
     */
    public ChatClient() {
        userInputScanner = new Scanner(System.in);
    }

    /**
     * Starts the chat client.
     */
    public void startClient() {

        System.out.println(
                "You are not connected to any server. Use /connect <server IP> [port # optional] to connect.");

        while (true) {
            String userInput = userInputScanner.nextLine();
            if ("/quit".equalsIgnoreCase(userInput)) {
                System.out.println("Goodbye!");
                System.exit(0);
            }
            if ("/connect".equalsIgnoreCase(userInput.split(" ")[0])) {
                handleConnectCommand(userInput);
            } else {
                sendMessageToServer(userInput);
            }
        }
    }

    /**
     * Sends a message to the chat server
     * 
     * @param message message to display in server
     */
    private synchronized void sendMessageToServer(String message) {
        outputWriter.println(message);
    }

    /**
     * Handles the client connect command
     * 
     * @param userInput the clients input
     */
    private synchronized void handleConnectCommand(String userInput) {
        String[] commandParts = userInput.split(" ");
        if (commandParts.length < 2 || commandParts.length > 3) {
            System.out.println("Usage: /connect <server-ip> [port # optional]");
            return;
        }

        String serverName = commandParts[1];
        int port = commandParts.length == 3 ? Integer.parseInt(commandParts[2]) : 5000;
        try {
            socket = new Socket(serverName, port);
            inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputWriter = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connected to " + serverName + " on port " + port);
            listenToServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the listener for client -> server.
     */
    private void listenToServer() {
        Thread serverListen = new Thread(() -> {
            try {
                String serverResponse;
                while ((serverResponse = inputReader.readLine()) != null) {
                    System.out.println(serverResponse);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverListen.start();
    }

    /**
     * Main method to run the client
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ChatClient chatClient = new ChatClient();
        chatClient.startClient();
    }
}