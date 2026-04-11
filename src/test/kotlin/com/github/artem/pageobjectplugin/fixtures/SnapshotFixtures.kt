package com.github.artem.pageobjectplugin.fixtures

import com.github.artem.pageobjectplugin.model.SnapshotBundle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

object SnapshotFixtures {

    /** Minimal HTML with known selectors for predictable match counts in tests. */
    val MINIMAL_HTML = """
        <html><body>
          <input id="username" data-testid="login-username" placeholder="Username" type="text"/>
          <input id="password" data-testid="login-password" placeholder="Password" type="password"/>
          <button type="submit" data-testid="login-button" role="button">Login</button>
          <div id="flash" class="error">Bad credentials</div>
        </body></html>
    """.trimIndent()

    val LOGIN_PAGE_TS = """
        import { type Page } from '@playwright/test';
        export class LoginPage {
          constructor(private readonly page: Page) {}
          usernameInput = this.page.getByTestId('login-username');
          passwordInput = this.page.locator('#password');
          loginButton = this.page.getByRole('button', { name: 'Login' });
          errorMessage = this.page.getByText('Bad credentials');
        }
    """.trimIndent()

    /**
     * Minimal v2-compatible manifest.json for test fixtures. Keeps
     * SnapshotBundle.fromDirectory's version check happy without
     * claiming driver metadata the fixture doesn't have.
     */
    private val MINIMAL_MANIFEST_V2 = """
        {
          "version": 2,
          "url": "about:blank",
          "viewport": { "width": 1280, "height": 720 },
          "timestamp": "2026-04-11T00:00:00Z"
        }
    """.trimIndent()

    /** Creates a temp v2 snapshot dir with MINIMAL_HTML and returns the bundle. */
    fun createMinimalSnapshotDir(): SnapshotBundle {
        val dir = Files.createTempDirectory("pm-test-")
        dir.resolve("index.html").writeText(MINIMAL_HTML)
        dir.resolve("manifest.json").writeText(MINIMAL_MANIFEST_V2)
        return SnapshotBundle.fromDirectory(dir)!!
    }

    /** Creates a temp snapshot dir from real test resource snapshot files. */
    fun createLoginSnapshotDir(): SnapshotBundle {
        val resourceUrl = SnapshotFixtures::class.java.getResource("/testdata/snapshots/login/initial")
            ?: error("Test resource /testdata/snapshots/login/initial not found")
        val source = Path.of(resourceUrl.toURI())
        val dir = Files.createTempDirectory("pm-login-")
        Files.copy(source.resolve("index.html"), dir.resolve("index.html"))
        Files.copy(source.resolve("manifest.json"), dir.resolve("manifest.json"))
        return SnapshotBundle.fromDirectory(dir)!!
    }
}
