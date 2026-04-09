package com.github.artem.pageobjectplugin.ui.annotations

/**
 * Tags a UI test (or a whole test class) with a feature name. Consumed by
 * `FeatureTagListener` — the tag is written into `trace.json` and used by the
 * `demoReport` Gradle task to select tests for the PR demo bundle.
 *
 * Method-level `@Feature` wins over a class-level one on the same test.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Feature(val tag: String)
