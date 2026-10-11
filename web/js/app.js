/**
 * AURA SUB-LEVELS • UNDERGROUND PARKING CONTROLLER
 * Pure Java 25 Backend Integration + Supabase Client Telemetry
 */

// Application State
let currentFloorFilter = 'ALL';
let allSlots = [];
let activeVehicles = [];
let parkingStats = {};
let supabaseStatus = {};
let recentEnteredPlate = null;

// Audio synthesis for realistic gate feedback
const audioCtx = (typeof AudioContext !== 'undefined' || typeof webkitAudioContext !== 'undefined')
    ? new (window.AudioContext || window.webkitAudioContext)()
    : null;

function playBeep(freq = 600, duration = 0.15, type = 'sine') {
    if (!audioCtx) return;
    try {
        if (audioCtx.state === 'suspended') audioCtx.resume();
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = type;
        osc.frequency.setValueAtTime(freq, audioCtx.currentTime);
        gain.gain.setValueAtTime(0.12, audioCtx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + duration);
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        osc.start();
        osc.stop(audioCtx.currentTime + duration);
    } catch (e) {
        // Audio optional
    }
}

function playSuccessChime() {
    playBeep(523.25, 0.12);
    setTimeout(() => playBeep(659.25, 0.12), 100);
    setTimeout(() => playBeep(783.99, 0.25), 200);
}

