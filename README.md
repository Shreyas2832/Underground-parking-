# 🚗 AURA Underground Parking Management System (Pure Java & Supabase)

An enterprise-grade, subterranean smart parking system designed and strictly built entirely in **Java** (Java 25 LTS). It simulates a multi-level underground parking facility (Level B1 Upper Deck & Level B2 Deep Deck) with real-time bay allocation, ceiling ultrasonic telemetry sensors, digital vehicle entry/exit tickets, fee calculation, and bi-directional **Supabase PostgreSQL** cloud synchronization.

---

## 🌟 Key Highlights & Features

1. **Underground Parking Visualization**:
   - **Level B1 (Subterranean Upper Deck)** and **Level B2 (Subterranean Deep Deck)** with 28 individual bays.
   - Dual vehicle bays: **5-Seater bays** (Compact / Sedan / Hatchback) and **7-Seater bays** (SUV / MPV / Van).
   - Ultrasonic ceiling sensor simulation (🟢 Green for Available, 🔴 Red for Occupied).
   - Interactive slots with real-time car icons and embossed registration number plates.

2. **Vehicle Entry Gateway (Right Terminal • ENTER)**:
   - **Seating Capacity Selection**: Direct radio toggle between **5-Seater** and **7-Seater**.
   - **License Plate Input**: Stylized automobile number plate with auto-uppercase and quick presets.
   - **Login & Park Action**: Authenticates vehicle entry, calculates optimal underground bay, registers entry timestamp, syncs to Supabase, and issues a digital underground ticket.

3. **Entered Vehicles Live Telemetry (Left Section)**:
   - "VEHICLES ENTERED IN UNDERGROUND PARKING" real-time deck.
   - Live cards showing:
     - Embossed License Plate badge
     - 5-Seater vs 7-Seater designation
     - Assigned Underground Bay ID & Floor Level
     - Entry timestamp
     - Live ticking duration counter (`0m 15s`, `0m 16s`...)
     - 1-click quick exit checkout

4. **Vehicle Exit Gateway (Right Terminal • EXIT)**:
   - Enter vehicle number plate (or 1-click select from currently parked cars).
   - Releases the underground bay in real time on the parking floor plan.
   - Automatically computes parked duration (hours & minutes) and dynamic fees:
     - 5-Seater: $3.00 base + $2.00/hour
     - 7-Seater: $5.00 base + $3.50/hour
   - Produces a printable **Departure Invoice & Receipt** modal.

5. **Cloud Database (Supabase PostgreSQL Integration)**:
   - Integrated Java HTTP client communicating with Supabase PostgREST API.
   - Built-in status badge in header: live connection ping & sync indicator.
   - Dynamic credentials configuration directly from the browser without restarting.
   - Ready-to-run PostgreSQL schema script included in UI with 1-click copy.
   - Seamless local fallback mode if Supabase is offline or not yet configured.

6. **Strictly 100% Java Backend**:
   - Built with standard Java library: `com.sun.net.httpserver.HttpServer`, `java.net.http.HttpClient`, `java.util.concurrent.Executors`.
   - Requires zero external build dependencies (runs natively with standard `javac` and `java`).

---

## 🚀 How to Run

### Step 1: Compile & Start the Server
Open PowerShell or Command Prompt in this folder:
```cmd
run.bat
```
Or manually:
```cmd
javac -d bin src\main\java\com\underground\parking\*.java src\main\java\com\underground\parking\model\*.java src\main\java\com\underground\parking\service\*.java src\main\java\com\underground\parking\util\*.java
java -cp bin com.underground.parking.UndergroundParkingServer
```

### Step 2: Open in Browser
Visit:
```
http://localhost:8080
```

---

## 🗄️ Supabase PostgreSQL Setup

1. Create a free project at [supabase.com](https://supabase.com).
2. Go to **SQL Editor** in your Supabase dashboard and run the following script (also available inside the web UI by clicking **Supabase Sync**):

```sql
-- 1. Parking Slots Table
CREATE TABLE IF NOT EXISTS public.parking_slots (
    slot_id VARCHAR(10) PRIMARY KEY,
    floor_level VARCHAR(20) NOT NULL,
    slot_type VARCHAR(20) NOT NULL, -- '5-seater' or '7-seater'
    is_occupied BOOLEAN DEFAULT FALSE,
    plate_number VARCHAR(30),
    vehicle_type VARCHAR(20),
    entered_at TIMESTAMP WITH TIME ZONE
);

-- 2. Parking Logs Table
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

-- Enable RLS and public access for demo
ALTER TABLE public.parking_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parking_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public all access to parking_slots"
    ON public.parking_slots FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public all access to parking_logs"
    ON public.parking_logs FOR ALL USING (true) WITH CHECK (true);
```

3. In the web application, click the **Supabase Sync** badge in the top-right header, enter your **Project URL** and **Anon API Key**, and click **Save & Connect Supabase**!
