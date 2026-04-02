import * as path from 'node:path';
import { test, expect } from '@playwright/test';
import { LoginPage } from '../page-objects/login.page';
import { snapshot } from 'playwright-snapshot-saver';

const snapshotsDir = path.join(__dirname, '..', '.snapshots');

test.describe('Login Page', () => {
  test('captures initial and error-state snapshots', async ({ page }) => {
    const loginPage = new LoginPage(page);

    await loginPage.goto();
    await expect(loginPage.usernameInput).toBeVisible();
    await snapshot({page:'login', state:'initial'})

    await loginPage.login('bad-user', 'bad-pass');
    await expect(loginPage.errorMessage).toBeVisible();
    await snapshot({page: 'login', state: 'error-state'})
  });
});
