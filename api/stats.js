// Vercel Serverless Function: GET /api/stats

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, apikey');

  if (req.method === 'OPTIONS') return res.status(204).end();

  const supabaseUrl = process.env.SUPABASE_URL;
  const supabaseKey = process.env.SUPABASE_KEY;

  if (supabaseUrl && supabaseKey) {
    try {
      const response = await fetch(`${supabaseUrl.replace(/\/+$/, '')}/rest/v1/parking_slots?select=*`, {
        headers: {
          apikey: supabaseKey,
          Authorization: `Bearer ${supabaseKey}`
        }
      });
      if (response.ok) {
        const slots = await response.json();
        let total = slots.length;
        let occupied = 0;
        let fiveTotal = 0;
        let fiveOcc = 0;
        let sevenTotal = 0;
        let sevenOcc = 0;

        for (const s of slots) {
          if ((s.slot_type || '').toLowerCase() === '5-seater') {
            fiveTotal++;
            if (s.is_occupied) { fiveOcc++; occupied++; }
          } else {
            sevenTotal++;
            if (s.is_occupied) { sevenOcc++; occupied++; }
          }
        }

        return res.status(200).json({
          totalSlots: total,
          availableSlots: total - occupied,
          occupiedSlots: occupied,
          fiveSeaterTotal: fiveTotal,
          fiveSeaterOccupied: fiveOcc,
          sevenSeaterTotal: sevenTotal,
          sevenSeaterOccupied: sevenOcc,
          occupancyRate: total > 0 ? Math.round((occupied * 100) / total) : 0
        });
      }
    } catch (e) {
      console.warn('Supabase fetch stats error', e);
    }
  }

  return res.status(200).json({
    totalSlots: 28,
    availableSlots: 24,
    occupiedSlots: 4,
    fiveSeaterTotal: 16,
    fiveSeaterOccupied: 2,
    sevenSeaterTotal: 12,
    sevenSeaterOccupied: 2,
    occupancyRate: 14
  });
}
