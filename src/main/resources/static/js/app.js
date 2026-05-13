/* ═══════════════════════════════════════════════════════
   PatchOrchestrator — app.js
   Live clock, sidebar toggle, chart init, job polling,
   toast notifications, copy-to-clipboard helpers
   ═════════════════════════════════════════════════════ */

/* ── Live Clock ─────────────────────────────────────── */
function updateClock() {
    const el = document.getElementById('topbarTime');
    if (!el) return;
    const now = new Date();
    el.textContent = now.toLocaleString('en-IN', {
        weekday: 'short', day: '2-digit', month: 'short',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
        hour12: false
    });
}
setInterval(updateClock, 1000);
updateClock();

/* ── Sidebar Toggle ─────────────────────────────────── */
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    if (!sidebar) return;
    if (window.innerWidth <= 768) {
        sidebar.classList.toggle('open');
    } else {
        sidebar.classList.toggle('collapsed');
        document.querySelector('.main-content').style.marginLeft =
            sidebar.classList.contains('collapsed') ? '0' : '';
    }
}

/* ── Dashboard Charts ───────────────────────────────── */
function initDashboardCharts(osData, jobData) {
    const chartDefaults = {
        plugins: {
            legend: { display: false },
            tooltip: {
                backgroundColor: '#1e293b',
                borderColor: 'rgba(255,255,255,0.1)',
                borderWidth: 1,
                titleColor: '#f1f5f9',
                bodyColor: '#94a3b8',
                padding: 10,
                cornerRadius: 8
            }
        }
    };

    // OS Chart
    const osCtx = document.getElementById('osChart');
    if (osCtx) {
        new Chart(osCtx, {
            type: 'doughnut',
            data: {
                labels: ['Windows', 'Linux'],
                datasets: [{
                    data: [osData.windows, osData.linux],
                    backgroundColor: ['#6366f1', '#22d3ee'],
                    borderColor: '#1e293b',
                    borderWidth: 3,
                    hoverOffset: 6
                }]
            },
            options: {
                ...chartDefaults,
                cutout: '72%',
                responsive: true,
                maintainAspectRatio: true
            }
        });
    }

    // Job Status Chart
    const jobCtx = document.getElementById('jobChart');
    if (jobCtx) {
        new Chart(jobCtx, {
            type: 'doughnut',
            data: {
                labels: ['Completed', 'Pending', 'Failed', 'In Progress', 'Cancelled'],
                datasets: [{
                    data: [jobData.completed, jobData.pending, jobData.failed,
                           jobData.inProgress, jobData.cancelled],
                    backgroundColor: ['#22c55e', '#f59e0b', '#ef4444', '#6366f1', '#64748b'],
                    borderColor: '#1e293b',
                    borderWidth: 3,
                    hoverOffset: 6
                }]
            },
            options: {
                ...chartDefaults,
                cutout: '72%',
                responsive: true,
                maintainAspectRatio: true
            }
        });
    }
}

/* ── Job Status + Live Log Polling ──────────────────── */
let pollingInterval = null;

function startJobPolling(jobId) {
    if (pollingInterval) clearInterval(pollingInterval);

    const logEl       = document.getElementById('logContent');
    const masterLog   = document.getElementById('masterLog');
    const spinner     = document.getElementById('logSpinner');
    const notice      = document.getElementById('pollingNotice');
    const statusBadge = document.getElementById('statusBadge');
    const liveIndicator = document.getElementById('liveIndicator');
    const tsEl        = document.getElementById('logTimestamp');

    pollingInterval = setInterval(async () => {
        try {
            // Fetch both status and log content in one call
            const res = await fetch('/api/v1/jobs/' + jobId + '/logs');
            if (!res.ok) return;
            const data = await res.json();

            // Update live log content
            if (data.masterLog && logEl) {
                const wasAtBottom = masterLog
                    ? (masterLog.scrollHeight - masterLog.scrollTop - masterLog.clientHeight) < 60
                    : true;

                logEl.textContent = data.masterLog;

                // Hide spinner and placeholder once we have log content
                if (spinner) spinner.style.display = 'none';
                const placeholder = document.getElementById('logPlaceholder');
                if (placeholder) placeholder.style.display = 'none';

                // Auto-scroll only if user was already near the bottom
                if (wasAtBottom && masterLog) {
                    masterLog.scrollTop = masterLog.scrollHeight;
                }

                // Update timestamp
                if (tsEl) tsEl.textContent = 'Updated ' + new Date().toLocaleTimeString();
            }

            // Update status badge
            if (statusBadge && data.status) {
                const cls = 'status-' + data.status.toLowerCase().replace(/_/g, '-');
                statusBadge.className = 'status-badge status-badge-lg ' + cls;
                statusBadge.textContent = data.status;
            }

            // Job finished
            if (data.status !== 'IN_PROGRESS') {
                clearInterval(pollingInterval);

                // Hide LIVE indicator + spinning notice
                if (liveIndicator) liveIndicator.remove();
                if (notice) {
                    const icon = data.status === 'COMPLETED' ? 'fa-circle-check' : 'fa-circle-xmark';
                    const color = data.status === 'COMPLETED' ? 'success' : 'danger';
                    notice.className = 'alert alert-' + color + ' mt-3';
                    notice.innerHTML = '<i class="fas ' + icon + ' me-2"></i>Job ' + data.status + '. Reloading in 2s…';
                }

                // Reload after short delay to get final server-rendered state
                setTimeout(() => window.location.reload(), 2000);
            }
        } catch (e) {
            console.warn('Polling error:', e);
        }
    }, 2000); // Poll every 2 seconds for near-real-time logs
}


/* ── Toast Notifications ────────────────────────────── */
function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = 'alert alert-' + type;
    toast.style.cssText = 'position:fixed;bottom:24px;right:24px;z-index:9999;min-width:280px;max-width:400px;animation:slideInRight 0.3s ease';
    toast.innerHTML = `<i class="fas fa-circle-check me-2"></i>${message}
        <button class="alert-close" onclick="this.parentElement.remove()"><i class="fas fa-xmark"></i></button>`;
    document.body.appendChild(toast);
    setTimeout(() => { if (toast.parentElement) toast.remove(); }, 4000);
}

/* ── Auto-dismiss alerts ────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.alert').forEach(alert => {
        setTimeout(() => {
            if (alert.parentElement) {
                alert.style.opacity = '0';
                alert.style.transition = 'opacity 0.5s';
                setTimeout(() => alert.remove(), 500);
            }
        }, 5000);
    });
});

/* ── Confirm dangerous actions ──────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-confirm]').forEach(el => {
        el.addEventListener('click', e => {
            if (!confirm(el.dataset.confirm)) e.preventDefault();
        });
    });
});

/* ── Copy log to clipboard ──────────────────────────── */
function copyLog() {
    const el = document.getElementById('logContent');
    if (!el) return;
    navigator.clipboard.writeText(el.textContent)
        .then(() => showToast('Log copied to clipboard!'))
        .catch(() => showToast('Could not copy log.', 'danger'));
}

/* ── Style injection for animations ────────────────── */
const style = document.createElement('style');
style.textContent = `
@keyframes slideInRight {
  from { transform: translateX(100%); opacity: 0; }
  to   { transform: translateX(0);   opacity: 1; }
}
`;
document.head.appendChild(style);
