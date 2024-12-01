
/**
 * Handles the client commands for the chat server.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ChatServer chatServer;
    private String clientNickname = "";
    private Set<String> joinedChannels;
    private int totalMessagesFromOne;

    /**
     * Constructor for the ClientHandler.
     * 
     * @param clientSocket the socket of the client
     * @param chatServer   the chat server object
     */
    public ClientHandler(Socket clientSocket, ChatServer chatServer) {
        this.clientSocket = clientSocket;
        this.chatServer = chatServer;
        this.joinedChannels = new HashSet<>();

        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (SocketException e) {
            sendMessageToClient("Goodbye!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            sendMessageToClient("Welcome to the ChatServer, choose a name: ");
            String nameResponse = reader.readLine();
            while (chatServer.isNicknameTaken(nameResponse)) {
                sendMessageToClient("Nickname is already taken. Choose another");
                nameResponse = reader.readLine();
            }
            handleNickname(nameResponse);
            handleHelp();

            String clientInput;
            while ((clientInput = reader.readLine()) != null) {
                handleClientCommand(clientInput);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            chatServer.removeClient(clientNickname);
            sendMessageToAll(clientNickname + " has left the server");
            closeAll();
        }
    }

    /**
     * Simply returns the clients nickname
     * 
     * @return client's nickname
     */
    public synchronized String getClientNickname() {
        return clientNickname;
    }

    /**
     * Sends a message just the client (no other clients see this message)
     * 
     * @param message the message to send to client
     */
    public synchronized void sendMessageToClient(String message) {
        try {
            PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
            clientWriter.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized Socket getClientSocket() {
        return clientSocket;
    }

    public synchronized void closeClient() {
        try {
            clientSocket.close();
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if there is a channel.
     * 
     * @param channel the channel name
     * @return true if channel exists, false otherwise
     */
    public synchronized boolean isInChannel(String channel) {
        return joinedChannels.contains(channel.toLowerCase());
    }

    /**
     * Sends a message to all clients.
     * 
     * @param message message to send to all clients
     */
    public synchronized void sendMessageToAll(String message) {
        chatServer.broadcastMessage(message, clientNickname, getCurrentChannel());
        totalMessagesFromOne++;
        chatServer.incrementTotalMessages(); // Increment total messages count in ChatServer
    }

    /**
     * get message number for /stats command
     * 
     */
    public synchronized int getMessageNum() {
        return totalMessagesFromOne;
    }

    /**
     * Handles the setting of a nickname on the chat server
     * 
     * @param nickname the nickname to set to
     */
    private synchronized void handleNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            sendMessageToClient("Invalid name. Choose another");
        } else if (chatServer.isNicknameTaken(nickname)) {
            sendMessageToClient("Nickname is already taken. Choose another");
        } else {
            sendMessageToClient("Nickname set to: " + nickname);
            clientNickname = nickname;
        }
    }

    /**
     * Handles the various client commands able to be used on the chat server
     * 
     * @param command the command being supplied
     */
    private synchronized void handleClientCommand(String command) {
        if (command.startsWith("/nick")) {
            handleNickname(command.substring(6).trim());
        } else if (command.startsWith("/join")) {
            handleJoin(command.substring(6).trim());
        } else if (command.startsWith("/leave")) {
            handleLeave(command.substring(7).trim());
        } else if (command.equals("/quit")) {
            sendMessageToClient("Goodbye!");
            closeAll();
        } else if (command.equals("/help")) {
            handleHelp();
        } else if (command.equals("/list")) {
            handleList();
        } else {
            sendMessageToAll("[" + getCurrentChannel() + "] " + clientNickname + ": " + command);
        }
    }

    /**
     * Prints a list of the connected clients, and the channels on the server.
     */
    private synchronized void handleList() {
        // list of connected clients
        sendMessageToClient("\nList of connected clients: ");
        for (ClientHandler client : chatServer.getClients()) {
            sendMessageToClient(client.getClientNickname());
        }
        // list of channels
        sendMessageToClient("\nList of channels in the server: ");
        for (String channel : chatServer.getChannelLists()) {
            sendMessageToClient(channel);
        }
    }

    /**
     * Handles the joining of a client to a channel
     * 
     * @param channel the channel to join
     */
    private synchronized void handleJoin(String channel) {
        if (channel.isEmpty()) {
            sendMessageToClient("Invalid channel, choose a different one");
        } else {
            channel = channel.toLowerCase();
            joinedChannels.add(channel);
            chatServer.addChannel(channel); // add channel to channelLists in ChatServer
            chatServer.broadcastMessage("User " + clientNickname + " has joined the channel: " + channel,
                    clientNickname, channel);
        }
    }

    /**
     * Handles a client leaving a channel
     * 
     * @param channel channel to leave
     */
    private synchronized void handleLeave(String channel) {
        if (channel.isEmpty()) {
            String currentChannel = getCurrentChannel();
            sendMessageToAll("User " + clientNickname + " has left channel: " + currentChannel);
            joinedChannels.remove(currentChannel);
            chatServer.removeChannel(currentChannel);
        } else {
            sendMessageToAll("User " + clientNickname + " has left channel: " + channel);
            joinedChannels.remove(channel);
        }
    }

    /*
     * Manual for the user, technically a help message
     */
    private synchronized void handleHelp() {
        sendMessageToClient("List of available commands: \n");
        sendMessageToClient("/nick <nickname> - sets your nickname");
        sendMessageToClient("/list - lists all available channels");
        sendMessageToClient("/join <channel> - joins a channel");
        sendMessageToClient("/leave [<channel>] - leaves a channel");
        sendMessageToClient("/quit - quits the server");
        sendMessageToClient("/help - displays this message");
    }

    /**
     * Gets the current channel and returns it
     * 
     * @return the current channel
     */
    private synchronized String getCurrentChannel() {
        return joinedChannels.isEmpty() ? "general" : joinedChannels.iterator().next();
    }

    /**
     * Cleanup method to close sockets, readers, and writers.
     */
    private void closeAll() {
        try {
            reader.close();
            writer.close();
            clientSocket.close();
        } catch (SocketException e) {
            sendMessageToClient("Goodbye!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}