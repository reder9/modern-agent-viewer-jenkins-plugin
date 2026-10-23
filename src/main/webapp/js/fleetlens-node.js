/**
 * FleetLens - Modern Agent Node Inspector
 * Renders a state-of-the-art telemetry control center directly on /computer/{name}/
 * Zero emojis, fully WCAG AAA/AA compliant in dark and light modes.
 */
(function() {
  'use strict';

  // Prevent multiple injections
  if (window.__fleetlens_node_initialized) return;
  window.__fleetlens_node_initialized = true;

  // Configuration and state
  const scriptTag = document.querySelector('script[src*="fleetlens-node.js"]');
  const ROOT_URL = (scriptTag && scriptTag.getAttribute('data-root-url')) ||
                   (document.querySelector('[data-root-url]') ? document.querySelector('[data-root-url]').getAttribute('data-root-url') : '');

  let agentName = window.__FLEETLENS_AGENT_NAME__ || (scriptTag && scriptTag.getAttribute('data-agent-name')) || '';
  if (!agentName) {
    const m = window.location.pathname.match(/\/computer\/([^/]+)\/?/);
    if (m) {
      try {
        agentName = decodeURIComponent(m[1]);
      } catch (e) {
        agentName = m[1];
      }
    }
  }

  if (!agentName || agentName === 'new') return;

  const state = {
    agentName: agentName,
    agentData: window.__FLEETLENS_PRELOADED_AGENT__ || null,
    loading: !window.__FLEETLENS_PRELOADED_AGENT__,
    error: null,
    timerId: null,
    viewMode: localStorage.getItem('fleetlens_node_view') || 'modern', // 'modern' or 'classic'
    refreshInterval: 15
  };

  // URL override: ?view=classic or ?view=modern
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has('view')) {
    state.viewMode = urlParams.get('view') === 'classic' ? 'classic' : 'modern';
  } else if (urlParams.has('legacy') && urlParams.get('legacy') === 'true') {
    state.viewMode = 'classic';
  }

  // DOM Elements cache
  let container = null;
  let classicElements = [];

  function init() {
    const mainPanel = document.getElementById('main-panel');
    if (!mainPanel) return;

    // Build container
    container = document.createElement('div');
    container.id = 'fleetlens-node-inspector';
    container.className = 'fleetlens-node-root';
    mainPanel.insertBefore(container, mainPanel.firstChild);

    // Identify classic elements in main-panel to hide when in modern view
    const children = Array.from(mainPanel.children);
    classicElements = children.filter(c => c !== container && c.id !== 'breadcrumbBar');

    applyViewMode(state.viewMode);
    fetchAgentTelemetry();

    // Set auto-refresh
    state.timerId = setInterval(fetchAgentTelemetry, state.refreshInterval * 1000);
  }

  function applyViewMode(mode) {
    state.viewMode = mode;
    localStorage.setItem('fleetlens_node_view', mode);

    if (mode === 'modern') {
      classicElements.forEach(el => {
        el.setAttribute('data-fleetlens-hidden', 'true');
        el.style.display = 'none';
      });
      if (container) container.classList.remove('classic-mode-collapsed');
    } else {
      classicElements.forEach(el => {
        if (el.getAttribute('data-fleetlens-hidden') === 'true') {
          el.removeAttribute('data-fleetlens-hidden');
          el.style.display = '';
        }
      });
      if (container) container.classList.add('classic-mode-collapsed');
    }

    if (state.agentData) {
      render();
    }
  }

  function fetchAgentTelemetry(callback) {
    const apiUrl = ROOT_URL + '/agents/agent?name=' + encodeURIComponent(state.agentName);
    const xhr = new XMLHttpRequest();
    xhr.open('GET', apiUrl, true);
    xhr.withCredentials = true;
    xhr.setRequestHeader('Accept', 'application/json');

    xhr.onload = function() {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const resp = JSON.parse(xhr.responseText);
          if (resp.status === 'ok' && resp.data) {
            state.agentData = resp.data;
            state.loading = false;
            state.error = null;
            render();
          } else {
            state.error = resp.message || 'Unable to load agent telemetry';
          }
        } catch (e) {
          state.error = 'Invalid JSON response';
        }
      } else {
        state.error = 'HTTP ' + xhr.status + ' loading agent data';
      }
      if (callback) callback();
    };

    xhr.onerror = function() {
      state.error = 'Network error contacting Modern Agent Viewer telemetry';
      if (callback) callback();
    };

    xhr.send();
  }

  function render() {
    if (!container) return;

    if (state.viewMode === 'classic') {
      renderClassicBar();
      return;
    }

    if (state.loading) {
      container.innerHTML = `
        <div class="node-loading-box">
          <div class="loading-spinner"></div>
          <span>Loading live agent telemetry for ${escapeHtml(state.agentName)}...</span>
        </div>
      `;
      return;
    }

    if (state.error || !state.agentData) {
      container.innerHTML = `
        <div class="node-error-box">
          <svg class="fl-svg-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
          <div>
            <strong>Telemetry Unavailable</strong>
            <p>${escapeHtml(state.error || 'Agent data could not be retrieved.')}</p>
          </div>
          <button class="jenkins-button jenkins-button--tertiary btn-switch-classic">Switch to Classic View</button>
        </div>
      `;
      container.querySelector('.btn-switch-classic')?.addEventListener('click', () => applyViewMode('classic'));
      return;
    }

    const agent = state.agentData;

    // Status classes and text
    let statusDotClass = 'status-offline';
    let statusLabel = 'OFFLINE';
    let statusPillClass = 'status-pill-offline';

    if (agent.isTemporarilyOffline) {
      statusDotClass = 'status-temp-offline';
      statusLabel = 'TEMPORARILY OFFLINE';
      statusPillClass = 'status-pill-temp-offline';
    } else if (agent.status === 'BUSY') {
      statusDotClass = 'status-busy';
      statusLabel = 'BUSY';
      statusPillClass = 'status-pill-busy';
    } else if (agent.isOnline) {
      statusDotClass = 'status-online';
      statusLabel = 'ONLINE & READY';
      statusPillClass = 'status-pill-online';
    }

    // Provider origin
    const provBadgeClass = (agent.provisioner && agent.provisioner.badgeClass) || 'badge-static';
    const provName = (agent.provisioner && agent.provisioner.displayName) || 'Static Node';
    const provIcon = getProviderIconSvg(agent.provisioner ? agent.provisioner.id : 'static');

    // OS
    const osFamily = agent.os ? agent.os.family : 'OTHER';
    const osPretty = agent.os ? agent.os.prettyName : 'Unknown OS';
    const osArch = (agent.os && agent.os.arch) ? agent.os.arch : '';
    const osIcon = getOsIconSvg(osFamily);

    // Storage
    const storage = agent.storage || {};
    const percentUsed = storage.percentUsed >= 0 ? storage.percentUsed : 0;
    let fillClass = 'fill-healthy';
    let storageHealthBadge = 'status-badge-healthy';
    if (storage.healthStatus === 'CRITICAL') {
      fillClass = 'fill-critical';
      storageHealthBadge = 'status-badge-critical';
    } else if (storage.healthStatus === 'WARNING') {
      fillClass = 'fill-warning';
      storageHealthBadge = 'status-badge-warning';
    }

    // Time metrics
    const tm = agent.timeMetrics || {};
    const connDuration = tm.connectedDurationHuman || '--';
    const hostUptime = tm.hostUptimeHuman || '--';

    // Workload
    const activeExecutors = agent.executors ? agent.executors.filter(e => e.busy).length : 0;
    const totalExecutors = agent.numExecutors || 0;

    const computerUrl = ROOT_URL + '/computer/' + encodeURIComponent(agent.name === '(built-in)' ? '(built-in)' : agent.name);

    let html = `
      <!-- Top Bar: Title, Badges, View Toggle -->
      <div class="node-hero-card">
        <div class="node-hero-header">
          <div class="node-title-group">
            <div class="node-headline">
              <span class="status-indicator ${statusDotClass}" style="width: 12px; height: 12px;"></span>
              <h1 class="node-title">${escapeHtml(agent.displayName || agent.name)}</h1>
              <button class="fl-copy-icon-btn btn-copy" data-copy="${escapeHtml(agent.name)}" title="Copy Agent Name: ${escapeHtml(agent.name)}" type="button">
                <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
              </button>
              <span class="node-status-pill ${statusPillClass}">${statusLabel}</span>
            </div>
            <div class="node-badge-row">
              <span class="origin-badge ${provBadgeClass}">${provIcon} ${escapeHtml(provName)}</span>
              <span class="os-badge">${osIcon} ${escapeHtml(osPretty)}</span>
              ${osArch ? `<span class="arch-chip">${escapeHtml(osArch)}</span>` : ''}
              ${agent.ipAddress ? `
                <button class="fl-copy-chip btn-copy btn-copy-ip" data-copy="${escapeHtml(agent.ipAddress)}" title="Click to copy IP: ${escapeHtml(agent.ipAddress)}" type="button">
                  <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                  <span>IP: <strong class="copy-text">${escapeHtml(agent.ipAddress)}</strong></span>
                </button>
              ` : ''}
              ${agent.hostName && agent.hostName !== agent.ipAddress ? `
                <button class="fl-copy-chip btn-copy" data-copy="${escapeHtml(agent.hostName)}" title="Click to copy Host Address: ${escapeHtml(agent.hostName)}" type="button">
                  <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                  <span>Host: <strong class="copy-text">${escapeHtml(agent.hostName)}</strong></span>
                </button>
              ` : ''}
              ${agent.remoteFs ? `
                <button class="fl-copy-chip btn-copy" data-copy="${escapeHtml(agent.remoteFs)}" title="Click to copy Remote Root: ${escapeHtml(agent.remoteFs)}" type="button">
                  <svg class="fl-svg-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                  <span>Root: <strong class="copy-text">${escapeHtml(agent.remoteFs)}</strong></span>
                </button>
              ` : ''}
            </div>
          </div>

          <div class="node-controls-group">
            <div class="view-switcher" style="margin-bottom: 6px;">
              <button class="view-btn active" id="btn-view-modern" type="button">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>
                Modern Inspector
              </button>
              <button class="view-btn" id="btn-view-classic" type="button">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"></line><line x1="8" y1="12" x2="21" y2="12"></line><line x1="8" y1="18" x2="21" y2="18"></line><line x1="3" y1="6" x2="3.01" y2="6"></line><line x1="3" y1="12" x2="3.01" y2="12"></line><line x1="3" y1="18" x2="3.01" y2="18"></line></svg>
                Classic Details
              </button>
            </div>
            <div class="node-toolbar-clusters">
              <!-- Cluster 1: Operational Tools -->
              <div class="toolbar-cluster" title="Operational & Diagnostic Tools">
                <button class="jenkins-button jenkins-button--tertiary btn-connect-node" id="btn-node-connect" type="button" title="View SSH, CLI, and terminal connect commands">
                  <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>
                  Connect
                </button>
                ${agent.canConfigure ? `
                  <button class="jenkins-button jenkins-button--tertiary btn-terminal-node" id="btn-node-terminal" type="button" title="Interactive Agent Diagnostic Console">
                    <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>
                    Diagnostics
                  </button>
                ` : ''}
                <a href="${computerUrl}/log" class="jenkins-button jenkins-button--tertiary" title="View agent logs">
                  <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>
                  Agent Log
                </a>
              </div>

              <!-- Cluster 2: Machine Management -->
              <div class="toolbar-cluster" title="Agent Lifecycle & Configuration">
                <a href="${computerUrl}/configure" class="jenkins-button jenkins-button--tertiary" title="Configure agent settings">
                  <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
                  Configure
                </a>
                ${agent.canConfigure && agent.name !== '(built-in)' ? `
                  ${agent.isTemporarilyOffline ?
                    `<button class="jenkins-button jenkins-button--tertiary btn-toggle-online" id="btn-action-online" type="button" title="Bring agent online">
                       <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
                       Bring Online
                     </button>` :
                    `<button class="jenkins-button jenkins-button--tertiary btn-toggle-offline" id="btn-action-offline" type="button" title="Take agent temporarily offline">
                       <svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>
                       Take Offline
                     </button>`
                  }
                ` : ''}
              </div>

              <!-- Cluster 3: Navigation & Utilities -->
              <div class="toolbar-cluster" title="Navigation & Telemetry">
                <button class="jenkins-button jenkins-button--tertiary btn-icon-only" id="btn-node-refresh" type="button" title="Refresh live telemetry">
                  <svg class="spin-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"></path><path d="M1 20v-6h6"></path><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>
                </button>
                <a href="${ROOT_URL}/agents?search=${encodeURIComponent(agent.name)}" class="jenkins-button jenkins-button--tertiary btn-icon-only" title="Open full agent view">
                  <svg class="fl-svg-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 3 21 3 21 9"></polyline><polyline points="9 21 3 21 3 15"></polyline><line x1="21" y1="3" x2="14" y2="10"></line><line x1="3" y1="21" x2="10" y2="14"></line></svg>
                </a>
              </div>
            </div>
          </div>
        </div>

        ${agent.isTemporarilyOffline ? `
          <div class="agent-offline-reason-banner" style="margin: 16px 0 0;">
            <svg class="fl-svg-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
            <div class="offline-reason-content">
              <span class="offline-reason-label">MAINTENANCE IN PROGRESS</span>
              <span class="offline-reason-text">${escapeHtml(agent.offlineCauseReason || agent.offlineCause || 'Marked offline for maintenance')}</span>
            </div>
          </div>
        ` : ''}
      </div>

      <!-- Telemetry Metrics Grid -->
      <div class="node-metrics-grid">
        <!-- Storage Card -->
        <div class="node-metric-card">
          <div class="metric-card-header">
            <div class="metric-title-wrap">
              <svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="12" x2="2" y2="12"></line><path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"></path><line x1="6" y1="16" x2="6.01" y2="16"></line><line x1="10" y1="16" x2="10.01" y2="16"></line></svg>
              <span>Storage &amp; Disk Health</span>
            </div>
            <span class="storage-health-pill ${storageHealthBadge}">${escapeHtml(storage.healthStatus || 'HEALTHY')}</span>
          </div>
          <div class="metric-big-num">${percentUsed}% <span class="metric-big-sub">Used</span></div>
          <div class="progress-track" style="margin: 8px 0 10px; height: 9px;">
            <div class="progress-fill ${fillClass}" style="width: ${percentUsed}%;"></div>
          </div>
          <div class="metric-kv-list">
            <div class="metric-kv-row">
              <span class="kv-k">Used / Total:</span>
              <span class="kv-v">${escapeHtml(storage.usedHuman || '--')} / ${escapeHtml(storage.totalHuman || '--')}</span>
            </div>
            <div class="metric-kv-row">
              <span class="kv-k">Available Free:</span>
              <span class="kv-v text-highlight">${escapeHtml(storage.freeHuman || '--')}</span>
            </div>
            ${storage.tempAvailable ? `
              <div class="metric-kv-row">
                <span class="kv-k">Temp Free (/tmp):</span>
                <span class="kv-v">${escapeHtml(storage.tempFreeHuman)}</span>
              </div>
            ` : ''}
            <div class="metric-kv-row">
              <span class="kv-k">Remote Root:</span>
              <span class="kv-v">
                <button class="fl-copy-chip btn-copy" data-copy="${escapeHtml(storage.rootPath || agent.remoteFs || '')}" title="Click to copy root path" type="button">
                  <span class="code-text copy-text">${escapeHtml(storage.rootPath || agent.remoteFs || '--')}</span>
                  <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                </button>
              </span>
            </div>
          </div>
        </div>

        <!-- Dual Clock Uptime Card -->
        <div class="node-metric-card">
          <div class="metric-card-header">
            <div class="metric-title-wrap">
              <svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              <span>Dual Uptime &amp; Remoting</span>
            </div>
            ${agent.responseTimeMs ? `<span class="node-meta-chip">Ping: ${agent.responseTimeMs} ms</span>` : ''}
          </div>
          <div class="metric-big-num">${escapeHtml(connDuration)} <span class="metric-big-sub">Session</span></div>
          <div class="progress-track" style="margin: 8px 0 10px; height: 9px; opacity: 0.4;">
            <div class="progress-fill fill-healthy" style="width: 100%;"></div>
          </div>
          <div class="metric-kv-list">
            <div class="metric-kv-row">
              <span class="kv-k">Host OS Uptime:</span>
              <span class="kv-v text-highlight">${escapeHtml(hostUptime)}</span>
            </div>
            <div class="metric-kv-row">
              <span class="kv-k">Remoting Status:</span>
              <span class="kv-v">${agent.isOnline ? 'Active Remoting Channel' : 'Disconnected'}</span>
            </div>
            <div class="metric-kv-row">
              <span class="kv-k">Clock Difference:</span>
              <span class="kv-v">${agent.clockDifferenceMs !== undefined ? `${agent.clockDifferenceMs} ms (In sync)` : 'In sync'}</span>
            </div>
            ${agent.ipAddress ? `
              <div class="metric-kv-row">
                <span class="kv-k">IP Address:</span>
                <span class="kv-v">
                  <button class="fl-copy-chip btn-copy-ip-inline" data-copy="${escapeHtml(agent.ipAddress)}" title="Click to copy IP" type="button">
                    <span class="code-text copy-text">${escapeHtml(agent.ipAddress)}</span>
                    <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                  </button>
                </span>
              </div>
            ` : ''}
            ${agent.hostName ? `
              <div class="metric-kv-row">
                <span class="kv-k">Host Address:</span>
                <span class="kv-v">
                  <button class="fl-copy-chip btn-copy" data-copy="${escapeHtml(agent.hostName)}" title="Click to copy host" type="button">
                    <span class="code-text copy-text">${escapeHtml(agent.hostName)}</span>
                    <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                  </button>
                </span>
              </div>
            ` : ''}
          </div>
        </div>

        <!-- Workload & Capacity Card -->
        <div class="node-metric-card">
          <div class="metric-card-header">
            <div class="metric-title-wrap">
              <svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
              <span>Workload &amp; Executors</span>
            </div>
            <span class="node-meta-chip">${activeExecutors} of ${totalExecutors} Active</span>
          </div>
          <div class="metric-big-num">${activeExecutors} <span class="metric-big-sub">Busy / ${totalExecutors} Slots</span></div>
          <div class="exec-slots-row" style="margin: 12px 0;">
            ${renderExecutorSlots(agent.executors)}
          </div>
          <div class="metric-kv-list">
            <div class="metric-kv-row">
              <span class="kv-k">Executor Mode:</span>
              <span class="kv-v">Normal (Shared builds)</span>
            </div>
            <div class="metric-kv-row">
              <span class="kv-k">Idle Capacity:</span>
              <span class="kv-v text-highlight">${Math.max(0, totalExecutors - activeExecutors)} Slots Free</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Specifications & Infrastructure Row -->
      <div class="node-details-grid">
        <!-- Infrastructure & Cloud Details -->
        <div class="node-panel-card">
          <div class="panel-card-title">
            <svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path></svg>
            Infrastructure &amp; Cloud Specifications
          </div>
          <div class="panel-details-table">
            <div class="details-row">
              <span class="details-label">Origin / Provisioner:</span>
              <span class="details-val"><strong>${escapeHtml(provName)}</strong> (${escapeHtml(agent.provisioner ? agent.provisioner.id : 'static')})</span>
            </div>
            <div class="details-row">
              <span class="details-label">Operating System:</span>
              <span class="details-val">${escapeHtml(osPretty)}</span>
            </div>
            <div class="details-row">
              <span class="details-label">CPU Architecture:</span>
              <span class="details-val">${escapeHtml(agent.os ? agent.os.arch : '--')}</span>
            </div>
            <div class="details-row">
              <span class="details-label">OS Kernel / Version:</span>
              <span class="details-val code-text">${escapeHtml(agent.os ? agent.os.version : '--')}</span>
            </div>
            ${agent.ipAddress ? `
              <div class="details-row">
                <span class="details-label">Agent IP Address:</span>
                <span class="details-val">
                  <button class="fl-copy-chip btn-copy-ip-inline" data-copy="${escapeHtml(agent.ipAddress)}" title="Click to copy IP" type="button">
                    <span class="code-text copy-text">${escapeHtml(agent.ipAddress)}</span>
                    <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                  </button>
                </span>
              </div>
            ` : ''}
            ${renderProvisionerDetails(agent.provisioner ? agent.provisioner.details : null)}
          </div>
        </div>

        <!-- Labels & Assignments -->
        <div class="node-panel-card">
          <div class="panel-card-title" style="justify-content: space-between;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <svg class="fl-svg-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"></path><line x1="7" y1="7" x2="7.01" y2="7"></line></svg>
              <span>Assigned Labels (${agent.labels ? agent.labels.length : 0})</span>
            </div>
            ${agent.labels && agent.labels.length > 0 ? `
              <button class="btn-copy-code btn-copy" data-copy="${escapeHtml(agent.labels.join(' '))}" title="Copy all labels space-separated" type="button" style="font-size: 11px; padding: 2px 8px;">
                <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                Copy All
              </button>
            ` : ''}
          </div>
          <div class="node-labels-container">
            ${agent.labels && agent.labels.length > 0 ?
              agent.labels.map(l => `<button class="agent-label-pill btn-copy" data-copy="${escapeHtml(l)}" title="Click to copy label: ${escapeHtml(l)}" type="button">${escapeHtml(l)}</button>`).join('') :
              '<span class="text-sub">No explicit labels configured for this machine.</span>'
            }
          </div>
          <div class="panel-footer-hint">
            Labels are used by pipeline and freestyle jobs to dispatch workloads to this agent. Click any label to copy.
          </div>
        </div>
      </div>

      <!-- Offline Modal -->
      <div id="node-modal-offline" class="fleet-modal-backdrop" style="display: none;">
        <div class="fleet-modal" role="dialog" aria-modal="true" aria-labelledby="node-modal-title">
          <div class="modal-header">
            <div class="modal-title-wrap">
              <svg id="node-modal-header-icon" class="modal-header-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>
              </svg>
              <h3 id="node-modal-title">Take ${escapeHtml(agent.name)} Offline</h3>
            </div>
            <button class="modal-close" id="node-modal-close-btn" aria-label="Close dialog" type="button">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <p id="node-modal-desc" class="modal-description">Mark this machine temporarily offline. Running jobs will finish, but no new builds will be scheduled on it:</p>
            
            <div id="node-modal-reason-container" class="modal-reason-group">
              <div class="modal-label-row">
                <label for="node-modal-offline-reason" class="modal-field-label">Offline Reason</label>
                <span class="modal-field-hint">Visible in queue bottlenecks and agent status</span>
              </div>

              <div class="modal-presets-row" id="node-modal-presets">
                <span class="preset-label">Quick Presets:</span>
                <button type="button" class="preset-pill" data-preset="Scheduled OS maintenance and security patching">OS Patching</button>
                <button type="button" class="preset-pill" data-preset="Workspace disk cleanup and cache purge in progress">Disk Cleanup</button>
                <button type="button" class="preset-pill" data-preset="Upgrading build tooling, SDKs, and runtime dependencies">Tooling Upgrade</button>
                <button type="button" class="preset-pill" data-preset="Investigating persistent build environment failure">Investigation</button>
              </div>

              <textarea id="node-modal-offline-reason"
                        class="fleet-textarea"
                        rows="3"
                        placeholder="Provide a clear reason for your team (e.g. Upgrading kernel, ETA 30 minutes)..."></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button class="jenkins-button jenkins-button--tertiary" id="node-modal-cancel-btn" type="button">Cancel</button>
            <button class="jenkins-button jenkins-button--primary" id="node-modal-confirm-btn" type="button">Take Offline</button>
          </div>
        </div>
      </div>

      <!-- Connect Modal -->
      <div id="node-modal-connect" class="fleet-modal-backdrop" style="display: none;">
        <div class="fleet-modal" style="max-width: 680px;" role="dialog" aria-modal="true" aria-labelledby="node-connect-modal-title">
          <div class="modal-header">
            <div class="modal-title-wrap">
              <svg class="modal-header-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--fl-brand-blue, #2563eb);">
                <polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line>
              </svg>
              <h3 id="node-connect-modal-title">Connect to ${escapeHtml(agent.name)}</h3>
            </div>
            <button class="modal-close" id="node-connect-close-btn" aria-label="Close dialog" type="button">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px;">
              <span class="origin-badge ${provBadgeClass}">${provIcon} ${escapeHtml(provName)}</span>
              <span class="os-badge">${osIcon} ${escapeHtml(osPretty)}</span>
              ${agent.ipAddress ? `<span class="node-meta-chip">IP: ${escapeHtml(agent.ipAddress)}</span>` : ''}
              ${agent.hostName && agent.hostName !== agent.ipAddress ? `<span class="node-meta-chip">Host: ${escapeHtml(agent.hostName)}</span>` : ''}
            </div>
            <p class="modal-description" style="margin-bottom: 14px;">Select a command recipe below to interact with this build node directly from your local terminal or CLI:</p>
            <div class="connect-commands-list">
              ${renderConnectCommandsList(agent)}
            </div>
          </div>
          <div class="modal-footer" style="justify-content: space-between;">
            <div style="display: flex; gap: 8px;">
              <a href="${computerUrl}/script" target="_blank" class="jenkins-button jenkins-button--tertiary" style="display: inline-flex; align-items: center; gap: 6px; text-decoration: none;">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>
                Groovy Console
              </a>
              ${agent.canConfigure ? `
                <button type="button" class="jenkins-button jenkins-button--tertiary" id="node-connect-open-terminal-btn" style="display: inline-flex; align-items: center; gap: 6px;">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>
                  Diagnostic Console
                </button>
              ` : ''}
            </div>
            <button class="jenkins-button jenkins-button--primary" id="node-connect-done-btn" type="button">Done</button>
          </div>
        </div>
      </div>

      <!-- Diagnostic Terminal Modal -->
      <div id="node-modal-terminal" class="fleet-modal-backdrop" style="display: none;">
        <div class="fleet-modal terminal-modal-dialog" role="dialog" aria-modal="true" aria-labelledby="node-terminal-modal-title">
          <div class="modal-header">
            <div class="modal-title-wrap">
              <svg class="modal-header-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--fl-brand-blue, #2563eb);">
                <polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line>
              </svg>
              <h3 id="node-terminal-modal-title">Diagnostic Console: ${escapeHtml(agent.name)}</h3>
            </div>
            <button class="modal-close" id="node-terminal-close-btn" aria-label="Close dialog" type="button">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>
          <div class="modal-body" style="padding-bottom: 8px;">
            <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px;">
              <span class="origin-badge ${provBadgeClass}">${provIcon} ${escapeHtml(provName)}</span>
              <span class="os-badge">${osIcon} ${escapeHtml(osPretty)}</span>
              ${agent.ipAddress ? `<span class="node-meta-chip">IP: ${escapeHtml(agent.ipAddress)}</span>` : ''}
              ${agent.hostName && agent.hostName !== agent.ipAddress ? `<span class="node-meta-chip">Host: ${escapeHtml(agent.hostName)}</span>` : ''}
            </div>
            <div class="terminal-presets-bar">
              <span class="terminal-presets-label">Diagnostic Presets:</span>
              <div class="terminal-presets-list" id="node-terminal-presets"></div>
            </div>
            <div class="terminal-input-bar">
              <span class="terminal-prompt-symbol">$</span>
              <input type="text" class="terminal-cmd-input" id="node-terminal-input" placeholder="Enter shell diagnostic command (e.g. df -h, ps aux, java -version)..." spellcheck="false" autocomplete="off" />
              <button class="jenkins-button jenkins-button--primary terminal-run-btn" id="node-terminal-run-btn" type="button">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
                Run
              </button>
            </div>
            <div class="terminal-frame">
              <div class="terminal-window-header">
                <div class="terminal-window-controls">
                  <span class="terminal-dot red"></span>
                  <span class="terminal-dot yellow"></span>
                  <span class="terminal-dot green"></span>
                </div>
                <span class="terminal-window-title" id="node-terminal-window-title">${escapeHtml(agent.name)}: remoting channel</span>
                <div class="terminal-actions-group">
                  <button class="terminal-btn-sm" id="node-terminal-copy-btn" type="button" title="Copy output">
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                    <span>Copy</span>
                  </button>
                  <button class="terminal-btn-sm" id="node-terminal-clear-btn" type="button" title="Clear terminal">
                    <span>Clear</span>
                  </button>
                </div>
              </div>
              <div class="terminal-viewport" id="node-terminal-viewport" tabindex="0">Modern Agent Viewer Diagnostic Console initialized.
Select a 1-click diagnostic preset above or enter a custom shell command.
Remoting channel encrypted, executions are audited to Jenkins system logs.</div>
              <div class="terminal-status-bar">
                <div class="terminal-status-info">
                  <span id="node-terminal-status-text">Ready</span>
                  <span id="node-terminal-exit-badge" class="terminal-exit-badge" style="display: none;">Exit: 0</span>
                  <span id="node-terminal-duration" style="display: none;">0ms</span>
                </div>
                <div class="terminal-security-notice">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                  <span>Timeout: 20s | Buffer: 256KB | Audit Logged</span>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer" style="justify-content: space-between;">
            <a href="${computerUrl}/script" target="_blank" class="jenkins-button jenkins-button--tertiary" style="display: inline-flex; align-items: center; gap: 6px; text-decoration: none;">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>
              Open Groovy Script Console
            </a>
            <button class="jenkins-button jenkins-button--primary" id="node-terminal-done-btn" type="button">Close</button>
          </div>
        </div>
      </div>
    `;

    container.innerHTML = html;
    attachNodeEvents();
  }

  function renderClassicBar() {
    container.innerHTML = `
      <div class="classic-mode-banner">
        <div class="classic-banner-left">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2">
            <rect x="2" y="2" width="20" height="8" rx="2" ry="2"></rect><rect x="2" y="14" width="20" height="8" rx="2" ry="2"></rect>
            <line x1="6" y1="6" x2="6.01" y2="6"></line><line x1="6" y1="18" x2="6.01" y2="18"></line>
          </svg>
          <div>
            <strong>Modern Agent Viewer available for this machine</strong>
            <span class="text-sub" style="margin-left: 8px;">Viewing classic Jenkins agent details.</span>
          </div>
        </div>
        <div class="classic-banner-right">
          <button class="jenkins-button jenkins-button--primary" id="btn-switch-to-modern" type="button">
            Switch to Modern View
          </button>
        </div>
      </div>
    `;
    container.querySelector('#btn-switch-to-modern')?.addEventListener('click', () => applyViewMode('modern'));
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

  function renderProvisionerDetails(details) {
    if (!details || Object.keys(details).length === 0) return '';
    return Object.entries(details).map(([k, v]) => `
      <div class="details-row">
        <span class="details-label">${escapeHtml(k)}:</span>
        <span class="details-val">
          <button class="fl-copy-chip btn-copy" data-copy="${escapeHtml(v)}" title="Copy ${escapeHtml(k)}" type="button">
            <span class="code-text copy-text">${escapeHtml(v)}</span>
            <svg class="fl-svg-icon" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
          </button>
        </span>
      </div>
    `).join('');
  }

  function attachNodeEvents() {
    // View Switchers
    container.querySelector('#btn-view-classic')?.addEventListener('click', () => applyViewMode('classic'));
    container.querySelector('#btn-view-modern')?.addEventListener('click', () => applyViewMode('modern'));

    // Connect Modal
    const connectModal = container.querySelector('#node-modal-connect');
    if (connectModal && connectModal.parentNode !== document.body) {
      document.body.appendChild(connectModal);
    }
    const connectBtn = container.querySelector('#btn-node-connect');
    const connectCloseBtn = document.getElementById('node-connect-close-btn');
    const connectDoneBtn = document.getElementById('node-connect-done-btn');

    function openConnectModal() {
      if (connectModal) connectModal.style.display = 'flex';
    }
    function closeConnectModal() {
      if (connectModal) connectModal.style.display = 'none';
    }

    connectBtn?.addEventListener('click', openConnectModal);
    connectCloseBtn?.addEventListener('click', closeConnectModal);
    connectDoneBtn?.addEventListener('click', closeConnectModal);

    // Diagnostic Terminal Modal
    const terminalModal = container.querySelector('#node-modal-terminal');
    if (terminalModal && terminalModal.parentNode !== document.body) {
      document.body.appendChild(terminalModal);
    }
    const terminalBtn = container.querySelector('#btn-node-terminal');
    const terminalCloseBtn = document.getElementById('node-terminal-close-btn');
    const terminalDoneBtn = document.getElementById('node-terminal-done-btn');
    const terminalInput = document.getElementById('node-terminal-input');
    const terminalRunBtn = document.getElementById('node-terminal-run-btn');
    const terminalViewport = document.getElementById('node-terminal-viewport');
    const terminalStatusText = document.getElementById('node-terminal-status-text');
    const terminalExitBadge = document.getElementById('node-terminal-exit-badge');
    const terminalDuration = document.getElementById('node-terminal-duration');
    const terminalCopyBtn = document.getElementById('node-terminal-copy-btn');
    const terminalClearBtn = document.getElementById('node-terminal-clear-btn');
    const terminalPresetsContainer = document.getElementById('node-terminal-presets');
    const connectOpenTerminalBtn = document.getElementById('node-connect-open-terminal-btn');

    function openTerminalModal() {
      if (!terminalModal) return;
      populateTerminalPresets();
      const agent = state.agentData || state.agent || {};
      const osFam = (agent && agent.os && agent.os.family) ? agent.os.family.toUpperCase() : '';
      const isWin = osFam === 'WINDOWS';
      const isMac = osFam === 'MACOS';
      const shellLabel = isWin ? 'PowerShell' : (isMac ? '/bin/sh (macOS)' : '/bin/sh');

      const windowTitle = document.getElementById('node-terminal-window-title');
      if (windowTitle) windowTitle.textContent = state.agentName + ': ' + shellLabel + ' over Remoting';

      const promptSym = terminalModal.querySelector('.terminal-prompt-symbol');
      if (promptSym) promptSym.textContent = isWin ? 'PS >' : '$';

      if (terminalInput) {
        if (isWin) {
          terminalInput.placeholder = 'Enter PowerShell or CMD diagnostic command (e.g. Get-Process, Get-PSDrive, ipconfig, java -version)...';
        } else if (isMac) {
          terminalInput.placeholder = 'Enter shell diagnostic command (e.g. df -h, sw_vers, brew --version, ping)...';
        } else {
          terminalInput.placeholder = 'Enter shell diagnostic command (e.g. df -h, ps aux, java -version, ping)...';
        }
      }

      terminalModal.style.display = 'flex';
      setTimeout(function() {
        if (terminalInput) terminalInput.focus();
      }, 50);
    }

    function closeTerminalModal() {
      if (terminalModal) terminalModal.style.display = 'none';
    }

    function populateTerminalPresets() {
      if (!terminalPresetsContainer) return;
      const agent = state.agentData || state.agent || {};
      const presets = getDiagnosticPresets(agent);
      terminalPresetsContainer.innerHTML = presets.map(p => `
        <button type="button" class="terminal-preset-pill" data-cmd="${escapeHtml(p.cmd)}" title="Run: ${escapeHtml(p.cmd)}">
          ${escapeHtml(p.label)}
        </button>
      `).join('');
    }

    function executeDiagnosticCommand(cmd) {
      if (!cmd || !cmd.trim()) return;
      const command = cmd.trim();
      if (terminalInput) terminalInput.value = command;

      if (terminalRunBtn) terminalRunBtn.disabled = true;
      if (terminalInput) terminalInput.disabled = true;
      if (terminalStatusText) terminalStatusText.textContent = 'Executing...';
      if (terminalExitBadge) terminalExitBadge.style.display = 'none';
      if (terminalDuration) terminalDuration.style.display = 'none';

      const promptLine = '\n$ ' + command + '\n';
      if (terminalViewport) {
        terminalViewport.textContent += promptLine;
        terminalViewport.scrollTop = terminalViewport.scrollHeight;
      }

      const xhr = new XMLHttpRequest();
      xhr.open('POST', ROOT_URL + '/agents/executeDiagnostic', true);
      xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

      // Crumb support
      const crumbHeader = document.querySelector('head')?.getAttribute('data-crumb-header') ||
                          (window.crumb && window.crumb.fieldName);
      const crumbValue = document.querySelector('head')?.getAttribute('data-crumb-value') ||
                         (window.crumb && window.crumb.value);
      if (crumbHeader && crumbValue) {
        xhr.setRequestHeader(crumbHeader, crumbValue);
      }

      const payload = 'name=' + encodeURIComponent(state.agentName) +
                      '&command=' + encodeURIComponent(command) +
                      '&timeout=20';

      xhr.onload = function() {
        if (terminalRunBtn) terminalRunBtn.disabled = false;
        if (terminalInput) {
          terminalInput.disabled = false;
          terminalInput.focus();
        }

        try {
          const res = JSON.parse(xhr.responseText);
          const data = res.data || res;
          if (res.status === 'ok' || data.status === 'ok') {
            const out = data.output !== undefined ? data.output : (res.output ? res.output : '(No output returned)');
            if (terminalViewport) {
              terminalViewport.textContent += out + (out.endsWith('\n') ? '' : '\n');
            }
            const exitCode = data.exitCode !== undefined ? data.exitCode : res.exitCode;
            if (terminalExitBadge && exitCode !== undefined) {
              terminalExitBadge.textContent = 'Exit: ' + exitCode;
              terminalExitBadge.className = 'terminal-exit-badge ' + (exitCode === 0 ? 'terminal-exit-success' : 'terminal-exit-error');
              terminalExitBadge.style.display = 'inline-block';
            }
            const durationMs = data.durationMs !== undefined ? data.durationMs : res.durationMs;
            if (terminalDuration && durationMs !== undefined) {
              terminalDuration.textContent = durationMs + 'ms';
              terminalDuration.style.display = 'inline-block';
            }
            if (terminalStatusText) {
              terminalStatusText.textContent = (data.timedOut || res.timedOut) ? 'Timed out after 20s' : 'Done';
            }
          } else {
            if (terminalViewport) {
              terminalViewport.textContent += '[Error: ' + (data.message || res.message || 'Execution failed') + ']\n';
            }
            if (terminalStatusText) terminalStatusText.textContent = 'Error';
          }
        } catch (err) {
          if (terminalViewport) {
            terminalViewport.textContent += '[Execution error: HTTP ' + xhr.status + ']\n';
          }
          if (terminalStatusText) terminalStatusText.textContent = 'Failed';
        }
        if (terminalViewport) terminalViewport.scrollTop = terminalViewport.scrollHeight;
      };

      xhr.onerror = function() {
        if (terminalRunBtn) terminalRunBtn.disabled = false;
        if (terminalInput) {
          terminalInput.disabled = false;
          terminalInput.focus();
        }
        if (terminalViewport) {
          terminalViewport.textContent += '[Network error connecting to Jenkins controller]\n';
          terminalViewport.scrollTop = terminalViewport.scrollHeight;
        }
        if (terminalStatusText) terminalStatusText.textContent = 'Network Error';
      };

      xhr.send(payload);
    }

    terminalBtn?.addEventListener('click', openTerminalModal);
    connectOpenTerminalBtn?.addEventListener('click', function() {
      closeConnectModal();
      openTerminalModal();
    });
    terminalCloseBtn?.addEventListener('click', closeTerminalModal);
    terminalDoneBtn?.addEventListener('click', closeTerminalModal);
    terminalModal?.addEventListener('click', function(e) {
      if (e.target === terminalModal) closeTerminalModal();
    });

    terminalPresetsContainer?.addEventListener('click', function(e) {
      const pill = e.target.closest('.terminal-preset-pill');
      if (!pill) return;
      const cmd = pill.getAttribute('data-cmd');
      if (cmd) executeDiagnosticCommand(cmd);
    });

    terminalRunBtn?.addEventListener('click', function() {
      executeDiagnosticCommand(terminalInput?.value);
    });

    terminalInput?.addEventListener('keydown', function(e) {
      if (e.key === 'Enter') {
        e.preventDefault();
        executeDiagnosticCommand(terminalInput.value);
      }
    });

    terminalClearBtn?.addEventListener('click', function() {
      if (terminalViewport) terminalViewport.textContent = '$ Ready\n';
      if (terminalExitBadge) terminalExitBadge.style.display = 'none';
      if (terminalDuration) terminalDuration.style.display = 'none';
      if (terminalStatusText) terminalStatusText.textContent = 'Ready';
    });

    terminalCopyBtn?.addEventListener('click', function() {
      if (!terminalViewport) return;
      copyToClipboard(terminalViewport.textContent, function() {
        terminalCopyBtn.classList.add('copied');
        const span = terminalCopyBtn.querySelector('span');
        if (span) span.textContent = 'Copied!';
        setTimeout(function() {
          terminalCopyBtn.classList.remove('copied');
          if (span) span.textContent = 'Copy';
        }, 1800);
      });
    });

    // Universal copy handler
    container.addEventListener('click', function(e) {
      const btn = e.target.closest('.btn-copy, .fl-copy-chip, .fl-copy-icon-btn, .btn-copy-code, .agent-label-pill, .btn-copy-ip, .btn-copy-ip-inline');
      if (!btn) return;
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
        } else if (btn.classList.contains('agent-label-pill')) {
          const oldText = btn.textContent;
          btn.textContent = 'Copied!';
          setTimeout(function() {
            btn.textContent = oldText;
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

    // Refresh button
    const refreshBtn = container.querySelector('#btn-node-refresh');
    if (refreshBtn) {
      refreshBtn.addEventListener('click', function() {
        refreshBtn.classList.add('spinning');
        fetchAgentTelemetry(function() {
          refreshBtn.classList.remove('spinning');
        });
      });
    }

    // Modal elements
    const modal = container.querySelector('#node-modal-offline');
    const modalTitle = container.querySelector('#node-modal-title');
    const modalDesc = container.querySelector('#node-modal-desc');
    const modalReason = container.querySelector('#node-modal-offline-reason');
    const modalReasonContainer = container.querySelector('#node-modal-reason-container');
    const modalConfirmBtn = container.querySelector('#node-modal-confirm-btn');
    const modalCloseBtn = container.querySelector('#node-modal-close-btn');
    const modalCancelBtn = container.querySelector('#node-modal-cancel-btn');
    const modalPresets = container.querySelector('#node-modal-presets');
    const modalHeaderIcon = container.querySelector('#node-modal-header-icon');

    let targetIsOffline = true;

    function openModal(isOffline) {
      targetIsOffline = isOffline;
      if (isOffline) {
        modalTitle.textContent = 'Take ' + state.agentName + ' Offline';
        modalDesc.textContent = 'Mark this machine temporarily offline. Running jobs will finish, but no new builds will be scheduled on it:';
        if (modalReasonContainer) modalReasonContainer.style.display = 'flex';
        if (modalReason) modalReason.value = 'Scheduled maintenance and health inspection';
        modalConfirmBtn.textContent = 'Take Offline';
        if (modalHeaderIcon) {
          modalHeaderIcon.innerHTML = '<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>';
          modalHeaderIcon.style.color = 'var(--fl-amber-warning)';
        }
        setTimeout(() => {
          if (modalReason) {
            modalReason.focus();
            modalReason.select();
          }
        }, 50);
      } else {
        modalTitle.textContent = 'Bring ' + state.agentName + ' Online';
        modalDesc.textContent = 'Clear maintenance offline status and allow Jenkins to resume dispatching queued builds to this machine.';
        if (modalReasonContainer) modalReasonContainer.style.display = 'none';
        modalConfirmBtn.textContent = 'Bring Online';
        if (modalHeaderIcon) {
          modalHeaderIcon.innerHTML = '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>';
          modalHeaderIcon.style.color = 'var(--fl-green-online)';
        }
      }
      modal.style.display = 'flex';
    }

    function closeModal() {
      if (modal) modal.style.display = 'none';
    }

    container.querySelector('#btn-action-offline')?.addEventListener('click', () => openModal(true));
    container.querySelector('#btn-action-online')?.addEventListener('click', () => openModal(false));
    modalCloseBtn?.addEventListener('click', closeModal);
    modalCancelBtn?.addEventListener('click', closeModal);

    // Quick presets
    if (modalPresets) {
      modalPresets.addEventListener('click', function(e) {
        const btn = e.target.closest('.preset-pill');
        if (!btn) return;
        const presetText = btn.getAttribute('data-preset');
        if (presetText && modalReason) {
          modalReason.value = presetText;
          modalReason.focus();
        }
      });
    }

    // Submit handler
    function confirmToggle() {
      modalConfirmBtn.disabled = true;
      modalConfirmBtn.textContent = targetIsOffline ? 'Taking Offline...' : 'Bringing Online...';

      const reason = modalReason ? modalReason.value.trim() : '';
      const xhr = new XMLHttpRequest();
      xhr.open('POST', ROOT_URL + '/agents/toggleOffline', true);
      xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

      // Crumb support
      const crumbHeader = document.querySelector('head')?.getAttribute('data-crumb-header') ||
                          (window.crumb && window.crumb.fieldName);
      const crumbValue = document.querySelector('head')?.getAttribute('data-crumb-value') ||
                         (window.crumb && window.crumb.value);
      if (crumbHeader && crumbValue) {
        xhr.setRequestHeader(crumbHeader, crumbValue);
      }

      const payload = 'name=' + encodeURIComponent(state.agentName) +
                      '&offline=' + targetIsOffline +
                      '&reason=' + encodeURIComponent(reason);

      xhr.onload = function() {
        modalConfirmBtn.disabled = false;
        closeModal();
        fetchAgentTelemetry();
      };

      xhr.onerror = function() {
        modalConfirmBtn.disabled = false;
        alert('Error updating agent state');
        closeModal();
      };

      xhr.send(payload);
    }

    modalConfirmBtn?.addEventListener('click', confirmToggle);

    // Cmd+Enter / Ctrl+Enter in textarea
    modalReason?.addEventListener('keydown', function(e) {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        confirmToggle();
      }
    });

    // Escape key
    window.addEventListener('keydown', function(e) {
      if (e.key === 'Escape') {
        if (modal && modal.style.display === 'flex') closeModal();
        if (connectModal && connectModal.style.display === 'flex') closeConnectModal();
      }
    });
  }


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

  function renderConnectCommandsList(agent) {
    const list = getConnectCommands(agent);
    return list.map(c => `
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
        return '<svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 448 512" fill="currentColor"><path d="M220.8 123.3c1 .5 1.8 1.7 3 1.7 1.1 0 2.8-.4 2.9-1.5.2-1.4-1.9-2.3-3.2-2.9-1.7-.7-3.9-1-5.5-.1-.4.2-.8.7-.6 1.1.3 1.3 2.3 1.1 3.4 1.7zm-21.9 1.7c1.2 0 2-1.2 3-1.7 1.1-.6 3.1-.4 3.5-1.6.2-.4-.2-.9-.6-1.1-1.6-.9-3.8-.6-5.5.1-1.3.6-3.4 1.5-3.2 2.9.1 1 1.8 1.5 2.8 1.4zM420 403.8c-3.6-4-5.3-11.6-7.2-19.7-1.8-8.1-3.9-16.8-10.5-22.4-1.3-1.1-2.6-2.1-4-2.9-1.3-.8-2.7-1.5-4.1-2 9.2-27.3 5.6-54.5-3.7-79.1-11.4-30.1-31.3-56.4-46.5-74.4-17.1-21.5-33.7-41.9-33.4-72C311.1 85.4 315.7.1 234.8 0 132.4-.2 158 103.4 156.9 135.2c-1.7 23.4-6.4 41.8-22.5 64.7-18.9 22.5-45.5 58.8-58.1 96.7-6 17.9-8.8 36.1-6.2 53.3-6.5 5.8-11.4 14.7-16.6 20.2-4.2 4.3-10.3 5.9-17 8.3s-14 6-18.5 14.5c-2.1 3.9-2.8 8.1-2.8 12.4 0 3.9.6 7.9 1.2 11.8 1.2 8.1 2.5 15.7.8 20.8-5.2 14.4-5.9 24.4-2.2 31.7 3.8 7.3 11.4 10.5 20.1 12.3 17.3 3.6 40.8 2.7 59.3 12.5 19.8 10.4 39.9 14.1 55.9 10.4 11.6-2.6 21.1-9.6 25.9-20.2 12.5-.1 26.3-5.4 48.3-6.6 14.9-1.2 33.6 5.3 55.1 4.1.6 2.3 1.4 4.6 2.5 6.7v.1c8.3 16.7 23.8 24.3 40.3 23 16.6-1.3 34.1-11 48.3-27.9 13.6-16.4 36-23.2 50.9-32.2 7.4-4.5 13.4-10.1 13.9-18.3.4-8.2-4.4-17.3-15.5-29.7zM223.7 87.3c9.8-22.2 34.2-21.8 44-.4 6.5 14.2 3.6 30.9-4.3 40.4-1.6-.8-5.9-2.6-12.6-4.9 1.1-1.2 3.1-2.7 3.9-4.6 4.8-11.8-.2-27-9.1-27.3-7.3-.5-13.9 10.8-11.8 23-4.1-2-9.4-3.5-13-4.4-1-6.9-.3-14.6 2.9-21.8zM183 75.8c10.1 0 20.8 14.2 19.1 33.5-3.5 1-7.1 2.5-10.2 4.6 1.2-8.9-3.3-20.1-9.6-19.6-8.4.7-9.8 21.2-1.8 28.1 1 .8 1.9-.2-5.9 5.5-15.6-14.6-10.5-52.1 8.4-52.1zm-13.6 60.7c6.2-4.6 13.6-10 14.1-10.5 4.7-4.4 13.5-14.2 27.9-14.2 7.1 0 15.6 2.3 25.9 8.9 6.3 4.1 11.3 4.4 22.6 9.3 8.4 3.5 13.7 9.7 10.5 18.2-2.6 7.1-11 14.4-22.7 18.1-11.1 3.6-19.8 16-38.2 14.9-3.9-.2-7-1-9.6-2.1-8-3.5-12.2-10.4-20-15-8.6-4.8-13.2-10.4-14.7-15.3-1.4-4.9 0-9 4.2-12.3zm3.3 334c-2.7 35.1-43.9 34.4-75.3 18-29.9-15.8-68.6-6.5-76.5-21.9-2.4-4.7-2.4-12.7 2.6-26.4v-.2c2.4-7.6.6-16-.6-23.9-1.2-7.8-1.8-15 .9-20 3.5-6.7 8.5-9.1 14.8-11.3 10.3-3.7 11.8-3.4 19.6-9.9 5.5-5.7 9.5-12.9 14.3-18 5.1-5.5 10-8.1 17.7-6.9 8.1 1.2 15.1 6.8 21.9 16l19.6 35.6c9.5 19.9 43.1 48.4 41 68.9zm-1.4-25.9c-4.1-6.6-9.6-13.6-14.4-19.6 7.1 0 14.2-2.2 16.7-8.9 2.3-6.2 0-14.9-7.4-24.9-13.5-18.2-38.3-32.5-38.3-32.5-13.5-8.4-21.1-18.7-24.6-29.9s-3-23.3-.3-35.2c5.2-22.9 18.6-45.2 27.2-59.2 2.3-1.7.8 3.2-8.7 20.8-8.5 16.1-24.4 53.3-2.6 82.4.6-20.7 5.5-41.8 13.8-61.5 12-27.4 37.3-74.9 39.3-112.7 1.1.8 4.6 3.2 6.2 4.1 4.6 2.7 8.1 6.7 12.6 10.3 12.4 10 28.5 9.2 42.4 1.2 6.2-3.5 11.2-7.5 15.9-9 9.9-3.1 17.8-8.6 22.3-15 7.7 30.4 25.7 74.3 37.2 95.7 6.1 11.4 18.3 35.5 23.6 64.6 3.3-.1 7 .4 10.9 1.4 13.8-35.7-11.7-74.2-23.3-84.9-4.7-4.6-4.9-6.6-2.6-6.5 12.6 11.2 29.2 33.7 35.2 59 2.8 11.6 3.3 23.7.4 35.7 16.4 6.8 35.9 17.9 30.7 34.8-2.2-.1-3.2 0-4.2 0 3.2-10.1-3.9-17.6-22.8-26.1-19.6-8.6-36-8.6-38.3 12.5-12.1 4.2-18.3 14.7-21.4 27.3-2.8 11.2-3.6 24.7-4.4 39.9-.5 7.7-3.6 18-6.8 29-32.1 22.9-76.7 32.9-114.3 7.2zm257.4-11.5c-.9 16.8-41.2 19.9-63.2 46.5-13.2 15.7-29.4 24.4-43.6 25.5s-26.5-4.8-33.7-19.3c-4.7-11.1-2.4-23.1 1.1-36.3 3.7-14.2 9.2-28.8 9.9-40.6.8-15.2 1.7-28.5 4.2-38.7 2.6-10.3 6.6-17.2 13.7-21.1.3-.2.7-.3 1-.5.8 13.2 7.3 26.6 18.8 29.5 12.6 3.3 30.7-7.5 38.4-16.3 9-.3 15.7-.9 22.6 5.1 9.9 8.5 7.1 30.3 17.1 41.6 10.6 11.6 14 19.5 13.7 24.6zM173.3 148.7c2 1.9 4.7 4.5 8 7.1 6.6 5.2 15.8 10.6 27.3 10.6 11.6 0 22.5-5.9 31.8-10.8 4.9-2.6 10.9-7 14.8-10.4s5.9-6.3 3.1-6.6-2.6 2.6-6 5.1c-4.4 3.2-9.7 7.4-13.9 9.8-7.4 4.2-19.5 10.2-29.9 10.2s-18.7-4.8-24.9-9.7c-3.1-2.5-5.7-5-7.7-6.9-1.5-1.4-1.9-4.6-4.3-4.9-1.4-.1-1.8 3.7 1.7 6.5z"/></svg>';
      case 'WINDOWS':
        return '<svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M0 3.449L9.75 2.1v9.451H0V3.449zm10.949-1.651L24 0v11.4H10.949V1.798zM0 12.6h9.75v9.451L0 20.699V12.6zm10.949 0H24V24l-13.051-1.801V12.6z"/></svg>';
      case 'MACOS':
        return '<svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M15.97 6.43c.66-.82 1.11-1.96.99-3.1-.96.04-2.12.64-2.8 1.44-.61.71-1.14 1.87-1 2.99 1.07.08 2.16-.54 2.81-1.33z"/></svg>';
      default:
        return '<svg class="fl-svg-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>';
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
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // Initialize once DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
