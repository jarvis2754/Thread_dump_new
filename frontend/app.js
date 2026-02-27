const API_URL = '/api/analyze';

async function analyze() {
  const fileInput = document.getElementById('fileInput');
  const errorMsg  = document.getElementById('errorMsg');
  const btn       = document.getElementById('analyzeBtn');

  errorMsg.style.display = 'none';
  hide('summarySection');
  hide('controlsSection');
  hide('tableSection');

  if (!fileInput.files || !fileInput.files[0]) {
    showError('Please select a file.');
    return;
  }

  btn.disabled    = true;
  btn.textContent = 'Analyzing...';

  const formData = new FormData();
  formData.append('dumpfile', fileInput.files[0]);

  try {
    const res  = await fetch(API_URL, { method: 'POST', body: formData });
    const data = await res.json();

    if (!res.ok) {
      showError(data.error || 'Server returned an error.');
      return;
    }

    renderSummary(data);
    renderTable(data);

    show('summarySection');
    show('controlsSection');
    show('tableSection');

  } catch (err) {
    showError('Cannot reach the backend. Make sure the server is running on port 8080.');
  } finally {
    btn.disabled    = false;
    btn.textContent = 'Analyze';
  }
}

function applyFilters() {
  const search  = document.getElementById('searchInput').value.toLowerCase();
  const stateF  = document.getElementById('stateFilter').value;
  const healthF = document.getElementById('healthFilter').value;

  const rows = document.querySelectorAll('#mainTable tbody tr:not(.stack-row)');
  let visible = 0;

  rows.forEach(row => {
    const match = (!search  || (row.dataset.name   || '').includes(search))
               && (!stateF  || (row.dataset.state  || '') === stateF)
               && (!healthF || (row.dataset.health || '') === healthF);

    row.style.display = match ? '' : 'none';
    const next = row.nextElementSibling;
    if (next && next.classList.contains('stack-row'))
      next.style.display = match ? '' : 'none';
    if (match) visible++;
  });

  document.getElementById('emptyMsg').style.display = visible === 0 ? 'block' : 'none';
}

function renderSummary(threads) {
  let run = 0, block = 0, wait = 0, timed = 0, hot = 0, daemon = 0;
  threads.forEach(t => {
    if (t.state === 'RUNNABLE')      run++;
    if (t.state === 'BLOCKED')       block++;
    if (t.state === 'WAITING')       wait++;
    if (t.state === 'TIMED_WAITING') timed++;
    if (t.health === 'HOT')          hot++;
    if (t.daemon)                    daemon++;
  });
  document.getElementById('sTotal').textContent  = threads.length;
  document.getElementById('sRun').textContent    = run;
  document.getElementById('sBlock').textContent  = block;
  document.getElementById('sWait').textContent   = wait;
  document.getElementById('sTimed').textContent  = timed;
  document.getElementById('sHot').textContent    = hot;
  document.getElementById('sDaemon').textContent = daemon;
}

function renderTable(threads) {
  const tbody = document.getElementById('tableBody');
  tbody.innerHTML = '';

  threads.forEach((t, idx) => {
    const stackId = 'stack_' + idx;

    // ── Format each field, show blank when value is absent (null / -1) ───────
    const cpuFmt    = t.cpuMs     != null ? fmt(t.cpuMs)     : '—';
    const elFmt     = t.elapsedMs != null ? fmt(t.elapsedMs) : '—';

    // CPU% — only show if backend sent a real value (not null)
    const pctFmt    = t.cpuPercent != null ? t.cpuPercent.toFixed(1) + '%' : '';
    const pctCls    = t.cpuPercent != null
                        ? (t.cpuPercent > 50 ? 'red' : t.cpuPercent > 10 ? 'yellow' : 'muted')
                        : '';

    const threadNum = t.threadNum >= 0  ? '#' + t.threadNum : '—';
    const jvmPrio   = t.priority  >= 0  ? t.priority        : '—';
    const osPrio    = t.osPriority >= 0 ? t.osPriority      : '—';
    const tid       = t.tid        || '—';
    const nid       = t.nid        || '—';
    const nidDec    = t.nidDecimal || '—';
    const detail    = t.stateDetail || '';
    const lock      = t.lockInfo   || '';

    const tr = document.createElement('tr');
    tr.dataset.name   = t.name.toLowerCase();
    tr.dataset.state  = t.state;
    tr.dataset.health = t.health;

    tr.innerHTML = `
      <td class="muted">${idx + 1}</td>
      <td><div class="thread-name" title="${esc(t.name)}">${esc(t.name)}</div></td>
      <td class="muted">${threadNum}</td>
      <td><span class="badge badge-${t.state}">${t.state}</span></td>
      <td class="muted small">${esc(detail)}</td>
      <td><span class="health health-${t.health}">${t.health}</span></td>
      <td class="${t.daemon ? 'muted' : 'yellow'}">${t.daemon ? 'yes' : 'no'}</td>
      <td class="muted">${jvmPrio}</td>
      <td class="muted">${osPrio}</td>
      <td class="mono-sm" title="JVM internal thread pointer">${esc(tid)}</td>
      <td class="mono-sm blue" title="OS native thread ID (hex) — use with jstack, top -H">${esc(nid)}</td>
      <td class="mono-sm" title="OS native thread ID (decimal) — matches PID in ps/top -H">${esc(nidDec)}</td>
      <td class="blue">${cpuFmt}</td>
      <td class="muted">${elFmt}</td>
      <td class="${pctCls}">${pctFmt}</td>
      <td class="small lock-info" title="${esc(lock)}">${esc(lock ? truncate(lock, 45) : '')}</td>
      <td><button class="expand-btn" onclick="toggleStack('${stackId}', this)">▶ trace</button></td>
    `;
    tbody.appendChild(tr);

    // Stack trace expand row
    const stackRow = document.createElement('tr');
    stackRow.className = 'stack-row';
    stackRow.id = stackId;
    stackRow.innerHTML = `
      <td class="stack-cell" colspan="17">
        ${t.stackTrace
          ? `<pre class="stack-pre">${esc(t.stackTrace)}</pre>`
          : `<span class="no-stack">No stack trace available</span>`}
      </td>
    `;
    tbody.appendChild(stackRow);
  });
}

function toggleStack(id, btn) {
  const row  = document.getElementById(id);
  const open = row.classList.toggle('open');
  btn.textContent = open ? '▼ trace' : '▶ trace';
}

// ── Utilities ──────────────────────────────────────────────────────────────
function fmt(n) {
  return Number(n).toLocaleString(undefined, { maximumFractionDigits: 2 });
}
function truncate(s, max) {
  return s.length > max ? s.slice(0, max) + '…' : s;
}
function esc(s) {
  return String(s ?? '')
    .replace(/&/g,  '&amp;')
    .replace(/</g,  '&lt;')
    .replace(/>/g,  '&gt;')
    .replace(/"/g,  '&quot;');
}
function show(id) { document.getElementById(id).style.display = 'block'; }
function hide(id) { document.getElementById(id).style.display = 'none';  }
