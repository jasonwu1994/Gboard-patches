package dev.jason.gboardpatches.extension;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class GboardHttpServer {
    private static final String TAG = "GboardHttpServer";
    private static final int PORT = 8124;
    private static Thread serverThread;
    private static volatile boolean running = false;

    public static synchronized void start(Context context) {
        if (running) return;
        running = true;
        serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))) {
                Log.i(TAG, "Server listening on 127.0.0.1:" + PORT);
                while (running) {
                    try (Socket client = serverSocket.accept()) {
                        handleClient(client);
                    } catch (IOException e) {
                        if (running) Log.e(TAG, "Error accepting client", e);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not start server", e);
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static void handleClient(Socket client) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        String line = reader.readLine();
        if (line == null) return;

        String[] parts = line.split(" ");
        if (parts.length < 2) return;
        String path = parts[1];

        String response;
        if ("/context".equals(path)) {
            response = getScreenContextJson();
            Log.d(TAG, "Serving /context, length=" + response.length());
        } else {
            response = "{\"error\":\"not found\"}";
        }

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        OutputStream out = client.getOutputStream();
        out.write("HTTP/1.1 200 OK\r\n".getBytes());
        out.write("Content-Type: application/json\r\n".getBytes());
        out.write(("Content-Length: " + responseBytes.length + "\r\n").getBytes());
        out.write("\r\n".getBytes());
        out.write(responseBytes);
        out.flush();
    }

    private static String getScreenContextJson() {
        Bundle b = GboardAccessibilityService.getScreenContext();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"app\":\"").append(escape(b.getString("app", ""))).append("\",");
        sb.append("\"title\":\"").append(escape(b.getString("title", ""))).append("\",");
        sb.append("\"activity\":\"").append(escape(b.getString("activity", ""))).append("\",");
        sb.append("\"focusedId\":\"").append(escape(b.getString("focusedId", ""))).append("\",");
        sb.append("\"contentDesc\":\"").append(escape(b.getString("contentDesc", ""))).append("\",");
        sb.append("\"isEditable\":").append(b.getBoolean("isEditable", false)).append(",");
        sb.append("\"isGroup\":").append(b.getBoolean("isGroup", false)).append(",");
        
        sb.append("\"allText\":[");
        List<String> allText = b.getStringArrayList("allText");
        if (allText != null) {
            for (int i = 0; i < allText.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(allText.get(i))).append("\"");
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
