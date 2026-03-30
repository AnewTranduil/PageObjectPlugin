// query.js — iframe DOM querying

function getIframeDoc() {
    var iframe = document.querySelector('#viewport iframe');
    if (!iframe || !iframe.contentDocument) return null;
    return iframe.contentDocument;
}

function getOwnText(el) {
    var text = '';
    for (var i = 0; i < el.childNodes.length; i++) {
        if (el.childNodes[i].nodeType === 3) text += el.childNodes[i].textContent;
    }
    return text;
}

function queryIframeDom(type, value) {
    var doc = getIframeDoc();
    if (!doc) return [];
    try {
        switch (type) {
            case 'locator':
                return Array.from(doc.querySelectorAll(value));
            case 'getByTestId':
                return Array.from(doc.querySelectorAll('[data-testid="' + value + '"]'));
            case 'getByRole': {
                var parts = value.split(':');
                var role = parts[0];
                var name = parts.length > 1 ? parts.slice(1).join(':') : null;
                var els = Array.from(doc.querySelectorAll('[role="' + role + '"]'));
                if (name) {
                    var nameLower = name.toLowerCase();
                    els = els.filter(function(el) {
                        return el.textContent.toLowerCase().indexOf(nameLower) !== -1;
                    });
                }
                return els;
            }
            case 'getByText': {
                var textLower = value.toLowerCase();
                return Array.from(doc.querySelectorAll('*')).filter(function(el) {
                    return getOwnText(el).toLowerCase().indexOf(textLower) !== -1;
                });
            }
            case 'getByPlaceholder':
                return Array.from(doc.querySelectorAll('[placeholder="' + value + '"]'));
            default:
                return [];
        }
    } catch (e) {
        return [];
    }
}
