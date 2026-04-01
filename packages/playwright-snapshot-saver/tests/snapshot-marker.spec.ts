import { test, expect } from '@playwright/test';
import { snapshot } from '../src/snapshot-marker';

test.describe('snapshot marker', () => {
  test('creates a test step with correct label using default state', async () => {
    // snapshot() calls test.step(`[snapshot:login/main]`, ...) — resolves without error
    await expect(snapshot({ page: 'login' })).resolves.toBeUndefined();
  });

  test('creates a test step with custom state', async () => {
    // snapshot() calls test.step(`[snapshot:login/error]`, ...) — resolves without error
    await expect(snapshot({ page: 'login', state: 'error' })).resolves.toBeUndefined();
  });

  test('rejects empty page string', async () => {
    await expect(snapshot({ page: '' })).rejects.toThrow('page is required');
  });

  test('rejects invalid characters in page', async () => {
    await expect(snapshot({ page: 'my page/bad' })).rejects.toThrow(
      'page must contain only alphanumeric characters, hyphens, and underscores'
    );
  });

  test('rejects invalid characters in state', async () => {
    await expect(snapshot({ page: 'login', state: 'bad state!' })).rejects.toThrow(
      'state must contain only alphanumeric characters, hyphens, and underscores'
    );
  });
});
