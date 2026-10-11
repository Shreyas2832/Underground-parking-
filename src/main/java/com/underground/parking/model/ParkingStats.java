package com.underground.parking.model;

public class ParkingStats {
    private int totalSlots;
    private int availableSlots;
    private int occupiedSlots;
    private int fiveSeaterTotal;
    private int fiveSeaterOccupied;
    private int sevenSeaterTotal;
    private int sevenSeaterOccupied;

    public ParkingStats(int totalSlots, int availableSlots, int occupiedSlots,
                        int fiveSeaterTotal, int fiveSeaterOccupied,
                        int sevenSeaterTotal, int sevenSeaterOccupied) {
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
        this.occupiedSlots = occupiedSlots;
        this.fiveSeaterTotal = fiveSeaterTotal;
        this.fiveSeaterOccupied = fiveSeaterOccupied;
        this.sevenSeaterTotal = sevenSeaterTotal;
        this.sevenSeaterOccupied = sevenSeaterOccupied;
    }

    public String toJson() {
        return "{" +
                "\"totalSlots\":" + totalSlots + "," +
                "\"availableSlots\":" + availableSlots + "," +
                "\"occupiedSlots\":" + occupiedSlots + "," +
                "\"fiveSeaterTotal\":" + fiveSeaterTotal + "," +
                "\"fiveSeaterOccupied\":" + fiveSeaterOccupied + "," +
                "\"sevenSeaterTotal\":" + sevenSeaterTotal + "," +
                "\"sevenSeaterOccupied\":" + sevenSeaterOccupied + "," +
                "\"occupancyRate\":" + (totalSlots > 0 ? (occupiedSlots * 100 / totalSlots) : 0) +
                "}";
    }
}
