// Vercel Serverless Function: GET /api/slots
// Syncs with Supabase if configured, otherwise returns default underground layout

const defaultSlots = [
  ...Array.from({ length: 8 }, (_, i) => ({
    slotId: `B1-${String(i + 1).padStart(2, '0')}`,
    floorLevel: 'Level B1',
    slotType: '5-seater',
    isOccupied: i === 1,
    plateNumber: i === 1 ? 'KA 03 MG 8821' : null,
    vehicleType: i === 1 ? '5-seater' : null,
    enteredAt: i === 1 ? new Date(Date.now() - 42 * 60000).toISOString() : null
  })),
  ...Array.from({ length: 6 }, (_, i) => ({
    slotId: `B1-${String(i + 9).padStart(2, '0')}`,
    floorLevel: 'Level B1',
    slotType: '7-seater',
    isOccupied: i === 1,
    plateNumber: i === 1 ? 'MH 12 QP 5519' : null,
    vehicleType: i === 1 ? '7-seater' : null,
    enteredAt: i === 1 ? new Date(Date.now() - 115 * 60000).toISOString() : null
  })),
  ...Array.from({ length: 8 }, (_, i) => ({
    slotId: `B2-${String(i + 1).padStart(2, '0')}`,
    floorLevel: 'Level B2',
    slotType: '5-seater',
    isOccupied: i === 3,
    plateNumber: i === 3 ? 'DL 08 CA 3302' : null,
    vehicleType: i === 3 ? '5-seater' : null,
    enteredAt: i === 3 ? new Date(Date.now() - 25 * 60000).toISOString() : null
  })),
  ...Array.from({ length: 6 }, (_, i) => ({
    slotId: `B2-${String(i + 9).padStart(2, '0')}`,
    floorLevel: 'Level B2',
    slotType: '7-seater',
    isOccupied: i === 3,
    plateNumber: i === 3 ? 'TS 09 EU 9940' : null,
    vehicleType: i === 3 ? '7-seater' : null,
    enteredAt: i === 3 ? new Date(Date.now() - 68 * 60000).toISOString() : null
  }))
];

let memorySlots = [...defaultSlots];

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, apikey');

  if (req.method === 'OPTIONS') return res.status(204).end();

  const supabaseUrl = process.env.SUPABASE_URL;
  const supabaseKey = process.env.SUPABASE_KEY;

  if (supabaseUrl && supabaseKey) {
    try {
      const response = await fetch(`${supabaseUrl.replace(/\/+$/, '')}/rest/v1/parking_slots?select=*&order=slot_id.asc`, {
        headers: {
          apikey: supabaseKey,
          Authorization: `Bearer ${supabaseKey}`
        }
      });
      if (response.ok) {
        const data = await response.json();
        if (Array.isArray(data) && data.length > 0) {
          const mapped = data.map(d => ({
            slotId: d.slot_id,
            floorLevel: d.floor_level,
            slotType: d.slot_type,
            isOccupied: !!d.is_occupied,
            plateNumber: d.plate_number,
            vehicleType: d.vehicle_type,
            enteredAt: d.entered_at
          }));
          return res.status(200).json(mapped);
        }
      }
    } catch (e) {
      console.warn('Supabase fetch error, fallback to memory', e);
    }
  }

  return res.status(200).json(memorySlots);
}
export { memorySlots };
