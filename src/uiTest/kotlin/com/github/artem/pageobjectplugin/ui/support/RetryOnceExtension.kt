package com.github.artem.pageobjectplugin.ui.support

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * JUnit 5 [InvocationInterceptor] that re-runs a failing test method exactly once.
 *
 * - On the first attempt we call [Invocation.proceed] normally.
 * - If it throws, we publish two report entries (`flaky=true`, `firstFailure=...`),
 *   stash a `flaky` flag in the JUnit store under this extension's namespace
 *   so [com.github.artem.pageobjectplugin.ui.support.TraceBundleExtension] can
 *   surface it in `trace.json`, then re-invoke the test method reflectively.
 * - JUnit's [Invocation] is consumed after [Invocation.proceed]; we cannot call
 *   it twice. Reflective re-invocation is the supported workaround.
 *
 * Caveat: only the test method body is re-run. `@BeforeEach` / `@AfterEach`
 * callbacks do NOT fire on the retry — JUnit limitation. Tests must keep
 * `@BeforeEach` effects idempotent (close dialogs, reset mutable state).
 */
class RetryOnceExtension : InvocationInterceptor {

    override fun interceptTestMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        try {
            invocation.proceed()
            return
        } catch (firstFailure: Throwable) {
            extensionContext.publishReportEntry("flaky", "true")
            extensionContext.publishReportEntry(
                "firstFailure",
                "${firstFailure.javaClass.simpleName}: ${firstFailure.message}",
            )
            extensionContext.getStore(NAMESPACE).put(FLAKY_KEY, true)

            try {
                val target = invocationContext.target.orElseThrow {
                    IllegalStateException("RetryOnceExtension: no test instance available for retry")
                }
                val method = invocationContext.executable
                method.isAccessible = true
                method.invoke(target, *invocationContext.arguments.toTypedArray())
            } catch (e: Throwable) {
                val cause = (e as? InvocationTargetException)?.targetException ?: e
                throw cause
            }
        }
    }

    companion object {
        val NAMESPACE: Namespace = Namespace.create(RetryOnceExtension::class.java)
        const val FLAKY_KEY: String = "flaky"
    }
}
