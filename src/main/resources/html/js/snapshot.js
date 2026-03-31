// snapshot.js — Snapshot lifecycle and viewport scaling

var _viewportWidth = 1280;
var _viewportHeight = 720;
var _scale = 1;

function applyScale() {
    var container = document.getElementById('viewport-container');
    var wrapper = document.getElementById('viewport-wrapper');
    if (!container || !wrapper) return;

    var containerWidth = container.clientWidth;
    _scale = containerWidth / _viewportWidth;

    wrapper.style.width = _viewportWidth + 'px';
    wrapper.style.height = _viewportHeight + 'px';
    wrapper.style.transform = 'scale(' + _scale + ')';

    container.style.minHeight = '0';
}

window.addEventListener('resize', applyScale);

window.loadSnapshot = function(html) {
    var viewport = document.getElementById('viewport');
    var container = document.getElementById('viewport-container');
    var emptyState = document.getElementById('empty-state');
    var status = document.getElementById('status');

    // Reset viewport to defaults; will update once iframe loads
    _viewportWidth = 1280;
    _viewportHeight = 720;

    viewport.innerHTML = '';
    var iframe = document.createElement('iframe');
    iframe.setAttribute('sandbox', 'allow-same-origin');
    iframe.style.width = _viewportWidth + 'px';
    iframe.style.height = _viewportHeight + 'px';
    iframe.srcdoc = html;
    viewport.appendChild(iframe);

    iframe.onload = function() {
        // Try to read viewport from iframe's meta tag or body dimensions
        var doc = iframe.contentDocument;
        if (doc) {
            var meta = doc.querySelector('meta[name="viewport"]');
            if (meta) {
                var content = meta.getAttribute('content') || '';
                var wMatch = content.match(/width=(\d+)/);
                var hMatch = content.match(/height=(\d+)/);
                if (wMatch) _viewportWidth = parseInt(wMatch[1], 10);
                if (hMatch) _viewportHeight = parseInt(hMatch[1], 10);
                iframe.style.width = _viewportWidth + 'px';
                iframe.style.height = _viewportHeight + 'px';
            }
        }
        applyScale();
    };

    container.style.display = 'block';
    emptyState.style.display = 'none';
    status.textContent = 'Loaded';

    applyScale();
    window.clearHighlight();
};
