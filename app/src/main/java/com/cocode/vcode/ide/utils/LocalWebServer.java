package com.cocode.vcode.ide.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * High-performance lightweight embedded application server.
 * Listens on dynamic loopback interfaces to stream working folder items directly into
 * internal web layout render views for real-time mobile code pre-visualization.
 */
public class LocalWebServer {

    private final File documentRoot;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private int port = 8080;
    private Thread serverThread;

    public LocalWebServer(File documentRoot) {
        this.documentRoot = documentRoot;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Spawns socket listener loops on alternative threads to bypass primary interface blocks.
     */
    public void start() {
        if (isRunning) return;

        try {
            // Setting port variable integer to 0 instructs the device to auto-allocate any free dynamic port
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            isRunning = true;

            serverThread = new Thread(() -> {
                try {
                    while (isRunning) {
                        Socket socket = serverSocket.accept();
                        handleRequest(socket);
                    }
                } catch (Exception e) {
                    isRunning = false;
                }
            });
            serverThread.start();
        } catch (Exception e) {
            isRunning = false;
        }
    }

    /**
     * Dismantles execution channels and socket structures during session termination events.
     */
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (serverThread != null) {
                serverThread.interrupt();
                serverThread = null;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Computes loopback navigation string paths for targeted project assets elements.
     */
    public String getUrl(String fileName) {
        return "http://localhost:" + port + "/" + fileName;
    }

    /**
     * Parses standard primitive HTTP request fields to pull target files, injecting
     * explicit CORS access allowance flags and calculated MIME metadata types into downstream returns.
     */
    private void handleRequest(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            String request = in.readLine();
            if (request == null) return;

            String[] parts = request.split(" ");
            if (parts.length < 2) return;

            String path = parts[1];
            if (path.equals("/")) path = "/index.html";

            if (path.startsWith("/")) path = path.substring(1);

            File file = new File(documentRoot, path);

            if (file.exists() && !file.isDirectory()) {
                String mimeType = getMimeType(path);

                byte[] content = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(content);
                }

                out.write(("HTTP/1.1 200 OK\r\n").getBytes());
                out.write(("Content-Type: " + mimeType + "\r\n").getBytes());
                out.write(("Content-Length: " + content.length + "\r\n").getBytes());
                out.write(("Access-Control-Allow-Origin: *\r\n").getBytes());
                out.write(("\r\n").getBytes());
                out.write(content);
            } else {
                out.write(("HTTP/1.1 404 Not Found\r\n\r\n").getBytes());
            }
            out.flush();
        } catch (Exception ignored) {
        }
    }

    /**
     * Resolves standard network content type mapping labels matching filename suffixes.
     */
    private String getMimeType(String path) {
        path = path.toLowerCase();
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "text/plain";
    }
}