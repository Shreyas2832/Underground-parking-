-- ====================================================================
-- SUPABASE POSTGRESQL SCHEMA FOR UNDERGROUND PARKING SYSTEM (JAVA BACKEND)
-- ====================================================================

-- 1. Parking Slots Table (Status of each underground bay)
CREATE TABLE IF NOT EXISTS public.parking_slots (
    slot_id VARCHAR(10) PRIMARY KEY,
    floor_level VARCHAR(20) NOT NULL,
    slot_type VARCHAR(20) NOT NULL, -- '5-seater' or '7-seater'
    is_occupied BOOLEAN DEFAULT FALSE,
    plate_number VARCHAR(30),
    vehicle_type VARCHAR(20),
    entered_at TIMESTAMP WITH TIME ZONE
);

-- 2. Parking Transaction Logs Table (Historical entry, exits, and fees)
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

-- 3. Row Level Security (RLS) Policies
ALTER TABLE public.parking_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parking_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public all access to parking_slots"
    ON public.parking_slots FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public all access to parking_logs"
    ON public.parking_logs FOR ALL USING (true) WITH CHECK (true);

-- 4. Sample Seed Data for Slots
INSERT INTO public.parking_slots (slot_id, floor_level, slot_type, is_occupied)
VALUES
    ('B1-01', 'Level B1', '5-seater', false),
    ('B1-02', 'Level B1', '5-seater', false),
    ('B1-03', 'Level B1', '5-seater', false),
    ('B1-04', 'Level B1', '5-seater', false),
    ('B1-05', 'Level B1', '5-seater', false),
    ('B1-06', 'Level B1', '5-seater', false),
    ('B1-07', 'Level B1', '5-seater', false),
    ('B1-08', 'Level B1', '5-seater', false),
    ('B1-09', 'Level B1', '7-seater', false),
    ('B1-10', 'Level B1', '7-seater', false),
    ('B1-11', 'Level B1', '7-seater', false),
    ('B1-12', 'Level B1', '7-seater', false),
    ('B1-13', 'Level B1', '7-seater', false),
    ('B1-14', 'Level B1', '7-seater', false),
    ('B2-01', 'Level B2', '5-seater', false),
    ('B2-02', 'Level B2', '5-seater', false),
    ('B2-03', 'Level B2', '5-seater', false),
    ('B2-04', 'Level B2', '5-seater', false),
    ('B2-05', 'Level B2', '5-seater', false),
    ('B2-06', 'Level B2', '5-seater', false),
    ('B2-07', 'Level B2', '5-seater', false),
    ('B2-08', 'Level B2', '5-seater', false),
    ('B2-09', 'Level B2', '7-seater', false),
    ('B2-10', 'Level B2', '7-seater', false),
    ('B2-11', 'Level B2', '7-seater', false),
    ('B2-12', 'Level B2', '7-seater', false),
    ('B2-13', 'Level B2', '7-seater', false),
    ('B2-14', 'Level B2', '7-seater', false)
ON CONFLICT (slot_id) DO NOTHING;
