package com.underground.parking.service;

import com.underground.parking.model.ParkingRecord;
import com.underground.parking.model.ParkingSlot;
import com.underground.parking.util.SimpleJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class SupabaseService {
    private String supabaseUrl;
    private String supabaseKey;
    private final HttpClient httpClient;
    private boolean isConnected = false;
    private String lastError = null;
    private Instant lastSyncTime = null;

    public SupabaseService(String supabaseUrl, String supabaseKey) {
        this.supabaseUrl = (supabaseUrl != null) ? supabaseUrl.trim().replaceAll("/+$", "") : "";
        this.supabaseKey = (supabaseKey != null) ? supabaseKey.trim() : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        checkConnection();
    }

    public synchronized void updateCredentials(String url, String key) {
        this.supabaseUrl = (url != null) ? url.trim().replaceAll("/+$", "") : "";
        this.supabaseKey = (key != null) ? key.trim() : "";
        checkConnection();
    }

    public boolean isConfigured() {
        return supabaseUrl != null && !supabaseUrl.isEmpty() && !supabaseUrl.equals("YOUR_SUPABASE_URL")
                && supabaseKey != null && !supabaseKey.isEmpty() && !supabaseKey.equals("YOUR_SUPABASE_ANON_KEY");
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLastSyncTime() {
        return lastSyncTime;
    }

    public boolean checkConnection() {
        if (!isConfigured()) {
            this.isConnected = false;
            this.lastError = "Credentials not configured. Set Supabase URL and Anon Key.";
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + "/rest/v1/parking_slots?select=slot_id&limit=1"))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                this.isConnected = true;
                this.lastError = null;
                this.lastSyncTime = Instant.now();
                System.out.println("[Supabase] Successfully connected to Supabase database!");
                return true;
            } else if (response.statusCode() == 404 || response.body().contains("relation \"public.parking_slots\" does not exist")) {
                this.isConnected = true; // Supabase reachable, tables need creation
                this.lastError = "Supabase project reachable, but 'parking_slots' table not found yet. Run schema.sql in Supabase SQL Editor.";
                System.out.println("[Supabase] " + lastError);
                return true;
            } else {
                this.isConnected = false;
                this.lastError = "HTTP " + response.statusCode() + ": " + response.body();
                System.err.println("[Supabase] Connection test failed: " + lastError);
                return false;
            }
        } catch (Exception e) {
            this.isConnected = false;
            this.lastError = "Network error: " + e.getMessage();
            System.err.println("[Supabase] Failed to reach " + supabaseUrl + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Initializes remote slots in Supabase if table exists.
     */
    public void seedSlotsIfRemoteEmpty(List<ParkingSlot> slots) {
        if (!isConfigured()) return;
        new Thread(() -> {
            try {
                for (ParkingSlot slot : slots) {
                    syncSlot(slot);
                }
            } catch (Exception e) {
                System.err.println("[Supabase] Error seeding slots: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Updates slot occupancy status in Supabase.
     */
    public void syncSlot(ParkingSlot slot) {
        if (!isConfigured()) return;

        new Thread(() -> {
            try {
                StringBuilder body = new StringBuilder();
                body.append("{");
                body.append("\"slot_id\":\"").append(SimpleJson.escape(slot.getSlotId())).append("\",");
                body.append("\"floor_level\":\"").append(SimpleJson.escape(slot.getFloorLevel())).append("\",");
                body.append("\"slot_type\":\"").append(SimpleJson.escape(slot.getSlotType())).append("\",");
                body.append("\"is_occupied\":").append(slot.isOccupied()).append(",");
                body.append("\"plate_number\":").append(slot.getPlateNumber() == null ? "null" : "\"" + SimpleJson.escape(slot.getPlateNumber()) + "\"").append(",");
                body.append("\"vehicle_type\":").append(slot.getVehicleType() == null ? "null" : "\"" + SimpleJson.escape(slot.getVehicleType()) + "\"").append(",");
                body.append("\"entered_at\":").append(slot.getEnteredAt() == null ? "null" : "\"" + slot.getEnteredAt().toString() + "\"");
                body.append("}");

                // Upsert via PostgREST
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(supabaseUrl + "/rest/v1/parking_slots?on_conflict=slot_id"))
                        .header("apikey", supabaseKey)
                        .header("Authorization", "Bearer " + supabaseKey)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "resolution=merge-duplicates,return=minimal")
                        .timeout(Duration.ofSeconds(6))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    lastSyncTime = Instant.now();
                } else {
                    lastError = "Failed sync slot " + slot.getSlotId() + ": HTTP " + response.statusCode();
                }
            } catch (Exception e) {
                lastError = "Sync slot error: " + e.getMessage();
            }
        }).start();
    }

    /**
     * Inserts new parking entry log into Supabase table `parking_logs`.
     */
    public void insertParkingLog(ParkingRecord record) {
        if (!isConfigured()) return;

        new Thread(() -> {
            try {
                StringBuilder body = new StringBuilder();
                body.append("{");
                body.append("\"plate_number\":\"").append(SimpleJson.escape(record.getPlateNumber())).append("\",");
                body.append("\"vehicle_type\":\"").append(SimpleJson.escape(record.getVehicleType())).append("\",");
                body.append("\"slot_id\":\"").append(SimpleJson.escape(record.getSlotId())).append("\",");
                body.append("\"floor_level\":\"").append(SimpleJson.escape(record.getFloorLevel())).append("\",");
                body.append("\"entered_at\":\"").append(record.getEnteredAt().toString()).append("\"");
                body.append("}");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(supabaseUrl + "/rest/v1/parking_logs"))
                        .header("apikey", supabaseKey)
                        .header("Authorization", "Bearer " + supabaseKey)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=representation")
                        .timeout(Duration.ofSeconds(6))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    lastSyncTime = Instant.now();
                }
            } catch (Exception e) {
                lastError = "Insert log error: " + e.getMessage();
            }
        }).start();
    }

    /**
     * Updates parking log with exit time, duration, and fee in Supabase.
     */
    public void updateParkingLogExit(ParkingRecord record) {
        if (!isConfigured()) return;

        new Thread(() -> {
            try {
                StringBuilder body = new StringBuilder();
                body.append("{");
                body.append("\"exited_at\":\"").append(record.getExitedAt().toString()).append("\",");
                body.append("\"duration_minutes\":").append(record.getDurationMinutes()).append(",");
                body.append("\"fee_charged\":").append(String.format(java.util.Locale.US, "%.2f", record.getFeeCharged()));
                body.append("}");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(supabaseUrl + "/rest/v1/parking_logs?plate_number=eq." + record.getPlateNumber() + "&exited_at=is.null"))
                        .header("apikey", supabaseKey)
                        .header("Authorization", "Bearer " + supabaseKey)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .timeout(Duration.ofSeconds(6))
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    lastSyncTime = Instant.now();
                }
            } catch (Exception e) {
                lastError = "Update exit log error: " + e.getMessage();
            }
        }).start();
    }

    public String getStatusJson() {
        return "{" +
                "\"configured\":" + isConfigured() + "," +
                "\"connected\":" + isConnected + "," +
                "\"url\":\"" + SimpleJson.escape(supabaseUrl) + "\"," +
                "\"lastError\":" + (lastError == null ? "null" : "\"" + SimpleJson.escape(lastError) + "\"") + "," +
                "\"lastSyncTime\":" + (lastSyncTime == null ? "null" : "\"" + lastSyncTime.toString() + "\"") +
                "}";
    }
}
