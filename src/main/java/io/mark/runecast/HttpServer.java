package io.mark.runecast;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpServer {
    private final PageManager pageManager;
    private final SSEManager sseManager;
    private final int port;

    private ServerSocket serverSocket;
    private ExecutorService serverExecutor;
    private ExecutorService clientExecutor;
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);

    public HttpServer(PageManager pageManager, SSEManager sseManager, int port) {
        this.pageManager = pageManager;
        this.sseManager = sseManager;
        this.port = port;
    }

    public void start() {
        if (serverRunning.get()) {
            return;
        }
        
        serverExecutor = Executors.newSingleThreadExecutor();
        clientExecutor = Executors.newCachedThreadPool();
        
        serverExecutor.submit(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("localhost", port));
                serverRunning.set(true);
                
                System.out.println("HTTP server started at: http://localhost:" + port);
                
                while (serverRunning.get() && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientExecutor.submit(() -> handleClient(clientSocket));
                    } catch (IOException e) {
                        if (serverRunning.get()) {
                            System.err.println("Error accepting client: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to start HTTP server: " + e.getMessage());
            }
        });
    }
    
    public void stop() {
        serverRunning.set(false);
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
        
        if (serverExecutor != null) {
            serverExecutor.shutdown();
        }
        
        if (clientExecutor != null) {
            clientExecutor.shutdown();
        }
    }
    
    private void handleClient(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String requestLine = reader.readLine();
            
            if (requestLine != null && requestLine.startsWith("GET")) {
                String path = extractPath(requestLine);
                
                switch (path) {
                    case "/sse":
                        sseManager.handleSSERequest(clientSocket);
                        break;
                    case "/hp":
                        sendPageResponse(clientSocket, "hp");
                        break;
                    case "/debug":
                        sendDebugResponse(clientSocket);
                        break;
                    default:
                        // Check if this is an asset request (images, CSS, JS, etc.)
                        if (isAssetRequest(path)) {
                            sendAssetResponse(clientSocket, path);
                        } else {
                            send404Response(clientSocket);
                        }
                        break;
                }
            } else {
                send404Response(clientSocket);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
    
    private String extractPath(String requestLine) {
        if (requestLine == null) return "/";
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return "/";
        
        String path = parts[1];
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        return path;
    }

    private void sendPageResponse(Socket clientSocket, String pageName) throws IOException {
        String html = pageManager.getPage(pageName);
        String response = "HTTP/1.1 200 OK\r\n" +
                         "Content-Type: text/html; charset=UTF-8\r\n" +
                         "Content-Length: " + html.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                         "Pragma: no-cache\r\n" +
                         "Expires: 0\r\n" +
                         "Connection: keep-alive\r\n" +
                         "\r\n" +
                         html;
        
        OutputStream out = clientSocket.getOutputStream();
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void sendJsonResponse(Socket clientSocket, String json) throws IOException {
        String response = "HTTP/1.1 200 OK\r\n" +
                         "Content-Type: application/json\r\n" +
                         "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Connection: close\r\n" +
                         "\r\n" +
                         json;
        
        OutputStream out = clientSocket.getOutputStream();
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void send404Response(Socket clientSocket) throws IOException {
        String response = "HTTP/1.1 404 Not Found\r\n" +
                         "Content-Type: text/plain\r\n" +
                         "Content-Length: 13\r\n" +
                         "Connection: close\r\n" +
                         "\r\n" +
                         "Page not found";
        
        OutputStream out = clientSocket.getOutputStream();
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    
    private boolean isAssetRequest(String path) {
        if (path == null || path.equals("/")) return false;
        
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".png") || 
               lowerPath.endsWith(".jpg") || 
               lowerPath.endsWith(".jpeg") || 
               lowerPath.endsWith(".gif") || 
               lowerPath.endsWith(".svg") || 
               lowerPath.endsWith(".css") || 
               lowerPath.endsWith(".js") || 
               lowerPath.endsWith(".ico") || 
               lowerPath.endsWith(".woff") || 
               lowerPath.endsWith(".woff2") || 
               lowerPath.endsWith(".ttf") || 
               lowerPath.endsWith(".eot");
    }
    
    private void sendAssetResponse(Socket clientSocket, String assetPath) throws IOException {
        // Remove leading slash
        String cleanPath = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        
        byte[] assetData = pageManager.getAsset(cleanPath);
        if (assetData != null) {
            String mimeType = pageManager.getAssetMimeType(cleanPath);
            String response = "HTTP/1.1 200 OK\r\n" +
                             "Content-Type: " + mimeType + "\r\n" +
                             "Content-Length: " + assetData.length + "\r\n" +
                             "Access-Control-Allow-Origin: *\r\n" +
                             "Cache-Control: public, max-age=31536000\r\n" +
                             "Connection: keep-alive\r\n" +
                         "\r\n";
            
            OutputStream out = clientSocket.getOutputStream();
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.write(assetData);
            out.flush();
        } else {
            send404Response(clientSocket);
        }
    }
    
    private void sendDebugResponse(Socket clientSocket) throws IOException {
        String debugInfo = pageManager.getAvailableAssetsDebug();
        String response = "HTTP/1.1 200 OK\r\n" +
                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                         "Content-Length: " + debugInfo.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Connection: close\r\n" +
                         "\r\n" +
                         debugInfo;
        
        OutputStream out = clientSocket.getOutputStream();
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    
    public boolean isRunning() {
        return serverRunning.get();
    }
}
