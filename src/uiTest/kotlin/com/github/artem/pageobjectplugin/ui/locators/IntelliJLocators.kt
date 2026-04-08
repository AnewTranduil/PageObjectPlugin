package com.github.artem.pageobjectplugin.ui.locators

import com.intellij.remoterobot.search.locators.Locator
import com.intellij.remoterobot.search.locators.byXpath

/**
 * XPath locators for stock IntelliJ chrome (frame, gutter, status bar, dialogs).
 *
 * No plugin-specific strings live here — that's what [PageMirrorLocators] is for.
 * Keeping the two namespaces separate means a future Page Mirror UI rename only
 * touches one file.
 */
object IntelliJLocators {

    val ideFrame: Locator = byXpath("//div[@class='IdeFrameImpl']")

    val editorGutter: Locator = byXpath("//div[@class='EditorGutterComponentImpl']")

    val editorComponent: Locator = byXpath("//div[@class='EditorComponentImpl']")

    val statusBar: Locator = byXpath("//div[@class='IdeStatusBarImpl']")

    val dialogRoot: Locator = byXpath("//div[@class='DialogRootPane']")

    val dialogOk: Locator = byXpath("//div[@class='DialogRootPane']//div[@text='OK']")

    val dialogApply: Locator = byXpath("//div[@class='DialogRootPane']//div[@text='Apply']")

    val dialogCancel: Locator = byXpath("//div[@class='DialogRootPane']//div[@text='Cancel']")

    /** Cancel button relative to a containing dialog (used in cleanup loops). */
    val dialogCancelRelative: Locator = byXpath(".//div[@text='Cancel']")
}