function playExitChime() {
    playBeep(440, 0.12);
    setTimeout(() => playBeep(349.23, 0.2), 120);
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

async function initApp() {
    startLiveClock();
    await fetchSupabaseStatus();
    await fetchSchemaText();
    await refreshData();

    // Auto-poll telemetry every 3.5 seconds
    setInterval(refreshData, 3500);

    // Live duration counter updater every 1 second
    setInterval(updateDurationCounters, 1000);
}

function startLiveClock() {
    const clockEl = document.getElementById('liveClock');
    function update() {
        const now = new Date();
        clockEl.textContent = now.toLocaleTimeString('en-US', { hour12: false });
    }
    update();
    setInterval(update, 1000);
}

// Fetch all parking data from Java backend
async function refreshData() {
    try {
        const [slotsRes, activeRes, statsRes] = await Promise.all([
            fetch('/api/slots'),
            fetch('/api/active'),
            fetch('/api/stats')
        ]);

        if (slotsRes.ok) {
            allSlots = await slotsRes.json();
            renderSlots();
        }

        if (activeRes.ok) {
            activeVehicles = await activeRes.json();
            renderActiveVehicles();
            renderParkedPlatesSelector();
        }

        if (statsRes.ok) {
            parkingStats = await statsRes.json();
            renderStats();
        }
    } catch (err) {
        console.warn('[Sync Error]', err);
    }
}

// Render Parking Slots Grid
function renderSlots() {
    const grid = document.getElementById('slotsGrid');
    if (!grid) return;

    let filtered = allSlots;
    if (currentFloorFilter !== 'ALL') {
        filtered = allSlots.filter(s => s.floorLevel === currentFloorFilter);
    }

    if (filtered.length === 0) {
        grid.innerHTML = '<div class="grid-loading">No slots configured for this level.</div>';
        return;
    }

    grid.innerHTML = filtered.map(slot => {
        const isOccupied = slot.isOccupied;
        const is5Seater = slot.slotType === '5-seater';
        const typeBadge = is5Seater 
            ? '<span class="slot-type-badge five">5S</span>' 
            : '<span class="slot-type-badge seven">7S</span>';
        
        const isRecent = recentEnteredPlate && slot.plateNumber === recentEnteredPlate;
        const occupiedClass = isOccupied ? 'occupied' : 'vacant';
        const recentClass = isRecent ? 'highlight-entered' : '';

        const carIconSvg = isOccupied ? `
            <svg class="slot-car-icon" width="46" height="46" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M19 17h2c.6 0 1-.4 1-1v-4c0-1.1-.9-2-2-2h-1l-2-4H5L3 10H2c-.6 0-1 .4-1 1v5c0 .6.4 1 1 1h2"/>
                <circle cx="7" cy="17" r="2"/>
                <path d="M9 17h6"/>
                <circle cx="17" cy="17" r="2"/>
                <path d="M5 10l1.5-3h8.5l1.5 3"/>
            </svg>
        ` : `
            <div class="slot-empty-outline">EMPTY</div>
        `;

        const plateDisplay = isOccupied ? `
            <div class="slot-plate-tag" title="${escapeHtml(slot.plateNumber)}">${escapeHtml(slot.plateNumber)}</div>
        ` : `
            <div class="slot-status-text vacant">AVAILABLE</div>
        `;

        return `
            <div class="slot-bay ${occupiedClass} ${recentClass}" 
                 onclick="handleSlotClick('${slot.slotId}', ${isOccupied}, '${escapeHtml(slot.plateNumber || '')}')"
                 title="Bay ${slot.slotId} • ${slot.floorLevel} • ${slot.slotType}">
                
                <div class="slot-sensor"></div>
                
                <div class="slot-top">
                    <span class="slot-id">${slot.slotId}</span>
                    ${typeBadge}
                </div>

                <div class="slot-floor">${slot.floorLevel}</div>

                <div class="slot-body">
                    ${carIconSvg}
                </div>

                <div class="slot-bottom">
                    ${plateDisplay}
                </div>
            </div>
        `;
    }).join('');
}

// Render Left-Hand Side "VEHICLES ENTERED IN UNDERGROUND PARKING" list
function renderActiveVehicles() {
    const list = document.getElementById('activeVehiclesList');
    const badge = document.getElementById('activeCountBadge');
    if (!list) return;

    badge.textContent = `${activeVehicles.length} Inside`;

    if (activeVehicles.length === 0) {
        list.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 2rem; color: var(--text-muted); font-size: 0.85rem;">
                No vehicles currently logged inside subterranean bays.
                <br><span style="color: var(--accent-green); cursor: pointer;" onclick="switchTerminalTab('enter')">Register an incoming vehicle on the right →</span>
            </div>
        `;
        return;
    }

    list.innerHTML = activeVehicles.map(v => {
        const isRecent = recentEnteredPlate && v.plateNumber === recentEnteredPlate;
        const recentClass = isRecent ? 'just-entered' : '';
        const is5Seater = v.vehicleType === '5-seater';
        const typeClass = is5Seater ? 'five' : 'seven';
        const enteredDate = v.enteredAt ? new Date(v.enteredAt) : new Date();
        const formattedTime = enteredDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

        return `
            <div class="vehicle-card ${recentClass}" data-entered-at="${v.enteredAt || ''}">
                <div class="card-top">
                    <div class="plate-badge-embossed">
                        <span class="plate-flag">IND</span>
                        <span>${escapeHtml(v.plateNumber)}</span>
                    </div>
                    <span class="seater-pill ${typeClass}">${v.vehicleType ? v.vehicleType.toUpperCase() : 'CAR'}</span>
                </div>

                <div class="card-mid">
                    <div class="bay-info">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <rect x="3" y="3" width="18" height="18" rx="2"/>
                            <path d="M9 17V7h4a3 3 0 0 1 0 6H9"/>
                        </svg>
                        <span>Bay ${v.slotId} (${v.floorLevel})</span>
                    </div>

                    <div class="duration-counter">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <circle cx="12" cy="12" r="10"/>
                            <polyline points="12 6 12 12 16 14"/>
                        </svg>
                        <span class="live-duration" data-time="${v.enteredAt}">${calcDuration(v.enteredAt)}</span>
                    </div>
                </div>

                <div class="card-bottom">
                    <span class="entry-time-stamp">Entered at: ${formattedTime}</span>
                    <button class="quick-exit-btn" onclick="quickExitPlate('${escapeHtml(v.plateNumber)}')">
                        Exit Vehicle ↗
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

// Calculates elapsed time string like "0m 32s" or "1h 14m"
function calcDuration(isoString) {
    if (!isoString) return 'Just now';
    const entered = new Date(isoString);
    const now = new Date();
    const diffSec = Math.floor((now - entered) / 1000);
    if (diffSec < 0) return '0s';

    const hours = Math.floor(diffSec / 3600);
    const mins = Math.floor((diffSec % 3600) / 60);
    const secs = diffSec % 60;

    if (hours > 0) {
        return `${hours}h ${mins}m`;
    }
    return `${mins}m ${secs}s`;
}

function updateDurationCounters() {
    const els = document.querySelectorAll('.live-duration');
    els.forEach(el => {
        const time = el.getAttribute('data-time');
        if (time) {
            el.textContent = calcDuration(time);
        }
    });
}

// Render Stats in Header
function renderStats() {
    document.getElementById('statAvailable').textContent = parkingStats.availableSlots ?? '--';
    document.getElementById('statOccupied').textContent = parkingStats.occupiedSlots ?? '--';
    
    document.getElementById('stat5Seater').textContent = 
        `${parkingStats.fiveSeaterOccupied ?? 0}/${parkingStats.fiveSeaterTotal ?? 0}`;
    
    document.getElementById('stat7Seater').textContent = 
        `${parkingStats.sevenSeaterOccupied ?? 0}/${parkingStats.sevenSeaterTotal ?? 0}`;
}

// Quick selector for exit tab
function renderParkedPlatesSelector() {
    const container = document.getElementById('parkedPlatesChips');
    if (!container) return;

    if (activeVehicles.length === 0) {
        container.innerHTML = '<span class="muted-note">No vehicles currently inside</span>';
        return;
    }

    container.innerHTML = activeVehicles.map(v => `
        <button type="button" class="parked-chip" onclick="fillExitPlate('${escapeHtml(v.plateNumber)}')">
            ${escapeHtml(v.plateNumber)} (${v.slotId})
        </button>
    `).join('');
}

// Filter floor
function filterFloor(floor) {
    currentFloorFilter = floor;
    document.querySelectorAll('.level-tab').forEach(t => {
        if (t.getAttribute('data-floor') === floor) {
            t.classList.add('active');
        } else {
            t.classList.remove('active');
        }
    });
    renderSlots();
}

// Switch Right-Side Terminal Tab
function switchTerminalTab(tab) {
    const enterBtn = document.getElementById('tabEnterBtn');
    const exitBtn = document.getElementById('tabExitBtn');
    const enterSec = document.getElementById('enterSection');
    const exitSec = document.getElementById('exitSection');

    if (tab === 'enter') {
        enterBtn.classList.add('active');
        exitBtn.classList.remove('active');
        enterSec.style.display = 'block';
        exitSec.style.display = 'none';
        document.getElementById('plateNumberInput').focus();
    } else {
        exitBtn.classList.add('active');
        enterBtn.classList.remove('active');
        exitSec.style.display = 'block';
        enterSec.style.display = 'none';
        document.getElementById('exitPlateInput').focus();
    }
}

// Slot click helper
function handleSlotClick(slotId, isOccupied, plate) {
    if (isOccupied) {
        switchTerminalTab('exit');
        fillExitPlate(plate);
        showToast(`Selected parked car ${plate} in bay ${slotId} for checkout.`, 'info');
    } else {
        switchTerminalTab('enter');
        showToast(`Selected available bay ${slotId}. Enter vehicle details.`, 'info');
    }
}

// Quick plate filler
function fillPlate(val) {
    const input = document.getElementById('plateNumberInput');
    input.value = val;
    input.focus();
}

function fillExitPlate(val) {
    const input = document.getElementById('exitPlateInput');
    input.value = val;
    input.focus();
}

function quickExitPlate(plate) {
    switchTerminalTab('exit');
    fillExitPlate(plate);
}

function updateVehicleIcon(type) {
    // Optional micro-animation
}

// ================= VEHICLE ENTRY HANDLER (LOGIN) =================
async function handleVehicleEntry(e) {
    e.preventDefault();
    const btn = document.getElementById('loginBtn');
    const plateInput = document.getElementById('plateNumberInput');
    const plateNumber = plateInput.value.trim().toUpperCase();
    
    const seaterRadio = document.querySelector('input[name="seaterType"]:checked');
    const vehicleType = seaterRadio ? seaterRadio.value : '5-seater';

    if (!plateNumber) {
        showToast('Please enter vehicle number plate.', 'danger');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<span>PARKING IN SUB-LEVELS...</span>';

    try {
        const res = await fetch('/api/park', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ plateNumber, vehicleType })
        });

        const data = await res.json();

        if (data.success) {
            playSuccessChime();
            recentEnteredPlate = plateNumber;
            showToast(`Vehicle ${plateNumber} entered! Bay ${data.record.slotId} allocated.`, 'success');
            
            // Show Entry Ticket Modal
            showEntryTicketModal(data.record);

            // Clear input
            plateInput.value = '';

            // Immediate refresh
            await refreshData();

            // Clear highlight pulse after 10s
            setTimeout(() => {
                if (recentEnteredPlate === plateNumber) {
                    recentEnteredPlate = null;
                    renderSlots();
                    renderActiveVehicles();
                }
            }, 10000);

        } else {
            showToast(data.error || 'Failed to enter vehicle.', 'danger');
            playBeep(220, 0.3, 'sawtooth');
        }
    } catch (err) {
        showToast('Server communication error: ' + err.message, 'danger');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<span class="btn-icon">⚡</span><span class="btn-text">LOGIN & PARK VEHICLE</span>';
    }
}

// Show Digital Entry Ticket Modal
function showEntryTicketModal(record) {
    const modal = document.getElementById('receiptModal');
    const content = document.getElementById('modalContent');
    const enteredTime = new Date(record.enteredAt).toLocaleString();

    content.innerHTML = `
        <div class="receipt-header">
            <div class="receipt-icon-circle success">✓</div>
            <h3 class="receipt-title">UNDERGROUND PARKING TICKET</h3>
            <p class="receipt-sub">Vehicle Checked In & Bay Allocated Successfully</p>
        </div>

        <div class="ticket-container">
            <div class="ticket-row">
                <span class="ticket-k">Vehicle License Plate</span>
                <span class="ticket-v highlight">${escapeHtml(record.plateNumber)}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Vehicle Capacity</span>
                <span class="ticket-v">${record.vehicleType.toUpperCase()}</span>
            </div>
            <div class="ticket-divider"></div>
            <div class="ticket-row">
                <span class="ticket-k">Allocated Sub-Bay</span>
                <span class="ticket-v highlight">BAY ${record.slotId}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Deck Level</span>
                <span class="ticket-v">${record.floorLevel}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Entry Timestamp</span>
                <span class="ticket-v">${enteredTime}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Supabase Sync</span>
                <span class="ticket-v" style="color: var(--supabase-color);">RECORDED</span>
            </div>
        </div>

        <button class="btn-primary" onclick="closeReceiptModal()">
            <span>Done & View on Parking Map</span>
        </button>
    `;

    modal.classList.add('open');
}

// ================= VEHICLE EXIT HANDLER =================
async function handleVehicleExit(e) {
    e.preventDefault();
    const btn = document.getElementById('exitBtn');
    const plateInput = document.getElementById('exitPlateInput');
    const plateNumber = plateInput.value.trim().toUpperCase();

    if (!plateNumber) {
        showToast('Please enter number plate for exit.', 'danger');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<span>PROCESSING CHECKOUT...</span>';

    try {
        const res = await fetch('/api/exit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ plateNumber })
        });

        const data = await res.json();

        if (data.success) {
            playExitChime();
            showToast(`Vehicle ${plateNumber} successfully exited from bay ${data.slotId}!`, 'success');
            
            // Show Exit Invoice Receipt
            showExitInvoiceModal(data);

            plateInput.value = '';
            await refreshData();
        } else {
            showToast(data.error || 'Failed to exit vehicle.', 'danger');
            playBeep(220, 0.3, 'sawtooth');
        }
    } catch (err) {
        showToast('Server communication error: ' + err.message, 'danger');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<span class="btn-icon">🏁</span><span class="btn-text">EXIT VEHICLE & PAY</span>';
    }
}

// Show Departure Invoice Modal
function showExitInvoiceModal(data) {
    const modal = document.getElementById('receiptModal');
    const content = document.getElementById('modalContent');
    const inTime = new Date(data.enteredAt).toLocaleTimeString();
    const outTime = new Date(data.exitedAt).toLocaleTimeString();

    content.innerHTML = `
        <div class="receipt-header">
            <div class="receipt-icon-circle exit">🏁</div>
            <h3 class="receipt-title">DEPARTURE RECEIPT & INVOICE</h3>
            <p class="receipt-sub">Underground Bay Released • Payment Settled</p>
        </div>

        <div class="ticket-container">
            <div class="ticket-row">
                <span class="ticket-k">Vehicle License Plate</span>
                <span class="ticket-v highlight">${escapeHtml(data.plateNumber)}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Category</span>
                <span class="ticket-v">${data.vehicleType ? data.vehicleType.toUpperCase() : 'CAR'}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Released Bay</span>
                <span class="ticket-v">${data.slotId} (${data.floorLevel})</span>
            </div>
            <div class="ticket-divider"></div>
            <div class="ticket-row">
                <span class="ticket-k">Entry Time</span>
                <span class="ticket-v">${inTime}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Exit Time</span>
                <span class="ticket-v">${outTime}</span>
            </div>
            <div class="ticket-row">
                <span class="ticket-k">Total Parked Duration</span>
                <span class="ticket-v">${data.durationMinutes} Minutes</span>
            </div>
            <div class="ticket-divider"></div>
            <div class="ticket-row">
                <span class="ticket-k" style="font-weight: 700;">TOTAL PARKING FEE</span>
                <span class="ticket-v amount">$${parseFloat(data.feeCharged).toFixed(2)}</span>
            </div>
        </div>

        <button class="btn-primary" onclick="closeReceiptModal()">
            <span>Complete & Release Barrier</span>
        </button>
    `;

    modal.classList.add('open');
}

function closeReceiptModal() {
    document.getElementById('receiptModal').classList.remove('open');
}

// ================= SUPABASE MODAL & CONFIG =================
async function fetchSupabaseStatus() {
    try {
        const res = await fetch('/api/supabase/status');
        if (res.ok) {
            supabaseStatus = await res.json();
            renderSupabaseIndicator();
        }
    } catch (e) {
        console.warn('Supabase status error', e);
    }
}

function renderSupabaseIndicator() {
    const dot = document.getElementById('supabaseDot');
    const text = document.getElementById('supabaseText');
    const modalBanner = document.getElementById('supabaseModalStatus');

    if (supabaseStatus.connected) {
        dot.className = 'status-indicator online';
        text.textContent = 'Supabase Connected';
        if (modalBanner) {
            modalBanner.className = 'supabase-status-banner connected';
            modalBanner.innerHTML = `
                <strong>🟢 Supabase PostgreSQL Connected</strong><br>
                Real-time synchronization active with: <code>${escapeHtml(supabaseStatus.url || '')}</code>
            `;
        }
    } else {
        dot.className = 'status-indicator offline';
        text.textContent = supabaseStatus.configured ? 'Supabase Sync Error' : 'Supabase Local Mode';
        if (modalBanner) {
            modalBanner.className = 'supabase-status-banner unconfigured';
            modalBanner.innerHTML = `
                <strong>🟡 Running on Java In-Memory Store</strong><br>
                ${escapeHtml(supabaseStatus.lastError || 'Provide Supabase Project URL and Anon API key below to link to your Supabase PostgreSQL database.')}
            `;
        }
    }
}

async function fetchSchemaText() {
    try {
        const res = await fetch('/api/schema');
        if (res.ok) {
            const sql = await res.text();
            document.getElementById('sqlSchemaText').textContent = sql;
        }
    } catch (e) {}
}

function openSupabaseModal() {
    fetchSupabaseStatus();
    const urlInput = document.getElementById('cfgSupabaseUrl');
    if (supabaseStatus.url) {
        urlInput.value = supabaseStatus.url;
    }
    document.getElementById('supabaseModal').classList.add('open');
}

function closeSupabaseModal() {
    document.getElementById('supabaseModal').classList.remove('open');
}

async function saveSupabaseConfig(e) {
    e.preventDefault();
    const url = document.getElementById('cfgSupabaseUrl').value.trim();
    const key = document.getElementById('cfgSupabaseKey').value.trim();

    try {
        const res = await fetch('/api/supabase/config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, key })
        });
        const data = await res.json();

        supabaseStatus = data.status || {};
        renderSupabaseIndicator();

        if (data.success) {
            showToast('Connected to Supabase PostgreSQL successfully!', 'success');
            playSuccessChime();
        } else {
            showToast('Credentials updated. Note: ' + (supabaseStatus.lastError || 'Check schema or connection.'), 'info');
        }
    } catch (err) {
        showToast('Error saving Supabase config: ' + err.message, 'danger');
    }
}

