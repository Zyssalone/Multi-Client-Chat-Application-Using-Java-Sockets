package com.csc3374.server.model;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final int port;
    private final ServerListener listener;
    // CopyOnWriteArrayList because every client thread loops over this to broadcast while
    // others join/leave. With a normal ArrayList I got a ConcurrentModificationException.
    private final List<ClientHandler> onlineClients = new CopyOnWriteArrayList<>();
    private int guestNumber = 0;
    private volatile ServerSocket serverSocket;

    public ChatServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
    }

    // Binding happens here (not in the background thread) so a "port already in use"
    // error reaches the UI and can be shown in a popup.
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        listener.onLog("Server started on port " + getPort());
        Thread.ofVirtual().name("accept-loop").start(this::waitForClients);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        try {
            serverSocket.close(); // makes accept() throw, which ends waitForClients
        } catch (IOException ignored) {
        }
        for (ClientHandler client : onlineClients) {
            client.disconnect();
        }
        listener.onLog("Server stopped");
    }

    private void waitForClients() {
        while (!serverSocket.isClosed()) {
            try {
                listener.onLog("Waiting for client...");
                Socket socket = serverSocket.accept();
                listener.onLog("Connection from " + socket.getRemoteSocketAddress());
                // one virtual thread per client, they're cheap so no thread pool needed
                ClientHandler handler = new ClientHandler(socket, this);
                Thread.ofVirtual().name("client-" + socket.getPort()).start(handler);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    listener.onLog("Accept failed: " + e.getMessage());
                }
                // if the socket is closed this is just stop() doing its job
            }
        }
    }

    // synchronized: without it two people joining as "alice" at the same moment could
    // both get the name, and the "X joined" notices could arrive out of order.
    synchronized String registerClient(ClientHandler client, String typedName) {
        String name = cleanUsername(typedName);
        boolean readOnly = name.isEmpty();
        if (readOnly) {
            guestNumber++;
            name = "Reader-" + guestNumber;
        }

        // same name already online -> alice(2), alice(3)...
        String baseName = name;
        int copy = 2;
        while (isNameTaken(name)) {
            name = baseName + "(" + copy + ")";
            copy++;
        }

        client.setIdentity(name, readOnly);
        onlineClients.add(client);

        String mode = readOnly ? Protocol.READ_ONLY : Protocol.FULL_ACCESS;
        client.send(Protocol.build(Protocol.WELCOME, name, mode));
        listener.onClientJoined(name, readOnly);
        if (readOnly) {
            listener.onLog("Welcome " + name + " (READ-ONLY MODE)");
            sendToAll(Protocol.build(Protocol.SYS, name + " joined the chat (read-only)"));
        } else {
            listener.onLog("Welcome " + name);
            sendToAll(Protocol.build(Protocol.SYS, name + " joined the chat"));
        }
        return name;
    }

    void removeClient(ClientHandler client) {
        boolean wasOnline = onlineClients.remove(client);
        if (!wasOnline) {
            return; // never finished joining (or already removed)
        }
        String name = client.getUsername();
        listener.onClientLeft(name);
        listener.onLog(name + " left");
        sendToAll(Protocol.build(Protocol.SYS, name + " left the chat"));
    }

    void sendChatMessage(String sender, String text) {
        String time = LocalTime.now().format(CLOCK);
        listener.onLog(sender + ": " + text);
        sendToAll(Protocol.build(Protocol.MSG, time, sender, text));
    }

    // includes the sender, so they see their own message with the server's timestamp
    void sendToAll(String message) {
        for (ClientHandler client : onlineClients) {
            client.send(message);
        }
    }

    String getOnlineUsernames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : onlineClients) {
            names.add(client.getUsername());
        }
        return String.join(",", names);
    }

    void log(String message) {
        listener.onLog(message);
    }

    private boolean isNameTaken(String name) {
        for (ClientHandler client : onlineClients) {
            if (client.getUsername().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // '|' and ',' would break the message format (USERS is comma separated),
    // and control characters could sneak a fake line into the stream.
    static String cleanUsername(String typedName) {
        if (typedName == null) {
            return "";
        }
        String cleaned = typedName.replaceAll("[|,\\p{Cntrl}]", "").strip();
        if (cleaned.length() > Protocol.MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, Protocol.MAX_NAME_LENGTH);
        }
        return cleaned;
    }
}
