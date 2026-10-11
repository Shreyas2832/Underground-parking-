package com.underground.parking.service;

import com.underground.parking.model.ParkingRecord;
import com.underground.parking.model.ParkingSlot;
import com.underground.parking.model.ParkingStats;
import com.underground.parking.util.SimpleJson;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ParkingService {
    private final Map<String, ParkingSlot> slots = new LinkedHashMap<>();
    private final List<ParkingRecord> records = new CopyOnWriteArrayList<>();
    private final SupabaseService supabaseService;

    // Pricing rates
    private static final double RATE_5_SEATER_BASE = 3.00;
    private static final double RATE_5_SEATER_HOURLY = 2.00;
    private static final double RATE_7_SEATER_BASE = 5.00;
    private static final double RATE_7_SEATER_HOURLY = 3.50;

    public ParkingService(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
        initializeSlots();
        seedInitialDemoVehicles();
    }

    private void initializeSlots() {
        slots.clear();
        // Level B1 - 14 bays (8 5-seater, 6 7-seater)
        for (int i = 1; i <= 8; i++) {
            String id = String.format("B1-%02d", i);
            slots.put(id, new ParkingSlot(id, "Level B1", "5-seater"));
        }
        for (int i = 9; i <= 14; i++) {
            String id = String.format("B1-%02d", i);
            slots.put(id, new ParkingSlot(id, "Level B1", "7-seater"));
        }

        // Level B2 - 14 bays (8 5-seater, 6 7-seater)
        for (int i = 1; i <= 8; i++) {
            String id = String.format("B2-%02d", i);
            slots.put(id, new ParkingSlot(id, "Level B2", "5-seater"));
        }
        for (int i = 9; i <= 14; i++) {
            String id = String.format("B2-%02d", i);
            slots.put(id, new ParkingSlot(id, "Level B2", "7-seater"));
        }
    }

    private void seedInitialDemoVehicles() {
        // Seed a couple of realistic vehicles so the underground parking map looks live right away
        Instant now = Instant.now();
        
        ParkingSlot s1 = slots.get("B1-02");
        if (s1 != null) {
            s1.occupy("KA 03 MG 8821", "5-seater");
            ParkingRecord r1 = new ParkingRecord(UUID.randomUUID().toString(), "KA 03 MG 8821", "5-seater", "B1-02", "Level B1", now.minus(Duration.ofMinutes(42)));
            records.add(r1);
        }

        ParkingSlot s2 = slots.get("B1-10");
        if (s2 != null) {
            s2.occupy("MH 12 QP 5519", "7-seater");
            ParkingRecord r2 = new ParkingRecord(UUID.randomUUID().toString(), "MH 12 QP 5519", "7-seater", "B1-10", "Level B1", now.minus(Duration.ofMinutes(115)));
            records.add(r2);
        }

        ParkingSlot s3 = slots.get("B2-04");
        if (s3 != null) {
            s3.occupy("DL 08 CA 3302", "5-seater");
            ParkingRecord r3 = new ParkingRecord(UUID.randomUUID().toString(), "DL 08 CA 3302", "5-seater", "B2-04", "Level B2", now.minus(Duration.ofMinutes(25)));
            records.add(r3);
        }

        ParkingSlot s4 = slots.get("B2-12");
        if (s4 != null) {
            s4.occupy("TS 09 EU 9940", "7-seater");
            ParkingRecord r4 = new ParkingRecord(UUID.randomUUID().toString(), "TS 09 EU 9940", "7-seater", "B2-12", "Level B2", now.minus(Duration.ofMinutes(68)));
            records.add(r4);
        }
    }

    public synchronized ParkingRecord parkVehicle(String plateNumber, String vehicleType) throws Exception {
        if (plateNumber == null || plateNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Number plate is required.");
        }
        plateNumber = plateNumber.trim().toUpperCase();

        if (vehicleType == null || (!vehicleType.equals("5-seater") && !vehicleType.equals("7-seater"))) {
            vehicleType = "5-seater"; // default
        }

        // Check if already parked
        for (ParkingSlot s : slots.values()) {
            if (s.isOccupied() && plateNumber.equalsIgnoreCase(s.getPlateNumber())) {
                throw new IllegalStateException("Vehicle " + plateNumber + " is already parked in bay " + s.getSlotId() + " (" + s.getFloorLevel() + ").");
            }
        }

        // Find best slot: first preference exact matching slotType
        ParkingSlot allocated = null;
        for (ParkingSlot s : slots.values()) {
            if (!s.isOccupied() && s.getSlotType().equalsIgnoreCase(vehicleType)) {
                allocated = s;
                break;
            }
        }

        // If no slot matching exact type, fallback to any available slot
        if (allocated == null) {
            for (ParkingSlot s : slots.values()) {
                if (!s.isOccupied()) {
                    allocated = s;
                    break;
                }
            }
        }

        if (allocated == null) {
            throw new IllegalStateException("Underground Parking is fully occupied! No bays available.");
        }

        allocated.occupy(plateNumber, vehicleType);

        String recordId = UUID.randomUUID().toString();
        ParkingRecord record = new ParkingRecord(recordId, plateNumber, vehicleType, allocated.getSlotId(), allocated.getFloorLevel(), allocated.getEnteredAt());
        records.add(record);

        // Sync with Supabase asynchronously
        supabaseService.syncSlot(allocated);
        supabaseService.insertParkingLog(record);

        return record;
    }

    public synchronized Map<String, Object> exitVehicle(String plateNumber) throws Exception {
        if (plateNumber == null || plateNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Number plate is required.");
        }
        plateNumber = plateNumber.trim().toUpperCase();

        ParkingSlot targetSlot = null;
        for (ParkingSlot s : slots.values()) {
            if (s.isOccupied() && plateNumber.equalsIgnoreCase(s.getPlateNumber())) {
                targetSlot = s;
                break;
            }
        }

        if (targetSlot == null) {
            throw new IllegalArgumentException("No vehicle with number plate '" + plateNumber + "' found currently parked underground.");
        }

        Instant exitTime = Instant.now();
        Instant entryTime = targetSlot.getEnteredAt();
        if (entryTime == null) entryTime = exitTime.minus(Duration.ofMinutes(15));

        long durationMinutes = Duration.between(entryTime, exitTime).toMinutes();
        if (durationMinutes < 1) durationMinutes = 1; // minimum 1 minute

        String vType = targetSlot.getVehicleType();
        double baseRate = "7-seater".equalsIgnoreCase(vType) ? RATE_7_SEATER_BASE : RATE_5_SEATER_BASE;
        double hourlyRate = "7-seater".equalsIgnoreCase(vType) ? RATE_7_SEATER_HOURLY : RATE_5_SEATER_HOURLY;

        double hours = Math.ceil(durationMinutes / 60.0);
        if (hours < 1) hours = 1;
        double totalFee = baseRate + (hours * hourlyRate);

        String slotId = targetSlot.getSlotId();
        String floorLevel = targetSlot.getFloorLevel();

        // Release slot
        targetSlot.release();

        // Update record
        ParkingRecord matchedRecord = null;
        for (int i = records.size() - 1; i >= 0; i--) {
            ParkingRecord r = records.get(i);
            if (plateNumber.equalsIgnoreCase(r.getPlateNumber()) && r.getExitedAt() == null) {
                matchedRecord = r;
                break;
            }
        }

        if (matchedRecord != null) {
            matchedRecord.completeExit(exitTime, durationMinutes, totalFee);
            supabaseService.updateParkingLogExit(matchedRecord);
        }

        supabaseService.syncSlot(targetSlot);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plateNumber", plateNumber);
        result.put("vehicleType", vType);
        result.put("slotId", slotId);
        result.put("floorLevel", floorLevel);
        result.put("enteredAt", entryTime.toString());
        result.put("exitedAt", exitTime.toString());
        result.put("durationMinutes", durationMinutes);
        result.put("feeCharged", totalFee);

        return result;
    }

    public List<ParkingSlot> getAllSlots() {
        return new ArrayList<>(slots.values());
    }

    public List<ParkingSlot> getActiveVehicles() {
        List<ParkingSlot> active = new ArrayList<>();
        for (ParkingSlot s : slots.values()) {
            if (s.isOccupied()) {
                active.add(s);
            }
        }
        // Sort newest first
        active.sort((a, b) -> {
            if (a.getEnteredAt() == null && b.getEnteredAt() == null) return 0;
            if (a.getEnteredAt() == null) return 1;
            if (b.getEnteredAt() == null) return -1;
            return b.getEnteredAt().compareTo(a.getEnteredAt());
        });
        return active;
    }

    public ParkingStats getStats() {
        int total = slots.size();
        int occupied = 0;
        int fiveTotal = 0;
        int fiveOcc = 0;
        int sevenTotal = 0;
        int sevenOcc = 0;

        for (ParkingSlot s : slots.values()) {
            if ("5-seater".equalsIgnoreCase(s.getSlotType())) {
                fiveTotal++;
                if (s.isOccupied()) {
                    fiveOcc++;
                    occupied++;
                }
            } else {
                sevenTotal++;
                if (s.isOccupied()) {
                    sevenOcc++;
                    occupied++;
                }
            }
        }

        return new ParkingStats(total, total - occupied, occupied, fiveTotal, fiveOcc, sevenTotal, sevenOcc);
    }

    public synchronized void resetDemo() {
        initializeSlots();
        records.clear();
        seedInitialDemoVehicles();
        for (ParkingSlot s : slots.values()) {
            supabaseService.syncSlot(s);
        }
    }
}