async function testSupabasePing() {
    showToast('Testing ping to Supabase...', 'info');
    await fetchSupabaseStatus();
    if (supabaseStatus.connected) {
        showToast('Supabase connection verified!', 'success');
        playSuccessChime();
    } else {
        showToast(supabaseStatus.lastError || 'Could not verify Supabase connection.', 'danger');
    }
}

function copySqlSchema() {
    const text = document.getElementById('sqlSchemaText').textContent;
    navigator.clipboard.writeText(text).then(() => {
        showToast('SQL schema copied to clipboard! Paste in Supabase SQL editor.', 'success');
    }).catch(() => {
        showToast('Could not copy automatically. Please select text manually.', 'info');
    });
}

// Reset Demo State
async function resetDemo() {
    if (!confirm('Reset underground parking to initial demo state?')) return;
    try {
        const res = await fetch('/api/reset', { method: 'POST' });
        if (res.ok) {
            showToast('Reset underground parking bays to demo state.', 'info');
            recentEnteredPlate = null;
            await refreshData();
        }
    } catch (e) {
        showToast('Failed to reset demo.', 'danger');
    }
}

// Toast notification helper
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'danger') icon = '⚠️';

    toast.innerHTML = `<span>${icon}</span><span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>"']/g, m => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[m]));
}
