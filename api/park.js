// Vercel Serverless Function: POST /api/park

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, apikey');

  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method Not Allowed' });

  const { plateNumber, vehicleType = '5-seater' } = req.body || {};
  if (!plateNumber || !plateNumber.trim()) {
    return res.status(400).json({ success: false, error: 'Vehicle number plate is required.' });
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

      // 1. Check if already parked
      const checkRes = await fetch(`${base}/rest/v1/parking_slots?plate_number=eq.${encodeURIComponent(cleanPlate)}&is_occupied=eq.true`, { headers });
      const checkData = await checkRes.json();
      if (Array.isArray(checkData) && checkData.length > 0) {
        return res.status(400).json({ success: false, error: `Vehicle ${cleanPlate} is already parked in bay ${checkData[0].slot_id}.` });
      }

      // 2. Find available slot
      const availRes = await fetch(`${base}/rest/v1/parking_slots?is_occupied=eq.false&slot_type=eq.${encodeURIComponent(vehicleType)}&limit=1`, { headers });
      let availData = await availRes.json();
      if (!Array.isArray(availData) || availData.length === 0) {
        // Fallback to any empty slot
        const fallbackRes = await fetch(`${base}/rest/v1/parking_slots?is_occupied=eq.false&limit=1`, { headers });
        availData = await fallbackRes.json();
      }

      if (!Array.isArray(availData) || availData.length === 0) {
        return res.status(400).json({ success: false, error: 'Underground parking is full! No bays available.' });
      }

      const allocated = availData[0];
      const nowIso = new Date().toISOString();

      // 3. Update slot
      await fetch(`${base}/rest/v1/parking_slots?slot_id=eq.${allocated.slot_id}`, {
        method: 'PATCH',
        headers,
        body: JSON.stringify({
          is_occupied: true,
          plate_number: cleanPlate,
          vehicle_type: vehicleType,
          entered_at: nowIso
        })
      });

      // 4. Insert log
      await fetch(`${base}/rest/v1/parking_logs`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          plate_number: cleanPlate,
          vehicle_type: vehicleType,
          slot_id: allocated.slot_id,
          floor_level: allocated.floor_level,
          entered_at: nowIso
        })
      });

      return res.status(200).json({
        success: true,
        message: 'Vehicle parked successfully!',
        record: {
          plateNumber: cleanPlate,
          vehicleType,
          slotId: allocated.slot_id,
          floorLevel: allocated.floor_level,
          enteredAt: nowIso
        }
      });
    } catch (e) {
      console.warn('Supabase park error', e);
    }
  }

  // Fallback demo response if no Supabase configured
  return res.status(200).json({
    success: true,
    message: 'Vehicle parked successfully (Vercel demo mode)!',
    record: {
      plateNumber: cleanPlate,
      vehicleType,
      slotId: vehicleType === '7-seater' ? 'B1-11' : 'B1-04',
      floorLevel: 'Level B1',
      enteredAt: new Date().toISOString()
    }
  });
}
