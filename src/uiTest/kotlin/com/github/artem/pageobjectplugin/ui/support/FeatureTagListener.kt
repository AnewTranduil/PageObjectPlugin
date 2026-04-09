package com.github.artem.pageobjectplugin.ui.support

import com.github.artem.pageobjectplugin.ui.annotations.Feature
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace

/**
 * Reads `@Feature` from the current test method (preferred) or declaring class
 * and stores the tag under [NS]/[KEY] so [TraceBundleExtension] can embed it in
 * `trace.json`. Method-level wins over class-level.
 */
class FeatureTagListener : BeforeEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        val tag = resolveTag(context) ?: return
        context.getStore(NS).put(KEY, tag)
    }

    private fun resolveTag(context: ExtensionContext): String? {
        val methodTag = context.testMethod
            .map { it.getAnnotation(Feature::class.java) }
            .orElse(null)
            ?.tag
        if (methodTag != null) return methodTag
        return context.testClass
            .map { it.getAnnotation(Feature::class.java) }
            .orElse(null)
            ?.tag
    }

    companion object {
        val NS: Namespace = Namespace.create(FeatureTagListener::class.java)
        const val KEY: String = "featureTag"

        fun readTag(context: ExtensionContext): String? =
            context.getStore(NS).get(KEY) as? String
    }
}
