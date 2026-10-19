package com.csc3374.server.view;

import com.csc3374.server.model.ChatServer;
import com.csc3374.server.model.ServerConfig;
import com.csc3374.server.model.ServerListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ServerApp extends Application implements ServerListener {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TextArea logArea = new TextArea();
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    // the color is picked once per user, otherwise it would change every time the list redraws
    private final Map<String, String> userColors = new HashMap<>();
    private final Set<String> readOnlyUsers = new HashSet<>();
    private final Random random = new Random();
    private ChatServer server;

    @Override
    public void start(Stage stage) {
        int port = ServerConfig.readPort();

        // --- header ---
        Label title = new Label("TCP Chat Server");
        title.getStyleClass().add("title");
        Circle greenDot = new Circle(6);
        greenDot.getStyleClass().add("status-dot");
        Label portLabel = new Label("Running on port " + port);
        portLabel.getStyleClass().add("subtitle");

        GridPane header = new GridPane();
        header.getStyleClass().add("header");
        header.add(title, 0, 0, 2, 1);
        header.add(greenDot, 0, 1);
        header.add(portLabel, 1, 1);

        // --- log + user list ---
        Label logTitle = new Label("Activity Log");
        logTitle.getStyleClass().add("section-header");
        Label usersTitle = new Label();
        usersTitle.getStyleClass().add("section-header");
        usersTitle.textProperty().bind(Bindings.size(onlineUsers).asString("Connected Users (%d)"));

        logArea.setId("log");
        logArea.setEditable(false);
        logArea.setWrapText(true);

        ListView<String> userList = new ListView<>(onlineUsers);
        userList.setId("userList");
        userList.setPlaceholder(new Label("No users connected"));
        // custom cells so each row can have its own background color
        userList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    // cells get reused, so the old color has to be cleared or it shows on empty rows
                    setText(null);
                    setStyle("");
                    return;
                }
                String shownName = name;
                if (readOnlyUsers.contains(name)) {
                    shownName = name + "  (read-only)";
                }
                setText(shownName);
                setStyle("-fx-background-color: " + userColors.get(name) + ";");
            }
        });

        GridPane root = new GridPane();
        root.getStyleClass().add("root-pane");
        root.add(header, 0, 0, 2, 1);
        root.add(logTitle, 0, 1);
        root.add(usersTitle, 1, 1);
        root.add(logArea, 0, 2);
        root.add(userList, 1, 2);

        ColumnConstraints logColumn = new ColumnConstraints();
        logColumn.setPercentWidth(68);
        ColumnConstraints usersColumn = new ColumnConstraints();
        usersColumn.setPercentWidth(32);
        root.getColumnConstraints().addAll(logColumn, usersColumn);

        RowConstraints normalRow = new RowConstraints();
        RowConstraints stretchRow = new RowConstraints();
        stretchRow.setVgrow(Priority.ALWAYS); // only the log/list row grows with the window
        root.getRowConstraints().addAll(normalRow, normalRow, stretchRow);

        Scene scene = new Scene(root, 760, 460);
        scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());
        stage.setTitle("TCP Chat Server - port " + port);
        stage.setMinWidth(520);
        stage.setMinHeight(320);
        stage.setScene(scene);
        stage.show();

        server = new ChatServer(port, this);
        try {
            server.start();
        } catch (IOException e) {
            // happens when a second server is started on the same port
            server = null;
            Alert error = new Alert(Alert.AlertType.ERROR,
                    "Could not listen on port " + port + ":\n" + e.getMessage()
                            + "\n\nIs another server already running? Change the port in " + ServerConfig.FILE_NAME + ".");
            error.setHeaderText("Server failed to start");
            error.showAndWait();
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        // closing the window should kick everyone instead of leaving them on a dead server
        if (server != null) {
            server.stop();
        }
    }

    // All three callbacks come from client threads. JavaFX throws
    // "Not on FX application thread" if we touch the UI directly, hence runLater.

    @Override
    public void onLog(String message) {
        String time = LocalTime.now().format(LOG_TIME);
        Platform.runLater(() -> logArea.appendText("[" + time + "] " + message + "\n"));
    }

    @Override
    public void onClientJoined(String username, boolean readOnly) {
        Platform.runLater(() -> {
            userColors.put(username, randomPastelColor());
            if (readOnly) {
                readOnlyUsers.add(username);
            }
            onlineUsers.add(username);
        });
    }

    @Override
    public void onClientLeft(String username) {
        Platform.runLater(() -> {
            onlineUsers.remove(username);
            userColors.remove(username);
            readOnlyUsers.remove(username);
        });
    }

    // Fully random RGB gave some really dark colors where the names were unreadable,
    // so only the hue is random and saturation/brightness stay light.
    private String randomPastelColor() {
        int hue = random.nextInt(360);
        return "hsb(" + hue + ", 35%, 97%)";
    }
}
