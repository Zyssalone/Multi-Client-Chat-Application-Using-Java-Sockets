package com.csc3374.client.view;

import com.csc3374.client.model.ChatClient;
import com.csc3374.client.model.ClientListener;
import com.csc3374.client.model.ServerAddress;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

// Only UI code in here. Anything about sockets or the message format lives in ChatClient.
public class ClientApp extends Application implements ClientListener {

    // one row in the chat list: my message, someone else's, or a grey server notice
    private enum Sender { ME, OTHER, SERVER }

    private record ChatRow(Sender from, String time, String name, String text) {
    }

    private Stage window;
    private Scene scene;
    private ServerAddress serverAddress;
    private ChatClient chatClient;
    private String myName = "";

    // login screen
    private TextField nameField;
    private Button joinButton;
    private Label loginMessage;

    // chat screen
    private final ObservableList<ChatRow> chatRows = FXCollections.observableArrayList();
    private Circle statusCircle;
    private Label statusText;
    private TextArea messageBox;
    private Button sendButton;
    private Button usersButton;
    private Button leaveButton;

    @Override
    public void start(Stage stage) {
        window = stage;
        // getParameters() is null when the app is started from a test instead of launch()
        Parameters params = getParameters();
        String[] args = new String[0];
        if (params != null) {
            args = params.getRaw().toArray(new String[0]);
        }
        serverAddress = ServerAddress.fromArgs(args);

        // one Scene for the whole app, the login/chat screens just swap its root
        scene = new Scene(createLoginScreen(""), 620, 560);
        scene.getStylesheets().add(getClass().getResource("/client.css").toExternalForm());
        stage.setTitle("Group Chat");
        stage.setMinWidth(460);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();
        nameField.requestFocus();
    }

    @Override
    public void stop() {
        // closing the window with the X should still say bye to the server
        if (chatClient != null) {
            chatClient.disconnect();
        }
    }

    // ======================= login screen =======================

    private Parent createLoginScreen(String message) {
        Label title = new Label("Group Chat");
        title.getStyleClass().add("title");
        Label serverInfo = new Label("Server  " + serverAddress);
        serverInfo.getStyleClass().add("muted");

        Label nameLabel = new Label("Username");
        nameLabel.getStyleClass().add("field-label");
        nameField = new TextField(myName); // keep the last name so rejoining is quick
        nameField.setId("username");
        nameField.setPromptText("Enter a username");

        Label hint = new Label("Leave it empty to join in READ-ONLY MODE: you will see the chat but cannot send messages.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        joinButton = new Button("JOIN CHAT");
        joinButton.setId("joinButton");
        joinButton.getStyleClass().add("primary");
        joinButton.setDefaultButton(true); // Enter in the name field = click
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setOnAction(e -> joinChat());

        loginMessage = new Label(message);
        loginMessage.setId("loginNotice");
        loginMessage.getStyleClass().add("notice");
        loginMessage.setWrapText(true);

        GridPane card = new GridPane();
        card.getStyleClass().add("card");
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE); // otherwise the card fills the whole window
        card.setPrefWidth(380);
        card.add(title, 0, 0);
        card.add(serverInfo, 0, 1);
        card.add(nameLabel, 0, 2);
        card.add(nameField, 0, 3);
        card.add(hint, 0, 4);
        card.add(joinButton, 0, 5);
        card.add(loginMessage, 0, 6);
        ColumnConstraints fullWidth = new ColumnConstraints();
        fullWidth.setHgrow(Priority.ALWAYS);
        fullWidth.setFillWidth(true);
        card.getColumnConstraints().add(fullWidth);

        GridPane background = new GridPane();
        background.getStyleClass().add("login-root");
        background.setAlignment(Pos.CENTER);
        background.add(card, 0, 0);
        return background;
    }

    private void joinChat() {
        myName = nameField.getText().strip();
        joinButton.setDisable(true); // stops double clicks from opening two connections
        joinButton.setText("CONNECTING...");
        loginMessage.setText("");

        // New ChatClient every time. Reusing the old one caused its old reader thread
        // to mark the new connection as disconnected.
        ChatClient client = new ChatClient(this);
        chatClient = client;

        // connecting can take a few seconds, doing it on the FX thread froze the window
        Thread.ofVirtual().start(() -> {
            try {
                client.connect(serverAddress, myName);
                // the chat screen opens in onWelcome, once the server confirms our name
            } catch (IOException e) {
                Platform.runLater(() -> {
                    joinButton.setDisable(false);
                    joinButton.setText("JOIN CHAT");
                    loginMessage.setText("Cannot reach the server at " + serverAddress + ". Is it running?");
                });
            }
        });
    }

