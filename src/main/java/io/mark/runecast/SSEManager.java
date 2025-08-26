package io.mark.runecast;

import io.mark.runecast.pages.PageRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.Gson;

public class SSEManager {
    private final RuneCastConfig config;
    private final PageRegistry pageRegistry;
    private final ConcurrentHashMap<Socket, OutputStream> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService updateExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Gson gson = new Gson();

    public SSEManager(RuneCastConfig config,PageRegistry pageRegistry) {
        this.config = config;
        this.pageRegistry = pageRegistry;
        this.updateExecutor = Executors.newScheduledThreadPool(1);
    }

    
    public void start() {
        if (running.get()) {
            return;
        }
        
        running.set(true);
        
        updateExecutor.scheduleAtFixedRate(() -> {
            if (running.get()) {
                broadcastUpdate();
            }
        }, 0, 1, TimeUnit.MILLISECONDS);
    }
    
    public void stop() {
        running.set(false);
        
        activeConnections.forEach((socket, outputStream) -> {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing SSE connection: " + e.getMessage());
            }
        });
        activeConnections.clear();
        
        updateExecutor.shutdown();
    }
    
    public void handleSSERequest(Socket clientSocket) {
        try {
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/event-stream\r\n" +
                                "Cache-Control: no-cache\r\n" +
                                "Connection: keep-alive\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Access-Control-Allow-Headers: Cache-Control\r\n" +
                                "\r\n";
            
            OutputStream out = clientSocket.getOutputStream();
            out.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            activeConnections.put(clientSocket, out);
            sendSSEData(out, createUpdateData());
            monitorConnection(clientSocket);
            
        } catch (IOException e) {
            System.err.println("Error setting up SSE connection: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (IOException closeException) {
                // Ignore close errors
            }
        }
    }
    
    private void monitorConnection(Socket clientSocket) {
        Thread monitorThread = new Thread(() -> {
            try {
                while (running.get() && !clientSocket.isClosed()) {
                    try {
                        Thread.sleep(1000);
                        if (activeConnections.containsKey(clientSocket)) {
                            OutputStream out = activeConnections.get(clientSocket);
                            if (out != null) {
                                out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
            } finally {
                activeConnections.remove(clientSocket);
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    private void broadcastUpdate() {
        String updateData = createUpdateData();

        activeConnections.forEach((socket, outputStream) -> {
            try {
                if (!socket.isClosed()) {
                    sendSSEData(outputStream, updateData);
                } else {
                    activeConnections.remove(socket);
                }
            } catch (IOException e) {
                activeConnections.remove(socket);
                try {
                    socket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
            }
        });
    }
    
    private void sendSSEData(OutputStream out, String data) throws IOException {
        String sseData = "data: " + data + "\n\n";
        out.write(sseData.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    
    private String createUpdateData() {
        Map<String, Object> data = new HashMap<>();

        if (pageRegistry != null) {
            Map<String, Map<String, Object>> allPageData = pageRegistry.getAllPageData();
            for (Map.Entry<String, Map<String, Object>> pageEntry : allPageData.entrySet()) {
                Map<String, Object> pageData = pageEntry.getValue();
                for (Map.Entry<String, Object> dataEntry : pageData.entrySet()) {
                    String key = dataEntry.getKey();
                    data.put(key, dataEntry.getValue());
                }
            }
        }

        return gson.toJson(data);
    }

    public void forceUpdate() {
        broadcastUpdate();
    }
    
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    public boolean isRunning() {
        return running.get();
    }
}
