import * as path from 'path';
import { test, expect } from '@playwright/test';
import { LoginPage } from '../page-objects/login.page';
import { saveSnapshot } from 'playwright-snapshot-saver';

const snapshotsDir = path.join(__dirname, '..', '.snapshots');

test.describe('Login Page', () => {
  test('captures initial and error-state snapshots', async ({ page }) => {
    const loginPage = new LoginPage(page);

    await loginPage.goto();
    await expect(loginPage.usernameInput).toBeVisible();
    await saveSnapshot(page, {
      outputDir: snapshotsDir,
      group: 'login',
      name: 'initial',
    });

    await loginPage.login('bad-user', 'bad-pass');
    await expect(loginPage.errorMessage).toBeVisible();
    await saveSnapshot(page, {
      outputDir: snapshotsDir,
      group: 'login',
      name: 'error-state',
    });
  });
});
