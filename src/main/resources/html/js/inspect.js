// inspect.js — Element picker mode (live iframe DOM queries)

var _inspectMode = false;

window.toggleInspectMode = function() {
    _inspectMode = !_inspectMode;
    document.body.classList.toggle('inspect-active', _inspectMode);
    var status = document.getElementById('status');
    if (_inspectMode) {
        status.textContent = 'Inspect Mode \u2014 click an element to pick it';
        setupInspectListeners();
    } else {
        status.textContent = 'Loaded';
        removeInspectHighlight();
    }
};

function setupInspectListeners() {
    var overlay = document.getElementById('overlay');

    overlay.onmousemove = function(e) {
        if (!_inspectMode) return;
        removeInspectHighlight();

        var nearest = findNearestElement(e.offsetX, e.offsetY);
        if (nearest && nearest.bounds) {
            var box = document.createElement('div');
            box.className = 'inspect-highlight';
            box.style.left = nearest.bounds.x + 'px';
            box.style.top = nearest.bounds.y + 'px';
            box.style.width = nearest.bounds.w + 'px';
            box.style.height = nearest.bounds.h + 'px';
            overlay.appendChild(box);

            var tip = document.createElement('div');
            tip.className = 'highlight-tooltip';
            tip.style.borderColor = '#22c55e';
            tip.textContent = nearest.selector;
            tip.style.left = nearest.bounds.x + 'px';
            tip.style.top = Math.max(0, nearest.bounds.y - 22) + 'px';
            overlay.appendChild(tip);
        }
    };

    overlay.onclick = function(e) {
        if (!_inspectMode) return;
        var nearest = findNearestElement(e.offsetX, e.offsetY);
        if (nearest && window.__pickerCallback) {
            window.__pickerCallback(JSON.stringify(nearest));
        }
        window.toggleInspectMode();
    };
}

/**
 * Find the smallest element at (x, y) by querying the live iframe DOM.
 * Returns an object matching the shape expected by the picker callback.
 */
function findNearestElement(x, y) {
    var doc = getIframeDoc();
    if (!doc) return null;

    // Collect all visible elements at this point
    var candidates = doc.elementsFromPoint(x, y);
    if (!candidates || candidates.length === 0) return null;

    var best = null;
    var bestArea = Infinity;

    for (var i = 0; i < candidates.length; i++) {
        var el = candidates[i];
        var tag = el.tagName.toLowerCase();
        // Skip html, body, and non-visual container elements
        if (tag === 'html' || tag === 'body') continue;

        var rect = el.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) continue;

        var area = rect.width * rect.height;
        if (area < bestArea) {
            bestArea = area;
            best = el;
        }
    }

    if (!best) return null;

    var rect = best.getBoundingClientRect();
    return {
        selector: computeSelector(best),
        tag: best.tagName.toLowerCase(),
        role: best.getAttribute('role') || undefined,
        text: (best.textContent || '').trim().substring(0, 80),
        bounds: { x: rect.left, y: rect.top, w: rect.width, h: rect.height },
        attributes: getPickerAttributes(best)
    };
}

/**
 * Compute a reasonable CSS selector for an element.
 * Priority: data-testid > id > role+name > tag path.
 */
function computeSelector(el) {
    var testId = el.getAttribute('data-testid');
    if (testId) return '[data-testid="' + testId + '"]';

    var id = el.getAttribute('id');
    if (id) return '#' + id;

    var role = el.getAttribute('role');
    if (role) {
        var text = (el.textContent || '').trim().substring(0, 30);
        if (text) return '[role="' + role + '"] "' + text + '"';
        return '[role="' + role + '"]';
    }

    var tag = el.tagName.toLowerCase();
    var placeholder = el.getAttribute('placeholder');
    if (placeholder) return tag + '[placeholder="' + placeholder + '"]';

    var name = el.getAttribute('name');
    if (name) return tag + '[name="' + name + '"]';

    return tag;
}

/**
 * Collect relevant attributes for the picker result.
 */
function getPickerAttributes(el) {
    var attrs = {};
    var names = ['id', 'class', 'type', 'name', 'placeholder', 'data-testid',
                 'role', 'aria-label', 'href', 'value'];
    for (var i = 0; i < names.length; i++) {
        var val = el.getAttribute(names[i]);
        if (val != null) attrs[names[i]] = val;
    }
    return attrs;
}

function removeInspectHighlight() {
    var overlay = document.getElementById('overlay');
    var inspects = overlay.querySelectorAll('.inspect-highlight, .highlight-tooltip');
    for (var i = 0; i < inspects.length; i++) {
        inspects[i].remove();
    }
}
