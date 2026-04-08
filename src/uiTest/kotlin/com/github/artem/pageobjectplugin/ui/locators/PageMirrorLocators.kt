package com.github.artem.pageobjectplugin.ui.locators

import com.intellij.remoterobot.search.locators.Locator
import com.intellij.remoterobot.search.locators.byXpath

/**
 * Page Mirror plugin UI locators. Centralizing them here means
 * fixtures contain no inline `byXpath(...)` calls — `grep -rE
 * 'byXpath\(' src/uiTest/.../fixtures/` should return zero matches
 * after Task 13d completes.
 */
object PageMirrorLocators {

    // ── Tool window ──────────────────────────────────────────────────────────

    val toolWindowDecorator: Locator = byXpath(
        "//div[@class='InternalDecoratorImpl' and contains(@accessiblename, 'Page Mirror')]"
    )

    val toolWindowCombo: Locator =
        byXpath(".//div[@class='JComboBox' or @class='ComboBox']")

    val toolWindowRefreshButton: Locator =
        byXpath(".//div[@class='JButton' and @text='Refresh']")

    // ── JCEF browser candidates ──────────────────────────────────────────────
    // Tried in order; the first match wins. Different IntelliJ versions and
    // platforms (mac/linux) expose the JCEF view under different class names.

    val jcefBrowserCandidates: List<Locator> = listOf(
        byXpath("//div[@class='JBCefOsrComponent']"),
        byXpath("//div[contains(@class, 'JBCefBrowser')]"),
        byXpath("//div[@class='CefBrowserWr']"),
    )

    val jcefBrowserInsideContainer: List<Locator> = listOf(
        byXpath(".//div[@class='JBCefOsrComponent']"),
        byXpath(".//div[contains(@class, 'JBCefBrowser')]"),
        byXpath(".//div[@class='CefBrowserWr']"),
    )

    /** Combined locator used by lightweight visibility checks. */
    val jcefBrowserAny: Locator = byXpath(
        ".//div[@class='JBCefOsrComponent' " +
            "or @class='CefBrowserWr' or contains(@class, 'JBCefBrowser')]"
    )

    // ── Settings dialog ──────────────────────────────────────────────────────

    val settingsPageMirrorPanel: Locator = byXpath(
        "//div[@class='DialogRootPane']//div[@accessiblename='Page Mirror']"
    )

    /** Generic JSpinner inside the dialog body — used as the "dialog ready" probe. */
    val settingsAnySpinner: Locator =
        byXpath("//div[@class='DialogRootPane']//div[@class='JSpinner']")

    val settingsSearchDepthSpinner: Locator =
        byXpath(".//div[@class='JSpinner']")

    val settingsSearchDepthField: Locator =
        byXpath(".//div[@class='JSpinner']//div[@class='JFormattedTextField']")

    val settingsAutoReloadCheckbox: Locator =
        byXpath(".//div[@class='JCheckBox']")

    val settingsHighlightColorField: Locator =
        byXpath(".//div[@class='JTextField']")

    val settingsCodeGenCombo: Locator =
        byXpath(".//div[@class='JComboBox']")

    // ── Status bar ───────────────────────────────────────────────────────────

    val statusBarWidgetById: Locator =
        byXpath("//div[@class='IdeStatusBarImpl']//div[@id='PageMirrorStatus']")

    val statusBarWidgetByText: Locator =
        byXpath("//div[@class='IdeStatusBarImpl']//div[contains(@text, 'Page Mirror')]")
}
