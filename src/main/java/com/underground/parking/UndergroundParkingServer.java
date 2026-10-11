package com.underground.parking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.underground.parking.model.ParkingRecord;
import com.underground.parking.model.ParkingSlot;
import com.underground.parking.model.ParkingStats;
import com.underground.parking.service.ParkingService;
import com.underground.parking.service.SupabaseService;
import com.underground.parking.util.SimpleJson;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

public class UndergroundParkingServer {
    private static int PORT = 8080;
    private static SupabaseService supabaseService;
    private static ParkingService parkingService;
    private static Path webRoot;

    public static void main(String[] args) throws IOException {
        loadProperties();

        // Initialize Services
        String envUrl = System.getenv("SUPABASE_URL");
        String envKey = System.getenv("SUPABASE_KEY");
        if (envUrl != null && !envUrl.isEmpty()) {
            System.out.println("[Config] Loaded SUPABASE_URL from environment.");
        }
        
        supabaseService = new SupabaseService(envUrl, envKey);
        parkingService = new ParkingService(supabaseService);

        // Resolve web static directory
        Path currentDir = Paths.get("").toAbsolutePath();
        if (Files.exists(currentDir.resolve("web"))) {
            webRoot = currentDir.resolve("web");
        } else if (Files.exists(currentDir.resolve("underground-parking-java/web"))) {
            webRoot = currentDir.resolve("underground-parking-java/web");
        } else {
            webRoot = Paths.get("C:/Users/Sambhaji/.gemini/antigravity-ide/scratch/underground-parking-java/web");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // API Endpoints
        server.createContext("/api/slots", new SlotsHandler());
        server.createContext("/api/active", new ActiveVehiclesHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/park", new ParkVehicleHandler());
        server.createContext("/api/exit", new ExitVehicleHandler());
        server.createContext("/api/supabase/status", new SupabaseStatusHandler());
        server.createContext("/api/supabase/config", new SupabaseConfigHandler());
        server.createContext("/api/reset", new ResetHandler());
        server.createContext("/api/schema", new SchemaHandler());

        // Static Files Handler
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println("===============================================================");
        System.out.println(" 🚗 AURA UNDERGROUND PARKING SERVER STARTED");
        System.out.println(" URL: http://localhost:" + PORT);
        System.out.println(" Static Web Directory: " + webRoot);
        System.out.println(" Supabase Status: " + (supabaseService.isConnected() ? "CONNECTED" : "NOT CONFIGURED / LOCAL MODE"));
        System.out.println("===============================================================");
    }

    private static void loadProperties() {
        try {
            File propFile = new File("config.properties");
            if (!propFile.exists()) {
                propFile = new File("underground-parking-java/config.properties");
            }
            if (propFile.exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(propFile)) {
                    props.load(fis);
                    String portStr = props.getProperty("server.port");
                    if (portStr != null) PORT = Integer.parseInt(portStr.trim());
                    String url = props.getProperty("supabase.url");
                    String key = props.getProperty("supabase.key");
                    if (url != null && !url.trim().isEmpty() && !url.contains("YOUR_")) {
                        System.setProperty("SUPABASE_URL", url.trim());
                    }
                    if (key != null && !key.trim().isEmpty() && !key.contains("YOUR_")) {
                        System.setProperty("SUPABASE_KEY", key.trim());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Config] Error loading config.properties: " + e.getMessage());
        }
    }

    // --- HTTP HANDLERS ---

    static class SlotsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            List<ParkingSlot> slots = parkingService.getAllSlots();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(slots.get(i).toJson());
            }
            sb.append("]");

            sendResponse(exchange, 200, sb.toString(), "application/json");
        }
    }

