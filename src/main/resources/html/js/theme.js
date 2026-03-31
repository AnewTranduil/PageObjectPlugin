// theme.js — Theme and color management

window.setTheme = function(theme) {
    document.body.classList.remove('theme-dark', 'theme-light');
    document.body.classList.add(theme === 'light' ? 'theme-light' : 'theme-dark');
};

window.setHighlightColor = function(color) {
    var style = document.getElementById('dynamic-highlight-style');
    if (!style) {
        style = document.createElement('style');
        style.id = 'dynamic-highlight-style';
        document.head.appendChild(style);
    }
    var r = parseInt(color.substring(1,3), 16);
    var g = parseInt(color.substring(3,5), 16);
    var b = parseInt(color.substring(5,7), 16);
    style.textContent = '.highlight-box { border-color: ' + color + ' !important; background: rgba(' + r + ',' + g + ',' + b + ',0.3) !important; } .highlight-tooltip { border-color: ' + color + ' !important; }';
};
