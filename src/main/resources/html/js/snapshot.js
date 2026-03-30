// snapshot.js — Snapshot lifecycle and viewport scaling

var _layoutElements = [];
var _layoutMap = {};
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

window.loadSnapshot = function(html, layoutJson) {
    var viewport = document.getElementById('viewport');
    var container = document.getElementById('viewport-container');
    var emptyState = document.getElementById('empty-state');
    var status = document.getElementById('status');

    try {
        var layout = JSON.parse(layoutJson);
        _layoutElements = layout.elements || [];
        _layoutMap = {};
        for (var i = 0; i < _layoutElements.length; i++) {
            var el = _layoutElements[i];
            _layoutMap[el.selector] = el;
        }
        if (layout.viewport) {
            _viewportWidth = layout.viewport.width || 1280;
            _viewportHeight = layout.viewport.height || 720;
        }
        status.textContent = 'Loaded | ' + _layoutElements.length + ' elements';
    } catch (e) {
        _layoutElements = [];
        _layoutMap = {};
        status.textContent = 'Loaded | layout parse error';
    }

    viewport.innerHTML = '';
    var iframe = document.createElement('iframe');
    iframe.setAttribute('sandbox', 'allow-same-origin');
    iframe.style.width = _viewportWidth + 'px';
    iframe.style.height = _viewportHeight + 'px';
    iframe.srcdoc = html;
    viewport.appendChild(iframe);

    container.style.display = 'block';
    emptyState.style.display = 'none';

    applyScale();
    window.clearHighlight();
};
