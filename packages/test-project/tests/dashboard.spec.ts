import * as path from 'node:path';
import { test, expect } from '@playwright/test';
import { DashboardPage } from '../page-objects/dashboard.page';
import { snapshot } from 'playwright-snapshot-saver';

const snapshotsDir = path.join(__dirname, '..', '.snapshots');

test.describe('Dashboard', () => {
  test('captures empty and filled ticket-form snapshots', async ({ page }) => {
    const dashboard = new DashboardPage(page);

    await dashboard.goto();
    await expect(dashboard.heading).toBeVisible();
    await expect(dashboard.projectsTable).toBeVisible();
    await snapshot({ page: 'dashboard', state: 'initial' });

    await dashboard.fillTicket(
      'Login flow rejects valid credentials',
      'high',
      'Steps: 1) enter tomsmith/SuperSecretPassword! 2) click Login. Expected: redirect to /secure. Actual: stays on /login with empty flash.',
    );
    await expect(dashboard.ticketTitleInput).toHaveValue(
      'Login flow rejects valid credentials',
    );
    await snapshot({ page: 'dashboard', state: 'ticket-filled' });
  });
});
