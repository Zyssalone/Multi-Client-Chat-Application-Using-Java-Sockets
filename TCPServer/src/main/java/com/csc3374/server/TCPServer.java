package com.csc3374.server;

import com.csc3374.server.view.ServerApp;
import javafx.application.Application;

// Run with: java -jar TCPServer.jar   (port comes from server.properties)
//
// This class intentionally does NOT extend Application. When the main class of a fat jar
// extends Application, Java refuses to start with "JavaFX runtime components are missing".
// Launching ServerApp from a plain class gets around that.
public class TCPServer {
    public static void main(String[] args) {
        Application.launch(ServerApp.class, args);
    }
}
