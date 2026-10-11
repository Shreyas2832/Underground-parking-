// Vercel Serverless Function: GET /api/active

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, apikey');

  if (req.method === 'OPTIONS') return res.status(204).end();

  const supabaseUrl = process.env.SUPABASE_URL;
  const supabaseKey = process.env.SUPABASE_KEY;

  if (supabaseUrl && supabaseKey) {
    try {
      const response = await fetch(`${supabaseUrl.replace(/\/+$/, '')}/rest/v1/parking_slots?is_occupied=eq.true&order=entered_at.desc`, {
        headers: {
          apikey: supabaseKey,
          Authorization: `Bearer ${supabaseKey}`
        }
      });
      if (response.ok) {
        const data = await response.json();
        const mapped = data.map(d => ({
          slotId: d.slot_id,
          floorLevel: d.floor_level,
          slotType: d.slot_type,
          isOccupied: true,
          plateNumber: d.plate_number,
          vehicleType: d.vehicle_type,
          enteredAt: d.entered_at
        }));
        return res.status(200).json(mapped);
      }
    } catch (e) {
      console.warn('Supabase fetch active error', e);
    }
  }

  // Demo fallback
  return res.status(200).json([
    {
      slotId: 'B1-02',
      floorLevel: 'Level B1',
      slotType: '5-seater',
      isOccupied: true,
      plateNumber: 'KA 03 MG 8821',
      vehicleType: '5-seater',
      enteredAt: new Date(Date.now() - 42 * 60000).toISOString()
    },
    {
      slotId: 'B1-10',
      floorLevel: 'Level B1',
      slotType: '7-seater',
      isOccupied: true,
      plateNumber: 'MH 12 QP 5519',
      vehicleType: '7-seater',
      enteredAt: new Date(Date.now() - 115 * 60000).toISOString()
    }
  ]);
}
