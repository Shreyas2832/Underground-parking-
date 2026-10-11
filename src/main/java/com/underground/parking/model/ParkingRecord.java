package com.underground.parking.model;

import com.underground.parking.util.SimpleJson;
import java.time.Instant;

public class ParkingRecord {
    private String id;
    private String plateNumber;
    private String vehicleType;   // "5-seater" or "7-seater"
    private String slotId;
    private String floorLevel;
    private Instant enteredAt;
    private Instant exitedAt;
    private long durationMinutes;
    private double feeCharged;

    public ParkingRecord(String id, String plateNumber, String vehicleType, String slotId, String floorLevel, Instant enteredAt) {
        this.id = id;
        this.plateNumber = plateNumber.toUpperCase().trim();
        this.vehicleType = vehicleType;
        this.slotId = slotId;
        this.floorLevel = floorLevel;
        this.enteredAt = enteredAt;
        this.exitedAt = null;
        this.durationMinutes = 0;
        this.feeCharged = 0.0;
    }

    public String getId() { return id; }
    public String getPlateNumber() { return plateNumber; }
    public String getVehicleType() { return vehicleType; }
    public String getSlotId() { return slotId; }
    public String getFloorLevel() { return floorLevel; }
    public Instant getEnteredAt() { return enteredAt; }
    public Instant getExitedAt() { return exitedAt; }
    public long getDurationMinutes() { return durationMinutes; }
    public double getFeeCharged() { return feeCharged; }

    public void completeExit(Instant exitedAt, long durationMinutes, double feeCharged) {
        this.exitedAt = exitedAt;
        this.durationMinutes = durationMinutes;
        this.feeCharged = feeCharged;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(SimpleJson.escape(id)).append("\",");
        sb.append("\"plateNumber\":\"").append(SimpleJson.escape(plateNumber)).append("\",");
        sb.append("\"vehicleType\":\"").append(SimpleJson.escape(vehicleType)).append("\",");
        sb.append("\"slotId\":\"").append(SimpleJson.escape(slotId)).append("\",");
        sb.append("\"floorLevel\":\"").append(SimpleJson.escape(floorLevel)).append("\",");
        sb.append("\"enteredAt\":").append(enteredAt == null ? "null" : "\"" + enteredAt.toString() + "\"").append(",");
        sb.append("\"exitedAt\":").append(exitedAt == null ? "null" : "\"" + exitedAt.toString() + "\"").append(",");
        sb.append("\"durationMinutes\":").append(durationMinutes).append(",");
        sb.append("\"feeCharged\":").append(String.format(java.util.Locale.US, "%.2f", feeCharged));
        sb.append("}");
        return sb.toString();
    }
}
