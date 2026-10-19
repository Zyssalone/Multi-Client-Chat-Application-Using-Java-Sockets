package com.csc3374.server.model;

// The model reports what happens through this interface so it never has to import JavaFX.
// Careful: these get called from the client threads, not the UI thread.
public interface ServerListener {
    void onLog(String message);

    void onClientJoined(String username, boolean readOnly);

    void onClientLeft(String username);
}
