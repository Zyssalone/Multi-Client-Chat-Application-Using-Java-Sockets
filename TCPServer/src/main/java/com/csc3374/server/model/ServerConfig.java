package com.csc3374.server.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ServerConfig {
    public static final String FILE_NAME = "server.properties";
    public static final int DEFAULT_PORT = 3000;

    private ServerConfig() {
    }

    public static int readPort() {
        Properties settings = new Properties();
        // A server.properties sitting next to the jar wins over the one packed inside it.
        // That way the port can be changed on the lab machine without rebuilding anything.
        Path fileNextToJar = Path.of(FILE_NAME);
        try (InputStream in = Files.isRegularFile(fileNextToJar)
                ? Files.newInputStream(fileNextToJar)
                : ServerConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                settings.load(in);
            }
        } catch (IOException e) {
            System.err.println("Couldn't read " + FILE_NAME + ", using port " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
        return checkPort(settings.getProperty("server.port"));
    }

    // A typo in the config file shouldn't stop the server from starting, so fall back to 3000.
    static int checkPort(String value) {
        if (value == null) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                return DEFAULT_PORT;
            }
            return port;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
