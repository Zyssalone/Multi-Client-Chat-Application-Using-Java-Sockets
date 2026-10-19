package com.csc3374.client;

import com.csc3374.client.model.ServerAddress;
import com.csc3374.client.view.ClientApp;
import javafx.application.Application;

// Not extending Application on purpose, same fat-jar JavaFX issue as TCPServer.
public class TCPClient {
    public static void main(String[] args) {
        ServerAddress address;
        try {
            address = ServerAddress.fromArgs(args);
        } catch (IllegalArgumentException e) {
            // check this before opening any window, a usage message is clearer than a popup
            System.err.println(e.getMessage());
            System.err.println("Usage: java -jar TCPClient.jar <ServerIPAddress> <PortNumber>");
            System.exit(1);
            return;
        }
        Application.launch(ClientApp.class, address.host(), String.valueOf(address.port()));
    }
}
