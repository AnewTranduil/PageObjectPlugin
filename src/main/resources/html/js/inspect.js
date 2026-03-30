// inspect.js — Element picker mode

var _inspectMode = false;

window.toggleInspectMode = function() {
    _inspectMode = !_inspectMode;
    document.body.classList.toggle('inspect-active', _inspectMode);
    var status = document.getElementById('status');
    if (_inspectMode) {
        status.textContent = 'Inspect Mode \u2014 click an element to pick it';
        setupInspectListeners();
    } else {
        status.textContent = 'Loaded | ' + _layoutElements.length + ' elements';
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

function findNearestElement(x, y) {
    var best = null;
    var bestArea = Infinity;
    for (var i = 0; i < _layoutElements.length; i++) {
        var el = _layoutElements[i];
        if (!el.bounds) continue;
        var b = el.bounds;
        if (x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h) {
            var area = b.w * b.h;
            if (area < bestArea) {
                bestArea = area;
                best = el;
            }
        }
    }
    return best;
}

function removeInspectHighlight() {
    var overlay = document.getElementById('overlay');
    var inspects = overlay.querySelectorAll('.inspect-highlight, .highlight-tooltip');
    for (var i = 0; i < inspects.length; i++) {
        inspects[i].remove();
    }
}
