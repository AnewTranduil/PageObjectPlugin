(function () {
  var data = window.__TRACE_DATA__;
  if (!data) {
    document.body.textContent = 'No trace data.';
    return;
  }

  var header = document.getElementById('summary');
  var totalSteps = data.tests.reduce(function (acc, t) { return acc + (t.steps || []).length; }, 0);
  var failures = data.tests.filter(function (t) { return t.status === 'failed'; }).length;
  header.innerHTML =
    '<span class="tag">' + escapeHtml(data.feature) + '</span>' +
    '<span>' + data.tests.length + ' tests</span>' +
    '<span>' + totalSteps + ' steps</span>' +
    '<span>' + failures + ' failures</span>' +
    '<span class="sha">' + escapeHtml(data.gitSha || '') + '</span>';

  var listEl = document.getElementById('test-list');
  var timelineEl = document.getElementById('timeline');
  var detailsEl = document.getElementById('details');

  // Group tests by feature, preserving the order in which features first appear.
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

  var selectedTestIdx = 0;
  var selectedStepIdx = 0;

  // Collapsed by default; auto-expand the group containing the initial selection.
  var expandedGroups = {};
  var initialGroup = groupNameForTestIdx(selectedTestIdx);
  if (initialGroup != null) expandedGroups[initialGroup] = true;

  // Resizer state for the screenshot pane (px). Persisted in-memory only.
  var screenshotPaneHeight = 320;

  function groupNameForTestIdx(idx) {
    var t = data.tests[idx];
    if (!t) return null;
    return t.feature || data.feature || 'Ungrouped';
  }

  function render() {
    listEl.innerHTML = '';
    groups.forEach(function (g) {
      var groupEl = document.createElement('div');
      var collapsed = !expandedGroups[g.name];
      groupEl.className = 'feature-group' + (collapsed ? ' collapsed' : '');

      var headerEl = document.createElement('div');
      headerEl.className = 'feature-header';
      headerEl.innerHTML =
        '<span class="caret">' + (collapsed ? '\u25B6' : '\u25BC') + '</span>' +
        '<span class="name">' + escapeHtml(g.name) + '</span>' +
        '<span class="count">' + g.tests.length + '</span>';
      headerEl.onclick = function () {
        expandedGroups[g.name] = !expandedGroups[g.name];
        render();
      };
      groupEl.appendChild(headerEl);

      var testsEl = document.createElement('div');
      testsEl.className = 'feature-tests';
      g.tests.forEach(function (entry) {
        var t = entry.test;
        var i = entry.index;
        var el = document.createElement('div');
        el.className = 'test-item' + (i === selectedTestIdx ? ' selected' : '');
        el.innerHTML =
          '<span class="status ' + t.status + '"></span>' +
          escapeHtml(t.displayName || t.method);
        el.onclick = function (ev) {
          ev.stopPropagation();
          selectedTestIdx = i;
          selectedStepIdx = 0;
          // Make sure the group containing the selected test stays expanded.
          expandedGroups[g.name] = true;
          render();
        };
        testsEl.appendChild(el);
      });
      groupEl.appendChild(testsEl);

      listEl.appendChild(groupEl);
    });

    var test = data.tests[selectedTestIdx];
    timelineEl.innerHTML = '';
    (test.steps || []).forEach(function (s, i) {
      var el = document.createElement('div');
      el.className = 'step' + (i === selectedStepIdx ? ' selected' : '') + (s.error ? ' has-error' : '');
      el.innerHTML =
        '<span class="label">' + escapeHtml(s.label) + '</span>' +
        '<span class="dur">' + s.durationMs + 'ms</span>';
      el.onclick = function () { selectedStepIdx = i; render(); };
      timelineEl.appendChild(el);
    });

    renderDetails(test);
  }

  function renderDetails(test) {
    detailsEl.innerHTML = '';
    var step = (test.steps || [])[selectedStepIdx];

    var screenshotPane = document.createElement('div');
    screenshotPane.className = 'screenshot-pane';
    screenshotPane.style.height = screenshotPaneHeight + 'px';
    if (step && step.screenshotDataUri) {
      var img = document.createElement('img');
      img.src = step.screenshotDataUri;
      img.alt = 'step screenshot';
      screenshotPane.appendChild(img);
    } else {
      var empty = document.createElement('div');
      empty.className = 'pane-empty';
      empty.textContent = 'No screenshot for this step.';
      screenshotPane.appendChild(empty);
    }
    detailsEl.appendChild(screenshotPane);

    var divider = document.createElement('div');
    divider.className = 'pane-divider';
    divider.title = 'Drag to resize screenshot';
    attachResizer(divider, screenshotPane);
    detailsEl.appendChild(divider);

    var domPane = document.createElement('div');
    domPane.className = 'dom-pane';
    if (test.failure) {
      var pre = document.createElement('pre');
      pre.textContent = test.failure.stack;
      domPane.appendChild(pre);
    }
    if (test.domHtmlDataUri) {
      var iframe = document.createElement('iframe');
      iframe.src = test.domHtmlDataUri;
      iframe.setAttribute('sandbox', '');
      domPane.appendChild(iframe);
    } else if (!test.failure) {
      var emptyDom = document.createElement('div');
      emptyDom.className = 'pane-empty';
      emptyDom.textContent = 'No DOM snapshot for this test.';
      domPane.appendChild(emptyDom);
    }
    detailsEl.appendChild(domPane);
  }

  function attachResizer(handle, screenshotPane) {
    handle.addEventListener('mousedown', function (e) {
      e.preventDefault();
      var startY = e.clientY;
      var startHeight = screenshotPane.getBoundingClientRect().height;
      document.body.classList.add('resizing-vert');

      function onMove(ev) {
        var delta = ev.clientY - startY;
        var next = Math.max(80, Math.min(2000, startHeight + delta));
        screenshotPaneHeight = next;
        screenshotPane.style.height = next + 'px';
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

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  render();
})();