    // ======================= chat screen =======================

    private Parent createChatScreen(boolean readOnly) {
        statusCircle = new Circle(6);
        statusCircle.setId("statusDot");
        statusCircle.getStyleClass().add("status-online");
        statusText = new Label("Online");
        statusText.setId("statusLabel");
        statusText.getStyleClass().add("status-label");

        Label nameLabel = new Label(myName);
        nameLabel.getStyleClass().add("user-label");
        Label readOnlyBadge = new Label("READ-ONLY MODE");
        readOnlyBadge.setId("readOnlyBadge");
        readOnlyBadge.getStyleClass().add("badge");
        readOnlyBadge.setVisible(readOnly);
        readOnlyBadge.setManaged(readOnly); // so it doesn't leave an empty gap when hidden

        usersButton = new Button("USERS");
        usersButton.setId("usersButton");
        usersButton.setOnAction(e -> chatClient.askForUserList());
        leaveButton = new Button("LEAVE");
        leaveButton.setId("leaveButton");
        leaveButton.getStyleClass().add("danger");
        leaveButton.setOnAction(e -> onLeaveClicked());

        Region spacer = new Region(); // pushes the buttons to the right
        GridPane header = new GridPane();
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.addRow(0, statusCircle, statusText, nameLabel, readOnlyBadge, spacer, usersButton, leaveButton);
        for (int col = 0; col < 7; col++) {
            ColumnConstraints column = new ColumnConstraints();
            if (col == 4) {
                column.setHgrow(Priority.ALWAYS); // the spacer column
            }
            header.getColumnConstraints().add(column);
        }

        ListView<ChatRow> chatList = new ListView<>(chatRows);
        chatList.setId("messages");
        chatList.setFocusTraversable(false);
        chatList.setPlaceholder(new Label("No messages yet. Say hi!"));
        chatList.setCellFactory(list -> {
            ChatBubbleCell cell = new ChatBubbleCell();
            // without this, long messages made the list scroll sideways instead of wrapping
            cell.setPrefWidth(0);
            return cell;
        });
        // always jump to the newest message
        chatRows.addListener((ListChangeListener<ChatRow>) change -> chatList.scrollTo(chatRows.size() - 1));

        messageBox = new TextArea();
        messageBox.setId("messageInput");
        messageBox.setPrefRowCount(2);
        messageBox.setWrapText(true);
        messageBox.setPromptText("Type a message and press Enter.   allUsers = who is online,  bye = leave");
        // A TextArea normally inserts a new line on Enter, we want Enter to send instead.
        // Shift+Enter still makes a new line.
        messageBox.addEventFilter(KeyEvent.KEY_PRESSED, key -> {
            if (key.getCode() == KeyCode.ENTER && !key.isShiftDown()) {
                key.consume();
                sendTypedMessage();
            }
        });
        sendButton = new Button("SEND");
        sendButton.setId("sendButton");
        sendButton.getStyleClass().add("primary");
        sendButton.setMaxHeight(Double.MAX_VALUE);
        sendButton.setOnAction(e -> sendTypedMessage());

        if (readOnly) {
            messageBox.setDisable(true);
            messageBox.setPromptText("READ-ONLY MODE: rejoin with a username to send messages");
            sendButton.setDisable(true);
        }

        GridPane layout = new GridPane();
        layout.getStyleClass().add("chat-root");
        layout.add(header, 0, 0, 2, 1);
        layout.add(chatList, 0, 1, 2, 1);
        layout.add(messageBox, 0, 2);
        layout.add(sendButton, 1, 2);
        GridPane.setHalignment(sendButton, HPos.RIGHT);

        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints buttonColumn = new ColumnConstraints(90);
        layout.getColumnConstraints().addAll(inputColumn, buttonColumn);
        RowConstraints fixedRow = new RowConstraints();
        RowConstraints growingRow = new RowConstraints();
        growingRow.setVgrow(Priority.ALWAYS);
        layout.getRowConstraints().addAll(fixedRow, growingRow, fixedRow);
        return layout;
    }

