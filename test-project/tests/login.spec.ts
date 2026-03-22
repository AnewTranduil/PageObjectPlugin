import * as path from 'path';
import { test, expect } from '@playwright/test';
import { LoginPage } from '../page-objects/login.page';
import { saveState } from '../utils/save-state';

const snapshotsDir = path.join(__dirname, '..', '.snapshots', 'login');

test.describe('Login Page', () => {
  test('captures initial and error-state snapshots', async ({ page }) => {
    const loginPage = new LoginPage(page);

    await loginPage.goto();
    await expect(loginPage.usernameInput).toBeVisible();
    await saveState(page, 'initial', snapshotsDir);

    await loginPage.login('bad-user', 'bad-pass');
    await expect(loginPage.errorMessage).toBeVisible();
    await saveState(page, 'error-state', snapshotsDir);
  });
});
