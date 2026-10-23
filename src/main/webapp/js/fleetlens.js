/**
 * FleetLens - Modern Agent & Node Fleet Operations Console
 * Client-side Controller & Reactive Renderer
 */
(function() {
  'use strict';

  const appEl = document.getElementById('fleetlens-app');
  if (!appEl) return;

  const ROOT_URL = appEl.getAttribute('data-root-url') || '';
  const API_URL = appEl.getAttribute('data-api-url') || (ROOT_URL + '/agents/api');
  const INITIAL_VIEW = appEl.getAttribute('data-default-view') || 'cards';
  const INITIAL_REFRESH = parseInt(appEl.getAttribute('data-refresh-interval') || '15', 10);

  // Application State
  const state = {
    agents: [],
    overview: null,
    queue: null,
    matchingNodesWhitelist: null,
    filters: {
      provisioner: 'ALL',
      os: 'ALL',
      status: 'ALL',
      search: ''
    },
    viewMode: INITIAL_VIEW,
    sort: {
      column: 'name',
      ascending: true
    },
    refreshInterval: INITIAL_REFRESH,
    timerId: null,
    lastUpdated: Date.now(),
    isLoading: true
  };

  // DOM Elements
  const el = {
    loading: document.getElementById('fleet-loading'),
    empty: document.getElementById('fleet-empty'),
    cardsView: document.getElementById('fleet-cards-view'),
    tableView: document.getElementById('fleet-table-view'),
    tableBody: document.getElementById('fleet-table-body'),
    searchInput: document.getElementById('fleet-search'),
    searchClear: document.getElementById('search-clear'),
    filterStatus: document.getElementById('filter-status'),
    filterProvPills: document.getElementById('filter-provisioner'),
    filterOsChips: document.getElementById('filter-os'),
    viewBtnCards: document.getElementById('view-mode-cards'),
    viewBtnTable: document.getElementById('view-mode-table'),
    btnRefreshNow: document.getElementById('btn-refresh-now'),
    selectRefreshRate: document.getElementById('select-refresh-rate'),
    lastUpdatedText: document.getElementById('last-updated-text'),
    btnResetFilters: document.getElementById('btn-reset-filters'),
    // Queue Inspector Elements
    queuePanel: document.getElementById('fleet-queue-panel'),
    queueBadgeCount: document.getElementById('queue-badge-count'),
    queueItemsList: document.getElementById('fleet-queue-items-list'),
    modalOffline: document.getElementById('modal-offline'),
    modalTitle: document.getElementById('modal-title'),
    modalHeaderIcon: document.getElementById('modal-header-icon'),
    modalDesc: document.getElementById('modal-desc'),
    modalReasonContainer: document.getElementById('modal-reason-container'),
    modalReason: document.getElementById('modal-offline-reason'),
    modalPresets: document.getElementById('modal-presets'),
    modalAgentName: document.getElementById('modal-agent-name'),
    modalTargetState: document.getElementById('modal-offline-target-state'),
    modalCloseBtn: document.getElementById('modal-close-btn'),
    modalCancelBtn: document.getElementById('modal-cancel-btn'),
    modalConfirmBtn: document.getElementById('modal-confirm-btn'),
    // Connect Modal Elements
    modalConnect: document.getElementById('modal-connect'),
    connectModalTitle: document.getElementById('connect-modal-title'),
    connectModalCloseBtn: document.getElementById('connect-modal-close-btn'),
    connectModalDoneBtn: document.getElementById('connect-modal-done-btn'),
    connectModalBadges: document.getElementById('connect-modal-agent-badges'),
    connectCommandsContainer: document.getElementById('connect-commands-container'),
    connectModalScriptLink: document.getElementById('connect-modal-script-link'),
    connectModalTerminalBtn: document.getElementById('connect-modal-terminal-btn'),
    // Terminal Modal Elements
    modalTerminal: document.getElementById('modal-terminal'),
    terminalModalTitle: document.getElementById('terminal-modal-title'),
    terminalModalCloseBtn: document.getElementById('terminal-modal-close-btn'),
    terminalModalDoneBtn: document.getElementById('terminal-modal-done-btn'),
    terminalModalBadges: document.getElementById('terminal-modal-agent-badges'),
    terminalPresetsContainer: document.getElementById('terminal-presets-container'),
    terminalCmdInput: document.getElementById('terminal-cmd-input'),
    terminalRunBtn: document.getElementById('terminal-run-btn'),
    terminalViewport: document.getElementById('terminal-viewport'),
    terminalWindowTitle: document.getElementById('terminal-window-title'),
    terminalStatusText: document.getElementById('terminal-status-text'),
    terminalExitBadge: document.getElementById('terminal-exit-badge'),
    terminalDuration: document.getElementById('terminal-duration'),
    terminalCopyBtn: document.getElementById('terminal-copy-btn'),
    terminalClearBtn: document.getElementById('terminal-clear-btn'),
    terminalModalScriptLink: document.getElementById('terminal-modal-script-link'),
    // KPI elements
    kpiTotal: document.getElementById('kpi-val-total'),
    kpiSubTotal: document.getElementById('kpi-sub-total'),
    kpiOnline: document.getElementById('kpi-val-online'),
    kpiSubOnline: document.getElementById('kpi-sub-online'),
    kpiBusy: document.getElementById('kpi-val-busy'),
    kpiSubBusy: document.getElementById('kpi-sub-busy'),
    kpiIdle: document.getElementById('kpi-val-idle'),
    kpiSubIdle: document.getElementById('kpi-sub-idle'),
    kpiOffline: document.getElementById('kpi-val-offline'),
    kpiSubOffline: document.getElementById('kpi-sub-offline'),
    kpiStorage: document.getElementById('kpi-val-storage'),
    kpiSubStorage: document.getElementById('kpi-sub-storage')
  };

  // Theme Synchronizer for seamless Dark/Light Mode Adaptability
  function setupThemeSync() {
    function syncTheme() {
      const rootTheme = document.documentElement ? document.documentElement.dataset.theme : null;
      const bodyTheme = document.body ? document.body.dataset.theme : null;
      const currentTheme = rootTheme || bodyTheme || '';
      const isDark = currentTheme === 'dark' ||
                     currentTheme === 'tomorrow_night' ||
                     document.documentElement.classList.contains('theme-dark') ||
                     (document.body && document.body.classList.contains('theme-dark')) ||
                     (currentTheme !== 'none' && currentTheme !== 'light' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
      if (isDark) {
        appEl.classList.add('fleetlens-dark');
      } else {
        appEl.classList.remove('fleetlens-dark');
      }
    }
    syncTheme();

    if (window.MutationObserver) {
      const themeObserver = new MutationObserver(syncTheme);
      if (document.documentElement) {
        themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] });
      }
      if (document.body) {
        themeObserver.observe(document.body, { attributes: true, attributeFilter: ['data-theme', 'class'] });
      }
    }
    if (window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', syncTheme);
    }
  }

  // Init
  function init() {
    setupThemeSync();
    setupEventListeners();

    // Check pre-hydrated state for instant 0ms rendering
    const initialDataEl = document.getElementById('fleetlens-initial-data');
    if (initialDataEl && initialDataEl.textContent.trim()) {
      try {
        const initialData = JSON.parse(initialDataEl.textContent.trim());
        if (initialData && initialData.overview) {
          state.overview = initialData.overview;
          state.agents = initialData.agents || [];
          state.queue = initialData.queue || null;
          state.isLoading = false;
          updateKpis(state.overview);
          updateProvisionerCounts(state.overview.provisionerCounts, state.overview.totalAgents);
          render();
        }
      } catch (err) {
        console.warn('[Modern Agent Viewer] Error reading pre-hydrated state:', err);
      }
    }

    setRefreshTimer(state.refreshInterval);
    fetchFleetData();

    // Start relative time ticker for "updated X seconds ago"
    setInterval(updateLastUpdatedTime, 5000);
  }

  function setupEventListeners() {
    // Search
    if (el.searchInput) {
      el.searchInput.addEventListener('input', function(e) {
        state.filters.search = e.target.value.trim().toLowerCase();
        if (el.searchClear) {
          el.searchClear.style.display = state.filters.search ? 'block' : 'none';
        }
        render();
      });
    }

    if (el.searchClear) {
      el.searchClear.addEventListener('click', function() {
        el.searchInput.value = '';
        state.filters.search = '';
        el.searchClear.style.display = 'none';
        render();
      });
    }

    // Status Filter
    if (el.filterStatus) {
      el.filterStatus.addEventListener('change', function(e) {
        state.filters.status = e.target.value;
        render();
      });
    }

    // Provisioner Pills
    if (el.filterProvPills) {
      el.filterProvPills.addEventListener('click', function(e) {
        const btn = e.target.closest('.filter-pill');
        if (!btn) return;
        el.filterProvPills.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        state.filters.provisioner = btn.getAttribute('data-prov');
        render();
      });
    }

    // OS Chips
    if (el.filterOsChips) {
      el.filterOsChips.addEventListener('click', function(e) {
        const btn = e.target.closest('.filter-chip');
        if (!btn) return;
        el.filterOsChips.querySelectorAll('.filter-chip').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        state.filters.os = btn.getAttribute('data-os');
        render();
      });
    }

    // View Switcher
    if (el.viewBtnCards) {
      el.viewBtnCards.addEventListener('click', function() {
        state.viewMode = 'cards';
        el.viewBtnCards.classList.add('active');
        el.viewBtnTable.classList.remove('active');
        render();
      });
    }

    if (el.viewBtnTable) {
      el.viewBtnTable.addEventListener('click', function() {
        state.viewMode = 'table';
        el.viewBtnTable.classList.add('active');
        el.viewBtnCards.classList.remove('active');
        render();
      });
    }

    // Refresh controls
    if (el.btnRefreshNow) {
      el.btnRefreshNow.addEventListener('click', function() {
        el.btnRefreshNow.classList.add('spinning');
        fetchFleetData(function() {
          el.btnRefreshNow.classList.remove('spinning');
        });
      });
    }

    if (el.selectRefreshRate) {
      el.selectRefreshRate.value = String(state.refreshInterval);
      el.selectRefreshRate.addEventListener('change', function(e) {
        setRefreshTimer(parseInt(e.target.value, 10));
      });
    }

    // Reset Filters
    if (el.btnResetFilters) {
      el.btnResetFilters.addEventListener('click', function() {
        resetFilters();
      });
    }

    // Table sorting
    if (el.tableView) {
      const headers = el.tableView.querySelectorAll('th[data-sort]');
      headers.forEach(function(th) {
        th.addEventListener('click', function() {
          const col = th.getAttribute('data-sort');
          if (state.sort.column === col) {
            state.sort.ascending = !state.sort.ascending;
          } else {
            state.sort.column = col;
            state.sort.ascending = true;
          }
          render();
        });
      });
    }

    // Modal Events
    if (el.modalCloseBtn) el.modalCloseBtn.addEventListener('click', closeModal);
    if (el.modalCancelBtn) el.modalCancelBtn.addEventListener('click', closeModal);
    if (el.modalConfirmBtn) el.modalConfirmBtn.addEventListener('click', handleModalConfirm);

    // Connect Modal Events
    if (el.connectModalCloseBtn) el.connectModalCloseBtn.addEventListener('click', closeConnectModal);
    if (el.connectModalDoneBtn) el.connectModalDoneBtn.addEventListener('click', closeConnectModal);

    // Universal copy delegation inside Connect Modal
    if (el.modalConnect) {
      el.modalConnect.addEventListener('click', function(e) {
        const btn = e.target.closest('.btn-copy, .btn-copy-code');
        if (!btn) return;
        e.preventDefault();
        e.stopPropagation();
        const text = btn.getAttribute('data-copy');
        if (!text) return;
        copyToClipboard(text, function() {
          btn.classList.add('copied');
          const span = btn.querySelector('span');
          if (span) {
            const old = span.textContent;
            span.textContent = 'Copied!';
            setTimeout(() => { span.textContent = old; btn.classList.remove('copied'); }, 1800);
          } else {
            setTimeout(() => { btn.classList.remove('copied'); }, 1800);
          }
        });
      });
    }

    // Modal preset pills
    if (el.modalPresets) {
      el.modalPresets.addEventListener('click', function(e) {
        const btn = e.target.closest('.preset-pill');
        if (btn && el.modalReason) {
          const presetText = btn.getAttribute('data-preset');
          el.modalReason.value = presetText;
          el.modalReason.focus();
        }
      });
    }

    // Modal submit on Cmd+Enter / Ctrl+Enter in textarea
    if (el.modalReason) {
      el.modalReason.addEventListener('keydown', function(e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
          e.preventDefault();
          handleModalConfirm();
        }
      });
    }

    // Global keyboard shortcuts
    window.addEventListener('keydown', function(e) {
      if (e.key === '/' && document.activeElement !== el.searchInput) {
        e.preventDefault();
        el.searchInput.focus();
      } else if (e.key === 'Escape') {
        closeModal();
        closeConnectModal();
        if (state.filters.search) {
          el.searchInput.value = '';
          state.filters.search = '';
          if (el.searchClear) el.searchClear.style.display = 'none';
          render();
        }
      }
    });
  }

  function resetFilters() {
    state.filters.provisioner = 'ALL';
    state.filters.os = 'ALL';
    state.filters.status = 'ALL';
    state.filters.search = '';
    state.matchingNodesWhitelist = null;

    if (el.searchInput) el.searchInput.value = '';
    if (el.searchClear) el.searchClear.style.display = 'none';
    if (el.filterStatus) el.filterStatus.value = 'ALL';

    if (el.filterProvPills) {
      el.filterProvPills.querySelectorAll('.filter-pill').forEach(p => {
        p.classList.toggle('active', p.getAttribute('data-prov') === 'ALL');
      });
    }
    if (el.filterOsChips) {
      el.filterOsChips.querySelectorAll('.filter-chip').forEach(p => {
        p.classList.toggle('active', p.getAttribute('data-os') === 'ALL');
      });
    }
    if (state.overview) {
      updateProvisionerCounts(state.overview.provisionerCounts, state.overview.totalAgents);
    }

    render();
  }

  function setRefreshTimer(seconds) {
    state.refreshInterval = seconds;
    if (state.timerId) {
      clearInterval(state.timerId);
      state.timerId = null;
    }
    if (seconds > 0) {
      state.timerId = setInterval(fetchFleetData, seconds * 1000);
    }
  }

  function updateLastUpdatedTime() {
    if (!el.lastUpdatedText) return;
    const elapsedSec = Math.floor((Date.now() - state.lastUpdated) / 1000);
    if (elapsedSec < 5) {
      el.lastUpdatedText.textContent = 'Updated just now';
    } else if (elapsedSec < 60) {
      el.lastUpdatedText.textContent = 'Updated ' + elapsedSec + 's ago';
    } else {
      const min = Math.floor(elapsedSec / 60);
      el.lastUpdatedText.textContent = 'Updated ' + min + 'm ago';
    }
  }

  // Fetch Telemetry from Jenkins REST endpoint
  function fetchFleetData(callback) {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', API_URL, true);
    xhr.withCredentials = true;
    xhr.setRequestHeader('Accept', 'application/json');

    xhr.onload = function() {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const resp = JSON.parse(xhr.responseText);
          const data = (resp && resp.data) ? resp.data : resp;
          state.overview = data.overview;
          state.agents = data.agents || [];
          state.queue = data.queue || null;
          state.lastUpdated = Date.now();
          state.isLoading = false;

          updateKpis(state.overview);
          updateProvisionerCounts(state.overview.provisionerCounts, state.overview.totalAgents);
          render();
          updateLastUpdatedTime();
        } catch (err) {
          console.error('[Modern Agent Viewer] Error parsing JSON telemetry:', err);
        }
      } else {
        console.warn('[Modern Agent Viewer] API returned status ' + xhr.status);
      }
      if (callback) callback();
    };

    xhr.onerror = function() {
      console.error('[Modern Agent Viewer] Network error fetching telemetry');
      if (callback) callback();
    };

    xhr.send();
  }

  // Update KPI Header Cards
  function updateKpis(ov) {
    if (!ov) return;
    if (el.kpiTotal) el.kpiTotal.textContent = ov.totalAgents;
    if (el.kpiSubTotal) el.kpiSubTotal.textContent = ov.totalExecutors + ' Total Executors';

    if (el.kpiOnline) el.kpiOnline.textContent = ov.onlineAgents;
    if (el.kpiSubOnline) {
      const pct = ov.totalAgents > 0 ? Math.round((ov.onlineAgents / ov.totalAgents) * 100) : 0;
      el.kpiSubOnline.textContent = pct + '% Fleet Availability';
    }

    if (el.kpiBusy) el.kpiBusy.textContent = ov.busyAgents;
    if (el.kpiSubBusy) el.kpiSubBusy.textContent = ov.busyExecutors + ' Executors Active';

    if (el.kpiIdle) el.kpiIdle.textContent = ov.idleAgents;
    if (el.kpiSubIdle) el.kpiSubIdle.textContent = ov.idleExecutors + ' Executors Free';

    if (el.kpiOffline) el.kpiOffline.textContent = ov.offlineAgents;
    if (el.kpiSubOffline) el.kpiSubOffline.textContent = ov.offlineAgents === 0 ? 'All nodes connected' : 'Requires attention';

    if (el.kpiStorage) {
      if (ov.lowStorageAgents > 0) {
        el.kpiStorage.textContent = ov.lowStorageAgents + (ov.lowStorageAgents === 1 ? ' Alert' : ' Alerts');
        el.kpiStorage.style.color = 'var(--fl-red-critical)';
        if (el.kpiSubStorage) el.kpiSubStorage.textContent = 'Disk space low / critical';
      } else {
        el.kpiStorage.textContent = 'Healthy';
        el.kpiStorage.style.color = 'var(--fl-green-online)';
        if (el.kpiSubStorage) el.kpiSubStorage.textContent = 'All storage checks passed';
      }
    }
  }

  // Update numbers on provisioner filter pills
  function updateProvisionerCounts(counts, total) {
    if (!el.filterProvPills || !counts) return;
    const configured = (state.overview && state.overview.configuredClouds) || [];
    el.filterProvPills.querySelectorAll('.filter-pill').forEach(pill => {
      const prov = pill.getAttribute('data-prov');
      const countEl = pill.querySelector('.count');
      if (!countEl) return;
      if (prov === 'ALL') {
        countEl.textContent = total;
        pill.style.display = 'inline-flex';
      } else {
        const c = counts[prov] || 0;
        countEl.textContent = c;
        if (c > 0 || state.filters.provisioner === prov || configured.includes(prov)) {
          pill.style.display = 'inline-flex';
        } else {
          pill.style.display = 'none';
        }
      }
    });
  }

  // Filter and Sort Agents
  function getFilteredAndSortedAgents() {
    let list = state.agents.slice();

    // 0. Filter by matching nodes whitelist (from queue item inspector)
    if (state.matchingNodesWhitelist && state.matchingNodesWhitelist.length > 0) {
      list = list.filter(a => {
        const name = (a.name || '').toLowerCase();
        return state.matchingNodesWhitelist.includes(name);
      });
    }

    // 1. Filter by Provisioner
    if (state.filters.provisioner !== 'ALL') {
      list = list.filter(a => a.provisioner && a.provisioner.id === state.filters.provisioner);
    }

    // 2. Filter by OS
    if (state.filters.os !== 'ALL') {
      list = list.filter(a => a.os && a.os.family === state.filters.os);
    }

    // 3. Filter by Status
    if (state.filters.status !== 'ALL') {
      if (state.filters.status === 'ONLINE') {
        list = list.filter(a => a.isOnline);
      } else if (state.filters.status === 'OFFLINE') {
        list = list.filter(a => a.isOffline);
      } else if (state.filters.status === 'BUSY') {
        list = list.filter(a => a.status === 'BUSY');
      } else if (state.filters.status === 'IDLE') {
        list = list.filter(a => a.status === 'IDLE');
      } else if (state.filters.status === 'TEMPORARILY_OFFLINE') {
        list = list.filter(a => a.isTemporarilyOffline);
      } else if (state.filters.status === 'STORAGE_ALERT') {
        list = list.filter(a => a.storage && (a.storage.healthStatus === 'WARNING' || a.storage.healthStatus === 'CRITICAL'));
      }
    }

    // 4. Filter by Search Query
    if (state.filters.search) {
      const q = state.filters.search;
      list = list.filter(a => {
        if (a.name && a.name.toLowerCase().includes(q)) return true;
        if (a.displayName && a.displayName.toLowerCase().includes(q)) return true;
        if (a.hostName && a.hostName.toLowerCase().includes(q)) return true;
        if (a.os && a.os.prettyName && a.os.prettyName.toLowerCase().includes(q)) return true;
        if (a.labels && a.labels.some(l => l.toLowerCase().includes(q))) return true;
        if (a.provisioner && a.provisioner.displayName && a.provisioner.displayName.toLowerCase().includes(q)) return true;
        if (a.provisioner && a.provisioner.details) {
          for (let k in a.provisioner.details) {
            if (a.provisioner.details[k].toLowerCase().includes(q)) return true;
          }
        }
        return false;
      });
    }

    // 5. Sort
    const col = state.sort.column;
    const asc = state.sort.ascending;
    list.sort(function(a, b) {
      let valA, valB;
      switch (col) {
        case 'provisioner':
          valA = (a.provisioner && a.provisioner.displayName) || '';
          valB = (b.provisioner && b.provisioner.displayName) || '';
          break;
        case 'os':
          valA = (a.os && a.os.prettyName) || '';
          valB = (b.os && b.os.prettyName) || '';
          break;
        case 'status':
          valA = a.status || '';
          valB = b.status || '';
          break;
        case 'storage':
          valA = (a.storage && a.storage.percentUsed) >= 0 ? a.storage.percentUsed : -1;
          valB = (b.storage && b.storage.percentUsed) >= 0 ? b.storage.percentUsed : -1;
          break;
        case 'connection':
          valA = (a.timeMetrics && a.timeMetrics.connectedDurationMs) >= 0 ? a.timeMetrics.connectedDurationMs : -1;
          valB = (b.timeMetrics && b.timeMetrics.connectedDurationMs) >= 0 ? b.timeMetrics.connectedDurationMs : -1;
          break;
        case 'uptime':
          valA = (a.timeMetrics && a.timeMetrics.hostUptimeMs) >= 0 ? a.timeMetrics.hostUptimeMs : -1;
          valB = (b.timeMetrics && b.timeMetrics.hostUptimeMs) >= 0 ? b.timeMetrics.hostUptimeMs : -1;
          break;
        case 'executors':
          valA = a.numExecutors || 0;
          valB = b.numExecutors || 0;
          break;
        case 'name':
        default:
          valA = a.name.toLowerCase();
          valB = b.name.toLowerCase();
          break;
      }

      if (valA < valB) return asc ? -1 : 1;
      if (valA > valB) return asc ? 1 : -1;
      return 0;
    });

    return list;
  }

  // Master Render
  function render() {
    renderQueue(state.queue);

    if (state.isLoading) {
      if (el.loading) el.loading.style.display = 'block';
      if (el.cardsView) el.cardsView.style.display = 'none';
      if (el.tableView) el.tableView.style.display = 'none';
      if (el.empty) el.empty.style.display = 'none';
      return;
    }

    if (el.loading) el.loading.style.display = 'none';
    const filtered = getFilteredAndSortedAgents();

    if (filtered.length === 0) {
      if (el.cardsView) el.cardsView.style.display = 'none';
      if (el.tableView) el.tableView.style.display = 'none';
      if (el.empty) {
        el.empty.style.display = 'block';
        const configured = (state.overview && state.overview.configuredClouds) || [];
        const prov = state.filters.provisioner;
        const emptyTitle = document.getElementById('fleet-empty-title');
        const emptyDesc = document.getElementById('fleet-empty-desc');
        if (prov !== 'ALL' && configured.includes(prov)) {
          if (emptyTitle) emptyTitle.textContent = 'No dynamic instances currently active';
          if (emptyDesc) emptyDesc.textContent = 'This cloud provider is registered and ready. Dynamic nodes are spawned automatically on-demand when queued builds request matching labels.';
        } else {
          if (emptyTitle) emptyTitle.textContent = 'No matching machines found';
          if (emptyDesc) emptyDesc.textContent = 'Try resetting the search query or adjusting your provider/OS filters.';
        }
      }
      return;
    }

    if (el.empty) el.empty.style.display = 'none';

    if (state.viewMode === 'cards') {
      if (el.tableView) el.tableView.style.display = 'none';
      if (el.cardsView) {
        el.cardsView.style.display = 'grid';
        renderCards(filtered);
      }
    } else {
      if (el.cardsView) el.cardsView.style.display = 'none';
      if (el.tableView) {
        el.tableView.style.display = 'block';
        renderTable(filtered);
      }
    }
  }

  // Render Live Queue Inspector
  function renderQueue(queue) {
    if (!el.queuePanel || !el.queueItemsList) return;
    if (!queue || !queue.items || queue.items.length === 0) {
      el.queuePanel.style.display = 'none';
      return;
    }

    el.queuePanel.style.display = 'block';
    const total = queue.totalWaiting;
    if (el.queueBadgeCount) {
      el.queueBadgeCount.textContent = total + (total === 1 ? ' build waiting' : ' builds waiting');
      if (queue.stuckCount > 0) {
        el.queueBadgeCount.classList.add('badge-stuck');
      } else {
        el.queueBadgeCount.classList.remove('badge-stuck');
      }
    }

    let activeFilterNotice = '';
    if (state.matchingNodesWhitelist && state.matchingNodesWhitelist.length > 0) {
      activeFilterNotice = `
        <div class="queue-active-filter-alert">
          <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon></svg>
          <span>Fleet filtered to matching candidates (${state.matchingNodesWhitelist.length} machines)</span>
          <button id="btn-clear-queue-filter" class="queue-clear-pill">Clear Filter</button>
        </div>
      `;
    }

    el.queueItemsList.innerHTML = activeFilterNotice + queue.items.map(function(item) {
      let badgeClass = 'queue-status-ready';
      let iconSvg = '';
      if (item.bottleneckReason === 'NO_MATCHING_NODES') {
        badgeClass = 'queue-status-danger';
        iconSvg = '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>';
      } else if (item.bottleneckReason === 'ALL_NODES_OFFLINE') {
        badgeClass = 'queue-status-warning';
        iconSvg = '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>';
      } else if (item.bottleneckReason === 'ALL_EXECUTORS_BUSY') {
        badgeClass = 'queue-status-busy';
        iconSvg = '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>';
      } else if (item.bottleneckReason === 'PROVISIONING_CLOUD') {
        badgeClass = 'queue-status-provisioning';
        iconSvg = '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path></svg>';
      } else {
        badgeClass = 'queue-status-ready';
        iconSvg = '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>';
      }

      const labelChip = item.assignedLabel ?
        `<span class="queue-label-chip" title="Requested label expression">Label: ${escapeHtml(item.assignedLabel)}</span>` :
        `<span class="queue-label-chip any" title="Any normal-mode node">Any Agent</span>`;

      const filterBtn = (item.matchingNodeNames && item.matchingNodeNames.length > 0) ?
        `<button class="queue-btn btn-filter-matching" data-nodes="${escapeHtml(item.matchingNodeNames.join(','))}" title="Filter fleet to matching candidate machines">
           <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon></svg>
           Filter Fleet (${item.matchingNodesCount})
         </button>` :
        `<span class="queue-no-match">0 candidate machines</span>`;

      const cancelBtn = item.canCancel ?
        `<button class="queue-btn btn-cancel-item" data-id="${item.id}" title="Remove build from queue">
           <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
           Cancel
         </button>` : '';

      return `
        <div class="queue-item-row" data-id="${item.id}">
          <div class="queue-item-main">
            <a href="${ROOT_URL}/${escapeHtml(item.url)}" class="queue-job-name" target="_blank" rel="noopener">
              ${escapeHtml(item.name)}
            </a>
            ${labelChip}
            <span class="queue-wait-time">
              <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              Waiting ${escapeHtml(item.inQueueDurationHuman)}
            </span>
          </div>
          <div class="queue-item-diagnostics">
            <span class="queue-diag-pill ${badgeClass}">
              ${iconSvg}
              ${escapeHtml(item.bottleneckMessage)}
            </span>
          </div>
          <div class="queue-item-actions">
            ${filterBtn}
            ${cancelBtn}
          </div>
        </div>
      `;
    }).join('');

    attachQueueEvents();
  }

  function attachQueueEvents() {
    if (!el.queueItemsList) return;

    // Filter matching agents
    el.queueItemsList.querySelectorAll('.btn-filter-matching').forEach(btn => {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        const nodesStr = btn.getAttribute('data-nodes');
        if (nodesStr) {
          state.matchingNodesWhitelist = nodesStr.split(',').map(s => s.trim().toLowerCase());
          render();
        }
      });
    });

    // Clear filter
    const clearBtn = document.getElementById('btn-clear-queue-filter');
    if (clearBtn) {
      clearBtn.addEventListener('click', function(e) {
        e.preventDefault();
        state.matchingNodesWhitelist = null;
        render();
      });
    }

    // Cancel queued build
    el.queueItemsList.querySelectorAll('.btn-cancel-item').forEach(btn => {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        const itemId = btn.getAttribute('data-id');
        if (!itemId) return;
        handleCancelQueue(itemId);
      });
    });
  }

  function handleCancelQueue(itemId) {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', ROOT_URL + '/agents/cancelQueue', true);
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

    if (window.crumb && window.crumb.fieldName && window.crumb.value) {
      xhr.setRequestHeader(window.crumb.fieldName, window.crumb.value);
    }

    xhr.onload = function() {
      fetchFleetData();
    };
    xhr.onerror = function() {
      alert('Error canceling queue item');
    };
    xhr.send('id=' + encodeURIComponent(itemId));
  }

  // Render Visual Card Matrix
  function renderCards(agents) {
    if (!el.cardsView) return;
    el.cardsView.innerHTML = '';

    agents.forEach(function(agent) {
      const card = document.createElement('div');
      card.className = 'fleet-card';

      // Status class
      let statusDotClass = 'status-offline';
      if (agent.isTemporarilyOffline) {
        statusDotClass = 'status-temp-offline';
      } else if (agent.status === 'BUSY') {
        statusDotClass = 'status-busy';
      } else if (agent.isOnline) {
        statusDotClass = 'status-online';
      }

      // Provisioner badge
      const provBadgeClass = (agent.provisioner && agent.provisioner.badgeClass) || 'badge-static';
      const provName = (agent.provisioner && agent.provisioner.displayName) || 'Static Node';
      const provIcon = getProviderIconSvg(agent.provisioner ? agent.provisioner.id : 'static');

      // OS badge
      const osFamily = agent.os ? agent.os.family : 'OTHER';
      const osPretty = agent.os ? agent.os.prettyName : 'Unknown OS';
      const osArch = (agent.os && agent.os.arch) ? agent.os.arch : '';
      const osIcon = getOsIconSvg(osFamily);

      // Storage
      const storage = agent.storage || {};
      const percentUsed = storage.percentUsed >= 0 ? storage.percentUsed : 0;
      let fillClass = 'fill-healthy';
      if (storage.healthStatus === 'CRITICAL') fillClass = 'fill-critical';
      else if (storage.healthStatus === 'WARNING') fillClass = 'fill-warning';

      // Uptime & Connection
      const tm = agent.timeMetrics || {};
      const connDuration = tm.connectedDurationHuman || '--';
      const hostUptime = tm.hostUptimeHuman || '--';

      // Build card HTML
      const computerUrl = ROOT_URL + '/computer/' + encodeURIComponent(agent.name === '(built-in)' ? '(built-in)' : agent.name);

      let cardHtml = `
        <div class="card-top">
          <div class="agent-title-block">
            <div style="display: flex; align-items: center; gap: 6px;">
              <a href="${computerUrl}" class="agent-name-link">
                <span class="status-indicator ${statusDotClass}"></span>
                ${escapeHtml(agent.displayName || agent.name)}
              </a>
              <button class="fl-copy-icon-btn btn-copy" data-copy="${escapeHtml(agent.name)}" title="Copy Agent Name: ${escapeHtml(agent.name)}" type="button">
                <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
              </button>
            </div>
            <div class="badge-row">
              <span class="origin-badge ${provBadgeClass}">${provIcon} ${escapeHtml(provName)}</span>
              <span class="os-badge">${osIcon} ${escapeHtml(osPretty)}</span>
              ${osArch ? `<span class="arch-chip">${escapeHtml(osArch)}</span>` : ''}
              ${agent.ipAddress ? `
                <button class="fl-copy-chip btn-copy-ip" data-copy="${escapeHtml(agent.ipAddress)}" title="Click to copy IP: ${escapeHtml(agent.ipAddress)}" type="button">
                  <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                  <span class="copy-text">${escapeHtml(agent.ipAddress)}</span>
                </button>
              ` : ''}
            </div>
          </div>
        </div>

        <div class="card-body">
          ${agent.isTemporarilyOffline ? `
            <div class="agent-offline-reason-banner">
              <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
              <div class="offline-reason-content">
                <span class="offline-reason-label">Maintenance:</span>
                <span class="offline-reason-text">${escapeHtml(agent.offlineCauseReason || agent.offlineCause || 'Marked offline')}</span>
              </div>
            </div>
          ` : ''}
          <!-- Storage Section -->
          <div class="metric-storage-box">
            <div class="storage-labels">
              <span class="storage-title">
                <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="12" x2="2" y2="12"></line><path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"></path><line x1="6" y1="16" x2="6.01" y2="16"></line><line x1="10" y1="16" x2="10.01" y2="16"></line></svg>
                Storage (${percentUsed}%)
              </span>
              <span class="storage-values">${escapeHtml(storage.usedHuman || '--')} / ${escapeHtml(storage.totalHuman || '--')}</span>
            </div>
            <div class="progress-track">
              <div class="progress-fill ${fillClass}" style="width: ${percentUsed}%;"></div>
            </div>
            <div class="storage-subtext">
              <span>Free: ${escapeHtml(storage.freeHuman || '--')}</span>
              ${storage.tempAvailable ? `<span>Temp: ${escapeHtml(storage.tempFreeHuman)} free</span>` : ''}
            </div>
          </div>

          <!-- Dual Clock Telemetry -->
          <div class="time-telemetry-grid">
            <div class="time-box">
              <span class="time-title">
                <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></svg>
                Session Connected
              </span>
              <span class="time-val">${escapeHtml(connDuration)}</span>
            </div>
            <div class="time-box">
              <span class="time-title">
                <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                Host OS Uptime
              </span>
              <span class="time-val">${escapeHtml(hostUptime)}</span>
            </div>
          </div>

          <!-- Executors & Workload -->
          <div class="executors-section">
            <div class="exec-header">
              <span>Executors (${agent.executors.filter(e => e.busy).length}/${agent.numExecutors} active)</span>
              ${agent.responseTimeMs ? `<span>Ping: ${agent.responseTimeMs} ms</span>` : ''}
            </div>
            <div class="exec-slots-row">
              ${renderExecutorSlots(agent.executors)}
            </div>
          </div>

          <!-- Assigned Labels -->
          ${agent.labels && agent.labels.length > 0 ? `
            <div class="agent-labels-row">
              ${agent.labels.map(l => `<span class="agent-label-pill" data-label="${escapeHtml(l)}">${escapeHtml(l)}</span>`).join('')}
            </div>
          ` : ''}
        </div>

        <div class="card-footer">
          <!-- Cluster 1: Operational & Diagnostic Tools -->
          <div class="card-footer-cluster tools-cluster" title="Operational &amp; Diagnostic Tools">
            <button class="card-cluster-btn btn-connect-agent" data-agent="${escapeHtml(agent.name)}" title="SSH &amp; CLI Connect Commands" type="button">
              <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>
              <span>Connect</span>
            </button>
            ${agent.canConfigure ? `
              <button class="card-cluster-btn btn-terminal-agent" data-agent="${escapeHtml(agent.name)}" title="Agent Diagnostic Console" type="button">
                <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>
                <span>Console</span>
              </button>
            ` : ''}
            <a href="${computerUrl}/log" class="card-cluster-btn btn-icon-only" title="View agent log">
              <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>
            </a>
          </div>

          <!-- Cluster 2: Machine Management -->
          <div class="card-footer-cluster management-cluster" title="Agent Lifecycle &amp; Configuration">
            <a href="${computerUrl}/configure" class="card-cluster-btn" title="Configure agent settings">
              <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
              <span>Settings</span>
            </a>
            ${agent.canConfigure && agent.name !== '(built-in)' ? `
              ${agent.isTemporarilyOffline ?
                `<button class="card-cluster-btn btn-toggle-online" data-action="online" data-name="${escapeHtml(agent.name)}" title="Bring agent online" type="button">
                   <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
                   <span>Bring Online</span>
                 </button>` :
                `<button class="card-cluster-btn btn-toggle-offline" data-action="offline" data-name="${escapeHtml(agent.name)}" title="Take agent temporarily offline" type="button">
                   <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="3.5" height="16"></rect><rect x="14.5" y="4" width="3.5" height="16"></rect></svg>
                   <span>Take Offline</span>
                 </button>`
              }
            ` : ''}
          </div>
        </div>
      `;

      card.innerHTML = cardHtml;
      attachCardEvents(card);
      el.cardsView.appendChild(card);
    });
  }

  // Render High Density Data Grid Table
  function renderTable(agents) {
    if (!el.tableBody) return;
    el.tableBody.innerHTML = '';

    agents.forEach(function(agent) {
      const tr = document.createElement('tr');
      const computerUrl = ROOT_URL + '/computer/' + encodeURIComponent(agent.name === '(built-in)' ? '(built-in)' : agent.name);

      let statusDotClass = 'status-offline';
      if (agent.isTemporarilyOffline) statusDotClass = 'status-temp-offline';
      else if (agent.status === 'BUSY') statusDotClass = 'status-busy';
      else if (agent.isOnline) statusDotClass = 'status-online';

      const provBadgeClass = (agent.provisioner && agent.provisioner.badgeClass) || 'badge-static';
      const provName = (agent.provisioner && agent.provisioner.displayName) || 'Static Node';
      const provIcon = getProviderIconSvg(agent.provisioner ? agent.provisioner.id : 'static');

      const osFamily = agent.os ? agent.os.family : 'OTHER';
      const osPretty = agent.os ? agent.os.prettyName : 'Unknown OS';
      const osArch = (agent.os && agent.os.arch) ? agent.os.arch : '';
      const osIcon = getOsIconSvg(osFamily);

      const storage = agent.storage || {};
      const percentUsed = storage.percentUsed >= 0 ? storage.percentUsed : 0;
      let fillClass = 'fill-healthy';
      if (storage.healthStatus === 'CRITICAL') fillClass = 'fill-critical';
      else if (storage.healthStatus === 'WARNING') fillClass = 'fill-warning';

      const tm = agent.timeMetrics || {};
      const connDuration = tm.connectedDurationHuman || '--';
      const hostUptime = tm.hostUptimeHuman || '--';
      const activeExec = agent.executors.filter(e => e.busy).length;

      tr.innerHTML = `
        <td>
          <div style="display: flex; align-items: center; gap: 6px;">
            <a href="${computerUrl}" class="agent-name-link">
              <span class="status-indicator ${statusDotClass}"></span>
              ${escapeHtml(agent.displayName || agent.name)}
            </a>
            <button class="fl-copy-icon-btn btn-copy" data-copy="${escapeHtml(agent.name)}" title="Copy Agent Name: ${escapeHtml(agent.name)}" type="button">
              <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
            </button>
          </div>
          ${agent.ipAddress ? `
            <div>
              <button class="fl-copy-chip btn-copy-ip table-copy-chip" data-copy="${escapeHtml(agent.ipAddress)}" title="Click to copy IP: ${escapeHtml(agent.ipAddress)}" type="button">
                <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                <span class="copy-text">${escapeHtml(agent.ipAddress)}</span>
              </button>
            </div>
          ` : ''}
        </td>
        <td>
          <span class="origin-badge ${provBadgeClass}">${provIcon} ${escapeHtml(provName)}</span>
        </td>
        <td>
          <span class="os-badge">${osIcon} ${escapeHtml(osPretty)}</span>
          ${osArch ? `<span class="arch-chip">${escapeHtml(osArch)}</span>` : ''}
        </td>
        <td>
          <div><strong>${escapeHtml(agent.status)}</strong></div>
          ${agent.isTemporarilyOffline && (agent.offlineCauseReason || agent.offlineCause) ? `
            <div class="table-offline-reason" title="${escapeHtml(agent.offlineCauseReason || agent.offlineCause)}">
              ${escapeHtml(agent.offlineCauseReason || agent.offlineCause)}
            </div>
          ` : ''}
        </td>
        <td>
          <div class="table-storage-bar">
            <span>${percentUsed}% (${escapeHtml(storage.usedHuman || '--')} / ${escapeHtml(storage.totalHuman || '--')})</span>
            <div class="progress-track" style="height: 6px;">
              <div class="progress-fill ${fillClass}" style="width: ${percentUsed}%;"></div>
            </div>
          </div>
        </td>
        <td>${escapeHtml(connDuration)}</td>
        <td>${escapeHtml(hostUptime)}</td>
        <td>${activeExec} / ${agent.numExecutors}</td>
        <td>
          <button class="footer-btn btn-connect-agent" data-agent="${escapeHtml(agent.name)}" style="padding: 2px 6px; margin-right: 4px;" title="Connect / SSH">Connect</button>
          ${agent.canConfigure ? `
            <button class="footer-btn btn-terminal-agent" data-agent="${escapeHtml(agent.name)}" style="padding: 2px 6px; margin-right: 4px;" title="Diagnostic Console">Console</button>
          ` : ''}
          <a href="${computerUrl}/log" class="footer-btn" style="padding: 2px 6px;">Log</a>
          ${agent.canConfigure && agent.name !== '(built-in)' ? `
            ${agent.isTemporarilyOffline ?
              `<button class="footer-btn btn-toggle-online" style="padding: 2px 6px;" data-action="online" data-name="${escapeHtml(agent.name)}">Online</button>` :
              `<button class="footer-btn btn-toggle-offline" style="padding: 2px 6px;" data-action="offline" data-name="${escapeHtml(agent.name)}">Offline</button>`
            }
          ` : ''}
        </td>
      `;

      attachCardEvents(tr);
      el.tableBody.appendChild(tr);
    });
  }

  function renderExecutorSlots(executors) {
    if (!executors || executors.length === 0) {
      return '<span class="exec-slot-chip">0 slots</span>';
    }
    return executors.map(e => {
      if (e.busy) {
        const jobTitle = e.currentExecutable || 'Executing...';
        return `<span class="exec-slot-chip busy" title="${escapeHtml(jobTitle)}"><span class="exec-dot busy-pulse"></span>#${e.number + 1} ${escapeHtml(jobTitle)}</span>`;
      }
      return `<span class="exec-slot-chip">#${e.number + 1} Idle</span>`;
    }).join('');
  }

  function attachCardEvents(parent) {
    // Label click filter
    parent.querySelectorAll('.agent-label-pill').forEach(pill => {
      pill.addEventListener('click', function(e) {
        e.stopPropagation();
        const label = pill.getAttribute('data-label');
        if (label && el.searchInput) {
          el.searchInput.value = label;
          state.filters.search = label.toLowerCase();
          if (el.searchClear) el.searchClear.style.display = 'block';
          render();
        }
      });
    });

    // Connect button
    parent.querySelectorAll('.btn-connect-agent').forEach(btn => {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        const agentName = btn.getAttribute('data-agent');
        if (agentName) openConnectModal(agentName);
      });
    });

    // Terminal button
    parent.querySelectorAll('.btn-terminal-agent').forEach(btn => {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        const agentName = btn.getAttribute('data-agent');
        if (agentName) openTerminalModal(agentName);
      });
    });

    // Universal copy handler
    parent.querySelectorAll('.btn-copy, .fl-copy-chip, .fl-copy-icon-btn, .btn-copy-code, .btn-copy-ip, .table-copy-chip').forEach(btn => {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        const text = btn.getAttribute('data-copy') || btn.textContent.trim();
        if (!text) return;
        copyToClipboard(text, function() {
          btn.classList.add('copied');
          const textSpan = btn.querySelector('.copy-text');
          if (textSpan) {
            const oldText = textSpan.textContent;
            textSpan.textContent = 'Copied!';
            setTimeout(function() {
              textSpan.textContent = oldText;
              btn.classList.remove('copied');
            }, 1800);
          } else {
            const originalHtml = btn.innerHTML;
            btn.innerHTML = '<svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg><span style="font-size: 10px; margin-left: 3px;">Copied!</span>';
            setTimeout(function() {
              btn.innerHTML = originalHtml;
              btn.classList.remove('copied');
            }, 1800);
          }
        });
      });
    });

    // Offline / Online action button
    parent.querySelectorAll('[data-action]').forEach(btn => {
      btn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        const action = btn.getAttribute('data-action');
        const agentName = btn.getAttribute('data-name');
        openModal(agentName, action === 'offline');
      });
    });
  }

  // Connect Modal Logic
  function getConnectCommands(agent) {
    const commands = [];
    const provId = (agent.provisioner && agent.provisioner.id) || 'static';
    const osFam = (agent.os && agent.os.family) || 'OTHER';
    const ip = agent.ipAddress || '';
    const host = agent.hostName || '';
    const targetHost = ip || host || agent.name;
    const isBuiltIn = agent.name === '(built-in)';
    const details = (agent.provisioner && agent.provisioner.details) || {};

    if (isBuiltIn) {
      commands.push({
        title: 'Docker Controller Exec',
        desc: 'Open an interactive root bash terminal inside the running Jenkins Controller container:',
        cmd: 'docker exec -it jenkins-controller /bin/bash'
      });
      commands.push({
        title: 'Controller Application Logs',
        desc: 'Follow live application server stdout/stderr logs from the controller:',
        cmd: 'docker logs -f --tail 100 jenkins-controller'
      });
      commands.push({
        title: 'Script Console Command',
        desc: 'Execute Groovy script text directly on the controller via curl API:',
        cmd: 'curl -d "script=println System.getenv()" -u admin:admin123 http://' + (window.location.host || 'localhost:8080') + '/scriptText'
      });
      return commands;
    }

    if (provId === 'aws-ec2' || provId === 'aws-ecs' || details['Instance ID'] || details['InstanceId']) {
      const instanceId = details['Instance ID'] || details['InstanceId'] || '<instance-id>';
      const region = details['Region'] || details['aws.region'] || 'us-east-1';
      const sshUser = osFam === 'WINDOWS' ? 'Administrator' : (osFam === 'LINUX' ? 'ec2-user' : 'admin');

      if (ip || host) {
        commands.push({
          title: 'Direct SSH via Key Pair',
          desc: 'Connect directly to the EC2 agent instance on port 22 (' + sshUser + '@' + targetHost + '):',
          cmd: 'ssh -i ~/.ssh/jenkins-ec2-key.pem ' + sshUser + '@' + targetHost
        });
      }

      commands.push({
        title: 'AWS Systems Manager (SSM) Session',
        desc: 'Zero open inbound ports required. Connect via AWS SSM Session Manager plugin:',
        cmd: 'aws ssm start-session --target ' + instanceId + ' --region ' + region
      });

      commands.push({
        title: 'AWS CLI Instance Telemetry',
        desc: 'Describe instance state, public/private IP, and security groups from AWS CLI:',
        cmd: 'aws ec2 describe-instances --instance-ids ' + instanceId + ' --region ' + region + ' --query "Reservations[*].Instances[*].[InstanceId,State.Name,PublicIpAddress,PrivateIpAddress]"'
      });

      if (osFam === 'WINDOWS') {
        commands.push({
          title: 'PowerShell Remoting (WinRM)',
          desc: 'Connect to Windows EC2 agent via PowerShell remoting session:',
          cmd: 'Enter-PSSession -ComputerName ' + targetHost + ' -Credential (Get-Credential) -UseSSL'
        });
      }

      return commands;
    }

    if (provId === 'docker' || provId === 'swarm' || host.includes('docker') || agent.name.includes('docker')) {
      let containerRef = host.replace('.jenkins-net', '').replace(/:\d+$/, '');
      if (!containerRef || containerRef.startsWith('172.') || containerRef.startsWith('10.')) {
        containerRef = agent.name;
      }
      commands.push({
        title: 'Docker Interactive Bash',
        desc: 'Open an interactive bash shell in container ' + containerRef + ':',
        cmd: 'docker exec -it ' + containerRef + ' /bin/bash'
      });
      commands.push({
        title: 'Docker Shell (Fallback /bin/sh)',
        desc: 'Fallback shell execution for Alpine or minimal container images:',
        cmd: 'docker exec -it ' + containerRef + ' /bin/sh'
      });
      commands.push({
        title: 'Docker Container Logs',
        desc: 'Stream remoting agent stdout and JNLP connection logs:',
        cmd: 'docker logs -f --tail 100 ' + containerRef
      });
      if (ip) {
        commands.push({
          title: 'SSH to Container (Bridge Network)',
          desc: 'Connect over internal bridge network (' + ip + '):',
          cmd: 'ssh -p 22 jenkins@' + ip
        });
      }
      return commands;
    }

    if (provId === 'kubernetes' || details['Pod Name'] || details['Namespace']) {
      const podName = details['Pod Name'] || agent.name;
      const namespace = details['Namespace'] || 'jenkins';
      const containerName = details['Container'] || 'jnlp';

      commands.push({
        title: 'Kubectl Pod Exec',
        desc: 'Exec interactive terminal into pod ' + podName + ' (namespace: ' + namespace + '):',
        cmd: 'kubectl exec -it ' + podName + ' -n ' + namespace + ' -c ' + containerName + ' -- /bin/sh'
      });
      commands.push({
        title: 'Kubectl Pod Logs',
        desc: 'Stream agent remoting container logs:',
        cmd: 'kubectl logs -f ' + podName + ' -n ' + namespace + ' -c ' + containerName + ' --tail=100'
      });
      commands.push({
        title: 'Kubectl Pod Diagnostics',
        desc: 'Inspect pod events, resource limits, and lifecycle events:',
        cmd: 'kubectl describe pod ' + podName + ' -n ' + namespace
      });
      return commands;
    }

    if (osFam === 'WINDOWS') {
      commands.push({
        title: 'PowerShell Remoting (WinRM)',
        desc: 'Open remote interactive PowerShell session to ' + targetHost + ':',
        cmd: 'Enter-PSSession -ComputerName ' + targetHost + ' -Credential (Get-Credential)'
      });
      commands.push({
        title: 'Remote Desktop (RDP)',
        desc: 'Launch Windows Remote Desktop connection directly to ' + targetHost + ':',
        cmd: 'mstsc /v:' + targetHost
      });
      if (ip || host) {
        commands.push({
          title: 'OpenSSH for Windows',
          desc: 'Direct SSH session (if OpenSSH Server feature is enabled on Windows):',
          cmd: 'ssh Administrator@' + targetHost
        });
      }
      return commands;
    }

    if (ip || host) {
      const defaultUser = osFam === 'MACOS' ? 'admin' : 'jenkins';
      commands.push({
        title: 'SSH Terminal Session',
        desc: 'Open secure interactive terminal connection to ' + targetHost + ':',
        cmd: 'ssh ' + defaultUser + '@' + targetHost
      });
      commands.push({
        title: 'SSH with Custom Identity File',
        desc: 'Specify SSH private key for authentication:',
        cmd: 'ssh -i ~/.ssh/id_rsa ' + defaultUser + '@' + targetHost
      });
      commands.push({
        title: 'SCP File Transfer',
        desc: 'Upload files to agent workspace or temp directory:',
        cmd: 'scp ./build-artifact.tar.gz ' + defaultUser + '@' + targetHost + ':' + (agent.remoteFs || '/tmp') + '/'
      });
      return commands;
    }

    commands.push({
      title: 'Agent Remoting Script Console',
      desc: 'Run groovy code directly on this node via Jenkins master-to-agent remoting channel:',
      cmd: 'println hudson.remoting.Channel.current().getName()'
    });
    commands.push({
      title: 'Ping / Network Reachability',
      desc: 'Check ICMP reachability to node host from controller:',
      cmd: 'ping -c 4 ' + targetHost
    });
    return commands;
  }

  function ensureConnectModal() {
    if (!el.modalConnect) {
      el.modalConnect = document.getElementById('modal-connect');
    }
    if (!el.modalConnect) {
      const modalDiv = document.createElement('div');
      modalDiv.id = 'modal-connect';
      modalDiv.className = 'fleet-modal-backdrop';
      modalDiv.style.display = 'none';
      modalDiv.innerHTML = `
        <div class="fleet-modal" style="max-width: 680px;" role="dialog" aria-modal="true" aria-labelledby="connect-modal-title">
          <div class="modal-header">
            <div class="modal-title-wrap">
              <svg class="modal-header-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--fl-brand-blue, #2563eb);">
                <polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line>
              </svg>
              <h3 id="connect-modal-title">Connect to Agent</h3>
            </div>
            <button class="modal-close" id="connect-modal-close-btn" aria-label="Close dialog" type="button">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div id="connect-modal-agent-badges" style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px;"></div>
            <p id="connect-modal-desc" class="modal-description" style="margin-bottom: 14px;">Select a command recipe below to interact with this build node directly from your local terminal or CLI:</p>
            <div id="connect-commands-container" class="connect-commands-list"></div>
          </div>
          <div class="modal-footer" style="justify-content: space-between;">
            <div style="display: flex; gap: 8px;">
              <a id="connect-modal-script-link" href="#" target="_blank" class="jenkins-button jenkins-button--tertiary" style="display: inline-flex; align-items: center; gap: 6px; text-decoration: none;">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>
                Groovy Console
              </a>
              <button id="connect-modal-terminal-btn" type="button" class="jenkins-button jenkins-button--tertiary" style="display: inline-flex; align-items: center; gap: 6px;">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>
                Diagnostic Console
              </button>
            </div>
            <button id="connect-modal-done-btn" type="button" class="jenkins-button jenkins-button--primary">Done</button>
          </div>
        </div>
      `;
      document.body.appendChild(modalDiv);
      el.modalConnect = modalDiv;
      el.connectModalTitle = modalDiv.querySelector('#connect-modal-title');
      el.connectModalCloseBtn = modalDiv.querySelector('#connect-modal-close-btn');
      el.connectModalDoneBtn = modalDiv.querySelector('#connect-modal-done-btn');
      el.connectModalBadges = modalDiv.querySelector('#connect-modal-agent-badges');
      el.connectCommandsContainer = modalDiv.querySelector('#connect-commands-container');
      el.connectModalScriptLink = modalDiv.querySelector('#connect-modal-script-link');
      el.connectModalTerminalBtn = modalDiv.querySelector('#connect-modal-terminal-btn');

      el.connectModalCloseBtn?.addEventListener('click', closeConnectModal);
      el.connectModalDoneBtn?.addEventListener('click', closeConnectModal);
      el.connectModalTerminalBtn?.addEventListener('click', function() {
        const agentName = state.activeConnectAgent;
        closeConnectModal();
        if (agentName) openTerminalModal(agentName);
      });
      modalDiv.addEventListener('click', function(e) {
        if (e.target === modalDiv) closeConnectModal();
        const btn = e.target.closest('.btn-copy, .btn-copy-code');
        if (!btn) return;
        e.preventDefault();
        e.stopPropagation();
        const text = btn.getAttribute('data-copy');
        if (!text) return;
        copyToClipboard(text, function() {
          btn.classList.add('copied');
          const span = btn.querySelector('span');
          if (span) {
            const old = span.textContent;
            span.textContent = 'Copied!';
            setTimeout(() => { span.textContent = old; btn.classList.remove('copied'); }, 1800);
          } else {
            setTimeout(() => { btn.classList.remove('copied'); }, 1800);
          }
        });
      });
    }
    return el.modalConnect;
  }

  function openConnectModal(agentName) {
    ensureConnectModal();
    if (!el.modalConnect) return;
    const agent = state.agents.find(a => a.name === agentName);
    if (!agent) return;

    state.activeConnectAgent = agent.name;

    // Wire connectModalTerminalBtn in case it was rendered statically from index.jelly
    if (el.connectModalTerminalBtn && !el.connectModalTerminalBtn._wired) {
      el.connectModalTerminalBtn._wired = true;
      el.connectModalTerminalBtn.addEventListener('click', function() {
        const target = state.activeConnectAgent;
        closeConnectModal();
        if (target) openTerminalModal(target);
      });
    }

    if (el.connectModalTitle) el.connectModalTitle.textContent = 'Connect to ' + agent.name;

    const provBadgeClass = (agent.provisioner && agent.provisioner.badgeClass) || 'badge-static';
    const provName = (agent.provisioner && agent.provisioner.displayName) || 'Static Node';
    const provIcon = getProviderIconSvg(agent.provisioner ? agent.provisioner.id : 'static');
    const osFamily = agent.os ? agent.os.family : 'OTHER';
    const osPretty = agent.os ? agent.os.prettyName : 'Unknown OS';
    const osIcon = getOsIconSvg(osFamily);

    if (el.connectModalBadges) {
      el.connectModalBadges.innerHTML = `
        <span class="origin-badge ${provBadgeClass}">${provIcon} ${escapeHtml(provName)}</span>
        <span class="os-badge">${osIcon} ${escapeHtml(osPretty)}</span>
        ${agent.ipAddress ? `<span class="node-meta-chip">IP: ${escapeHtml(agent.ipAddress)}</span>` : ''}
        ${agent.hostName && agent.hostName !== agent.ipAddress ? `<span class="node-meta-chip">Host: ${escapeHtml(agent.hostName)}</span>` : ''}
      `;
    }

    const commands = getConnectCommands(agent);
    if (el.connectCommandsContainer) {
      el.connectCommandsContainer.innerHTML = commands.map(c => `
        <div class="connect-command-card">
          <div class="connect-command-header">
            <div class="connect-command-title">
              <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>
              ${escapeHtml(c.title)}
            </div>
            <span class="connect-command-desc">${escapeHtml(c.desc)}</span>
          </div>
          <div class="connect-code-box">
            <code class="connect-code">${escapeHtml(c.cmd)}</code>
            <button class="btn-copy-code btn-copy" data-copy="${escapeHtml(c.cmd)}" title="Copy command to clipboard" type="button">
              <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
              <span>Copy</span>
            </button>
          </div>
        </div>
      `).join('');
    }

    if (el.connectModalScriptLink) {
      const computerUrl = (ROOT_URL || '') + '/computer/' + encodeURIComponent(agent.name === '(built-in)' ? '(built-in)' : agent.name);
      el.connectModalScriptLink.href = computerUrl + '/script';
    }

    el.modalConnect.style.display = 'flex';
  }

  function closeConnectModal() {
    if (el.modalConnect) el.modalConnect.style.display = 'none';
  }

  function getDiagnosticPresets(agent) {
    const osFam = (agent && agent.os && agent.os.family) ? agent.os.family.toUpperCase() : '';
    const isWin = osFam === 'WINDOWS';
    const isMac = osFam === 'MACOS';

    if (isWin) {
      return [
        { id: 'storage', label: 'Disk Space', cmd: 'Get-PSDrive -PSProvider FileSystem | Format-Table Name, @{N=\'Used(GB)\';E={[math]::Round($_.Used/1GB,1)}}, @{N=\'Free(GB)\';E={[math]::Round($_.Free/1GB,1)}}, Root -AutoSize' },
        { id: 'memory', label: 'Top Procs', cmd: 'Get-Process | Sort-Object CPU -Descending | Select-Object -First 10 Id, ProcessName, @{N=\'CPU(s)\';E={[math]::Round($_.CPU,1)}}, @{N=\'Mem(MB)\';E={[math]::Round($_.WorkingSet64/1MB,1)}} | Format-Table -AutoSize' },
        { id: 'toolchains', label: 'Toolchains', cmd: 'java -version; git --version; mvn -v; docker --version; python --version' },
        { id: 'network', label: 'Network Test', cmd: 'Test-NetConnection -ComputerName 8.8.8.8 -Port 53 -InformationLevel Quiet; ping -n 3 8.8.8.8' },
        { id: 'env', label: 'Env Vars', cmd: 'Get-ChildItem env: | Sort-Object Name | Format-Table Name, Value -AutoSize' },
        { id: 'os', label: 'OS & Uptime', cmd: "[PSCustomObject]@{ OS = [System.Environment]::OSVersion.VersionString; Arch = $env:PROCESSOR_ARCHITECTURE; Host = $env:COMPUTERNAME; Uptime = ((Get-Date) - (gcim Win32_OperatingSystem).LastBootUpTime).ToString('d\.hh\:mm\:ss') } | Format-List" },
        { id: 'services', label: 'Agent Services', cmd: 'Get-Service -Name *jenkins*, *docker*, *ssh* -ErrorAction SilentlyContinue | Format-Table Name, Status, DisplayName -AutoSize' }
      ];
    }

    if (isMac) {
      return [
        { id: 'storage', label: 'Disk Space', cmd: 'df -h' },
        { id: 'memory', label: 'Top Procs', cmd: 'ps -A -o pid,%cpu,%mem,comm -r | head -n 12' },
        { id: 'toolchains', label: 'Toolchains', cmd: 'java -version 2>&1; which git >/dev/null 2>&1 && git --version; which brew >/dev/null 2>&1 && brew --version; which xcode-select >/dev/null 2>&1 && xcode-select -p; which docker >/dev/null 2>&1 && docker --version; exit 0' },
        { id: 'network', label: 'Network Ping', cmd: 'ping -c 3 8.8.8.8' },
        { id: 'env', label: 'Env Vars', cmd: 'env | sort | head -n 35' },
        { id: 'os', label: 'macOS & Uptime', cmd: 'sw_vers; echo "---"; uptime; echo "---"; uname -a' }
      ];
    }

    // Linux / Containers / Cloud Agents
    return [
      { id: 'storage', label: 'Disk Space', cmd: 'df -h' },
      { id: 'memory', label: 'Top Procs', cmd: 'which ps >/dev/null 2>&1 && (ps aux --sort=-%cpu 2>/dev/null || ps aux) | head -n 12 || (echo "Load Avg: $(cat /proc/loadavg 2>/dev/null)"; echo "Active Process Count: $(ls -d /proc/[0-9]* 2>/dev/null | wc -l)")' },
      { id: 'toolchains', label: 'Toolchains', cmd: 'java -version 2>&1; which git >/dev/null 2>&1 && git --version; which mvn >/dev/null 2>&1 && mvn -v; which docker >/dev/null 2>&1 && docker --version; which python3 >/dev/null 2>&1 && python3 --version; exit 0' },
      { id: 'network', label: 'Network Ping', cmd: 'which ping >/dev/null 2>&1 && ping -c 3 8.8.8.8 || curl -I -s --connect-timeout 4 https://repo.maven.apache.org | head -n 5' },
      { id: 'env', label: 'Env Vars', cmd: 'env | sort | head -n 35' },
      { id: 'os', label: 'OS & Uptime', cmd: 'uname -a; echo "---"; uptime; [ -f /etc/os-release ] && grep PRETTY_NAME /etc/os-release' }
    ];
  }

  function ensureTerminalModal() {
    if (!el.modalTerminal) {
      el.modalTerminal = document.getElementById('modal-terminal');
    }
    if (!el.modalTerminal) return null;
    if (!el.terminalModalTitle) el.terminalModalTitle = el.modalTerminal.querySelector('#terminal-modal-title');
    if (!el.terminalModalCloseBtn) el.terminalModalCloseBtn = el.modalTerminal.querySelector('#terminal-modal-close-btn');
    if (!el.terminalModalDoneBtn) el.terminalModalDoneBtn = el.modalTerminal.querySelector('#terminal-modal-done-btn');
    if (!el.terminalModalBadges) el.terminalModalBadges = el.modalTerminal.querySelector('#terminal-modal-agent-badges');
    if (!el.terminalPresetsContainer) el.terminalPresetsContainer = el.modalTerminal.querySelector('#terminal-presets-container');
    if (!el.terminalCmdInput) el.terminalCmdInput = el.modalTerminal.querySelector('#terminal-cmd-input');
    if (!el.terminalRunBtn) el.terminalRunBtn = el.modalTerminal.querySelector('#terminal-run-btn');
    if (!el.terminalViewport) el.terminalViewport = el.modalTerminal.querySelector('#terminal-viewport');
    if (!el.terminalWindowTitle) el.terminalWindowTitle = el.modalTerminal.querySelector('#terminal-window-title');
    if (!el.terminalStatusText) el.terminalStatusText = el.modalTerminal.querySelector('#terminal-status-text');
    if (!el.terminalExitBadge) el.terminalExitBadge = el.modalTerminal.querySelector('#terminal-exit-badge');
    if (!el.terminalDuration) el.terminalDuration = el.modalTerminal.querySelector('#terminal-duration');
    if (!el.terminalCopyBtn) el.terminalCopyBtn = el.modalTerminal.querySelector('#terminal-copy-btn');
    if (!el.terminalClearBtn) el.terminalClearBtn = el.modalTerminal.querySelector('#terminal-clear-btn');
    if (!el.terminalModalScriptLink) el.terminalModalScriptLink = el.modalTerminal.querySelector('#terminal-modal-script-link');

    if (!el.modalTerminal._wired) {
      el.modalTerminal._wired = true;
      el.terminalModalCloseBtn?.addEventListener('click', closeTerminalModal);
      el.terminalModalDoneBtn?.addEventListener('click', closeTerminalModal);
      el.modalTerminal.addEventListener('click', function(e) {
        if (e.target === el.modalTerminal) closeTerminalModal();
      });

      el.terminalPresetsContainer?.addEventListener('click', function(e) {
        const pill = e.target.closest('.terminal-preset-pill');
        if (!pill) return;
        const cmd = pill.getAttribute('data-cmd');
        if (cmd) executeTerminalCommand(cmd);
      });

      el.terminalRunBtn?.addEventListener('click', function() {
        executeTerminalCommand(el.terminalCmdInput?.value);
      });

      el.terminalCmdInput?.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
          e.preventDefault();
          executeTerminalCommand(el.terminalCmdInput.value);
        }
      });

      el.terminalClearBtn?.addEventListener('click', function() {
        if (el.terminalViewport) el.terminalViewport.textContent = '$ Ready\n';
        if (el.terminalExitBadge) el.terminalExitBadge.style.display = 'none';
        if (el.terminalDuration) el.terminalDuration.style.display = 'none';
        if (el.terminalStatusText) el.terminalStatusText.textContent = 'Ready';
      });

      el.terminalCopyBtn?.addEventListener('click', function() {
        if (!el.terminalViewport) return;
        copyToClipboard(el.terminalViewport.textContent, function() {
          el.terminalCopyBtn.classList.add('copied');
          const span = el.terminalCopyBtn.querySelector('span');
          if (span) span.textContent = 'Copied!';
          setTimeout(function() {
            el.terminalCopyBtn.classList.remove('copied');
            if (span) span.textContent = 'Copy';
          }, 1800);
        });
      });
    }

    return el.modalTerminal;
  }

  function openTerminalModal(agentName) {
    ensureTerminalModal();
    if (!el.modalTerminal) return;
    const agent = state.agents.find(a => a.name === agentName);
    if (!agent) return;

    state.activeTerminalAgent = agent.name;

    if (el.terminalModalTitle) el.terminalModalTitle.textContent = 'Agent Diagnostic Console: ' + agent.name;
    const isWin = (agent.os && agent.os.family === 'WINDOWS');
    const isMac = (agent.os && agent.os.family === 'MACOS');
    const shellLabel = isWin ? 'PowerShell' : (isMac ? '/bin/sh (macOS)' : '/bin/sh');
    if (el.terminalWindowTitle) el.terminalWindowTitle.textContent = agent.name + ': ' + shellLabel + ' over Remoting';

    const promptSymbol = el.modalTerminal.querySelector('#terminal-prompt-symbol');
    if (promptSymbol) promptSymbol.textContent = isWin ? 'PS >' : '$';

    if (el.terminalCmdInput) {
      if (isWin) {
        el.terminalCmdInput.placeholder = 'Enter PowerShell or CMD diagnostic command (e.g. Get-Process, Get-PSDrive, ipconfig, java -version)...';
      } else if (isMac) {
        el.terminalCmdInput.placeholder = 'Enter shell diagnostic command (e.g. df -h, sw_vers, brew --version, ping)...';
      } else {
        el.terminalCmdInput.placeholder = 'Enter shell diagnostic command (e.g. df -h, ps aux, java -version, ping)...';
      }
    }

    const provBadgeClass = (agent.provisioner && agent.provisioner.badgeClass) || 'badge-static';
    const provName = (agent.provisioner && agent.provisioner.displayName) || 'Static Node';
    const provIcon = getProviderIconSvg(agent.provisioner ? agent.provisioner.id : 'static');
    const osFamily = agent.os ? agent.os.family : 'OTHER';
    const osPretty = agent.os ? agent.os.prettyName : 'Unknown OS';
    const osIcon = getOsIconSvg(osFamily);

    if (el.terminalModalBadges) {
      el.terminalModalBadges.innerHTML = `
        <span class="origin-badge ${provBadgeClass}">${provIcon} ${escapeHtml(provName)}</span>
        <span class="os-badge">${osIcon} ${escapeHtml(osPretty)}</span>
        ${agent.ipAddress ? `<span class="node-meta-chip">IP: ${escapeHtml(agent.ipAddress)}</span>` : ''}
        ${agent.hostName && agent.hostName !== agent.ipAddress ? `<span class="node-meta-chip">Host: ${escapeHtml(agent.hostName)}</span>` : ''}
      `;
    }

    if (el.terminalPresetsContainer) {
      const presets = getDiagnosticPresets(agent);
      el.terminalPresetsContainer.innerHTML = presets.map(p => `
        <button type="button" class="terminal-preset-pill" data-cmd="${escapeHtml(p.cmd)}" title="Run: ${escapeHtml(p.cmd)}">
          ${escapeHtml(p.label)}
        </button>
      `).join('');
    }

    if (el.terminalModalScriptLink) {
      const computerUrl = (ROOT_URL || '') + '/computer/' + encodeURIComponent(agent.name === '(built-in)' ? '(built-in)' : agent.name);
      el.terminalModalScriptLink.href = computerUrl + '/script';
    }

    el.modalTerminal.style.display = 'flex';
    setTimeout(function() {
      if (el.terminalCmdInput) el.terminalCmdInput.focus();
    }, 50);
  }

  function closeTerminalModal() {
    if (el.modalTerminal) el.modalTerminal.style.display = 'none';
  }

  function executeTerminalCommand(cmd) {
    if (!cmd || !cmd.trim()) return;
    const command = cmd.trim();
    if (el.terminalCmdInput) el.terminalCmdInput.value = command;

    if (el.terminalRunBtn) el.terminalRunBtn.disabled = true;
    if (el.terminalCmdInput) el.terminalCmdInput.disabled = true;
    if (el.terminalStatusText) el.terminalStatusText.textContent = 'Executing...';
    if (el.terminalExitBadge) el.terminalExitBadge.style.display = 'none';
    if (el.terminalDuration) el.terminalDuration.style.display = 'none';

    const promptLine = '\n$ ' + command + '\n';
    if (el.terminalViewport) {
      el.terminalViewport.textContent += promptLine;
      el.terminalViewport.scrollTop = el.terminalViewport.scrollHeight;
    }

    const xhr = new XMLHttpRequest();
    xhr.open('POST', ROOT_URL + '/agents/executeDiagnostic', true);
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

    // Attach crumb if present in Jenkins
    if (window.crumb && window.crumb.fieldName && window.crumb.value) {
      xhr.setRequestHeader(window.crumb.fieldName, window.crumb.value);
    }

    const payload = 'name=' + encodeURIComponent(state.activeTerminalAgent) +
                    '&command=' + encodeURIComponent(command) +
                    '&timeout=20';

    xhr.onload = function() {
      if (el.terminalRunBtn) el.terminalRunBtn.disabled = false;
      if (el.terminalCmdInput) {
        el.terminalCmdInput.disabled = false;
        el.terminalCmdInput.focus();
      }

      try {
        const res = JSON.parse(xhr.responseText);
        const data = res.data || res;
        if (res.status === 'ok' || data.status === 'ok') {
          const out = data.output !== undefined ? data.output : (res.output ? res.output : '(No output returned)');
          if (el.terminalViewport) {
            el.terminalViewport.textContent += out + (out.endsWith('\n') ? '' : '\n');
          }
          const exitCode = data.exitCode !== undefined ? data.exitCode : res.exitCode;
          if (el.terminalExitBadge && exitCode !== undefined) {
            terminalExitBadgeDisplay(exitCode);
          }
          const durationMs = data.durationMs !== undefined ? data.durationMs : res.durationMs;
          if (el.terminalDuration && durationMs !== undefined) {
            el.terminalDuration.textContent = durationMs + 'ms';
            el.terminalDuration.style.display = 'inline-block';
          }
          if (el.terminalStatusText) {
            el.terminalStatusText.textContent = (data.timedOut || res.timedOut) ? 'Timed out after 20s' : 'Done';
          }
        } else {
          if (el.terminalViewport) {
            el.terminalViewport.textContent += '[Error: ' + (data.message || res.message || 'Execution failed') + ']\n';
          }
          if (el.terminalStatusText) el.terminalStatusText.textContent = 'Error';
        }
      } catch (err) {
        if (el.terminalViewport) {
          el.terminalViewport.textContent += '[Execution error: HTTP ' + xhr.status + ']\n';
        }
        if (el.terminalStatusText) el.terminalStatusText.textContent = 'Failed';
      }
      if (el.terminalViewport) el.terminalViewport.scrollTop = el.terminalViewport.scrollHeight;
    };

    xhr.onerror = function() {
      if (el.terminalRunBtn) el.terminalRunBtn.disabled = false;
      if (el.terminalCmdInput) {
        el.terminalCmdInput.disabled = false;
        el.terminalCmdInput.focus();
      }
      if (el.terminalViewport) {
        el.terminalViewport.textContent += '[Network error connecting to Jenkins controller]\n';
        el.terminalViewport.scrollTop = el.terminalViewport.scrollHeight;
      }
      if (el.terminalStatusText) el.terminalStatusText.textContent = 'Network Error';
    };

    xhr.send(payload);
  }

  function terminalExitBadgeDisplay(code) {
    if (!el.terminalExitBadge) return;
    el.terminalExitBadge.textContent = 'Exit: ' + code;
    el.terminalExitBadge.className = 'terminal-exit-badge ' + (code === 0 ? 'terminal-exit-success' : 'terminal-exit-error');
    el.terminalExitBadge.style.display = 'inline-block';
  }

  // Modal Dialogs
  function openModal(agentName, targetOffline) {
    if (!el.modalOffline) return;
    el.modalAgentName.value = agentName;
    el.modalTargetState.value = targetOffline ? 'true' : 'false';

    if (targetOffline) {
      el.modalTitle.textContent = 'Take ' + agentName + ' Offline';
      el.modalDesc.textContent = 'Mark this machine temporarily offline. Running jobs will finish, but no new builds will be scheduled on it:';
      if (el.modalReasonContainer) el.modalReasonContainer.style.display = 'flex';
      if (el.modalReason) {
        el.modalReason.value = 'Scheduled maintenance and health inspection';
      }
      el.modalConfirmBtn.textContent = 'Take Offline';
      el.modalConfirmBtn.className = 'jenkins-button jenkins-button--primary';
      if (el.modalHeaderIcon) {
        el.modalHeaderIcon.innerHTML = '<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>';
        el.modalHeaderIcon.style.color = 'var(--fl-amber-warning)';
      }
      setTimeout(function() {
        if (el.modalReason) {
          el.modalReason.focus();
          el.modalReason.select();
        }
      }, 50);
    } else {
      el.modalTitle.textContent = 'Bring ' + agentName + ' Online';
      el.modalDesc.textContent = 'Clear maintenance offline status and allow Jenkins to resume dispatching queued builds to this machine.';
      if (el.modalReasonContainer) el.modalReasonContainer.style.display = 'none';
      el.modalConfirmBtn.textContent = 'Bring Online';
      el.modalConfirmBtn.className = 'jenkins-button jenkins-button--primary';
      if (el.modalHeaderIcon) {
        el.modalHeaderIcon.innerHTML = '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>';
        el.modalHeaderIcon.style.color = 'var(--fl-green-online)';
      }
    }

    el.modalOffline.style.display = 'flex';
  }

  function closeModal() {
    if (el.modalOffline) {
      el.modalOffline.style.display = 'none';
    }
  }

  function handleModalConfirm() {
    const name = el.modalAgentName.value;
    const isOffline = el.modalTargetState.value === 'true';
    const reason = el.modalReason ? el.modalReason.value.trim() : '';

    el.modalConfirmBtn.disabled = true;
    el.modalConfirmBtn.textContent = isOffline ? 'Taking Offline...' : 'Bringing Online...';

    const xhr = new XMLHttpRequest();
    xhr.open('POST', ROOT_URL + '/agents/toggleOffline', true);
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

    // Attach crumb if present in Jenkins
    if (window.crumb && window.crumb.fieldName && window.crumb.value) {
      xhr.setRequestHeader(window.crumb.fieldName, window.crumb.value);
    }

    const payload = 'name=' + encodeURIComponent(name) +
                    '&offline=' + isOffline +
                    '&reason=' + encodeURIComponent(reason);

    xhr.onload = function() {
      el.modalConfirmBtn.disabled = false;
      closeModal();
      fetchFleetData();
    };

    xhr.onerror = function() {
      el.modalConfirmBtn.disabled = false;
      alert('Error updating agent state');
      closeModal();
    };

    xhr.send(payload);
  }

  function getProviderIconSvg(id) {
    switch (id) {
      case 'docker':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>';
      case 'kubernetes':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"></circle><path d="M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"></path></svg>';
      case 'aws-ec2':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path></svg>';
      case 'azure':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>';
      case 'gcp':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"></path></svg>';
      case 'proxmox':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2"></rect><rect x="2" y="14" width="20" height="8" rx="2"></rect><line x1="6" y1="6" x2="6.01" y2="6"></line><line x1="6" y1="18" x2="6.01" y2="18"></line></svg>';
      case 'vsphere':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"></path><path d="M2 17l10 5 10-5"></path><path d="M2 12l10 5 10-5"></path></svg>';
      case 'swarm':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l8 4.5v9L12 20l-8-4.5v-9L12 2z"></path><path d="M12 12l8-4.5M12 12v8M12 12L4 7.5"></path></svg>';
      case 'openstack':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="4" rx="1"></rect><rect x="4" y="10" width="16" height="4" rx="1"></rect><rect x="4" y="16" width="16" height="4" rx="1"></rect></svg>';
      case 'nomad':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>';
      case 'openshift':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"></circle><path d="M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"></path></svg>';
      case 'oci':
      case 'digitalocean':
      case 'hetzner':
      case 'aliyun':
      case 'linode':
      case 'cloud':
      case 'aws-ecs':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path></svg>';
      case 'controller':
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="2"></rect><rect x="9" y="9" width="6" height="6"></rect><line x1="9" y1="1" x2="9" y2="4"></line><line x1="15" y1="1" x2="15" y2="4"></line><line x1="9" y1="20" x2="9" y2="23"></line><line x1="15" y1="20" x2="15" y2="23"></line><line x1="20" y1="9" x2="23" y2="9"></line><line x1="20" y1="14" x2="23" y2="14"></line><line x1="1" y1="9" x2="4" y2="9"></line><line x1="1" y1="14" x2="4" y2="14"></line></svg>';
      default:
        return '<svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>';
    }
  }

  function getOsIconSvg(family) {
    switch (family) {
      case 'LINUX':
        return '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 448 512" fill="currentColor"><path d="M220.8 123.3c1 .5 1.8 1.7 3 1.7 1.1 0 2.8-.4 2.9-1.5.2-1.4-1.9-2.3-3.2-2.9-1.7-.7-3.9-1-5.5-.1-.4.2-.8.7-.6 1.1.3 1.3 2.3 1.1 3.4 1.7zm-21.9 1.7c1.2 0 2-1.2 3-1.7 1.1-.6 3.1-.4 3.5-1.6.2-.4-.2-.9-.6-1.1-1.6-.9-3.8-.6-5.5.1-1.3.6-3.4 1.5-3.2 2.9.1 1 1.8 1.5 2.8 1.4zM420 403.8c-3.6-4-5.3-11.6-7.2-19.7-1.8-8.1-3.9-16.8-10.5-22.4-1.3-1.1-2.6-2.1-4-2.9-1.3-.8-2.7-1.5-4.1-2 9.2-27.3 5.6-54.5-3.7-79.1-11.4-30.1-31.3-56.4-46.5-74.4-17.1-21.5-33.7-41.9-33.4-72C311.1 85.4 315.7.1 234.8 0 132.4-.2 158 103.4 156.9 135.2c-1.7 23.4-6.4 41.8-22.5 64.7-18.9 22.5-45.5 58.8-58.1 96.7-6 17.9-8.8 36.1-6.2 53.3-6.5 5.8-11.4 14.7-16.6 20.2-4.2 4.3-10.3 5.9-17 8.3s-14 6-18.5 14.5c-2.1 3.9-2.8 8.1-2.8 12.4 0 3.9.6 7.9 1.2 11.8 1.2 8.1 2.5 15.7.8 20.8-5.2 14.4-5.9 24.4-2.2 31.7 3.8 7.3 11.4 10.5 20.1 12.3 17.3 3.6 40.8 2.7 59.3 12.5 19.8 10.4 39.9 14.1 55.9 10.4 11.6-2.6 21.1-9.6 25.9-20.2 12.5-.1 26.3-5.4 48.3-6.6 14.9-1.2 33.6 5.3 55.1 4.1.6 2.3 1.4 4.6 2.5 6.7v.1c8.3 16.7 23.8 24.3 40.3 23 16.6-1.3 34.1-11 48.3-27.9 13.6-16.4 36-23.2 50.9-32.2 7.4-4.5 13.4-10.1 13.9-18.3.4-8.2-4.4-17.3-15.5-29.7zM223.7 87.3c9.8-22.2 34.2-21.8 44-.4 6.5 14.2 3.6 30.9-4.3 40.4-1.6-.8-5.9-2.6-12.6-4.9 1.1-1.2 3.1-2.7 3.9-4.6 4.8-11.8-.2-27-9.1-27.3-7.3-.5-13.9 10.8-11.8 23-4.1-2-9.4-3.5-13-4.4-1-6.9-.3-14.6 2.9-21.8zM183 75.8c10.1 0 20.8 14.2 19.1 33.5-3.5 1-7.1 2.5-10.2 4.6 1.2-8.9-3.3-20.1-9.6-19.6-8.4.7-9.8 21.2-1.8 28.1 1 .8 1.9-.2-5.9 5.5-15.6-14.6-10.5-52.1 8.4-52.1zm-13.6 60.7c6.2-4.6 13.6-10 14.1-10.5 4.7-4.4 13.5-14.2 27.9-14.2 7.1 0 15.6 2.3 25.9 8.9 6.3 4.1 11.3 4.4 22.6 9.3 8.4 3.5 13.7 9.7 10.5 18.2-2.6 7.1-11 14.4-22.7 18.1-11.1 3.6-19.8 16-38.2 14.9-3.9-.2-7-1-9.6-2.1-8-3.5-12.2-10.4-20-15-8.6-4.8-13.2-10.4-14.7-15.3-1.4-4.9 0-9 4.2-12.3zm3.3 334c-2.7 35.1-43.9 34.4-75.3 18-29.9-15.8-68.6-6.5-76.5-21.9-2.4-4.7-2.4-12.7 2.6-26.4v-.2c2.4-7.6.6-16-.6-23.9-1.2-7.8-1.8-15 .9-20 3.5-6.7 8.5-9.1 14.8-11.3 10.3-3.7 11.8-3.4 19.6-9.9 5.5-5.7 9.5-12.9 14.3-18 5.1-5.5 10-8.1 17.7-6.9 8.1 1.2 15.1 6.8 21.9 16l19.6 35.6c9.5 19.9 43.1 48.4 41 68.9zm-1.4-25.9c-4.1-6.6-9.6-13.6-14.4-19.6 7.1 0 14.2-2.2 16.7-8.9 2.3-6.2 0-14.9-7.4-24.9-13.5-18.2-38.3-32.5-38.3-32.5-13.5-8.4-21.1-18.7-24.6-29.9s-3-23.3-.3-35.2c5.2-22.9 18.6-45.2 27.2-59.2 2.3-1.7.8 3.2-8.7 20.8-8.5 16.1-24.4 53.3-2.6 82.4.6-20.7 5.5-41.8 13.8-61.5 12-27.4 37.3-74.9 39.3-112.7 1.1.8 4.6 3.2 6.2 4.1 4.6 2.7 8.1 6.7 12.6 10.3 12.4 10 28.5 9.2 42.4 1.2 6.2-3.5 11.2-7.5 15.9-9 9.9-3.1 17.8-8.6 22.3-15 7.7 30.4 25.7 74.3 37.2 95.7 6.1 11.4 18.3 35.5 23.6 64.6 3.3-.1 7 .4 10.9 1.4 13.8-35.7-11.7-74.2-23.3-84.9-4.7-4.6-4.9-6.6-2.6-6.5 12.6 11.2 29.2 33.7 35.2 59 2.8 11.6 3.3 23.7.4 35.7 16.4 6.8 35.9 17.9 30.7 34.8-2.2-.1-3.2 0-4.2 0 3.2-10.1-3.9-17.6-22.8-26.1-19.6-8.6-36-8.6-38.3 12.5-12.1 4.2-18.3 14.7-21.4 27.3-2.8 11.2-3.6 24.7-4.4 39.9-.5 7.7-3.6 18-6.8 29-32.1 22.9-76.7 32.9-114.3 7.2zm257.4-11.5c-.9 16.8-41.2 19.9-63.2 46.5-13.2 15.7-29.4 24.4-43.6 25.5s-26.5-4.8-33.7-19.3c-4.7-11.1-2.4-23.1 1.1-36.3 3.7-14.2 9.2-28.8 9.9-40.6.8-15.2 1.7-28.5 4.2-38.7 2.6-10.3 6.6-17.2 13.7-21.1.3-.2.7-.3 1-.5.8 13.2 7.3 26.6 18.8 29.5 12.6 3.3 30.7-7.5 38.4-16.3 9-.3 15.7-.9 22.6 5.1 9.9 8.5 7.1 30.3 17.1 41.6 10.6 11.6 14 19.5 13.7 24.6zM173.3 148.7c2 1.9 4.7 4.5 8 7.1 6.6 5.2 15.8 10.6 27.3 10.6 11.6 0 22.5-5.9 31.8-10.8 4.9-2.6 10.9-7 14.8-10.4s5.9-6.3 3.1-6.6-2.6 2.6-6 5.1c-4.4 3.2-9.7 7.4-13.9 9.8-7.4 4.2-19.5 10.2-29.9 10.2s-18.7-4.8-24.9-9.7c-3.1-2.5-5.7-5-7.7-6.9-1.5-1.4-1.9-4.6-4.3-4.9-1.4-.1-1.8 3.7 1.7 6.5z"/></svg>';
      case 'WINDOWS':
        return '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M0 3.449L9.75 2.1v9.451H0V3.449zm10.949-1.651L24 0v11.4H10.949V1.798zM0 12.6h9.75v9.451L0 20.699V12.6zm10.949 0H24V24l-13.051-1.801V12.6z"/></svg>';
      case 'MACOS':
        return '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M15.97 6.43c.66-.82 1.11-1.96.99-3.1-.96.04-2.12.64-2.8 1.44-.61.71-1.14 1.87-1 2.99 1.07.08 2.16-.54 2.81-1.33z"/></svg>';
      default:
        return '<svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>';
    }
  }

  function copyToClipboard(text, onSuccess, onError) {
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(onSuccess).catch(function() {
        fallbackCopy(text, onSuccess, onError);
      });
    } else {
      fallbackCopy(text, onSuccess, onError);
    }
  }

  function fallbackCopy(text, onSuccess, onError) {
    try {
      const textArea = document.createElement('textarea');
      textArea.value = text;
      textArea.style.position = 'fixed';
      textArea.style.left = '-999999px';
      textArea.style.top = '-999999px';
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();
      const successful = document.execCommand('copy');
      document.body.removeChild(textArea);
      if (successful && onSuccess) onSuccess();
      else if (!successful && onError) onError();
    } catch (err) {
      if (onError) onError(err);
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // Start app on DOMContentLoaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
