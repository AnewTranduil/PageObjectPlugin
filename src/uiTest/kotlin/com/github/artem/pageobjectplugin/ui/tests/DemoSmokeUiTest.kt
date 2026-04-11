package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import org.junit.jupiter.api.Test

/**
 * Minimal two-scenario fixture used to exercise the `demoReport` pipeline
 * end-to-end. Both scenarios are trivial — they exist to produce two tagged
 * trace bundles so the >=2-scenarios rule is satisfied.
 */
@Feature("smoke")
class DemoSmokeUiTest : BaseUiTest() {

    @Test
    fun happyPath() {
        takeScreenshot("smoke happy path")
    }

    @Test
    fun negativePath() {
        takeScreenshot("smoke negative path")
    }
}