    private void sendTypedMessage() {
        String text = messageBox.getText();
        if (text.isBlank()) {
            return;
        }
        messageBox.clear();
        chatClient.send(text); // ChatClient also handles "bye"/"end" and the read-only check
    }

    private void onLeaveClicked() {
        if (chatClient.isConnected()) {
            chatClient.disconnect(); // goes back to the login screen through onDisconnected
        } else {
            // server already died, the button says BACK at this point
            showLoginScreen("");
        }
    }

    private void showLoginScreen(String message) {
        statusCircle = null; // marks that we're no longer on the chat screen
        scene.setRoot(createLoginScreen(message));
        window.setTitle("Group Chat");
        nameField.requestFocus();
    }

    private void showOffline() {
        statusCircle.getStyleClass().setAll("status-offline");
        statusText.setText("Offline");
        messageBox.setDisable(true);
        sendButton.setDisable(true);
        usersButton.setDisable(true);
        leaveButton.setText("BACK");
    }

    private void addServerNotice(String text) {
        chatRows.add(new ChatRow(Sender.SERVER, "", "", text));
    }

    // ======================= ClientListener (called from the socket thread) =======================

    @Override
    public void onWelcome(String assignedName, boolean readOnly) {
        Platform.runLater(() -> {
            myName = assignedName;
            chatRows.clear(); // leftovers from a previous session
            scene.setRoot(createChatScreen(readOnly));
            if (readOnly) {
                window.setTitle("Group Chat - " + assignedName + " (read-only)");
                addServerNotice("Welcome! You are in READ-ONLY MODE as " + assignedName + ".");
            } else {
                window.setTitle("Group Chat - " + assignedName);
                addServerNotice("Welcome " + assignedName + "! You are connected to " + serverAddress + ".");
                messageBox.requestFocus();
            }
        });
    }

    @Override
    public void onChatMessage(String time, String sender, String text) {
        Platform.runLater(() -> {
            // compare with the name the server gave us, not what was typed (could be alice(2))
            Sender from = sender.equals(myName) ? Sender.ME : Sender.OTHER;
            chatRows.add(new ChatRow(from, time, sender, text));
        });
    }

    @Override
    public void onSystemMessage(String text) {
        Platform.runLater(() -> addServerNotice(text));
    }

    @Override
    public void onUserList(List<String> users) {
        Platform.runLater(() -> addServerNotice("Active users (" + users.size() + "): " + String.join(", ", users)));
    }

    @Override
    public void onDisconnected(boolean leftOnPurpose) {
        Platform.runLater(() -> {
            if (leftOnPurpose) {
                showLoginScreen("You left the chat.");
            } else if (statusCircle != null) {
                // stay on the chat screen so the user can still read the conversation
                showOffline();
                addServerNotice("Connection to the server was lost.");
            } else {
                showLoginScreen("Connection to the server was lost.");
            }
        });
    }

    // Draws messages like a messenger app: mine on the right in blue, others on the left,
    // server notices centered in grey.
    private static class ChatBubbleCell extends ListCell<ChatRow> {
        @Override
        protected void updateItem(ChatRow row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }

            if (row.from() == Sender.SERVER) {
                Label notice = new Label(row.text());
                notice.getStyleClass().add("system-text");
                notice.setWrapText(true);
                HBox line = new HBox(notice);
                line.setAlignment(Pos.CENTER);
                setGraphic(line);
                return;
            }

            boolean isMine = row.from() == Sender.ME;
            String who = isMine ? "You" : row.name();
            Label nameAndTime = new Label(who + "  ·  " + row.time());
            nameAndTime.getStyleClass().add("meta");
            Label messageText = new Label(row.text());
            messageText.getStyleClass().add("body");
            messageText.setWrapText(true);

            VBox bubble = new VBox(2, nameAndTime, messageText);
            bubble.getStyleClass().add("bubble");
            bubble.getStyleClass().add(isMine ? "mine" : "other");
            // bubbles max out at 70% of the list so they look like chat bubbles, not full-width rows
            bubble.maxWidthProperty().bind(getListView().widthProperty().multiply(0.7));

            HBox line = new HBox(bubble);
            line.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            setGraphic(line);
        }
    }
}
