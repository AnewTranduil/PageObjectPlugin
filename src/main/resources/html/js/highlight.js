// highlight.js — Highlight rendering

window.highlightElement = function(type, value) {
    window.clearHighlight();

    var overlay = document.getElementById('overlay');
    var container = document.getElementById('viewport-container');
    var matches = queryIframeDom(type, value);

    if (matches.length > 0) {
        var el = matches[0];
        var rect = el.getBoundingClientRect();

        var box = document.createElement('div');
        box.className = 'highlight-box';
        box.style.left = rect.left + 'px';
        box.style.top = rect.top + 'px';
        box.style.width = rect.width + 'px';
        box.style.height = rect.height + 'px';
        overlay.appendChild(box);

        var tooltip = document.createElement('div');
        tooltip.className = 'highlight-tooltip';
        var tag = el.tagName.toLowerCase();
        var role = el.getAttribute('role');
        var text = (el.textContent || '').trim().substring(0, 40);
        var info = '<' + tag + '>';
        if (role) info += ' role=' + role;
        if (text) info += ' "' + text + '"';
        tooltip.textContent = info;
        tooltip.style.left = rect.left + 'px';
        tooltip.style.top = Math.max(0, rect.top - 22) + 'px';
        overlay.appendChild(tooltip);

        var scrollTop = container.scrollTop;
        var containerHeight = container.clientHeight;
        var scaledY = rect.top * _scale;
        var scaledH = rect.height * _scale;
        if (scaledY < scrollTop || scaledY + scaledH > scrollTop + containerHeight) {
            container.scrollTop = Math.max(0, scaledY - 50);
        }
    } else {
        var box = document.createElement('div');
        box.className = 'highlight-box not-found';
        box.style.left = '10px';
        box.style.top = '10px';
        box.style.width = 'auto';
        box.style.height = '24px';
        box.textContent = 'Not found: ' + type + '(' + value + ')';
        box.style.color = '#ef4444';
        box.style.fontSize = '11px';
        box.style.padding = '4px 8px';
        box.style.fontFamily = 'monospace';
        overlay.appendChild(box);
    }
};

window.clearHighlight = function() {
    var overlay = document.getElementById('overlay');
    overlay.innerHTML = '';
};

// --- Highlight All ---

var PALETTE = [
    '#3b82f6', '#22c55e', '#a855f7', '#f97316',
    '#06b6d4', '#ec4899', '#84cc16', '#f43f5e'
];

window.highlightAll = function(locatorsJson) {
    window.clearHighlight();
    var locators;
    if (typeof locatorsJson === 'string') {
        locators = JSON.parse(locatorsJson);
    } else {
        locators = locatorsJson;
    }

    var highlights = [];
    for (var i = 0; i < locators.length; i++) {
        var matches = queryIframeDom(locators[i].type, locators[i].value);
        for (var j = 0; j < matches.length; j++) {
            highlights.push({
                locator: locators[i],
                el: matches[j],
                rect: matches[j].getBoundingClientRect(),
                index: i
            });
        }
    }

    var analysis = analyzeOverlaps(highlights);
    renderAllHighlights(highlights, analysis);

    var status = document.getElementById('status');
    var dupCount = analysis.duplicates.size;
    var overlapCount = analysis.overlaps.size;
    var msg = 'Showing ' + locators.length + ' locators';
    if (dupCount > 0) msg += ' | ' + dupCount + ' duplicate';
    if (overlapCount > 0) msg += ' | ' + overlapCount + ' overlap';
    status.textContent = msg;
};

function analyzeOverlaps(highlights) {
    var duplicates = new Set();
    var overlaps = new Set();

    for (var i = 0; i < highlights.length; i++) {
        for (var j = i + 1; j < highlights.length; j++) {
            if (highlights[i].el === highlights[j].el) {
                duplicates.add(i);
                duplicates.add(j);
            } else if (rectsOverlap(highlights[i].rect, highlights[j].rect)) {
                overlaps.add(i);
                overlaps.add(j);
            }
        }
    }
    return { duplicates: duplicates, overlaps: overlaps };
}

function rectsOverlap(a, b) {
    return a.left < b.left + b.width &&
           a.left + a.width > b.left &&
           a.top < b.top + b.height &&
           a.top + a.height > b.top;
}

function renderAllHighlights(highlights, analysis) {
    var overlay = document.getElementById('overlay');

    for (var i = 0; i < highlights.length; i++) {
        var h = highlights[i];
        var rect = h.rect;
        if (rect.width === 0 && rect.height === 0) continue;

        var color = PALETTE[h.index % PALETTE.length];
        var isDuplicate = analysis.duplicates.has(i);
        var isOverlap = analysis.overlaps.has(i);

        var box = document.createElement('div');
        box.className = 'highlight-box';
        box.style.left = rect.left + 'px';
        box.style.top = rect.top + 'px';
        box.style.width = rect.width + 'px';
        box.style.height = rect.height + 'px';

        if (isDuplicate) {
            box.style.borderColor = '#ef4444';
            box.style.borderStyle = 'dashed';
            box.style.background = 'rgba(239, 68, 68, 0.15)';
        } else if (isOverlap) {
            box.style.borderColor = '#eab308';
            box.style.borderStyle = 'dashed';
            box.style.background = 'rgba(234, 179, 8, 0.15)';
        } else {
            var r = parseInt(color.substring(1,3), 16);
            var g = parseInt(color.substring(3,5), 16);
            var b = parseInt(color.substring(5,7), 16);
            box.style.borderColor = color;
            box.style.background = 'rgba(' + r + ',' + g + ',' + b + ',0.2)';
        }

        overlay.appendChild(box);

        // Tooltip
        var tooltip = document.createElement('div');
        tooltip.className = 'highlight-tooltip';
        var label = h.locator.type + '(' + h.locator.value + ')';
        if (isDuplicate) label = 'DUPLICATE: ' + label;
        else if (isOverlap) label = 'OVERLAP: ' + label;
        tooltip.textContent = label;
        tooltip.style.left = rect.left + 'px';
        tooltip.style.top = Math.max(0, rect.top - 22) + 'px';

        if (isDuplicate) {
            tooltip.style.borderColor = '#ef4444';
        } else if (isOverlap) {
            tooltip.style.borderColor = '#eab308';
        } else {
            tooltip.style.borderColor = color;
        }

        overlay.appendChild(tooltip);
    }
}
