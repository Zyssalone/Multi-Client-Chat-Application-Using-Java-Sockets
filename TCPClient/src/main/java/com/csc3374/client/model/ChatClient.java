package com.csc3374.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ChatClient {
    // without a timeout, a wrong IP made the JOIN button hang for ~20 seconds
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final ClientListener listener;
    // AtomicBoolean because both the LEAVE button (UI thread) and the reader thread
    // can try to disconnect at the same moment, and only one of them should report it.
    private final AtomicBoolean online = new AtomicBoolean(false);
    private Socket socket;
    private PrintWriter toServer;
    private volatile boolean readOnly;

    public ChatClient(ClientListener listener) {
        this.listener = listener;
    }

    // Blocks until connected, so the UI calls this from a background thread.
    // An empty username means the user wants read-only mode.
    public void connect(ServerAddress address, String username) throws IOException {
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(address.host(), address.port()), CONNECT_TIMEOUT_MS);
            BufferedReader fromServer = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), UTF_8));
            toServer = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream(), UTF_8), true);
            socket = newSocket;
            readOnly = false;
            online.set(true);

            String name = "";
            if (username != null) {
                name = username.replaceAll("\\R", " ").strip();
            }
            toServer.println(name);

            Thread.ofVirtual().name("server-listener").start(() -> listenToServer(fromServer));
        } catch (IOException e) {
            newSocket.close();
            throw e;
        }
    }

    public boolean isConnected() {
        return online.get();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void send(String typedText) {
        if (typedText == null || !online.get()) {
            return;
        }
        // The protocol is one message per line, so a pasted multi-line text
        // would turn into several messages. Flatten it into one instead.
        String message = typedText.replaceAll("\\R", " ").strip();
        if (message.isEmpty()) {
            return;
        }

        if (Protocol.isLeaveCommand(message)) {
            disconnect();
            return;
        }
        if (readOnly && !message.equalsIgnoreCase(Protocol.ALL_USERS_COMMAND)) {
            listener.onSystemMessage("READ-ONLY MODE: you cannot send messages");
            return;
        }
        toServer.println(message);
    }

    public void askForUserList() {
        send(Protocol.ALL_USERS_COMMAND);
    }

    public void disconnect() {
        if (!online.compareAndSet(true, false)) {
            return; // already disconnected
        }
        // tell the server first so it removes us right away instead of waiting for a socket error
        toServer.println(Protocol.BYE);
        closeSocket();
        listener.onDisconnected(true);
    }

    private void listenToServer(BufferedReader fromServer) {
        try (fromServer) {
            String line;
            while ((line = fromServer.readLine()) != null) {
                handleServerLine(line);
            }
        } catch (IOException e) {
            // socket got closed, handled right below
        }
        // If online is still true here, we didn't call disconnect() ourselves -> the server went away
        if (online.compareAndSet(true, false)) {
            closeSocket();
            listener.onDisconnected(false);
        }
    }

    void handleServerLine(String line) {
        int firstBar = line.indexOf('|');
        if (firstBar < 0) {
            listener.onSystemMessage(line); // not our format, just show it
            return;
        }
        String type = line.substring(0, firstBar);
        String rest = line.substring(firstBar + 1);

        switch (type) {
            case Protocol.WELCOME -> {
                String[] parts = rest.split("\\|", 2);
                String assignedName = parts[0]; // can differ from what we typed, e.g. alice(2)
                readOnly = parts.length > 1 && parts[1].equals(Protocol.READ_ONLY);
                listener.onWelcome(assignedName, readOnly);
            }
            case Protocol.MSG -> {
                // limit 3 so a '|' inside the message text doesn't get split
                String[] parts = rest.split("\\|", 3);
                if (parts.length == 3) {
                    listener.onChatMessage(parts[0], parts[1], parts[2]);
                } else {
                    listener.onSystemMessage(rest);
                }
            }
            case Protocol.USERS -> {
                List<String> names = new ArrayList<>();
                if (!rest.isEmpty()) {
                    names = List.of(rest.split(","));
                }
                listener.onUserList(names);
            }
            case Protocol.SYS -> listener.onSystemMessage(rest);
            default -> listener.onSystemMessage(line);
        }
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
