(function() {
  'use strict';
  var script = document.currentScript || document.querySelector('script[src*="fleetlens-banner.js"]');
  var rootUrl = (script && script.getAttribute('data-root-url')) || '';
  var shouldRedirect = script && script.getAttribute('data-redirect') === 'true';

  if (shouldRedirect && !window.location.search.includes('legacy=true')) {
    window.location.replace(rootUrl + '/agents');
    return;
  }

  document.addEventListener('DOMContentLoaded', function() {
    var mainPanel = document.getElementById('main-panel');
    if (!mainPanel || document.getElementById('fleetlens-classic-banner')) return;

    var banner = document.createElement('div');
    banner.id = 'fleetlens-classic-banner';
    banner.className = 'fleetlens-classic-banner';
    banner.style.cssText = 'background: linear-gradient(135deg, rgba(37, 99, 235, 0.1) 0%, rgba(99, 102, 241, 0.1) 100%); border: 1px solid rgba(59, 130, 246, 0.3); border-radius: 8px; padding: 12px 18px; margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between; font-family: inherit;';

    banner.innerHTML = '<div style="display: flex; align-items: center; gap: 12px;">' +
      '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink: 0;"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"></rect><rect x="2" y="14" width="20" height="8" rx="2" ry="2"></rect><line x1="6" y1="6" x2="6.01" y2="6"></line><line x1="6" y1="18" x2="6.01" y2="18"></line></svg>' +
      '<div>' +
        '<strong style="color: var(--text-color, #1f2937); font-size: 14px;">Modern Agent Viewer</strong>' +
        '<div style="font-size: 12px; color: var(--text-color-secondary, #6b7280);">View your machines categorized by Cloud/Provider (K8s, AWS, Docker) &amp; OS, with real-time storage gauges and dual-clock uptime telemetry.</div>' +
      '</div>' +
    '</div>' +
    '<a href="' + rootUrl + '/agents" class="jenkins-button jenkins-button--primary" style="white-space: nowrap; text-decoration: none;">' +
      'Open Modern Agent Viewer' +
    '</a>';
    mainPanel.insertBefore(banner, mainPanel.firstChild);
  });
})();
