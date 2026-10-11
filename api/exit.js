// Vercel Serverless Function: POST /api/exit

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, apikey');

  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method Not Allowed' });

  const { plateNumber } = req.body || {};
  if (!plateNumber || !plateNumber.trim()) {
    return res.status(400).json({ success: false, error: 'Number plate is required.' });
  }

  const cleanPlate = plateNumber.trim().toUpperCase();
  const supabaseUrl = process.env.SUPABASE_URL;
  const supabaseKey = process.env.SUPABASE_KEY;

  if (supabaseUrl && supabaseKey) {
    try {
      const base = supabaseUrl.replace(/\/+$/, '');
      const headers = {
        apikey: supabaseKey,
        Authorization: `Bearer ${supabaseKey}`,
        'Content-Type': 'application/json',
        Prefer: 'return=representation'
      };

      // 1. Locate occupied slot
      const slotRes = await fetch(`${base}/rest/v1/parking_slots?plate_number=eq.${encodeURIComponent(cleanPlate)}&is_occupied=eq.true`, { headers });
      const slotData = await slotRes.json();

      if (!Array.isArray(slotData) || slotData.length === 0) {
        return res.status(400).json({ success: false, error: `No active vehicle found with plate ${cleanPlate}.` });
      }

      const slot = slotData[0];
      const entryTime = slot.entered_at ? new Date(slot.entered_at) : new Date(Date.now() - 30 * 60000);
      const exitTime = new Date();
      const durationMinutes = Math.max(1, Math.round((exitTime - entryTime) / 60000));

      const is7 = (slot.vehicle_type || '').toLowerCase() === '7-seater';
      const baseFee = is7 ? 5.0 : 3.0;
      const hourly = is7 ? 3.5 : 2.0;
      const hours = Math.max(1, Math.ceil(durationMinutes / 60));
      const feeCharged = baseFee + (hours * hourly);

      // 2. Release slot
      await fetch(`${base}/rest/v1/parking_slots?slot_id=eq.${slot.slot_id}`, {
        method: 'PATCH',
        headers,
        body: JSON.stringify({
          is_occupied: false,
          plate_number: null,
          vehicle_type: null,
          entered_at: null
        })
      });

      // 3. Update log
      await fetch(`${base}/rest/v1/parking_logs?plate_number=eq.${encodeURIComponent(cleanPlate)}&exited_at=is.null`, {
        method: 'PATCH',
        headers,
        body: JSON.stringify({
          exited_at: exitTime.toISOString(),
          duration_minutes: durationMinutes,
          fee_charged: feeCharged
        })
      });

      return res.status(200).json({
        success: true,
        message: 'Vehicle checkout completed!',
        plateNumber: cleanPlate,
        vehicleType: slot.vehicle_type || '5-seater',
        slotId: slot.slot_id,
        floorLevel: slot.floor_level,
        enteredAt: entryTime.toISOString(),
        exitedAt: exitTime.toISOString(),
        durationMinutes,
        feeCharged
      });
    } catch (e) {
      console.warn('Supabase exit error', e);
    }
  }

  // Fallback demo response
  return res.status(200).json({
    success: true,
    message: 'Vehicle checkout completed (Vercel demo mode)!',
    plateNumber: cleanPlate,
    vehicleType: '5-seater',
    slotId: 'B1-02',
    floorLevel: 'Level B1',
    enteredAt: new Date(Date.now() - 35 * 60000).toISOString(),
    exitedAt: new Date().toISOString(),
    durationMinutes: 35,
    feeCharged: 5.0
  });
}
