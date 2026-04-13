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

    val toolWindowShowAllButton: Locator =
        byXpath(".//div[@class='JButton' and contains(@text, 'Show All')]")

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
    //
    // All four testable fields have explicit accessible names set by
    // PageMirrorConfigurable (see main/...settings/PageMirrorConfigurable.kt)
    // so locators do not depend on IntelliJ UI DSL's labelFor propagation or
    // on the concrete runtime class (JBIntSpinner, JBTextField, JBCheckBox,
    // ComboBox, ...). Accessible names are standard Swing accessibility —
    // setting them is a no-cost improvement that also helps screen readers.

    val settingsPageMirrorPanel: Locator = byXpath(
        "//div[@class='DialogRootPane']//div[@accessiblename='Page Mirror']"
    )

    /**
     * "Dialog ready" probe — the spinner is the first Page Mirror-specific
     * control that appears inside the dialog body, so we poll for it to
     * confirm the dialog finished rendering.
     */
    val settingsAnySpinner: Locator = byXpath(
        "//div[@class='DialogRootPane']//div[@accessiblename='Snapshot search depth']"
    )

    val settingsSearchDepthSpinner: Locator =
        byXpath(".//div[@accessiblename='Snapshot search depth']")

    /**
     * The editable text field inside the spinner. IntelliJ's JBIntSpinner
     * uses a JFormattedTextField for the value editor; we target it by class
     * name to click/focus it before typing a new value.
     */
    val settingsSearchDepthField: Locator = byXpath(
        ".//div[@accessiblename='Snapshot search depth']//div[contains(@class,'FormattedTextField')]"
    )

    val settingsAutoReloadCheckbox: Locator =
        byXpath(".//div[@accessiblename='Auto-reload on file change']")

    val settingsHighlightColorField: Locator =
        byXpath(".//div[@accessiblename='Highlight color']")

    val settingsCodeGenCombo: Locator =
        byXpath(".//div[@accessiblename='Code generation style']")

    // ── Status bar ───────────────────────────────────────────────────────────

    val statusBarWidgetById: Locator =
        byXpath("//div[@class='IdeStatusBarImpl']//div[@id='PageMirrorStatus']")

    val statusBarWidgetByText: Locator =
        byXpath("//div[@class='IdeStatusBarImpl']//div[contains(@text, 'Page Mirror')]")
}
