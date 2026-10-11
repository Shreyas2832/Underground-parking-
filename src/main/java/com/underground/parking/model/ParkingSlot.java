package com.underground.parking.model;

import com.underground.parking.util.SimpleJson;
import java.time.Instant;

public class ParkingSlot {
    private String slotId;        // e.g., "B1-01", "B2-08"
    private String floorLevel;    // "Level B1" or "Level B2"
    private String slotType;      // "5-seater" or "7-seater"
    private boolean isOccupied;
    private String plateNumber;   // null if empty
    private String vehicleType;   // "5-seater" or "7-seater"
    private Instant enteredAt;

    public ParkingSlot(String slotId, String floorLevel, String slotType) {
        this.slotId = slotId;
        this.floorLevel = floorLevel;
        this.slotType = slotType;
        this.isOccupied = false;
        this.plateNumber = null;
        this.vehicleType = null;
        this.enteredAt = null;
    }

    public String getSlotId() { return slotId; }
    public String getFloorLevel() { return floorLevel; }
    public String getSlotType() { return slotType; }
    public boolean isOccupied() { return isOccupied; }
    public String getPlateNumber() { return plateNumber; }
    public String getVehicleType() { return vehicleType; }
    public Instant getEnteredAt() { return enteredAt; }

    public void occupy(String plateNumber, String vehicleType) {
        this.isOccupied = true;
        this.plateNumber = plateNumber.toUpperCase().trim();
        this.vehicleType = vehicleType;
        this.enteredAt = Instant.now();
    }

    public void release() {
        this.isOccupied = false;
        this.plateNumber = null;
        this.vehicleType = null;
        this.enteredAt = null;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"slotId\":\"").append(SimpleJson.escape(slotId)).append("\",");
        sb.append("\"floorLevel\":\"").append(SimpleJson.escape(floorLevel)).append("\",");
        sb.append("\"slotType\":\"").append(SimpleJson.escape(slotType)).append("\",");
        sb.append("\"isOccupied\":").append(isOccupied).append(",");
        sb.append("\"plateNumber\":").append(plateNumber == null ? "null" : "\"" + SimpleJson.escape(plateNumber) + "\"").append(",");
        sb.append("\"vehicleType\":").append(vehicleType == null ? "null" : "\"" + SimpleJson.escape(vehicleType) + "\"").append(",");
        sb.append("\"enteredAt\":").append(enteredAt == null ? "null" : "\"" + enteredAt.toString() + "\"");
        sb.append("}");
        return sb.toString();
    }
}
