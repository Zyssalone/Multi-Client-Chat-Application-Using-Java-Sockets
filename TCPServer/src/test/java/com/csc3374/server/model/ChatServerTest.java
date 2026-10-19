package com.csc3374.server.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives a real ChatServer over real TCP sockets, speaking the raw protocol. */
class ChatServerTest {
    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private ChatServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new ChatServer(0, new ServerListener() { // port 0 = any free port
            public void onLog(String message) {
            }

            public void onClientJoined(String username, boolean readOnly) {
                events.add("joined:" + username + (readOnly ? ":ro" : ""));
            }

            public void onClientLeft(String username) {
                events.add("left:" + username);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void broadcastsWithSenderAndTime_allUsersOnlyToRequester_byeLeaves() throws Exception {
        try (Peer alice = join("alice", "WELCOME|alice|FULL");
             Peer bob = join("bob", "WELCOME|bob|FULL")) {
            assertEquals("SYS|alice joined the chat", alice.read());
            assertEquals("SYS|bob joined the chat", alice.read());
            assertEquals("SYS|bob joined the chat", bob.read());

            alice.send("hello everyone");
            assertTrue(alice.read().matches("MSG\\|\\d\\d:\\d\\d\\|alice\\|hello everyone"));
            assertTrue(bob.read().matches("MSG\\|\\d\\d:\\d\\d\\|alice\\|hello everyone"));

            bob.send("allUsers");
            assertEquals("USERS|alice,bob", bob.read());
            alice.send("next");
            assertTrue(alice.read().endsWith("|alice|next"), "alice must not receive bob's user list");
            assertTrue(bob.read().endsWith("|alice|next"));

            bob.send("bye");
            assertEquals("SYS|bob left the chat", alice.read());
            assertNull(bob.read(), "server closes the connection after bye");
            assertEquals("joined:alice", nextEvent());
            assertEquals("joined:bob", nextEvent());
            assertEquals("left:bob", nextEvent());
        }
    }

    @Test
    void blankUsernameIsReadOnlyAndCannotSend() throws Exception {
        try (Peer alice = join("alice", "WELCOME|alice|FULL");
             Peer reader = join("   ", "WELCOME|Reader-1|READ_ONLY")) {
            alice.read(); // own join notice
            assertEquals("SYS|Reader-1 joined the chat (read-only)", alice.read());
            reader.read();

            reader.send("can I talk?");
            assertTrue(reader.read().startsWith("SYS|You are in READ-ONLY MODE"));
            reader.send("allUsers"); // commands still work
            assertEquals("USERS|alice,Reader-1", reader.read());

            alice.send("only alice talks");
            assertTrue(alice.read().endsWith("|alice|only alice talks"), "read-only text was not broadcast");
            assertTrue(reader.read().endsWith("|alice|only alice talks"), "read-only users still receive");
            assertEquals("joined:alice", nextEvent());
            assertEquals("joined:Reader-1:ro", nextEvent());
        }
    }

    @Test
    void duplicateNamesAreMadeUniqueAndSeparatorsStripped() throws Exception {
        try (Peer a = join("Sam", "WELCOME|Sam|FULL");
             Peer b = join("sam", "WELCOME|sam(2)|FULL");
             Peer c = join("x|y,z", "WELCOME|xyz|FULL")) {
            assertEquals(3, server.getOnlineUsernames().split(",").length);
        }
    }

    @Test
    void abruptDisconnectIsDetected() throws Exception {
        Peer ghost = join("ghost", "WELCOME|ghost|FULL");
        ghost.close(); // no bye, just gone
        assertEquals("joined:ghost", nextEvent());
        assertEquals("left:ghost", nextEvent());
    }

    @Test
    void silentConnectionIsDroppedAfterTimeout() throws Exception {
        ClientHandler.usernameTimeoutMs = 300;
        try (Socket lurker = new Socket("localhost", server.getPort())) {
            lurker.setSoTimeout(3000);
            BufferedReader in = new BufferedReader(new InputStreamReader(lurker.getInputStream(), UTF_8));
            // never sends a username -> server should hang up on us
            assertNull(in.readLine());
        } finally {
            ClientHandler.usernameTimeoutMs = 30_000;
        }
    }

    @Test
    void badPortsFallBackToDefault() {
        assertEquals(4000, ServerConfig.checkPort(" 4000 "));
        assertEquals(ServerConfig.DEFAULT_PORT, ServerConfig.checkPort("abc"));
        assertEquals(ServerConfig.DEFAULT_PORT, ServerConfig.checkPort("70000"));
        assertEquals(ServerConfig.DEFAULT_PORT, ServerConfig.checkPort(null));
    }

    private String nextEvent() throws InterruptedException {
        return events.poll(3, TimeUnit.SECONDS);
    }

    /** Connects and waits for the WELCOME line, so joins happen in a known order. */
    private Peer join(String name, String expectedWelcome) throws IOException {
        Peer peer = new Peer(new Socket("localhost", server.getPort()));
        peer.send(name);
        assertEquals(expectedWelcome, peer.read());
        return peer;
    }

    private record Peer(Socket socket, BufferedReader in, PrintWriter out) implements AutoCloseable {
        Peer(Socket socket) throws IOException {
            this(socket,
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8)),
                    new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8), true));
            socket.setSoTimeout(3000);
        }

        void send(String line) {
            out.println(line);
        }

        String read() throws IOException {
            return in.readLine();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
