import { type Page, type Locator } from '@playwright/test';

export class LoginPage {
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly errorMessage: Locator;
  // Intentionally unmatched locator — used by the plugin's UI tests
  // (GutterAnnotationUiTest UT-18) to exercise the "0 matches" gutter
  // badge path. Never referenced by the Playwright test itself.
  readonly nonexistentElement: Locator;

  constructor(private readonly page: Page) {
    this.usernameInput = page.locator('#username');
    this.passwordInput = page.locator('#password');
    this.loginButton = page.locator('button[type="submit"]');
    this.errorMessage = page.locator('#flash.error');
    this.nonexistentElement = page.locator('#pagemirror-test-nonexistent-element-zzz');
  }

  async goto() {
    await this.page.goto('http://localhost:8089/login.html');
  }

  async login(user: string, pass: string) {
    await this.usernameInput.fill(user);
    await this.passwordInput.fill(pass);
    await this.loginButton.click();
  }

  async getError(): Promise<string | null> {
    return await this.errorMessage.textContent();
  }
}
