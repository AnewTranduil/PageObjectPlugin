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

  var selectedTestIdx = 0;
  var selectedStepIdx = 0;

  function render() {
    listEl.innerHTML = '';
    data.tests.forEach(function (t, i) {
      var el = document.createElement('div');
      el.className = 'test-item' + (i === selectedTestIdx ? ' selected' : '');
      el.innerHTML =
        '<span class="status ' + t.status + '"></span>' +
        escapeHtml(t.displayName || t.method);
      el.onclick = function () { selectedTestIdx = i; selectedStepIdx = 0; render(); };
      listEl.appendChild(el);
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

    var step = (test.steps || [])[selectedStepIdx];
    detailsEl.innerHTML = '';
    if (step && step.screenshotDataUri) {
      var img = document.createElement('img');
      img.src = step.screenshotDataUri;
      detailsEl.appendChild(img);
    }
    if (test.failure) {
      var pre = document.createElement('pre');
      pre.textContent = test.failure.stack;
      detailsEl.appendChild(pre);
    }
    if (test.domHtmlDataUri) {
      var iframe = document.createElement('iframe');
      iframe.src = test.domHtmlDataUri;
      iframe.style.width = '100%';
      iframe.style.height = '300px';
      iframe.setAttribute('sandbox', '');
      detailsEl.appendChild(iframe);
    }
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  render();
})();
