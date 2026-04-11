(function () {
  var data = window.__TRACE_DATA__;
  if (!data) {
    document.body.textContent = 'No trace data.';
    return;
  }

  // ── DOM refs ─────────────────────────────────────────────
  var headerEl = document.getElementById('summary');
  var testListEl = document.getElementById('test-list');
  var screenshotPaneEl = document.getElementById('screenshot-pane');
  var dividerEl = document.getElementById('pane-divider');
  var tabsEl = document.getElementById('tabs');
  var tabListEl = document.getElementById('tab-list');
  var tabBodyEl = document.getElementById('tab-body');
  var stepListEl = document.getElementById('step-list');

  // ── State ────────────────────────────────────────────────
  var selectedTestIdx = 0;
  var selectedStepIdx = 0;
  var selectedTab = 'dom';          // 'dom' | 'failure'
  var bottomPaneHeight = 260;       // px, controlled by the divider drag
  var expandedGroups = {};

  // ── Feature grouping ─────────────────────────────────────
  var groups = [];
  var groupIndexByName = {};
  data.tests.forEach(function (t, i) {
    var name = t.feature || data.feature || 'Ungrouped';
    if (!(name in groupIndexByName)) {
      groupIndexByName[name] = groups.length;
      groups.push({ name: name, tests: [] });
    }
    groups[groupIndexByName[name]].tests.push({ test: t, index: i });
  });

  function groupNameForTestIdx(idx) {
    var t = data.tests[idx];
    if (!t) return null;
    return t.feature || data.feature || 'Ungrouped';
  }

  // Collapse everything by default; expand the group containing the initial test.
  var initialGroup = groupNameForTestIdx(selectedTestIdx);
  if (initialGroup != null) expandedGroups[initialGroup] = true;

  // ── Header ───────────────────────────────────────────────
  function renderHeader() {
    var totalSteps = data.tests.reduce(function (acc, t) {
      return acc + (t.steps || []).length;
    }, 0);
    var failures = data.tests.filter(function (t) { return t.status === 'failed'; }).length;
    headerEl.innerHTML =
      '<span class="tag">' + escapeHtml(data.feature) + '</span>' +
      '<span>' + data.tests.length + ' tests</span>' +
      '<span>' + totalSteps + ' steps</span>' +
      '<span>' + failures + ' failures</span>' +
      '<span class="sha">' + escapeHtml(data.gitSha || '') + '</span>';
  }

  // ── Left sidebar: test list grouped by feature ──────────
  function renderTestList() {
    testListEl.innerHTML = '';
    groups.forEach(function (g) {
      var groupEl = document.createElement('div');
      var collapsed = !expandedGroups[g.name];
      groupEl.className = 'feature-group' + (collapsed ? ' collapsed' : '');

      var gHeader = document.createElement('div');
      gHeader.className = 'feature-header';
      gHeader.innerHTML =
        '<span class="caret">' + (collapsed ? '\u25B6' : '\u25BC') + '</span>' +
        '<span class="name">' + escapeHtml(g.name) + '</span>' +
        '<span class="count">' + g.tests.length + '</span>';
      gHeader.onclick = function () {
        expandedGroups[g.name] = !expandedGroups[g.name];
        renderTestList();
      };
      groupEl.appendChild(gHeader);

      var testsEl = document.createElement('div');
      testsEl.className = 'feature-tests';
      g.tests.forEach(function (entry) {
        var t = entry.test;
        var i = entry.index;
        var el = document.createElement('div');
        el.className = 'test-item' + (i === selectedTestIdx ? ' selected' : '');
        el.innerHTML =
          '<span class="status ' + t.status + '"></span>' +
          '<span class="test-name">' + escapeHtml(t.displayName || t.method) + '</span>';
        el.onclick = function (ev) {
          ev.stopPropagation();
          selectedTestIdx = i;
          selectedStepIdx = 0;
          expandedGroups[g.name] = true;
          selectedTab = pickDefaultTab(data.tests[selectedTestIdx]);
          renderAll();
        };
        testsEl.appendChild(el);
      });
      groupEl.appendChild(testsEl);
      testListEl.appendChild(groupEl);
    });
  }

  // ── Right sidebar: step timeline ────────────────────────
  function renderStepList() {
    stepListEl.innerHTML = '';
    var test = data.tests[selectedTestIdx];
    var steps = test.steps || [];

    var heading = document.createElement('div');
    heading.className = 'step-list-heading';
    heading.textContent = 'Steps (' + steps.length + ')';
    stepListEl.appendChild(heading);

    if (steps.length === 0) {
      var empty = document.createElement('div');
      empty.className = 'pane-empty';
      empty.textContent = 'No steps recorded.';
      stepListEl.appendChild(empty);
      return;
    }

    steps.forEach(function (s, i) {
      var el = document.createElement('div');
      el.className = 'step' +
        (i === selectedStepIdx ? ' selected' : '') +
        (s.error ? ' has-error' : '');
      el.innerHTML =
        '<div class="step-index">' + (s.index || (i + 1)) + '</div>' +
        '<div class="step-body">' +
          '<div class="label">' + escapeHtml(s.label) + '</div>' +
          '<div class="dur">' + s.durationMs + 'ms</div>' +
        '</div>';
      el.onclick = function () {
        selectedStepIdx = i;
        renderStepList();
        renderScreenshot();
      };
      stepListEl.appendChild(el);
    });
  }

  // ── Center top: screenshot pane ─────────────────────────
  function renderScreenshot() {
    var test = data.tests[selectedTestIdx];
    var step = (test.steps || [])[selectedStepIdx];
    screenshotPaneEl.innerHTML = '';

    if (step && step.screenshotDataUri) {
      var img = document.createElement('img');
      img.src = step.screenshotDataUri;
      img.alt = 'step screenshot';
      img.title = 'Click to view at full resolution';
      img.onclick = function () { openLightbox(step.screenshotDataUri); };
      screenshotPaneEl.appendChild(img);
    } else {
      var empty = document.createElement('div');
      empty.className = 'pane-empty';
      empty.textContent = step
        ? 'No screenshot recorded for this step.'
        : 'No step selected.';
      screenshotPaneEl.appendChild(empty);
    }
  }

  // ── Center bottom: tabs (DOM snapshot / Failure) ───────
  function pickDefaultTab(test) {
    if (test && test.failure) return 'failure';
    if (test && test.domHtmlDataUri) return 'dom';
    return 'dom';
  }

  function renderTabs() {
    var test = data.tests[selectedTestIdx];
    var hasDom = !!test.domHtmlDataUri;
    var hasFailure = !!test.failure;

    // If neither tab has anything, hide the entire bottom section.
    if (!hasDom && !hasFailure) {
      tabsEl.classList.add('hidden');
      dividerEl.classList.add('hidden');
      return;
    }
    tabsEl.classList.remove('hidden');
    dividerEl.classList.remove('hidden');
    tabsEl.style.height = bottomPaneHeight + 'px';

    // Fall back to an enabled tab if the current one is empty for this test.
    if (selectedTab === 'dom' && !hasDom) selectedTab = hasFailure ? 'failure' : 'dom';
    if (selectedTab === 'failure' && !hasFailure) selectedTab = hasDom ? 'dom' : 'failure';

    var specs = [
      { id: 'dom', label: 'DOM Snapshot', enabled: hasDom },
      { id: 'failure', label: 'Failure', enabled: hasFailure },
    ];

    tabListEl.innerHTML = '';
    specs.forEach(function (spec) {
      var btn = document.createElement('button');
      btn.className = 'tab-button' + (spec.id === selectedTab ? ' active' : '');
      btn.textContent = spec.label;
      btn.disabled = !spec.enabled;
      btn.onclick = function () {
        if (!spec.enabled) return;
        selectedTab = spec.id;
        renderTabs();
      };
      tabListEl.appendChild(btn);
    });

    tabBodyEl.innerHTML = '';
    if (selectedTab === 'dom' && hasDom) {
      var iframe = document.createElement('iframe');
      iframe.src = test.domHtmlDataUri;
      iframe.setAttribute('sandbox', '');
      tabBodyEl.appendChild(iframe);
    } else if (selectedTab === 'failure' && hasFailure) {
      var pre = document.createElement('pre');
      pre.textContent = test.failure.stack;
      tabBodyEl.appendChild(pre);
    }
  }

  // ── Lightbox (full-resolution zoom) ─────────────────────
  function openLightbox(dataUri) {
    var overlay = document.createElement('div');
    overlay.className = 'lightbox-overlay';

    var img = document.createElement('img');
    img.className = 'lightbox-img fit';
    img.src = dataUri;
    img.alt = 'screenshot';
    overlay.appendChild(img);

    var hint = document.createElement('div');
    hint.className = 'lightbox-hint';
    hint.textContent = 'Fit to viewport · Click image for 100% · Esc to close';
    overlay.appendChild(hint);

    function close() {
      document.removeEventListener('keydown', onKey);
      overlay.remove();
    }
    function onKey(e) { if (e.key === 'Escape') close(); }

    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) close();
    });
    img.addEventListener('click', function (e) {
      e.stopPropagation();
      if (img.classList.contains('fit')) {
        img.classList.remove('fit');
        img.classList.add('actual');
        hint.textContent = '100% (native pixels) · Click image to fit · Esc to close';
      } else {
        img.classList.remove('actual');
        img.classList.add('fit');
        hint.textContent = 'Fit to viewport · Click image for 100% · Esc to close';
      }
    });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(overlay);
  }

  // ── Divider resize ─────────────────────────────────────
  // Dragging the handle UP shrinks the tabs pane (grows the screenshot),
  // dragging DOWN grows the tabs pane.
  function attachResizer() {
    dividerEl.addEventListener('mousedown', function (e) {
      e.preventDefault();
      var startY = e.clientY;
      var startHeight = tabsEl.getBoundingClientRect().height;
      document.body.classList.add('resizing-vert');

      function onMove(ev) {
        var delta = startY - ev.clientY;
        var next = Math.max(40, Math.min(1500, startHeight + delta));
        bottomPaneHeight = next;
        tabsEl.style.height = next + 'px';
      }
      function onUp() {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.classList.remove('resizing-vert');
      }
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  }

  function renderAll() {
    renderTestList();
    renderStepList();
    renderScreenshot();
    renderTabs();
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // ── Init ───────────────────────────────────────────────
  selectedTab = pickDefaultTab(data.tests[selectedTestIdx]);
  renderHeader();
  attachResizer();
  renderAll();
})();
