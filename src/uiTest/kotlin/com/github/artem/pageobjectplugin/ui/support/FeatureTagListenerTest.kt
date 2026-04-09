package com.github.artem.pageobjectplugin.ui.support

import com.github.artem.pageobjectplugin.ui.annotations.Feature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.reflect.Method
import java.util.Optional

/**
 * Unit tests for [FeatureTagListener]'s tag resolution rules. Runs under the
 * `uiTest` source set (not `test`) because `FeatureTagListener`, `@Feature`,
 * and `ExtensionContext` are only on the uiTest classpath.
 */
class FeatureTagListenerTest {

    @Feature("class-feature")
    private class ClassTagged {
        fun plain() {}

        @Feature("method-feature")
        fun overridden() {}
    }

    private class Untagged {
        fun plain() {}
    }

    @Test
    fun `class-level @Feature is captured when method has no annotation`() {
        val listener = FeatureTagListener()
        val store = FakeStore()
        val method = ClassTagged::class.java.getDeclaredMethod("plain")
        val context = FakeContext(ClassTagged::class.java, method, store)

        listener.beforeEach(context)

        assertEquals("class-feature", store.map[FeatureTagListener.KEY])
        assertEquals("class-feature", FeatureTagListener.readTag(context))
    }

    @Test
    fun `method-level @Feature overrides class-level`() {
        val listener = FeatureTagListener()
        val store = FakeStore()
        val method = ClassTagged::class.java.getDeclaredMethod("overridden")
        val context = FakeContext(ClassTagged::class.java, method, store)

        listener.beforeEach(context)

        assertEquals("method-feature", store.map[FeatureTagListener.KEY])
        assertEquals("method-feature", FeatureTagListener.readTag(context))
    }

    @Test
    fun `untagged test leaves store empty and readTag returns null`() {
        val listener = FeatureTagListener()
        val store = FakeStore()
        val method = Untagged::class.java.getDeclaredMethod("plain")
        val context = FakeContext(Untagged::class.java, method, store)

        listener.beforeEach(context)

        assertNull(store.map[FeatureTagListener.KEY])
        assertNull(FeatureTagListener.readTag(context))
    }
}

private class FakeStore : ExtensionContext.Store {
    val map: MutableMap<Any, Any?> = mutableMapOf()

    override fun get(key: Any): Any? = map[key]
    override fun <V : Any?> get(key: Any, requiredType: Class<V>): V? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as V?
    }
    override fun <K : Any?, V : Any?> getOrComputeIfAbsent(
        key: K,
        defaultCreator: java.util.function.Function<K, V>
    ): Any {
        @Suppress("UNCHECKED_CAST")
        return map.getOrPut(key as Any) { defaultCreator.apply(key) as Any }!!
    }
    override fun <K : Any?, V : Any?> getOrComputeIfAbsent(
        key: K,
        defaultCreator: java.util.function.Function<K, V>,
        requiredType: Class<V>
    ): V {
        @Suppress("UNCHECKED_CAST")
        return map.getOrPut(key as Any) { defaultCreator.apply(key) as Any } as V
    }
    override fun put(key: Any, value: Any?) {
        map[key] = value
    }
    override fun remove(key: Any): Any? = map.remove(key)
    override fun <V : Any?> remove(key: Any, requiredType: Class<V>): V? {
        @Suppress("UNCHECKED_CAST")
        return map.remove(key) as V?
    }
}

private class FakeContext(
    private val clazz: Class<*>,
    private val method: Method,
    private val store: FakeStore
) : ExtensionContext {
    override fun getParent(): Optional<ExtensionContext> = Optional.empty()
    override fun getRoot(): ExtensionContext = this
    override fun getUniqueId(): String = "fake"
    override fun getDisplayName(): String = method.name
    override fun getTags(): MutableSet<String> = mutableSetOf()
    override fun getElement(): Optional<java.lang.reflect.AnnotatedElement> = Optional.of(method)
    override fun getTestClass(): Optional<Class<*>> = Optional.of(clazz)
    override fun getTestInstanceLifecycle(): Optional<org.junit.jupiter.api.TestInstance.Lifecycle> = Optional.empty()
    override fun getTestInstance(): Optional<Any> = Optional.empty()
    override fun getTestInstances(): Optional<org.junit.jupiter.api.extension.TestInstances> = Optional.empty()
    override fun getTestMethod(): Optional<Method> = Optional.of(method)
    override fun getExecutionException(): Optional<Throwable> = Optional.empty()
    override fun getConfigurationParameter(key: String): Optional<String> = Optional.empty()
    override fun <T : Any> getConfigurationParameter(
        key: String,
        transformer: java.util.function.Function<String, T>
    ): Optional<T> = Optional.empty<T>()
    override fun publishReportEntry(map: MutableMap<String, String>) {}
    override fun getStore(namespace: ExtensionContext.Namespace): ExtensionContext.Store = store
    override fun getExecutionMode(): org.junit.jupiter.api.parallel.ExecutionMode =
        org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD
    override fun getExecutableInvoker(): org.junit.jupiter.api.extension.ExecutableInvoker =
        throw UnsupportedOperationException()
}
