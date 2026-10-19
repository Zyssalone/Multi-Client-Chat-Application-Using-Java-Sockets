package com.csc3374.server.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;

class ClientHandler implements Runnable {
    // Someone who connects but never sends a name would otherwise sit there forever.
    // (Found this by connecting with a raw socket and just not typing anything.)
    // not final so the test can shorten it instead of waiting 30s
    static int usernameTimeoutMs = 30_000;

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter toClient;
    private volatile String username;
    private volatile boolean readOnly;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    String getUsername() {
        return username;
    }

    void setIdentity(String username, boolean readOnly) {
        this.username = username;
        this.readOnly = readOnly;
    }

    // Several client threads can broadcast to this same client at once,
    // synchronized keeps two messages from getting mixed into one line.
    synchronized void send(String message) {
        if (toClient != null) {
            toClient.println(message);
        }
    }

    void disconnect() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        // UTF-8 on both ends, otherwise accents/emojis turn into ? on Windows
        try (socket;
             BufferedReader fromClient = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8), true)) {
            synchronized (this) {
                toClient = writer;
            }

            socket.setSoTimeout(usernameTimeoutMs);
            String typedName = fromClient.readLine();
            if (typedName == null) {
                return; // closed before even sending a name
            }
            socket.setSoTimeout(0); // after joining, people can stay idle as long as they want
            server.registerClient(this, typedName);

            String line;
            while ((line = fromClient.readLine()) != null) {
                String text = line.strip();
                if (text.isEmpty()) {
                    continue;
                }
                if (Protocol.isLeaveCommand(text)) {
                    break;
                }

                if (text.equalsIgnoreCase(Protocol.ALL_USERS_COMMAND)) {
                    // only the person who asked gets the list
                    send(Protocol.build(Protocol.USERS, server.getOnlineUsernames()));
                } else if (readOnly) {
                    // the client already disables the text box, but don't trust that
                    send(Protocol.build(Protocol.SYS, "You are in READ-ONLY MODE and cannot send messages"));
                } else {
                    if (text.length() > Protocol.MAX_MESSAGE_LENGTH) {
                        text = text.substring(0, Protocol.MAX_MESSAGE_LENGTH);
                    }
                    server.sendChatMessage(username, text);
                }
            }
        } catch (SocketTimeoutException e) {
            server.log("Dropped a connection that never sent a username");
        } catch (IOException e) {
            // "Connection reset" when someone kills the client window, nothing to fix
            if (!socket.isClosed()) {
                server.log("Connection error (" + username + "): " + e.getMessage());
            }
        } finally {
            // runs for bye, LEAVE, crashes and server shutdown alike
            server.removeClient(this);
        }
    }
}
