package com.github.artem.pageobjectplugin.services

import com.intellij.openapi.diagnostic.logger
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

private val LOG = logger<SnapshotHtmlResolver>()

/**
 * Pure helper that inlines the plugin's "local" v2 bundle resources
 * into the index.html body before the text reaches JCEF.
 *
 * Why this exists: a v2 snapshot bundle writes CSS as sidecar files
 * (`resources/<sha1>.css`) referenced by `<link rel="stylesheet"
 * href="resources/<sha1>.css">`. The plugin renders snapshot HTML via
 * `iframe.srcdoc = html` (see `resources/html/js/snapshot.js:39`),
 * which gives the iframe an `about:srcdoc` base URL. That base URL
 * cannot resolve relative paths on the host filesystem, so without
 * this pass sidecar CSS would silently 404.
 *
 * The fix is to read each sidecar before JS handoff and substitute
 * the `<link>` with a `<style>` block containing the CSS text. Inline
 * style tags on the Kotlin side are identical to what the old v1
 * html-inliner produced, so `snapshot.js` sees unchanged input.
 *
 * Missing sidecars (e.g. a partial / corrupted bundle) are logged and
 * dropped from the rendered HTML rather than left as dead `<link>`
 * tags, so the iframe never sees a broken reference.
 */
object SnapshotHtmlResolver {

    /**
     * Read `htmlPath`, inline every `<link rel="stylesheet"
     * href="resources/...">` sidecar that lives under `resourcesDir`,
     * and return the serialized HTML. Non-local `<link>` tags (absolute
     * URLs, other relative paths) are left untouched — they were valid
     * before this pass and remain valid after.
     *
     * If `resourcesDir` is null, only the HTML is read and returned
     * verbatim — the bundle simply has no sidecar resources.
     */
    fun loadResolved(htmlPath: Path, resourcesDir: Path?): String {
        val html = htmlPath.readText()
        if (resourcesDir == null) return html
        return inlineLocalResources(html, resourcesDir)
    }

    /**
     * Inline all `<link rel="stylesheet" href="resources/...">` tags
     * as `<style>` blocks, reading the sidecars from `resourcesDir`.
     * Public for unit testing.
     */
    fun inlineLocalResources(html: String, resourcesDir: Path): String {
        val doc: Document = Jsoup.parse(html)
        val links = doc.select("link[rel=stylesheet]")
        if (links.isEmpty()) return html

        var mutated = false
        for (link in links) {
            val href = link.attr("href")
            val localName = extractLocalResourceName(href) ?: continue
            val sidecar = resourcesDir.resolve(localName)
            if (!sidecar.exists()) {
                LOG.warn("Snapshot bundle references missing sidecar: $sidecar; removing <link>")
                link.remove()
                mutated = true
                continue
            }
            val css = try {
                sidecar.readText()
            } catch (e: Exception) {
                LOG.warn("Failed to read snapshot sidecar $sidecar", e)
                link.remove()
                mutated = true
                continue
            }
            // Use a DataNode for the CSS body so Jsoup doesn't HTML-escape
            // characters like `>` in descendant selectors — `.parent >
            // .child { ... }` must survive the serialization round trip.
            val style = doc.createElement("style")
            style.appendChild(DataNode(css))
            link.replaceWith(style)
            mutated = true
        }
        return if (mutated) {
            // Disable Jsoup's pretty-printing — we want to preserve the
            // original whitespace as much as possible so the iframe's
            // layout matches what the saver captured.
            doc.outputSettings().prettyPrint(false)
            doc.outerHtml()
        } else {
            html
        }
    }

    /**
     * Returns the basename of `href` if it points inside `resources/`
     * (our local sidecar convention), or null for absolute URLs and
     * anything else that shouldn't be rewritten.
     */
    private fun extractLocalResourceName(href: String): String? {
        if (href.isEmpty()) return null
        if (href.contains("://")) return null
        val trimmed = href.trimStart('.', '/')
        if (!trimmed.startsWith("resources/")) return null
        val name = trimmed.removePrefix("resources/")
        // Reject path traversal attempts or nested subdirectories.
        if (name.contains('/') || name.contains("..")) return null
        return name
    }
}
