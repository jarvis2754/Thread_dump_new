package com.analyzer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ThreadDumpServer {

    private static final int[] PORTS = {8080, 8081, 8082, 9090, 9091};

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            startOn(Integer.parseInt(args[0]));
            return;
        }
        for (int port : PORTS) {
            try { startOn(port); return; }
            catch (java.net.BindException e) {
                System.out.println("Port " + port + " in use, trying next...");
            }
        }
        System.err.println("ERROR: All ports in use.");
        System.exit(1);
    }

    private static void startOn(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/analyze", new AnalyzeHandler());
        server.createContext("/health",      new HealthHandler());
        server.createContext("/",            new FrontendHandler());  // serves frontend files
        server.start();
        System.out.println("==============================================");
        System.out.println("  Thread Dump Analyzer running on :" + port);
        System.out.println("  Open: http://localhost:" + port);
        System.out.println("==============================================");
    }

    // GET / → serves frontend/index.html, app.js, style.css
    static class FrontendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            java.io.File file = new java.io.File("frontend" + path);
            if (!file.exists() || !file.isFile()) {
                byte[] b = "Not found".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                return;
            }

            String ct = path.endsWith(".js")  ? "application/javascript; charset=UTF-8"
                      : path.endsWith(".css") ? "text/css; charset=UTF-8"
                      : "text/html; charset=UTF-8";

            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            ex.getResponseHeaders().set("Content-Type", ct);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    // POST /api/analyze → JSON array of threads
    static class AnalyzeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                String contentType = ex.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.contains("multipart/form-data")) {
                    sendJson(ex, 400, "{\"error\":\"Expected multipart/form-data\"}");
                    return;
                }

                String fileContent = MultipartParser.extractFileContent(ex.getRequestBody(), contentType);
                if (fileContent == null || fileContent.trim().isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Uploaded file is empty\"}");
                    return;
                }

                List<ThreadInfo> threads = new ThreadDumpParser().parse(fileContent);
                if (threads.isEmpty()) {
                    sendJson(ex, 422, "{\"error\":\"No threads found. Is this a valid Java thread dump?\"}");
                    return;
                }

                threads.sort((a, b) -> Double.compare(b.cpuMs, a.cpuMs));
                System.out.println("Parsed " + threads.size() + " threads.");
                sendJson(ex, 200, JsonSerializer.toJson(threads));

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            }
        }
    }

    // GET /health → liveness check
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendJson(ex, 200, "{\"status\":\"ok\"}");
        }
    }

    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
