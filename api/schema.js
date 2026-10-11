// Vercel Serverless Function: GET /api/schema

export default function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'text/plain; charset=UTF-8');

  res.status(200).send(`-- SUPABASE SQL SCHEMA FOR UNDERGROUND PARKING
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

ALTER TABLE public.parking_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parking_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public all access to parking_slots"
    ON public.parking_slots FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public all access to parking_logs"
    ON public.parking_logs FOR ALL USING (true) WITH CHECK (true);
`);
}
