import { type Page, type Locator } from '@playwright/test';

export class LoginPage {
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly errorMessage: Locator;
  readonly nonexistent: Locator;

  constructor(private readonly page: Page) {
    this.usernameInput = page.locator('#username');
    this.passwordInput = page.locator('#password');
    this.loginButton = page.locator('button[type="submit"]');
    this.errorMessage = page.locator('#flash.error');
    // Appended at the END of the constructor so earlier locator lines
    // (10-13) stay at their original positions — HighlightBridgeUiTest
    // hardcodes those line numbers. This locator is intentionally
    // unmatched in any snapshot and gives GutterAnnotationUiTest UT-18
    // a guaranteed "0 matches" gutter badge to assert on.
    this.nonexistent = page.locator('#pagemirror-test-nonexistent-element-zzz');
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
