package com.csc3374.client.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs ChatClient against a fake server socket that plays the server side of the protocol. */
class ChatClientTest {
    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private ServerSocket fakeServer;
    private ChatClient client;
    private BufferedReader serverIn;
    private PrintWriter serverOut;
    private Socket serverSide;

    @BeforeEach
    void setUp() throws IOException {
        fakeServer = new ServerSocket(0);
        client = new ChatClient(new ClientListener() {
            public void onWelcome(String username, boolean readOnly) {
                events.add("welcome:" + username + ":" + readOnly);
            }

            public void onChatMessage(String time, String sender, String text) {
                events.add("msg:" + time + ":" + sender + ":" + text);
            }

            public void onSystemMessage(String text) {
                events.add("sys:" + text);
            }

            public void onUserList(List<String> users) {
                events.add("users:" + users);
            }

            public void onDisconnected(boolean byUser) {
                events.add("disconnected:" + (byUser ? "user" : "lost"));
            }
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        fakeServer.close();
        if (serverSide != null) serverSide.close();
    }

    @Test
    void parsesServerLinesAndLeavesWithBye() throws Exception {
        connectAs("alice");
        assertEquals("alice", serverIn.readLine());

        serverOut.println("WELCOME|alice|FULL");
        serverOut.println("MSG|10:42|bob|pipes | are | fine");
        serverOut.println("SYS|bob joined the chat");
        serverOut.println("USERS|alice,bob");
        assertEquals("welcome:alice:false", next());
        assertEquals("msg:10:42:bob:pipes | are | fine", next());
        assertEquals("sys:bob joined the chat", next());
        assertEquals("users:[alice, bob]", next());

        client.send("  hi\nthere  ");
        assertEquals("hi there", serverIn.readLine(), "newlines are flattened so one message = one line");
        client.askForUserList();
        assertEquals("allUsers", serverIn.readLine());

        client.disconnect(); // LEAVE button
        assertEquals("bye", serverIn.readLine(), "server is notified");
        assertEquals("disconnected:user", next());
        assertFalse(client.isConnected());
        assertNull(next(), "no second disconnect event from the reader thread");
    }

    @Test
    void typingEndOrByeDisconnects() throws Exception {
        connectAs("alice");
        serverIn.readLine();
        client.send("END");
        assertEquals("bye", serverIn.readLine());
        assertEquals("disconnected:user", next());
    }

    @Test
    void readOnlyCannotSendButCanAskForUsers() throws Exception {
        connectAs("");
        assertEquals("", serverIn.readLine(), "blank username requests read-only mode");
        serverOut.println("WELCOME|Reader-1|READ_ONLY");
        assertEquals("welcome:Reader-1:true", next());
        assertTrue(client.isReadOnly());

        client.send("let me talk");
        assertTrue(next().startsWith("sys:READ-ONLY MODE"));
        client.send("allUsers");
        assertEquals("allUsers", serverIn.readLine(), "the blocked message never reached the server");
    }

    @Test
    void serverGoingAwayIsReportedAsLost() throws Exception {
        connectAs("alice");
        serverIn.readLine();
        serverSide.close();
        assertEquals("disconnected:lost", next());
        assertFalse(client.isConnected());
    }

    @Test
    void unreachableServerThrows() throws IOException {
        int closedPort = fakeServer.getLocalPort();
        fakeServer.close();
        assertThrows(IOException.class, () -> client.connect(new ServerAddress("localhost", closedPort), "x"));
        assertFalse(client.isConnected());
    }

    @Test
    void addressFromArgsOrConfig() {
        assertEquals(new ServerAddress("10.0.0.5", 4000), ServerAddress.fromArgs(new String[]{"10.0.0.5", "4000"}));
        assertEquals(new ServerAddress("localhost", 3000), ServerAddress.fromArgs(new String[0])); // bundled client.properties
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.fromArgs(new String[]{"localhost", "abc"}));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.fromArgs(new String[]{"localhost", "0"}));
        // empty host used to give a confusing connect error
        assertEquals(new ServerAddress("localhost", 3000), ServerAddress.fromArgs(new String[]{"  ", "3000"}));
    }

    private void connectAs(String username) throws IOException {
        client.connect(new ServerAddress("localhost", fakeServer.getLocalPort()), username);
        serverSide = fakeServer.accept();
        serverSide.setSoTimeout(3000);
        serverIn = new BufferedReader(new InputStreamReader(serverSide.getInputStream(), UTF_8));
        serverOut = new PrintWriter(new OutputStreamWriter(serverSide.getOutputStream(), UTF_8), true);
    }

    private String next() throws InterruptedException {
        return events.poll(2, TimeUnit.SECONDS);
    }
}