    static class ActiveVehiclesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            List<ParkingSlot> active = parkingService.getActiveVehicles();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < active.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(active.get(i).toJson());
            }
            sb.append("]");

            sendResponse(exchange, 200, sb.toString(), "application/json");
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            ParkingStats stats = parkingService.getStats();
            sendResponse(exchange, 200, stats.toJson(), "application/json");
        }
    }

    static class ParkVehicleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            String body = readRequestBody(exchange);
            String plate = SimpleJson.getString(body, "plateNumber");
            String type = SimpleJson.getString(body, "vehicleType");

            try {
                ParkingRecord record = parkingService.parkVehicle(plate, type);
                String resp = "{\"success\":true,\"message\":\"Vehicle parked successfully!\",\"record\":" + record.toJson() + "}";
                sendResponse(exchange, 200, resp, "application/json");
            } catch (Exception e) {
                String resp = "{\"success\":false,\"error\":\"" + SimpleJson.escape(e.getMessage()) + "\"}";
                sendResponse(exchange, 400, resp, "application/json");
            }
        }
    }

    static class ExitVehicleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            String body = readRequestBody(exchange);
            String plate = SimpleJson.getString(body, "plateNumber");

            try {
                Map<String, Object> exitData = parkingService.exitVehicle(plate);
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"message\":\"Vehicle checkout completed!\",");
                sb.append("\"plateNumber\":\"").append(SimpleJson.escape((String)exitData.get("plateNumber"))).append("\",");
                sb.append("\"vehicleType\":\"").append(SimpleJson.escape((String)exitData.get("vehicleType"))).append("\",");
                sb.append("\"slotId\":\"").append(SimpleJson.escape((String)exitData.get("slotId"))).append("\",");
                sb.append("\"floorLevel\":\"").append(SimpleJson.escape((String)exitData.get("floorLevel"))).append("\",");
                sb.append("\"enteredAt\":\"").append(exitData.get("enteredAt")).append("\",");
                sb.append("\"exitedAt\":\"").append(exitData.get("exitedAt")).append("\",");
                sb.append("\"durationMinutes\":").append(exitData.get("durationMinutes")).append(",");
                sb.append("\"feeCharged\":").append(String.format(java.util.Locale.US, "%.2f", exitData.get("feeCharged")));
                sb.append("}");
                sendResponse(exchange, 200, sb.toString(), "application/json");
            } catch (Exception e) {
                String resp = "{\"success\":false,\"error\":\"" + SimpleJson.escape(e.getMessage()) + "\"}";
                sendResponse(exchange, 400, resp, "application/json");
            }
        }
    }

    static class SupabaseStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            sendResponse(exchange, 200, supabaseService.getStatusJson(), "application/json");
        }
    }

    static class SupabaseConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            String body = readRequestBody(exchange);
            String url = SimpleJson.getString(body, "url");
            String key = SimpleJson.getString(body, "key");

            supabaseService.updateCredentials(url, key);
            boolean connected = supabaseService.checkConnection();

            String resp = "{\"success\":" + connected + ",\"status\":" + supabaseService.getStatusJson() + "}";
            sendResponse(exchange, 200, resp, "application/json");
        }
    }

    static class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            parkingService.resetDemo();
            sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Demo reset completed!\"}", "application/json");
        }
    }

    static class SchemaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            String schema = getSqlSchema();
            sendResponse(exchange, 200, schema, "text/plain");
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            Path file = webRoot.resolve(path.substring(1)).normalize();
            if (!file.startsWith(webRoot) || !Files.exists(file) || Files.isDirectory(file)) {
                // If not found, try index.html
                file = webRoot.resolve("index.html");
            }

            if (!Files.exists(file)) {
                sendResponse(exchange, 404, "404 Not Found", "text/plain");
                return;
            }

            String mime = getMimeType(file.getFileName().toString());
            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE, PATCH");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, apikey");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int read;
            while ((read = is.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private static void sendResponse(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".sql")) return "text/plain";
        return "text/plain";
    }

    private static String getSqlSchema() {
        return """
                -- SUPABASE SQL SCHEMA FOR UNDERGROUND PARKING
                -- Run this in your Supabase Project > SQL Editor

                CREATE TABLE IF NOT EXISTS public.parking_slots (
                    slot_id VARCHAR(10) PRIMARY KEY,
                    floor_level VARCHAR(20) NOT NULL,
                    slot_type VARCHAR(20) NOT NULL, -- '5-seater' or '7-seater'
                    is_occupied BOOLEAN DEFAULT FALSE,
                    plate_number VARCHAR(30),
                    vehicle_type VARCHAR(20),
                    entered_at TIMESTAMP WITH TIME ZONE
                );

                CREATE TABLE IF NOT EXISTS public.parking_logs (
                    id BIGSERIAL PRIMARY KEY,
                    plate_number VARCHAR(30) NOT NULL,
                    vehicle_type VARCHAR(20) NOT NULL,
                    slot_id VARCHAR(10) NOT NULL,
                    floor_level VARCHAR(20) NOT NULL,
                    entered_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    exited_at TIMESTAMP WITH TIME ZONE,
                    duration_minutes INTEGER,
                    fee_charged NUMERIC(10, 2)
                );

                -- Enable Row Level Security (RLS) and allow public read/write for demo
                ALTER TABLE public.parking_slots ENABLE ROW LEVEL SECURITY;
                ALTER TABLE public.parking_logs ENABLE ROW LEVEL SECURITY;

                CREATE POLICY "Allow public all access to parking_slots"
                    ON public.parking_slots FOR ALL USING (true) WITH CHECK (true);

                CREATE POLICY "Allow public all access to parking_logs"
                    ON public.parking_logs FOR ALL USING (true) WITH CHECK (true);
                """;
    }
}
