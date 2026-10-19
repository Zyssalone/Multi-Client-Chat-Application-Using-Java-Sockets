package com.csc3374.client.model;

import java.util.List;

// How ChatClient talks back to the UI without knowing it's JavaFX.
// These run on the socket reader thread, so the UI has to use Platform.runLater.
public interface ClientListener {
    void onWelcome(String username, boolean readOnly);

    void onChatMessage(String time, String sender, String text);

    void onSystemMessage(String text);

    void onUserList(List<String> users);

    // leftOnPurpose = user clicked LEAVE / typed bye, false = server died or network dropped
    void onDisconnected(boolean leftOnPurpose);
}
