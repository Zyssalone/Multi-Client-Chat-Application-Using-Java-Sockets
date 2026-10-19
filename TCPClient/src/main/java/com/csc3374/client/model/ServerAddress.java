package com.csc3374.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ServerAddress(String host, int port) {
    public static final String FILE_NAME = "client.properties";

    // Command line args come first (that's what the spec asks for: java TCPClient localhost 3000).
    // client.properties is only the fallback when they're missing.
    public static ServerAddress fromArgs(String[] args) {
        Properties settings = readSettingsFile();

        String host;
        if (args.length > 0) {
            host = args[0].strip();
        } else {
            host = settings.getProperty("server.host", "localhost").strip();
        }
        // An empty host ("" from a bad script) silently became a weird connect error, default it instead
        if (host.isEmpty()) {
            host = "localhost";
        }

        String portText;
        if (args.length > 1) {
            portText = args[1];
        } else {
            portText = settings.getProperty("server.port", "3000");
        }

        int port;
        try {
            port = Integer.parseInt(portText.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + portText);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port number: " + portText);
        }
        return new ServerAddress(host, port);
    }

    // same idea as the server: a file next to the jar beats the one bundled inside
    private static Properties readSettingsFile() {
        Properties settings = new Properties();
        Path fileNextToJar = Path.of(FILE_NAME);
        try (InputStream in = Files.isRegularFile(fileNextToJar)
                ? Files.newInputStream(fileNextToJar)
                : ServerAddress.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                settings.load(in);
            }
        } catch (IOException e) {
            // no file is fine, the defaults above kick in
        }
        return settings;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
