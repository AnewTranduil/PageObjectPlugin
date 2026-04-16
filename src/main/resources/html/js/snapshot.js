// snapshot.js — Snapshot lifecycle and viewport scaling

var _viewportWidth = 1280;
var _viewportHeight = 720;
var _scale = 1;

function applyScale() {
    var container = document.getElementById('viewport-container');
    var sizer = document.getElementById('viewport-sizer');
    var wrapper = document.getElementById('viewport-wrapper');
    if (!container || !sizer || !wrapper) return;

    var containerWidth = container.clientWidth;
    _scale = containerWidth / _viewportWidth;

    wrapper.style.width = _viewportWidth + 'px';
    wrapper.style.height = _viewportHeight + 'px';
    wrapper.style.transform = 'scale(' + _scale + ')';

    // CSS transforms do not shrink the layout box, so we mirror the scaled
    // dimensions onto the sizer. Without this, #viewport-container treats
    // the wrapper as its pre-scale 1280x720 layout box and grows scrollbars
    // into empty space below/right of the visual snapshot.
    sizer.style.width = (_viewportWidth * _scale) + 'px';
    sizer.style.height = (_viewportHeight * _scale) + 'px';

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
    iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts');
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
            }
            // Use the full rendered page height so snapshots of pages taller
            // than the capture viewport aren't truncated. Fall back to the
            // declared viewport height when the document hasn't laid out yet.
            var contentHeight = Math.max(
                doc.body ? doc.body.scrollHeight : 0,
                doc.documentElement ? doc.documentElement.scrollHeight : 0,
                _viewportHeight
            );
            _viewportHeight = contentHeight;
            iframe.style.width = _viewportWidth + 'px';
            iframe.style.height = _viewportHeight + 'px';
        }
        applyScale();
    };

    container.style.display = 'block';
    emptyState.style.display = 'none';
    status.textContent = 'Loaded';

    applyScale();
    window.clearHighlight();
};

window.clearSnapshot = function() {
    var viewport = document.getElementById('viewport');
    var container = document.getElementById('viewport-container');
    var emptyState = document.getElementById('empty-state');
    var status = document.getElementById('status');

    viewport.innerHTML = '';
    container.style.display = 'none';
    emptyState.style.display = '';
    status.textContent = 'No snapshot loaded';
    window.clearHighlight();
};

// Outdated-bundle banner — shown when the snapshot scanner finds
// bundle directories on disk whose manifest.version != 2 (i.e. v1
// bundles left over from before the breaking v2 upgrade). The Kotlin
// side drives visibility via showOutdatedBanner / hideOutdatedBanner;
// the user can also temporarily dismiss it via the × button.
(function () {
    var dismissBtn = document.getElementById('banner-dismiss');
    if (dismissBtn) {
        dismissBtn.addEventListener('click', function () {
            window.hideOutdatedBanner();
        });
    }
})();

window.showOutdatedBanner = function (payload) {
    var banner = document.getElementById('banner');
    var text = document.getElementById('banner-text');
    if (!banner || !text) return;

    var count = (payload && payload.count) || 0;
    var versions = (payload && payload.versions) || [];
    var versionList = versions.length
        ? 'v' + versions.join(', v')
        : 'an unsupported version';
    var plural = count === 1 ? '' : 's';

    text.textContent =
        'Found ' + count + ' outdated snapshot bundle' + plural + ' (' +
        versionList + '). This plugin requires v2. Regenerate with ' +
        'playwright-snapshot-saver >= 0.7.0 to refresh them.';
    banner.classList.remove('hidden');
};

window.hideOutdatedBanner = function () {
    var banner = document.getElementById('banner');
    if (banner) banner.classList.add('hidden');
};
